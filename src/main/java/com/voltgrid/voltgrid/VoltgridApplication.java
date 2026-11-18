package com.voltgrid.voltgrid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoltgridApplication {

	public static void main(String[] args) {
		SpringApplication.run(VoltgridApplication.class, args);
	}

}
