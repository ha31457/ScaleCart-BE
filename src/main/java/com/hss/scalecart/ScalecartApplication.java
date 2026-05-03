package com.hss.scalecart;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ScalecartApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScalecartApplication.class, args);
	}
}
