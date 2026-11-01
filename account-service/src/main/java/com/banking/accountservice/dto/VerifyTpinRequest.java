package com.banking.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyTpinRequest {

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "TPIN is required")
    private String tpin;
}