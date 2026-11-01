package com.banking.transactionservice.client;


import com.banking.transactionservice.config.FeignConfig;
import com.banking.transactionservice.dto.VerifyTpinRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@FeignClient(name = "account-service", url = "${account.service.url}", configuration = FeignConfig.class)
public interface AccountServiceClient {

    @PutMapping("/api/v1/accounts/{accountNumber}/deduct")
    String deductBalance(
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam("amount") BigDecimal amount
    );

    @PutMapping("/api/v1/accounts/{accountNumber}/credit")
    String creditBalance(
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam("amount") BigDecimal amount
    );

    @PostMapping("/api/v1/accounts/tpin/verify")
    Boolean verifyTpin(@RequestBody VerifyTpinRequest request);

}
