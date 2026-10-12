package com.frotafantasma.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FrotaFantasmaApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrotaFantasmaApplication.class, args);
    }
}

