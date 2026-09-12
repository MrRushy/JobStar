package com.jobstar.backend.controller;

import java.util.List;

import com.jobstar.backend.model.Contact;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.service.ContactService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications/{applicationId}/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public List<Contact> getAllContacts(@PathVariable Long applicationId, @AuthenticationPrincipal UserAccount currentUser) {
        return contactService.getAllContacts(applicationId, currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Contact createContact(@PathVariable Long applicationId, @RequestBody Contact contact,
            @AuthenticationPrincipal UserAccount currentUser) {
        return contactService.createContact(applicationId, contact, currentUser);
    }

    @PutMapping("/{contactId}")
    public Contact updateContact(@PathVariable Long applicationId, @PathVariable Long contactId,
            @RequestBody Contact contact, @AuthenticationPrincipal UserAccount currentUser) {
        return contactService.updateContact(applicationId, contactId, contact, currentUser);
    }

    @DeleteMapping("/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContact(@PathVariable Long applicationId, @PathVariable Long contactId,
            @AuthenticationPrincipal UserAccount currentUser) {
        contactService.deleteContact(applicationId, contactId, currentUser);
    }
}
