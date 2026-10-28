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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock TokenService tokenService;

    // Real BCrypt (low cost factor so tests stay fast), wrapped in a spy so calls can be checked.
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, tokenService);
        clearInvocations(passwordEncoder); // ignore the dummy-hash encode done in the constructor
    }

    @Test
    void register_lowercasesEmail_andStoresABcryptHashNotThePassword() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(call -> {
            AppUser saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        UserResponse response = service.register(new RegisterRequest("  Alice@Example.COM ", "password123"));

        assertThat(response).isEqualTo(new UserResponse(1L, "alice@example.com"));
        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash())
                .isNotEqualTo("password123")
                .startsWith("$2a$");
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void register_duplicateEmail_isRejected() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ALICE@example.com", "password123")))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_twoSignupsAtTheSameMoment_uniqueConstraintBecomesDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice@example.com", "password123")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void login_withTheRightPassword_returnsA60MinuteBearerToken() {
        AppUser alice = storedUser(1L, "alice@example.com", "password123");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(tokenService.createToken(alice)).thenReturn("signed.jwt.token");

        TokenResponse response = service.login(new LoginRequest("Alice@Example.com", "password123"));

        assertThat(response).isEqualTo(new TokenResponse("signed.jwt.token", "Bearer", 3600));
    }

    @Test
    void login_wrongPassword_and_unknownEmail_failTheSameWay() {
        AppUser alice = storedUser(1L, "alice@example.com", "password123");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Throwable wrongPassword = catchThrowable(() ->
                service.login(new LoginRequest("alice@example.com", "wrong-password")));
        Throwable unknownEmail = catchThrowable(() ->
                service.login(new LoginRequest("nobody@example.com", "wrong-password")));

        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownEmail).hasMessage(wrongPassword.getMessage()).hasMessage("Invalid email or password");
        verifyNoInteractions(tokenService);
    }

    @Test
    void login_unknownEmail_stillRunsABcryptCheck_soTimingDoesNotRevealWhichEmailsExist() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "whatever1")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches(eq("whatever1"), anyString());
    }

    @Test
    void currentUser_returnsTheAccount_orRejectsATokenForADeletedUser() {
        AppUser alice = storedUser(1L, "alice@example.com", "password123");
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(service.currentUser(1L)).isEqualTo(new UserResponse(1L, "alice@example.com"));
        assertThatThrownBy(() -> service.currentUser(2L)).isInstanceOf(InvalidCredentialsException.class);
    }

    private AppUser storedUser(long id, String email, String password) {
        AppUser user = new AppUser(email, new BCryptPasswordEncoder(4).encode(password));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
