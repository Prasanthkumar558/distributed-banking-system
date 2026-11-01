package com.banking.accountservice;

import org.junit.jupiter.api.Test;

//@SpringBootTest
class AccountServiceApplicationTests {

	@Test
	void contextLoads() {
	}

    @Test
    void printHash() {
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

        String hash = encoder.encode("Prasanth@90");
        System.out.println("COPY_THIS_HASH -> " + hash);
    }

}
