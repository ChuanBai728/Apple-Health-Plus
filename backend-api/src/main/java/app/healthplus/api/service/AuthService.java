package app.healthplus.api.service;

import app.healthplus.api.dto.AuthDtos.*;
import app.healthplus.api.repository.UserRepository;
import app.healthplus.api.security.JwtTokenProvider;
import app.healthplus.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwtProvider;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtTokenProvider jwtProvider) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepo.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = new User(req.username(), req.email(), encoder.encode(req.password()));
        user = userRepo.save(user);
        String token = jwtProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, "Bearer", user.getId().toString(), user.getUsername(), user.getRole());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }
        String token = jwtProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, "Bearer", user.getId().toString(), user.getUsername(), user.getRole());
    }
}
