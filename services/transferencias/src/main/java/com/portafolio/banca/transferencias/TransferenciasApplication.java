package com.portafolio.banca.transferencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransferenciasApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransferenciasApplication.class, args);
    }
}
