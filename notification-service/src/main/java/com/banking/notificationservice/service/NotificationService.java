package com.banking.notificationservice.service;

import com.banking.notificationservice.client.AccountServiceClient;
import com.banking.notificationservice.dto.AccountResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.username:pkbanking.alerts@gmail.com}")
    private String senderEmail;

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(Object rawPayload) {
        if (rawPayload instanceof Map) {
            return (Map<String, Object>) rawPayload;
        } else if (rawPayload instanceof String) {
            String str = (String) rawPayload;
            try {
                return objectMapper.readValue(str, Map.class);
            } catch (Exception e) {
                log.error("Failed to parse JSON string payload: {}", e.getMessage());
            }
        }
        return Collections.emptyMap();
    }

    @KafkaListener(topics = "transaction.completed", groupId = "notification-service-group-v3")
    public void consumeTransactionCompleted(String rawPayload) {
        log.info(">>>> Notification Service received transaction.completed: {}", rawPayload);
        try {
            Map<String, Object> payload = parsePayload(rawPayload);

            String senderAccount = payload.containsKey("sendAccountNumber")
                    ? String.valueOf(payload.get("sendAccountNumber"))
                    : (payload.containsKey("senderAccountNumber") ? String.valueOf(payload.get("senderAccountNumber")) : null);

            String receiverAccount = payload.get("receiverAccountNumber") != null
                    ? String.valueOf(payload.get("receiverAccountNumber"))
                    : null;

            String amount = String.valueOf(payload.get("amount"));

//            // DEBIT ALERT
//            if (senderAccount != null && !senderAccount.isBlank() && !"null".equalsIgnoreCase(senderAccount)) {
//                sendAlert(senderAccount,
//                        "DEBIT ALERT",
//                        String.format("₹%s debited from your account to recipient %s", amount, receiverAccount));
//            }

            // CREDIT ALERT
            if (receiverAccount != null && !receiverAccount.isBlank() && !"null".equalsIgnoreCase(receiverAccount)) {
                sendAlert(receiverAccount,
                        "CREDIT ALERT",
                        String.format("₹%s credited to your account from %s", amount, senderAccount));
            }
        } catch (Exception e) {
            log.error("Error sending transaction notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "transaction.otp.generated", groupId = "notification-service-group-v3")
    public void consumeOtpGenerated(String rawPayload) {
        log.info(">>>> Notification Service received transaction.otp.generated: {}", rawPayload);
        try {
            Map<String, Object> payload = parsePayload(rawPayload);
            String accountNumber = String.valueOf(payload.get("accountNumber"));
            String otp = String.valueOf(payload.get("otp"));
            String amount = String.valueOf(payload.get("amount"));
            String reason = String.valueOf(payload.get("reason"));

            sendAlert(accountNumber, "TRANSACTION VERIFICATION REQUIRED",
                    String.format("Suspicious activity detected on your account. Reason: %s. " +
                                    "A transaction of ₹%s is pending verification. Your OTP is: %s. Valid for 5 minutes. " +
                                    "If this was not you, please contact support immediately.",
                            reason, amount, otp));
        } catch (Exception e) {
            log.error("Error sending OTP notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "fraud.detected", groupId = "notification-service-group-v3")
    public void consumeFraudDetected(String rawPayload) {
        log.info(">>>> Notification Service received fraud.detected: {}", rawPayload);
        try {
            Map<String, Object> payload = parsePayload(rawPayload);
            String accountNumber = String.valueOf(payload.get("accountNumber"));
            String reason = String.valueOf(payload.get("reason"));

            sendAlert(accountNumber,
                    "SUSPICIOUS ACTIVITY DETECTED",
                    String.format("Your account %s has been temporarily blocked. Reason: %s. Please contact your bank immediately.",
                            accountNumber, reason));
        } catch (Exception e) {
            log.error("Error sending fraud alert: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service-group-v3")
    public void consumePaymentCompleted(String rawPayload) {
        log.info(">>>> Notification Service received payment.completed: {}", rawPayload);
        try {
            Map<String, Object> payload = parsePayload(rawPayload);
            String accountNumber = String.valueOf(payload.get("accountNumber"));
            String amount = String.valueOf(payload.get("amount"));

            sendAlert(accountNumber,
                    "PAYMENT SUCCESSFUL",
                    String.format("Deposit of ₹%s completed successfully. Gateway ID: %s",
                            amount, payload.get("razorpayPaymentId")));
        } catch (Exception e) {
            log.error("Error sending payment notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service-group-v3")
    public void consumePaymentFailed(String rawPayload) {
        log.info(">>>> Notification Service received payment.failed: {}", rawPayload);
        try {
            Map<String, Object> payload = parsePayload(rawPayload);
            String accountNumber = String.valueOf(payload.get("accountNumber"));
            String amount = String.valueOf(payload.get("amount"));

            sendAlert(accountNumber,
                    "PAYMENT FAILED",
                    String.format("Your payment deposit of ₹%s failed. If your money was deducted, it will be refunded automatically.", amount));
        } catch (Exception e) {
            log.error("Error sending payment failure notification: {}", e.getMessage(), e);
        }
    }

    private void sendAlert(String accountNumber, String subject, String message) {
        log.info("---------------------------------------");
        log.info("Processing Alert for Account: {}", accountNumber);
        log.info("Subject: {}", subject);
        log.info("Message: {}", message);
        log.info("---------------------------------------");

        String recipientEmail = null;

        try {
            AccountResponse account = accountServiceClient.getAccount(accountNumber);
            if (account != null && account.getEmail() != null && !account.getEmail().isBlank()) {
                recipientEmail = account.getEmail();
                log.info("Resolved email [{}] for account [{}]", recipientEmail, accountNumber);
            }
        } catch (Exception ex) {
            log.warn("Could not fetch account details for account [{}]: {}. Using default sender fallback.",
                    accountNumber, ex.getMessage());
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            recipientEmail = senderEmail;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(senderEmail);
            mailMessage.setTo(recipientEmail);
            mailMessage.setSubject(subject + " - Account " + accountNumber);
            mailMessage.setText(message);

            mailSender.send(mailMessage);
            log.info("Email alert successfully sent to: {}", recipientEmail);
        } catch (Exception ex) {
            log.error("Failed to deliver email alert to {}: {}", recipientEmail, ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "transaction.initiated", groupId = "notification-service-group-v3")
    public void consumeTransactionInitiated(String rawPayload) {
        log.info(">>>> Notification Service received transaction.initiated: {}", rawPayload);
        try {
            Map<String, Object> payload = parsePayload(rawPayload);

            String senderAccount = payload.containsKey("senderAccountNumber")
                    ? String.valueOf(payload.get("senderAccountNumber"))
                    : String.valueOf(payload.get("sendAccountNumber"));
            String receiverAccount = String.valueOf(payload.get("receiverAccountNumber"));
            String amount = String.valueOf(payload.get("amount"));
            String transactionId = String.valueOf(payload.get("transactionId"));

            // Debit sender immediately
            if (senderAccount != null && !senderAccount.isBlank() && !"null".equalsIgnoreCase(senderAccount)) {
                String subject = "Debit Alert: A/C ending " + senderAccount.substring(Math.max(0, senderAccount.length() - 4));
                String message = "Dear PK Bank User, your A/C ending " + senderAccount.substring(Math.max(0, senderAccount.length() - 4))
                        + " has been debited by Rs " + amount + " towards transfer to " + receiverAccount
                        + " Ref No " + transactionId.substring(0, 8) + ". Status: In Progress.";
                sendAlert(senderAccount, subject, message);
            }
        } catch (Exception e) {
            log.error("Error processing immediate debit notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "transaction.refunded", groupId = "notification-service-group-v3")
    public void consumeTransactionRefunded(String rawPayload) {
        log.info(">>>> Notification Service received transaction.refunded: {}", rawPayload);
        try {
            Map<String, Object> payload = parsePayload(rawPayload);

            String senderAccount = String.valueOf(payload.get("senderAccountNumber"));
            String amount = String.valueOf(payload.get("amount"));
            String reason = String.valueOf(payload.get("reason"));
            String transactionId = String.valueOf(payload.get("transactionId"));

            String subject = "REFUND ALERT: A/C ending " + senderAccount.substring(Math.max(0, senderAccount.length() - 4));
            String message = String.format(
                    "Dear PK Bank User,\n\n" +
                            "Your refund of Rs %s has been successfully credited back to your account %s.\n\n" +
                            "================ REFUND DETAILS ================\n" +
                            "Transaction Reference : %s\n" +
                            "Refunded Amount       : Rs %s\n" +
                            "Reason                : %s\n" +
                            "================================================\n\n" +
                            "If you have questions regarding this reversal, please contact support.\n\n" +
                            "Warm Regards,\n" +
                            "PK Banking System",
                    amount, senderAccount, transactionId, amount, reason
            );

            sendAlert(senderAccount, subject, message);

        } catch (Exception e) {
            log.error("Error processing transaction refund notification: {}", e.getMessage(), e);
        }
    }
}