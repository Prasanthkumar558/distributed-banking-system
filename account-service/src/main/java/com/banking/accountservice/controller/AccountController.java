package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.dto.LoginRequest;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AccountResponse response = accountService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request
            ){

        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountNumber) {

        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(@PathVariable String accountNumber) {

        accountService.blockAccount(accountNumber);

        return ResponseEntity.ok("Account blocked Successfully");
    }


    /*
    SAGA STEP 1 -  Deduct balance
    Called by Transaction Service, when transfer is initiated
     */

    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ){

        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok("Amount deducted successfully");
    }

    /*
    SAGA step 4 - Compensating transaction endpoint
    Called By Trasanction service in 2 scenarios
    1. Fraud detected -> refund sender (undo step 1)
    2. Transaction completed -> credit receiver
     */

    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ){

        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok("BALANCE CREDITED SUCCESSFULLY");
    }

    @PutMapping("/tpin/set")
    public ResponseEntity<String> setTpin(@Valid @RequestBody com.banking.accountservice.dto.SetTpinRequest request) {
        accountService.setTpin(request.getAccountNumber(), request.getTpin());
        return ResponseEntity.ok("TPIN has been successfully set.");
    }

    @PostMapping("/tpin/verify")
    public ResponseEntity<Boolean> verifyTpin(@Valid @RequestBody com.banking.accountservice.dto.VerifyTpinRequest request) {
        boolean isValid = accountService.verifyTpin(request.getAccountNumber(), request.getTpin());
        return ResponseEntity.ok(isValid);
    }

    @PostMapping("/{accountNumber}/deposit")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Deposit amount must be greater than zero");
        }

        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

}
