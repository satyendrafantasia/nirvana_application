package com.nirvana.application.service.payout;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@Getter
@Setter
@ConfigurationProperties(prefix = "payouts")
public class PayoutProperties {
    /** MANUAL: statements only, ops pays by bank transfer and records the reference. RAZORPAY_ROUTE: transfers through Route. */
    private Mode provider = Mode.MANUAL;
    /** Money is held this many days after it is captured before it is paid out, so most refunds land first. */
    private int holdDays = 2;
    /** Ignore money collected before this date (it was settled some other way). Blank: all history. */
    private LocalDate since;
    /** Settlement periods end at midnight in this zone (the platform's market). */
    private java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
    /** When the weekly settlement runs (Spring cron, UTC). */
    private String cron = "0 30 3 * * MON";
    /** Razorpay Route linked-account profile for a spa business. */
    private String routeCategory = "healthcare";
    private String routeSubcategory = "clinic";

    public enum Mode { MANUAL, RAZORPAY_ROUTE }
}
