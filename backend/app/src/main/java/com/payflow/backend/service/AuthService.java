
package com.payflow.backend.service;

import com.payflow.backend.dto.AuthResponse;
import com.payflow.backend.dto.LoginRequest;
import com.payflow.backend.dto.RegisterRequest;
import com.payflow.backend.entity.User;
import com.payflow.backend.repository.UserRepository;
import com.payflow.backend.security.JwtService;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                "USER"
        );

        user = userRepository.save(user);

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                "Bearer",
                token
        );
    }

    public AuthResponse login(LoginRequest request) {

        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password")
                );

        if (!passwordEncoder.matches(
                request.password(), user.getPassword())) {
            throw new BadCredentialsException(
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                "Bearer",
                token
        );
    }
}
