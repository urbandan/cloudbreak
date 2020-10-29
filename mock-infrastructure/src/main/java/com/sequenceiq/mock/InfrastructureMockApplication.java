package com.sequenceiq.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InfrastructureMockApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            SpringApplication.run(InfrastructureMockApplication.class);
        } else {
            SpringApplication.run(InfrastructureMockApplication.class, args);
        }
    }
}
