package com.jobstar.backend.controller;

import java.util.List;

import com.jobstar.backend.model.FollowUpReminder;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.service.FollowUpReminderService;
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
public class FollowUpReminderController {

    private final FollowUpReminderService followUpReminderService;

    public FollowUpReminderController(FollowUpReminderService followUpReminderService) {
        this.followUpReminderService = followUpReminderService;
    }

    @GetMapping("/api/follow-ups")
    public List<FollowUpReminder> getOpenReminders(@AuthenticationPrincipal UserAccount currentUser) {
        return followUpReminderService.getOpenReminders(currentUser);
    }

    @GetMapping("/api/applications/{applicationId}/follow-ups")
    public List<FollowUpReminder> getAllReminders(@PathVariable Long applicationId,
            @AuthenticationPrincipal UserAccount currentUser) {
        return followUpReminderService.getAllReminders(applicationId, currentUser);
    }

    @PostMapping("/api/applications/{applicationId}/follow-ups")
    @ResponseStatus(HttpStatus.CREATED)
    public FollowUpReminder createReminder(@PathVariable Long applicationId, @RequestBody FollowUpReminder reminder,
            @AuthenticationPrincipal UserAccount currentUser) {
        return followUpReminderService.createReminder(applicationId, reminder, currentUser);
    }

    @PutMapping("/api/applications/{applicationId}/follow-ups/{reminderId}")
    public FollowUpReminder updateReminder(@PathVariable Long applicationId, @PathVariable Long reminderId,
            @RequestBody FollowUpReminder reminder, @AuthenticationPrincipal UserAccount currentUser) {
        return followUpReminderService.updateReminder(applicationId, reminderId, reminder, currentUser);
    }

    @DeleteMapping("/api/applications/{applicationId}/follow-ups/{reminderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReminder(@PathVariable Long applicationId, @PathVariable Long reminderId,
            @AuthenticationPrincipal UserAccount currentUser) {
        followUpReminderService.deleteReminder(applicationId, reminderId, currentUser);
    }
}
