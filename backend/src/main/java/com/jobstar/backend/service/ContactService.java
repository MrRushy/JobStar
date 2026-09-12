package com.jobstar.backend.service;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.Contact;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ContactRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactService {

    private final ApplicationService applicationService;
    private final ContactRepository contactRepository;

    public ContactService(ApplicationService applicationService, ContactRepository contactRepository) {
        this.applicationService = applicationService;
        this.contactRepository = contactRepository;
    }

    public List<Contact> getAllContacts(Long applicationId, UserAccount owner) {
        return contactRepository.findAllByApplicationOrderByNameAsc(findApplication(applicationId, owner));
    }

    public Contact createContact(Long applicationId, Contact contact, UserAccount owner) {
        normalizeAndValidate(contact);
        contact.setApplication(findApplication(applicationId, owner));
        return contactRepository.save(contact);
    }

    public Contact updateContact(Long applicationId, Long contactId, Contact updatedContact, UserAccount owner) {
        Application application = findApplication(applicationId, owner);
        Contact contact = findContact(contactId, application);
        normalizeAndValidate(updatedContact);

        contact.setName(updatedContact.getName());
        contact.setRole(updatedContact.getRole());
        contact.setEmail(updatedContact.getEmail());
        contact.setProfileUrl(updatedContact.getProfileUrl());
        contact.setNotes(updatedContact.getNotes());
        return contactRepository.save(contact);
    }

    public void deleteContact(Long applicationId, Long contactId, UserAccount owner) {
        contactRepository.delete(findContact(contactId, findApplication(applicationId, owner)));
    }

    private Application findApplication(Long applicationId, UserAccount owner) {
        return applicationService.getApplicationById(applicationId, owner);
    }

    private Contact findContact(Long contactId, Application application) {
        return contactRepository.findByIdAndApplication(contactId, application)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact was not found"));
    }

    private void normalizeAndValidate(Contact contact) {
        if (contact.getName() == null || contact.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact name is required");
        }

        contact.setName(contact.getName().trim());
        contact.setRole(normalizeOptionalText(contact.getRole()));
        contact.setEmail(normalizeOptionalText(contact.getEmail()));
        contact.setProfileUrl(normalizeOptionalText(contact.getProfileUrl()));
        contact.setNotes(normalizeOptionalText(contact.getNotes()));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
