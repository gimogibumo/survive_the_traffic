package com.survive_the_traffic.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
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

                        /*
                         * 인증 진입점
                         */
                        .requestMatchers(
                            "/api/auth/signup",
                            "/api/auth/login",
                            "/api/csrf"
                        )
                        .permitAll()

                        /*
                         * 공연 조회는 비로그인 사용자도 가능
                         */
                        .requestMatchers(
                            HttpMethod.GET,
                            "/api/concerts/**"
                        )
                        .permitAll()

                        /*
                         * 그 외 API는 로그인 필요
                         */
                        .anyRequest()
                        .authenticated()
            )

            /*
             * 세션 기반 SecurityContext 저장
             */
            .securityContext(
                securityContext ->
                    securityContext
                        .securityContextRepository(
                            securityContextRepository
                        )
            )

            /*
             * SPA + Cookie 기반 CSRF
             */
            .csrf(
                csrf ->
                    csrf
                        .spa()
                        .csrfTokenRepository(
                            csrfTokenRepository
                        )
            )

            /*
             * REST API에서는 인증되지 않은 요청에
             * redirect가 아니라 401을 반환
             */
            .exceptionHandling(
                exception ->
                    exception
                        .authenticationEntryPoint(
                            new HttpStatusEntryPoint(
                                HttpStatus.UNAUTHORIZED
                            )
                        )
            )

            /*
             * Spring Security logout pipeline
             */
            .logout(
                logout ->
                    logout
                        .logoutUrl(
                            "/api/auth/logout"
                        )
                        .logoutSuccessHandler(
                            new HttpStatusReturningLogoutSuccessHandler(
                                HttpStatus.NO_CONTENT
                            )
                        )
                        .permitAll()
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