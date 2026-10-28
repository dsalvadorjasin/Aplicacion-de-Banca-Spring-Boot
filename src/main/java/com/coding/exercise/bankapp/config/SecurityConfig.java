package com.coding.exercise.bankapp.config;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue;

/**
 * 
 * Spring security denied access to h2-console.
 * This configuration will resolve 403 forbidden error when accessing h2-console.
 * 
 * @author sbathina
 *
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(antMatcher("/")).permitAll()
                        .requestMatchers(antMatcher("/h2-console/**")).permitAll()
                        // Security 5 let unmatched requests through; Security 6 denies them by default.
                        .anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.disable())
                        // Security 6 defaults X-XSS-Protection to "0"; keep the Security 5 value.
                        .xssProtection(xss -> xss.headerValue(HeaderValue.ENABLED_MODE_BLOCK)));
        return httpSecurity.build();
    }
}
