package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    /*
        Consume transaction completed from event from kafka
        Credits reciver account
     */
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String, Object> payload) {

        try {
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            log.info("Crediting account: {} amount; {}", receiverAccount, amount);

            accountService.creditBalance(receiverAccount, amount);

        }
        catch (Exception e) {
            log.error("Error crediting amount: {}", e.getMessage());
        }
    }

    /**
     * Consume fraud. Detected event from kafka
     * Blocks the flagged account
     */
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String, Object> payload) {

        try {
            String accountNumber = (String) payload.get("accountNumber");
            log.info("Fraud detected - blocking account: {}", accountNumber);

            accountService.blockAccount(accountNumber);

        }
        catch (Exception e) {
            log.error("Error blocking account {}", e.getMessage());
        }
    }

    /**
     * Consume payment.completed event from kafka
     * Credits the deposited amount into the user's account balance
     */
    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(
            @Payload Map<String, Object> payload) {

        try {
            String accountNumber = (String) payload.get("accountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            String razorpayPaymentId = (String) payload.get("razorpayPaymentId");

            log.info("Payment deposit received - Crediting account: {} amount: {} (Razorpay ID: {})",
                    accountNumber, amount, razorpayPaymentId);

            accountService.creditBalance(accountNumber, amount);

            log.info("Balance successfully credited for account: {}", accountNumber);
        }
        catch (Exception e) {
            log.error("Error processing payment.completed event: {}", e.getMessage());
        }
    }
}