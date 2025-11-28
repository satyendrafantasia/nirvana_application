package com.nirvana.application.otp.sender;

import com.nirvana.application.config.EmailProperties;
import com.nirvana.application.otp.exception.EmailSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SesEmailSender implements EmailSender {

    private final SesClient sesClient;
    private final EmailProperties emailProperties;

    @Override
    public void sendOtpEmail(String toEmail, String subject, String body) {
        if (!emailProperties.isEnabled()) {
            log.info("Email sending disabled; skipping SES send for {}", toEmail);
            return;
        }
        try {
            String source = emailProperties.getFromName() != null && !emailProperties.getFromName().isBlank()
                    ? String.format("%s <%s>", emailProperties.getFromName(), emailProperties.getFromAddress())
                    : emailProperties.getFromAddress();

            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(toEmail).build())
                    .source(source)
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).build())
                            .body(Body.builder()
                                    .text(Content.builder().data(body).build())
                                    .build())
                            .build())
                    .build();
            sesClient.sendEmail(request);
            log.info("OTP email sent to {}", toEmail);
        } catch (SesException ex) {
            log.error("Failed to send OTP email via SES: {}", ex.awsErrorDetails().errorMessage(), ex);
            throw new EmailSendException("Failed to send email via AWS SES", ex);
        } catch (Exception ex) {
            log.error("Unexpected error sending OTP email", ex);
            throw new EmailSendException("Failed to send email via AWS SES", ex);
        }
    }
}
