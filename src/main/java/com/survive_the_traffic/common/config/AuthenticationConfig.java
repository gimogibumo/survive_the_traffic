package com.survive_the_traffic.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
public class AuthenticationConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
            new DaoAuthenticationProvider(
                userDetailsService
            );

        provider.setPasswordEncoder(
            passwordEncoder
        );

        return new ProviderManager(
            provider
        );
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
        CsrfTokenRepository csrfTokenRepository
    ) {

        ChangeSessionIdAuthenticationStrategy sessionFixationStrategy =
            new ChangeSessionIdAuthenticationStrategy();

        CsrfAuthenticationStrategy csrfStrategy =
            new CsrfAuthenticationStrategy(
                csrfTokenRepository
            );

        return new CompositeSessionAuthenticationStrategy(
            List.of(
                sessionFixationStrategy,
                csrfStrategy
            )
        );
    }
}