package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.dto.LoginRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountSatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.exception.AccountAlreadyExistsException;
import com.banking.accountservice.exception.AccountInactiveException;
import com.banking.accountservice.exception.AccountNotFoundException;
import com.banking.accountservice.exception.InsufficientBalanceException;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom();
    private final PasswordEncoder passwordEncoder;

    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new AccountAlreadyExistsException("An account already exists with email: " + request.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountSatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());

        // Hash and store the login password
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS
                        ? new BigDecimal("100000")
                        : new BigDecimal("500000")
        );

        Account savedAccount = accountRepository.save(account);
        log.info("Account created successfully with A/C number: {}", savedAccount.getAccountNumber());

        return mapToResponse(savedAccount);
    }

    public AccountResponse login(LoginRequest request) {
        log.info("Login attempt for account: {}", request.getAccountNumber());

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("Invalid account number or password"));

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new RuntimeException("Invalid account number or password");
        }

        if (account.getStatus() == AccountSatus.BLOCKED) {
            throw new AccountInactiveException("Account is blocked. Please contact support.");
        }

        log.info("Login successful for account: {}", account.getAccountNumber());
        return mapToResponse(account);
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));
        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));
        return account.getBalance();
    }

    public void blockAccount(String accountNumber) {
        log.info("Blocking account: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));

        account.setStatus(AccountSatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked successfully: {}", accountNumber);
    }

    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting Balance {} from account: {}", amount, accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));

        if (account.getStatus() != AccountSatus.ACTIVE) {
            throw new AccountInactiveException("Account " + accountNumber + " is " + account.getStatus() + ". Debits are suspended.");
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient funds in A/C " + accountNumber + ". Available balance: INR " + account.getBalance());
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance updated. New Balance is INR {}", account.getBalance());
    }

    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to account: {}", amount, accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Balance credited. New balance is INR {}", account.getBalance());
    }

    public void setTpin(String accountNumber, String rawTpin) {
        log.info("Setting TPIN for account: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));

        account.setTpin(passwordEncoder.encode(rawTpin));
        accountRepository.save(account);
        log.info("TPIN successfully set for account: {}", accountNumber);
    }

    public boolean verifyTpin(String accountNumber, String rawTpin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));

        if (account.getTpin() == null || account.getTpin().isBlank()) {
            throw new RuntimeException("TPIN is not set for this account. Please set a TPIN first.");
        }

        return passwordEncoder.matches(rawTpin, account.getTpin());
    }
}