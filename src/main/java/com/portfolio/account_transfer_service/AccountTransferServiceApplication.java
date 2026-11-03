package com.portfolio.account_transfer_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class AccountTransferServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountTransferServiceApplication.class, args);
	}

}
