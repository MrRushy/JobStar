package com.jobstar.backend.service;

import java.util.Locale;

import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ApplicationRepository;
import com.jobstar.backend.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAccountService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository userAccountRepository, ApplicationRepository applicationRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount register(String email, String password) {
        String normalizedEmail = normalizeAndValidateEmail(email);
        validatePassword(password);

        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
        }

        boolean isFirstAccount = userAccountRepository.count() == 0;
        UserAccount account = userAccountRepository.save(new UserAccount(normalizedEmail, passwordEncoder.encode(password)));

        if (isFirstAccount) {
            // Preserve the Phase 1 development entries when the first local account is created.
            applicationRepository.findAllByOwnerIsNull().forEach(application -> application.setOwner(account));
        }

        return account;
    }

    public UserAccount authenticate(String email, String password) {
        String normalizedEmail = normalizeAndValidateEmail(email);
        UserAccount account = userAccountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));

        if (password == null || !passwordEncoder.matches(password, account.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }

        return account;
    }

    @Override
    public UserAccount loadUserByUsername(String email) throws UsernameNotFoundException {
        return userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account exists for this email"));
    }

    private String normalizeAndValidateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.contains("@") || normalizedEmail.length() > 254) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        }

        return normalizedEmail;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters long");
        }
    }
}
