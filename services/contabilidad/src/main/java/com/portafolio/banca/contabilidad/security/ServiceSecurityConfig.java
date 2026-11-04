package com.portafolio.banca.contabilidad.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        .requestMatchers(HttpMethod.GET, "/internal/v1/cuentas", "/internal/v1/cuentas/**")
                        .access((authentication, context) ->
                                new AuthorizationDecision(isTransferenciasService(authentication.get())))
                        .requestMatchers("/internal/v1/movimientos/**")
                        .access((authentication, context) ->
                                new AuthorizationDecision(isTransferenciasService(authentication.get())))
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    private boolean isTransferenciasService(org.springframework.security.core.Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token
                && "transferencias-service".equals(token.getToken().getClaimAsString("azp"));
    }
}
