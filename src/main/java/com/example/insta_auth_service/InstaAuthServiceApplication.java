package com.example.insta_auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InstaAuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InstaAuthServiceApplication.class, args);
	}

}
