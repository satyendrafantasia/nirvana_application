package com.nirvana.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Embeddable @Getter @NoArgsConstructor
public class RefundPosting {
    @Column(name="amount_cents",nullable=false)
    private int amountCents;
    @Column(name="recorded_at",nullable=false)
    private OffsetDateTime recordedAt;

    /** Gateways report cumulative refunds. A replay or out-of-order smaller total creates no posting. */
    public static void record(Map<Integer,RefundPosting> history,int previous,int cumulative){
        int recorded=history.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int baseline=Math.max(previous,recorded);
        if(cumulative<=baseline)return;
        RefundPosting posting=new RefundPosting();
        posting.amountCents=cumulative-baseline;
        posting.recordedAt=OffsetDateTime.now(ZoneOffset.UTC);
        history.put(cumulative,posting);
    }
}
