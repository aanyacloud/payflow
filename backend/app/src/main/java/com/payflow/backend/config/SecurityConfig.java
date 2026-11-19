
package com.payflow.backend.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(
            ObjectMapper objectMapper) {

        return (request, response, exception) ->
                writeErrorResponse(
                        request,
                        response,
                        objectMapper,
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required. Please provide a valid JWT token."
                );
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(
            ObjectMapper objectMapper) {

        return (request, response, exception) ->
                writeErrorResponse(
                        request,
                        response,
                        objectMapper,
                        HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource."
                );
    }

    private void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )

            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(Customizer.withDefaults())
            );

        return http.build();
    }
}
