package com.banking.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetTpinRequest {

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "TPIN is required")
    @Pattern(regexp = "^\\d{4}$", message = "TPIN must be a 4-digit numeric code")
    private String tpin;
}