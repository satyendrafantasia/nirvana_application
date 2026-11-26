package com.nirvana.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvana.application.security.SecurityUtils;
import com.nirvana.application.security.StaffAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/owner/spas/{spa}/finance") @RequiredArgsConstructor
@Transactional
public class FinancialReports {
    private final OwnerFinance finance;
    private final JdbcTemplate db;
    private final StaffAccess access;
    private final ObjectMapper json;
    public enum Kind { SHIFT, PAYOUT }
    public record Input(@NotNull LocalDate date,LocalDate periodStart,@Pattern(regexp="[A-Z]{3}") @NotNull String currency,
        @PositiveOrZero long cash,@PositiveOrZero long card,@PositiveOrZero long upi,
        @PositiveOrZero long transferred,@Size(max=100) String reference,LocalDate transferDate,
        @Size(max=500) String reason,long adjustment){}
    private List<Map<String,Object>> history(long spa,Kind kind){
        return db.queryForList("SELECT id,business_date AS date,currency,state,revision,actor,recorded_at AS recordedAt,details FROM spa_financial_report WHERE spa_id=? AND kind=? ORDER BY id DESC LIMIT 100",spa,kind.name());
    }
    @GetMapping("/{kind}") @PreAuthorize("@staffAccess.spa(#spa)")
    public List<Map<String,Object>> list(@PathVariable long spa,@PathVariable Kind kind){return history(spa,kind);}
    @PostMapping("/{kind}") @PreAuthorize("@staffAccess.desk(#spa)")
    public void record(@PathVariable long spa,@PathVariable Kind kind,@Valid @RequestBody Input input) throws JsonProcessingException {
        if(kind==Kind.PAYOUT && !access.admin())throw new org.springframework.security.access.AccessDeniedException("Platform finance access required");
        var settings=db.queryForMap("SELECT timezone,commission_pct FROM spa WHERE id=? FOR UPDATE",spa);
        var zone=ZoneId.of(Objects.toString(settings.get("timezone"),"Asia/Kolkata"));
        if(input.date().isAfter(LocalDate.now(zone)))throw new IllegalArgumentException("Choose today or an earlier date");
        var prior=db.queryForList("SELECT revision,state FROM spa_financial_report WHERE spa_id=? AND kind=? AND business_date=? AND currency=? ORDER BY revision DESC LIMIT 1",spa,kind.name(),input.date(),input.currency());
        int revision=prior.isEmpty()?1:((Number)prior.get(0).get("revision")).intValue()+1;
        boolean explanation=input.reason()!=null && !input.reason().isBlank();
        if(revision>1 && !"REOPENED".equals(prior.get(0).get("state")) && (!access.spa(spa) || !explanation))throw new IllegalStateException("An owner must explain a correction");
        LocalDate start=kind==Kind.PAYOUT && input.periodStart()!=null?input.periodStart():input.date();
        if(start.isAfter(input.date()) || start.isBefore(input.date().minusDays(365)))throw new IllegalArgumentException("Choose a settlement period up to one year");
        var lines=finance.reportLines(Map.of("spa",spa,"currency",input.currency(),"channel",kind==Kind.SHIFT?"DESK":"ONLINE",
            "start",Timestamp.from(start.atStartOfDay(zone).toInstant()),"end",Timestamp.from(input.date().plusDays(1).atStartOfDay(zone).toInstant())));
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("lines",lines);details.put("reason",input.reason());
        String state;
        if(kind==Kind.SHIFT){
            Map<String,Long> expected=new TreeMap<>();long tips=0;
            for(var row:lines){
                long tip=amount(row,"tip"); tips+=tip;
                expected.merge(row.get("method").toString(),amount(row,"collected")-amount(row,"refunded")+tip,Long::sum);
            }
            var counted=Map.of("CASH",input.cash(),"CARD",input.card(),"UPI",input.upi());
            var differences=new TreeMap<String,Long>();
            counted.forEach((method,value)->differences.put(method,value-expected.getOrDefault(method,0L)));
            if(differences.values().stream().anyMatch(n->n!=0) && !explanation)throw new IllegalArgumentException("Explain the difference between counted and recorded payments");
            details.put("counted",counted);details.put("expected",expected);details.put("differences",differences);details.put("tips",tips);
            state="SUBMITTED";
        } else {
            if(input.reference()==null || input.reference().isBlank() || input.transferDate()==null || input.transferDate().isAfter(LocalDate.now(zone)))throw new IllegalArgumentException("Enter the completed transfer date and bank reference");
            long gross=0,refunds=0;
            for(var row:lines){gross+=amount(row,"collected");refunds+=amount(row,"refunded");}
            long commission=com.nirvana.application.service.payout.PayoutMath.split(gross,refunds,((Number)settings.get("commission_pct")).intValue()).commission();
            long expected=gross-refunds-commission+input.adjustment();
            if(input.adjustment()!=0 && !explanation)throw new IllegalArgumentException("Explain the payout adjustment");
            details.put("periodStart",start);
            for(LocalDate d=start;!d.isAfter(input.date());d=d.plusDays(1)){
                db.update("INSERT INTO payout_business_day(spa_id,currency,covered_date,statement_date) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE spa_id=spa_id",spa,input.currency(),d,input.date());
                if(db.queryForObject("SELECT COUNT(*) FROM payout_business_day WHERE spa_id=? AND currency=? AND covered_date=? AND statement_date=? AND payout_id IS NULL",Integer.class,spa,input.currency(),d,input.date())!=1)
                    throw new IllegalStateException("The settlement period overlaps another statement");
            }
            db.update("DELETE FROM payout_business_day WHERE spa_id=? AND currency=? AND statement_date=? AND covered_date<? AND payout_id IS NULL",spa,input.currency(),input.date(),start);
            String reference=input.reference().trim();
            db.update("INSERT INTO spa_transfer_reference(reference_no,spa_id,business_date,currency) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE reference_no=reference_no",reference,spa,input.date(),input.currency());
            if(db.queryForObject("SELECT COUNT(*) FROM spa_transfer_reference WHERE reference_no=? AND spa_id=? AND business_date=? AND currency=?",Integer.class,reference,spa,input.date(),input.currency())!=1)
                throw new IllegalStateException("Transfer already matched to another statement");
            details.put("reference",reference);details.put("transferDate",input.transferDate());details.put("gross",gross);details.put("refunds",refunds);
            details.put("commission",commission);details.put("adjustment",input.adjustment());details.put("expected",expected);
            details.put("transferred",input.transferred());details.put("difference",input.transferred()-expected);
            state=expected==input.transferred()?"MATCHED":"EXCEPTION";
        }
        save(spa,input.date(),input.currency(),kind,revision,state,json.writeValueAsString(details));
    }
    @PostMapping("/shift/{id}/approve") @PreAuthorize("@staffAccess.spa(#spa)")
    public void approve(@PathVariable long spa,@PathVariable long id){
        db.queryForObject("SELECT id FROM spa WHERE id=? FOR UPDATE",Long.class,spa);
        int rows=db.update("""
            INSERT INTO spa_financial_report(spa_id,business_date,currency,kind,revision,state,actor,recorded_at,details)
            SELECT r.spa_id,r.business_date,r.currency,r.kind,r.revision+1,'APPROVED',?,UTC_TIMESTAMP(),r.details
            FROM spa_financial_report r LEFT JOIN spa_financial_report n ON n.spa_id=r.spa_id AND n.kind=r.kind AND n.business_date=r.business_date
            AND n.currency=r.currency AND n.revision>r.revision WHERE r.id=? AND r.spa_id=? AND r.kind='SHIFT' AND r.state='SUBMITTED' AND n.id IS NULL
            """,SecurityUtils.getCurrentUserId(),id,spa);
        if(rows!=1)throw new IllegalStateException("Refresh the latest shift report before approval");
    }
    public record Correction(@NotBlank @Size(max=500) String reason){}
    @PostMapping("/shift/{id}/reopen") @PreAuthorize("@staffAccess.spa(#spa)")
    public void reopen(@PathVariable long spa,@PathVariable long id,@Valid @RequestBody Correction input) throws JsonProcessingException {
        db.queryForObject("SELECT id FROM spa WHERE id=? FOR UPDATE",Long.class,spa);
        var rows=db.queryForList("SELECT id,business_date,currency,revision,state FROM spa_financial_report WHERE spa_id=? AND kind='SHIFT' AND (business_date,currency)=(SELECT business_date,currency FROM spa_financial_report WHERE id=? AND spa_id=? AND kind='SHIFT') ORDER BY revision DESC LIMIT 1",spa,id,spa);
        if(rows.isEmpty() || ((Number)rows.get(0).get("id")).longValue()!=id || "REOPENED".equals(rows.get(0).get("state")))throw new IllegalStateException("Refresh the latest shift");
        var row=rows.get(0);
        save(spa,LocalDate.parse(row.get("business_date").toString()),row.get("currency").toString(),Kind.SHIFT,
             ((Number)row.get("revision")).intValue()+1,"REOPENED",json.writeValueAsString(Map.of("reason",input.reason())));
    }
    @GetMapping("/unmatched") @PreAuthorize("@staffAccess.spa(#spa)")
    public List<LocalDate> unmatched(@PathVariable long spa,@RequestParam LocalDate through,@RequestParam String currency){
        var zone=ZoneId.of(Objects.toString(db.queryForObject("SELECT timezone FROM spa WHERE id=?",String.class,spa),"Asia/Kolkata"));
        var from=through.minusDays(30);
        var lines=finance.reportLines(Map.of("spa",spa,"currency",currency,"channel","ONLINE","start",Timestamp.from(from.atStartOfDay(zone).toInstant()),"end",Timestamp.from(through.plusDays(1).atStartOfDay(zone).toInstant())));
        Set<LocalDate> days=new TreeSet<>();
        for(var line:lines)days.add(com.nirvana.application.service.SpaBookingAlerts.fromUtc(line.get("eventUtc")).atZone(zone).toLocalDate());
        db.query("SELECT b.covered_date FROM spa_financial_report r JOIN payout_business_day b ON b.spa_id=r.spa_id AND b.currency=r.currency AND b.statement_date=r.business_date WHERE r.spa_id=? AND r.kind='PAYOUT' AND r.currency=? AND state='MATCHED' AND revision=(SELECT MAX(n.revision) FROM spa_financial_report n WHERE n.spa_id=r.spa_id AND n.business_date=r.business_date AND n.currency=r.currency AND n.kind=r.kind)",(org.springframework.jdbc.core.RowCallbackHandler)rs->days.remove(rs.getDate(1).toLocalDate()),spa,currency);
        db.query("SELECT b.covered_date FROM payout_business_day b JOIN payout p ON p.id=b.payout_id WHERE b.spa_id=? AND b.currency=? AND p.status='COMPLETED'",
            (org.springframework.jdbc.core.RowCallbackHandler)rs->days.remove(rs.getDate(1).toLocalDate()),spa,currency);
        return List.copyOf(days);
    }
    private static long amount(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private void save(long spa,LocalDate date,String currency,Kind kind,int revision,String state,String details){
        db.update("INSERT INTO spa_financial_report(spa_id,business_date,currency,kind,revision,state,actor,recorded_at,details) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(),?)",
            spa,date,currency,kind.name(),revision,state,SecurityUtils.getCurrentUserId(),details);
    }
}
