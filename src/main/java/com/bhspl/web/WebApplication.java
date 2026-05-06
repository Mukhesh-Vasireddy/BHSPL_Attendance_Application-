package com.bhspl.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.bhspl"})
public class WebApplication {
    public static void start(String[] args) {
        System.out.println("WebApplication: Starting Spring Boot thread...");
        Thread t = new Thread(() -> {
            try {
                SpringApplication.run(WebApplication.class, args);
                System.out.println("WebApplication: Spring Boot started successfully.");
            } catch (Exception e) {
                System.err.println("WebApplication: Failed to start Spring Boot: " + e.getMessage());
                e.printStackTrace();
            }
        });
        t.setName("Spring-Boot-Launcher");
        t.setDaemon(true);
        t.start();
    }
}
