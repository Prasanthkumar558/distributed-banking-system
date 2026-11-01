package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.dto.VerifyTpinRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.exception.BankingException;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    /**
     * SAGA STEPS: Initiate transaction
     * Deducts money from sender via Feign client
     * Saves transaction as PROCESSING
     * Publishes Kafka event for fraud check
     */
    public TransactionResponse transfer(TransferRequest request) {

        log.info("Saga START - Transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        // SAGA STEP 0 : Verify TPIN before initiating any balance deductions

        Boolean isTpinValid = accountServiceClient.verifyTpin(
                new VerifyTpinRequest(request.getSenderAccountNumber(),request.getTpin())
        );

        if(Boolean.FALSE.equals(isTpinValid)){
            throw new BankingException("Invalid Transaction PIN. Transfer cancelled",org.springframework.http.HttpStatus.UNAUTHORIZED);
        }

        // SAGA STEP 1: Deduct from sender
        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());

        // Step: Save transaction as PROCESSING
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());

        // SAGA STEP 2: Publish for fraud check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published {}", savedTransaction.getId());

        return mapToResponse(savedTransaction);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());
        return response;
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId)));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toUnmodifiableList());
    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("OTP: verification for the transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found " + transactionId));

        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP Expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if (!storedOtp.equals(otp)) {
            log.warn("Wrong OTP - blocking account and refunding: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction, "Wrong OTP entered - transaction cancelled, account blocked for security");
            return mapToResponse(transaction);
        }

        log.info("OTP verified - completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);

        return mapToResponse(transaction);
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}", transaction.getSenderAccountNumber(), transaction.getAmount());

        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(), transaction.getAmount());

        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason + " - SAGA Compensation executed, amount refunded at " + LocalDateTime.now());

        transactionRepository.save(transaction);

        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETE - {} refunded to {}",
                transaction.getAmount(), transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {

        compensateTransaction(transaction, reason);

        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("Fraud.detected published - account: {} will be blocked. Kindly contact your nearby bank.",
                transaction.getSenderAccountNumber());

    }

    private void completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        Map<String, Object> completedEvent = new HashMap<>();
        completedEvent.put("transactionId", transaction.getId());
        completedEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        completedEvent.put("sendAccountNumber", transaction.getSenderAccountNumber());
        completedEvent.put("receiverAccountNumber", transaction.getReceiverAccountNumber());
        completedEvent.put("amount", transaction.getAmount());
        completedEvent.put("description", transaction.getDescription());

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);

        log.info("SAGA COMPLETE: Transaction {} completed and published to topic {}",
                transaction.getId(), TRANSACTION_COMPLETED_TOPIC);
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction Not found " + transactionId));

        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Transaction {} not PROCESSING - skipping", transactionId);
            return;
        }

        completeTransaction(transaction);
    }

    /**
     * Schedular task: Runs every 60 seconds.
     * Finds transactions stuck in PENDING_VERIFICATION for > 5 minutes,
     * cleans up Redis OTP keys, and executes SAGA compensation to refund sender balance.
     */
    @Scheduled(fixedRate = 60000)
    public void autoRefundExpiredPendingTransactions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);

        List<Transaction> expiredTransactions = transactionRepository
                .findByStatusAndCreatedAtBefore(TransactionStatus.PENDING_VERIFICATION, cutoffTime);

        for (Transaction tx : expiredTransactions) {
            log.warn("AUTO-EXPIRY: Transaction {} exceeded 5-minute OTP window. Initiating automatic SAGA refund.", tx.getId());

            // 1. Clean up Redis OTP key if present
            String otpKey = "verification:otp" + tx.getId();
            redisTemplate.delete(otpKey);

            // 2. Compensate and refund sender balance synchronously
            compensateTransaction(tx, "OTP Timeout - Abandoned fraud-flagged transfer refunded automatically.");
        }
    }

    /**
     * Scheduled Task: Runs every 60 seconds.
     * Sweeps transactions stuck in PROCESSING for > 2 minutes (pipeline/service crash)
     * and triggers automatic SAGA compensation to refund the sender balance.
     */
    @Scheduled(fixedRate = 60000)
    public void autoRefundStuckProcessingTransactions() {
        // 2 minutes cutoff for pipeline failure
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(2);

        List<Transaction> stuckTransactions = transactionRepository
                .findByStatusAndCreatedAtBefore(TransactionStatus.PROCESSING, cutoffTime);

        for (Transaction tx : stuckTransactions) {
            log.warn("PIPELINE TIMEOUT: Transaction {} stuck in PROCESSING for > 2 minutes. Initiating automatic SAGA refund.", tx.getId());

            // Trigger SAGA compensation to credit sender balance back
            compensateTransaction(tx, "Pipeline Timeout - Transaction stuck in PROCESSING. SAGA refund executed.");
        }
    }
}