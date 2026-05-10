package com.hss.scalecart;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScalecartApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScalecartApplication.class, args);
	}

	// To Resume from making the order service things.
}
