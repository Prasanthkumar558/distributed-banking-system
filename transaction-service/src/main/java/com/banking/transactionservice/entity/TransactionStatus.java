package com.banking.transactionservice.entity;

/**
 * Transaction LifeCycle Flow
 * PENDING -> PROCESSING -> COMPLETED (CLEAN TRANSACTION)
 *
 *  "              "      -> PENDING_VERIFICATION ( SUSPICIOUS DETECTED)
 *                                 COMPLETED (VERIFIED)
 *                                 FLAGGED (SAGA REFUND)
 *                        -> FAILED
 *                        -> FLAGGED
 */

public enum TransactionStatus {

    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
