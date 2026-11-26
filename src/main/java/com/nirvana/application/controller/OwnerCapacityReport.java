package com.nirvana.application.controller;

import com.nirvana.application.repository.ClosureRepository;
import com.nirvana.application.service.impl.TherapistRoster;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
class OwnerCapacityReport {
    private final TherapistRoster roster;
    private final ClosureRepository closures;
    private final NamedParameterJdbcTemplate db;
    enum Layer { WORK, CLOSED, BOOKED }
    record Point(long second,Layer layer,int delta){}
    record Usage(long availableSeconds,long occupiedSeconds){}
    private record Resource(String type,long id){}
    static Usage measure(List<Point> points){
        points.sort(Comparator.comparingLong(Point::second));
        long available=0,busy=0,last=0;
        int[] counts=new int[Layer.values().length];
        for(var point:points){
            if(counts[0]>0 && counts[1]==0){
                long elapsed=point.second-last;
                available+=elapsed;
                if(counts[2]>0)busy+=elapsed;
            }
            counts[point.layer.ordinal()]+=point.delta;
            last=point.second;
        }
        return new Usage(available,busy);
    }
    private void interval(List<Point> list,long lo,long hi,long start,long end,Layer layer){
        long a=Math.max(lo,start), b=Math.min(hi,end);
        if(a<b){list.add(new Point(a,layer,1));list.add(new Point(b,layer,-1));}
    }
    Map<String,Object> report(long spa,ZoneId zone,LocalDate from,LocalDate to){
        var start=from.atStartOfDay(zone).toOffsetDateTime();
        var end=to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        long lo=start.toEpochSecond(),hi=end.toEpochSecond();
        var args=Map.of("spa",spa,"s",LocalDateTime.ofInstant(start.toInstant(),ZoneOffset.UTC),"e",LocalDateTime.ofInstant(end.toInstant(),ZoneOffset.UTC));
        Map<Resource,List<Point>> timelines=new LinkedHashMap<>();
        roster.load(spa,from.minusDays(1),to).forEach((key,intervals)->{
            var list=timelines.computeIfAbsent(new Resource("Therapists",key.therapist()),k->new ArrayList<>());
            for(var i:intervals)interval(list,lo,hi,i.start().toEpochSecond(),i.end().toEpochSecond(),
                i.kind()==TherapistRoster.Kind.WORK?Layer.WORK:Layer.CLOSED);
        });
        db.query("SELECT room_id,start_ts,end_ts FROM slot WHERE spa_id=:spa AND room_id IS NOT NULL AND is_blocked=0 AND start_ts<:e AND end_ts>:s",args,rs->{
            var list=timelines.computeIfAbsent(new Resource("Rooms",rs.getLong(1)),k->new ArrayList<>());
            interval(list,lo,hi,rs.getObject(2,LocalDateTime.class).toEpochSecond(ZoneOffset.UTC),rs.getObject(3,LocalDateTime.class).toEpochSecond(ZoneOffset.UTC),Layer.WORK);
        });
        int[][] heat=new int[7][24];
        db.query("SELECT room_id,therapist_id,start_ts,end_ts,status FROM booking WHERE spa_id=:spa AND start_ts<:e AND end_ts>:s",args,rs->{
            var begin=rs.getObject(3,LocalDateTime.class).toInstant(ZoneOffset.UTC);
            var local=begin.atZone(zone);
            if(begin.getEpochSecond()>=lo)heat[local.getDayOfWeek().getValue()-1][local.getHour()]++;
            if(!Set.of("CONFIRMED","ARRIVED","COMPLETED").contains(rs.getString(5)))return;
            long stop=rs.getObject(4,LocalDateTime.class).toEpochSecond(ZoneOffset.UTC);
            for(var resource:List.of(new Resource("Rooms",rs.getLong(1)),new Resource("Therapists",rs.getLong(2)))){
                var list=timelines.get(resource);
                if(list!=null)interval(list,lo,hi,begin.getEpochSecond(),stop,Layer.BOOKED);
            }
        });
        var closed=new ArrayList<Point>();
        for(var c:closures.findClosuresOverlapping(spa,start,end))interval(closed,lo,hi,c.getStartTs().toEpochSecond(),c.getEndTs().toEpochSecond(),Layer.CLOSED);
        var totals=new TreeMap<String,Usage>();
        timelines.forEach((key,list)->{
            list.addAll(closed);
            totals.merge(key.type,measure(list),(a,b)->new Usage(a.availableSeconds+b.availableSeconds,a.occupiedSeconds+b.occupiedSeconds));
        });
        return Map.of("utilization",totals,"heat",heat);
    }
}
