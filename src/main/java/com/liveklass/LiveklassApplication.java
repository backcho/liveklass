package com.liveklass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LiveklassApplication {

	public static void main(String[] args) {
		SpringApplication.run(LiveklassApplication.class, args);
	}

}
