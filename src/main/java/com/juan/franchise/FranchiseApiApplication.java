package com.juan.franchise;

import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FranchiseApiApplication {

	public static void main(String[] args) {
		// Validation messages follow the JVM locale; pin it so errors are the same on any machine.
		Locale.setDefault(Locale.ENGLISH);
		SpringApplication.run(FranchiseApiApplication.class, args);
	}

}
