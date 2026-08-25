package com.jobstar.backend.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ApplicationRepository;
import com.jobstar.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void firstRegistrationClaimsExistingOwnerlessApplications() {
        Application existingApplication = new Application();
        when(userAccountRepository.findByEmail("me@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("long-enough-password")).thenReturn("hashed-password");
        when(userAccountRepository.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationRepository.findAllByOwnerIsNull()).thenReturn(List.of(existingApplication));

        UserAccount account = userAccountService.register(" Me@Example.com ", "long-enough-password");

        assertSame(account, existingApplication.getOwner());
        verify(userAccountRepository).save(account);
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        when(userAccountRepository.findByEmail("me@example.com"))
                .thenReturn(Optional.of(new UserAccount("me@example.com", "hashed-password")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userAccountService.register("me@example.com", "long-enough-password"));

        assertSame(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
