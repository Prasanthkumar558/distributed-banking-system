package com.banking.transactionservice.config;

import com.banking.transactionservice.exception.BankingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.InputStream;

@Slf4j
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = "Downstream service error";
        HttpStatus status = HttpStatus.resolve(response.status());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        try {
            if (response.body() != null) {
                try (InputStream bodyIs = response.body().asInputStream()) {
                    JsonNode jsonNode = objectMapper.readTree(bodyIs);
                    if (jsonNode.has("message")) {
                        message = jsonNode.get("message").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to decode downstream error body: {}", e.getMessage());
        }

        return new BankingException(message, status);
    }
}