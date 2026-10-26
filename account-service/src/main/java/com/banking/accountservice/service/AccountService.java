package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountSatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request) {

        log.info("Creating account for {}",request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account already Exists for email: "+request.getEmail());
        }

        Account account = new Account();
        account.setAccountNumber(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccounType());
        account.setStatus(AccountSatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccoutnNumber());
        account.setDailyTransactionLimit(
                request.getAccounType() == AccountType.SAVINGS
                ? new BigDecimal("100000")
                : new BigDecimal("500000")
        );

        Account savedAccount = accountRepository.save(account);

        log.info("Account created: {}",savedAccount.getAccountNumber());

        return mapToResponce(savedAccount);
    }

    private AccountResponse mapToResponce(Account account) {
        AccountResponse response =  new AccountResponse();
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

    private String generateAccoutnNumber() {

        String accountNumber;

        // we need exact 12 digit number to create account thats why loop;

        do {
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d",number);   //  % -> starts with
                                                             //  0 -> fill with zero
                                                             //  12 -> 12 digits
                                                             //  d  -> decimal

        }while(accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    /*
        Get account by account number
     */

    public AccountResponse getAccount(String accountNumber) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->new RuntimeException("Account not found"));

        return mapToResponce(account);
    }

    /*
        Get account bakance

     */

    public BigDecimal getBalance(String accountNumber) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->new RuntimeException("Account not found"));

        return account.getBalance();
    }

    /*
     Block account - called by fraud detection service via kafka

     */

    public void blockAccount(String accountNumber) {

        log.info("Blocing account: {}",accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->new RuntimeException("Account not found"));

        account.setStatus(AccountSatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked {}",accountNumber);
    }

     public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting Balance {} from account: {}",amount,accountNumber);

         Account account = accountRepository.findByAccountNumber(accountNumber)
                 .orElseThrow(() ->new RuntimeException("Account not found"));

         if(account.getStatus() != AccountSatus.ACTIVE) {
             throw new RuntimeException("Account is not active"+accountNumber);
         }

         if(account.getBalance().compareTo(amount) < 0) {
             throw new RuntimeException("Insufficient funds for account "+accountNumber);

         }

         account.setBalance(account.getBalance().subtract(amount));
         accountRepository.save(account);

         log.info("Balance updated and new Balance is {} ",account.getBalance());
     }

    /**
     * Credit balancce
     * Called by Transaction service via kafka
     * @param accountNumber
     * @param amount
     */

    public void creditBalance(String accountNumber, BigDecimal amount) {

        log.info("Crediting {} to account: {}", amount,accountNumber);
         Account account = accountRepository.findByAccountNumber(accountNumber)
                 .orElseThrow(() ->new RuntimeException("Account not found"));

         account.setBalance(account.getBalance().add(amount));
         accountRepository.save(account);

         log.info("Balance Credited. New balance is {} ",account.getBalance());

     }
}
