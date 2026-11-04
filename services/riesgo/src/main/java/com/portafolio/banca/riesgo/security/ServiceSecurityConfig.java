package com.portafolio.banca.riesgo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ServiceSecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/internal/v1/evaluaciones")
                        .access((authentication, context) ->
                                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken token
                                        && "transferencias-service"
                                                .equals(token.getToken().getClaimAsString("azp"))))
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
