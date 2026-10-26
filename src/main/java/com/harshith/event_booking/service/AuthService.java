package com.harshith.event_booking.service;

import com.harshith.event_booking.dto.LoginRequest;
import com.harshith.event_booking.dto.RegisterRequest;
import com.harshith.event_booking.dto.TokenResponse;
import com.harshith.event_booking.dto.UserResponse;
import com.harshith.event_booking.exception.EmailAlreadyUsedException;
import com.harshith.event_booking.exception.InvalidCredentialsException;
import com.harshith.event_booking.model.AppUser;
import com.harshith.event_booking.repository.UserRepository;
import com.harshith.event_booking.security.TokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // Used when the email is unknown, so a failed login takes about as long either way.
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            AppUser user = userRepository.saveAndFlush(new AppUser(email, passwordEncoder.encode(request.password())));
            return UserResponse.from(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the UNIQUE constraint catches it.
            throw new EmailAlreadyUsedException();
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return new TokenResponse(tokenService.createToken(user), "Bearer", TokenService.TOKEN_LIFETIME.toSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                // Token is valid but the account is gone: treat as not logged in.
                .orElseThrow(InvalidCredentialsException::new);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
