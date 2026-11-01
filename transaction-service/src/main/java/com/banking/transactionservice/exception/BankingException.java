package com.banking.transactionservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BankingException extends RuntimeException {
    private final HttpStatus status;

    public BankingException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}