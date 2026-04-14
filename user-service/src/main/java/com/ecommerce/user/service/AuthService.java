package com.ecommerce.user.service;

import com.ecommerce.shared.security.JwtUtil;
import com.ecommerce.user.model.dto.AuthResponse;
import com.ecommerce.user.model.dto.LoginRequest;
import com.ecommerce.user.model.dto.RegisterRequest;
import com.ecommerce.user.model.entity.UserEntity;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new RuntimeException("Email already registered: " + req.email());
        }

        String hash = passwordEncoder.encode(req.password());
        UserEntity user = UserEntity.create(req.email(), hash, req.name());
        userRepo.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getName());
        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());
        return new AuthResponse(user.getId(), user.getEmail(), user.getName(), token);
    }

    public AuthResponse login(LoginRequest req) {
        UserEntity user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getName());
        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return new AuthResponse(user.getId(), user.getEmail(), user.getName(), token);
    }
}
