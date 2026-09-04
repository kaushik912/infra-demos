package com.example.outbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // enables the @Scheduled outbox relay poller
public class OutboxDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(OutboxDemoApplication.class, args);
	}

}