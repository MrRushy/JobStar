package com.jobstar.backend.controller;

import java.util.Map;

import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountService userAccountService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(UserAccountService userAccountService, SecurityContextRepository securityContextRepository) {
        this.userAccountService = userAccountService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@RequestBody CredentialsRequest credentials, HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount account = userAccountService.register(credentials.email(), credentials.password());
        saveAuthenticatedSession(account, request, response);
        return AccountResponse.from(account);
    }

    @PostMapping("/login")
    public AccountResponse login(@RequestBody CredentialsRequest credentials, HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount account = userAccountService.authenticate(credentials.email(), credentials.password());
        saveAuthenticatedSession(account, request, response);
        return AccountResponse.from(account);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public AccountResponse getCurrentAccount(@AuthenticationPrincipal UserAccount currentUser) {
        return AccountResponse.from(currentUser);
    }

    private void saveAuthenticatedSession(UserAccount account, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(account,
                null, account.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public record CredentialsRequest(String email, String password) {
    }

    public record AccountResponse(Long id, String email) {
        static AccountResponse from(UserAccount account) {
            return new AccountResponse(account.getId(), account.getEmail());
        }
    }
}
