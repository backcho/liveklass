package com.liveklass;

import org.springframework.boot.SpringApplication;

public class TestLiveklassApplication {

	public static void main(String[] args) {
		SpringApplication.from(LiveklassApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
