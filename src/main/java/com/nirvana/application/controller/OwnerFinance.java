package com.nirvana.application.controller;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.*;

@Service
@RequiredArgsConstructor
class OwnerFinance {
    private final NamedParameterJdbcTemplate db;
    /**
     * Collection and refund events, dated when the money moved. channel says who holds the money:
     * DESK is taken at the spa itself; ONLINE goes through Nirvana's payment gateway and reaches the spa
     * in a payout, after commission. Tips are kept apart from the service amount.
     */
    private static final String EVENTS = """
        SELECT p.captured_at AS eventUtc, b.booking_reference AS reference, p.transaction_id AS receipt,
          'Appointment' AS source,'ONLINE' AS channel,COALESCE(p.payment_method,p.gateway) AS method,p.currency,p.amount_cents AS collected,0 AS refunded,0 AS tip
        FROM booking b JOIN payment p ON p.booking_id=b.id WHERE b.spa_id=:spa AND p.captured_at>=:start AND p.captured_at<:end
          AND p.payment_status IN ('CAPTURED','COMPLETED','REFUNDED','PARTIALLY_REFUNDED')
        UNION ALL
        SELECT f.recorded_at,b.booking_reference,NULL,'Appointment refund','ONLINE',p.gateway,p.currency,0,f.amount_cents,0
        FROM booking b JOIN payment p ON p.booking_id=b.id JOIN booking_refund_posting f ON f.payment_id=p.id WHERE b.spa_id=:spa AND f.recorded_at>=:start AND f.recorded_at<:end
        UNION ALL
        SELECT d.collected_at,b.booking_reference,d.receipt_reference,'Front desk','DESK',d.method,d.currency,d.amount_cents,0,d.tip_cents
        FROM spa_desk_receipt d JOIN booking b ON b.id=d.booking_id WHERE d.spa_id=:spa AND d.collected_at>=:start AND d.collected_at<:end
        UNION ALL
        SELECT d.returned_at,b.booking_reference,d.receipt_reference,'Front desk return','DESK',d.method,d.currency,0,d.amount_cents,0
        FROM spa_desk_receipt d JOIN booking b ON b.id=d.booking_id WHERE d.spa_id=:spa AND d.returned_at>=:start AND d.returned_at<:end
        UNION ALL
        SELECT COALESCE(x.collected_at,x.purchase_date),CONCAT('Package ',x.id),x.payment_reference_id,'Spa package','ONLINE','Package checkout',
          COALESCE(x.currency,'INR'),ROUND(x.price_paid*100),0,0 FROM user_spa_package_subscription x
        WHERE x.spa_id=:spa AND x.payment_status='SUCCESS' AND COALESCE(x.collected_at,x.purchase_date)>=:start AND COALESCE(x.collected_at,x.purchase_date)<:end
        UNION ALL
        SELECT f.recorded_at,CONCAT('Package ',x.id),x.payment_reference_id,'Package refund','ONLINE','Package checkout',COALESCE(x.currency,'INR'),0,f.amount_cents,0
        FROM user_spa_package_subscription x JOIN spa_package_refund_posting f ON f.subscription_id=x.id
        WHERE x.spa_id=:spa AND f.recorded_at>=:start AND f.recorded_at<:end
        """;
    private static final String FILTER=" WHERE (reference LIKE :term ESCAPE '!' OR receipt LIKE :term ESCAPE '!') ";
    /** Totals per currency and channel. */
    List<Map<String,Object>> totals(Map<String,Object> args){
        return db.queryForList("SELECT currency,channel,SUM(collected) AS collected,SUM(refunded) AS refunds,SUM(tip) AS tips FROM ("+EVENTS+") events GROUP BY currency,channel ORDER BY currency,channel",args);
    }
    List<Map<String,Object>> trend(Map<String,Object> args,String buckets){
        return db.queryForList("SELECT days.day,e.currency,SUM(e.collected) AS collected,SUM(e.refunded) AS refunds,"
            +"SUM(CASE WHEN e.channel='DESK' THEN e.collected-e.refunded ELSE 0 END) AS desk,SUM(CASE WHEN e.channel='ONLINE' THEN e.collected-e.refunded ELSE 0 END) AS online "
            +"FROM ("+buckets+") days JOIN ("+EVENTS+") e ON e.eventUtc>=days.lo AND e.eventUtc<days.hi GROUP BY days.day,e.currency ORDER BY days.day,e.currency",args);
    }
    List<Map<String,Object>> entries(Map<String,Object> args,int limit,int offset){
        var params=new HashMap<>(args); params.put("limit",limit);params.put("offset",offset);
        return db.queryForList("SELECT * FROM ("+EVENTS+") events"+FILTER+" ORDER BY eventUtc DESC,reference,source LIMIT :limit OFFSET :offset",params);
    }
    /**
     * What Nirvana owes the spa for these dates: online money in, less online refunds, less commission.
     * An estimate; the payout statement is what's actually paid.
     */
    static List<Map<String,Object>> payout(List<Map<String,Object>> totals,int commissionPct){
        var out=new ArrayList<Map<String,Object>>();
        for(var t:totals){
            if(!"ONLINE".equals(t.get("channel")))continue;
            long net=num(t.get("collected"))-num(t.get("refunds"));
            long commission=Math.round(Math.max(0,net)*commissionPct/100.0);
            var row=new LinkedHashMap<String,Object>();
            row.put("currency",t.get("currency"));row.put("online",net);row.put("commission",commission);
            row.put("commissionPct",commissionPct);row.put("payout",net-commission);
            out.add(row);
        }
        return out;
    }
    private static long num(Object v){return v==null?0:((Number)v).longValue();}
}
