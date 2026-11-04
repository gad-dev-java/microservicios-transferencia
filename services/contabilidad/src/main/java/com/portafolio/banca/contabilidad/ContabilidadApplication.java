package com.portafolio.banca.contabilidad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContabilidadApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContabilidadApplication.class, args);
    }
}
