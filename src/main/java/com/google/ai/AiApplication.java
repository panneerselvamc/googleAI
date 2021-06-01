package com.google.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;


@SpringBootApplication
public class AiApplication {

	@Autowired
	TestGoogleAi testGoogleAi;

	public static void main(String[] args) {
		SpringApplication.run(AiApplication.class, args);
	}
	@Bean
	public void processDoc() throws IOException {
		testGoogleAi.test();
	}

}
