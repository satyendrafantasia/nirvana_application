package com.nirvana.application.service.payout;

import com.nirvana.application.config.RazorpayProperties;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PayoutProperties.class)
@Slf4j
public class PayoutConfig {
    /** Route when asked for and Razorpay keys are set; otherwise manual, so payouts work locally without keys. */
    @Bean
    @ConditionalOnMissingBean(PayoutProvider.class)
    public PayoutProvider payoutProvider(PayoutProperties props, RazorpayProperties razorpay) {
        if (props.getProvider() == PayoutProperties.Mode.RAZORPAY_ROUTE) {
            if (razorpay.readyForCheckout()) {
                try {
                    log.info("event=payout.provider provider=razorpay_route");
                    return new RazorpayRoutePayoutProvider(new RazorpayClient(razorpay.getKeyId(), razorpay.getKeySecret()),
                            razorpay.getKeyId(), razorpay.getKeySecret(), props);
                } catch (RazorpayException e) {
                    log.warn("event=payout.provider_unavailable reason={}", e.getMessage());
                }
            } else log.warn("event=payout.provider_unavailable reason=razorpay_not_configured");
        }
        log.info("event=payout.provider provider=manual");
        return new ManualPayoutProvider();
    }
}
