package com.survive_the_traffic.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityContextRepository securityContextRepository,
        CsrfTokenRepository csrfTokenRepository
    ) throws Exception {

        http
            .authorizeHttpRequests(
                authorize ->
                    authorize
                        .anyRequest()
                        .permitAll()
            )
            .securityContext(
                securityContext ->
                    securityContext
                        .securityContextRepository(
                            securityContextRepository
                        )
            )
            .csrf(
                csrf ->
                    csrf
                        .spa()
                        .csrfTokenRepository(
                            csrfTokenRepository
                        )
            )
            .httpBasic(
                httpBasic ->
                    httpBasic.disable()
            )
            .formLogin(
                formLogin ->
                    formLogin.disable()
            );

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository
            .withHttpOnlyFalse();
    }
}