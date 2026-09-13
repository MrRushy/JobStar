package com.jobstar.backend.service;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.Contact;
import com.jobstar.backend.model.FollowUpReminder;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.ContactRepository;
import com.jobstar.backend.repository.FollowUpReminderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FollowUpReminderService {

    private final ApplicationService applicationService;
    private final ContactRepository contactRepository;
    private final FollowUpReminderRepository followUpReminderRepository;

    public FollowUpReminderService(ApplicationService applicationService, ContactRepository contactRepository,
            FollowUpReminderRepository followUpReminderRepository) {
        this.applicationService = applicationService;
        this.contactRepository = contactRepository;
        this.followUpReminderRepository = followUpReminderRepository;
    }

    public List<FollowUpReminder> getAllReminders(Long applicationId, UserAccount owner) {
        return followUpReminderRepository.findAllByApplicationOrderByCompletedAscDueDateAsc(findApplication(applicationId, owner));
    }

    public List<FollowUpReminder> getOpenReminders(UserAccount owner) {
        return followUpReminderRepository.findAllByApplicationOwnerAndCompletedFalseOrderByDueDateAsc(owner);
    }

    public FollowUpReminder createReminder(Long applicationId, FollowUpReminder reminder, UserAccount owner) {
        Application application = findApplication(applicationId, owner);
        normalizeAndValidate(reminder);
        reminder.setApplication(application);
        reminder.setContact(findContact(reminder.getContactId(), application));
        return followUpReminderRepository.save(reminder);
    }

    public FollowUpReminder updateReminder(Long applicationId, Long reminderId, FollowUpReminder updatedReminder,
            UserAccount owner) {
        Application application = findApplication(applicationId, owner);
        FollowUpReminder reminder = findReminder(reminderId, application);
        normalizeAndValidate(updatedReminder);

        reminder.setDueDate(updatedReminder.getDueDate());
        reminder.setDescription(updatedReminder.getDescription());
        reminder.setCompleted(updatedReminder.isCompleted());
        reminder.setContact(findContact(updatedReminder.getContactId(), application));
        return followUpReminderRepository.save(reminder);
    }

    public void deleteReminder(Long applicationId, Long reminderId, UserAccount owner) {
        followUpReminderRepository.delete(findReminder(reminderId, findApplication(applicationId, owner)));
    }

    private Application findApplication(Long applicationId, UserAccount owner) {
        return applicationService.getApplicationById(applicationId, owner);
    }

    private FollowUpReminder findReminder(Long reminderId, Application application) {
        return followUpReminderRepository.findByIdAndApplication(reminderId, application)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follow-up reminder was not found"));
    }

    private Contact findContact(Long contactId, Application application) {
        if (contactId == null) {
            return null;
        }
        return contactRepository.findByIdAndApplication(contactId, application)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact was not found for this application"));
    }

    private void normalizeAndValidate(FollowUpReminder reminder) {
        if (reminder.getDueDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Follow-up due date is required");
        }
        if (reminder.getDescription() == null || reminder.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Follow-up description is required");
        }
        reminder.setDescription(reminder.getDescription().trim());
    }
}
