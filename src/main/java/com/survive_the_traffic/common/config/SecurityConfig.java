package com.survive_the_traffic.common.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityContextRepository securityContextRepository,
        CsrfTokenRepository csrfTokenRepository,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(
                authorize -> authorize
                    .requestMatchers(
                    "/api/auth/signup", "/api/auth/login", "/api/csrf").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/concerts/**").permitAll()
                    .anyRequest().authenticated()
            )

            /*
             * 세션 기반 SecurityContext 저장
             */
            .securityContext(
                securityContext -> securityContext
                        .securityContextRepository(
                            securityContextRepository
                        )
            )

            /*
             * SPA + Cookie 기반 CSRF
             */
            .csrf(csrf -> csrf
                .spa()
                .csrfTokenRepository(
                    csrfTokenRepository
                )
            )

            /*
             * REST API에서는 인증되지 않은 요청에
             * redirect가 아니라 401을 반환
             */
            .exceptionHandling(exception ->
                exception
                    .authenticationEntryPoint(
                        new HttpStatusEntryPoint(
                            HttpStatus.UNAUTHORIZED))
            )

            /*
             * Spring Security logout pipeline
             */
            .logout(logout -> logout
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

            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable());

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

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(corsProperties.allowedOrigins());

        configuration.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));

        configuration.setAllowedHeaders(List.of("Content-type","X-XSRF-TOKEN"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}