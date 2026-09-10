package com.jobstar.backend.service;

import java.util.List;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.Interview;
import com.jobstar.backend.model.InterviewType;
import com.jobstar.backend.model.UserAccount;
import com.jobstar.backend.repository.InterviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InterviewService {

    private final ApplicationService applicationService;
    private final InterviewRepository interviewRepository;

    public InterviewService(ApplicationService applicationService, InterviewRepository interviewRepository) {
        this.applicationService = applicationService;
        this.interviewRepository = interviewRepository;
    }

    public List<Interview> getAllInterviews(Long applicationId, UserAccount owner) {
        return interviewRepository.findAllByApplicationOrderByScheduledAtAsc(findApplication(applicationId, owner));
    }

    public Interview createInterview(Long applicationId, Interview interview, UserAccount owner) {
        normalizeAndValidate(interview);
        interview.setApplication(findApplication(applicationId, owner));
        return interviewRepository.save(interview);
    }

    public Interview updateInterview(Long applicationId, Long interviewId, Interview updatedInterview, UserAccount owner) {
        Application application = findApplication(applicationId, owner);
        Interview interview = findInterview(interviewId, application);
        normalizeAndValidate(updatedInterview);

        interview.setScheduledAt(updatedInterview.getScheduledAt());
        interview.setType(updatedInterview.getType());
        interview.setInterviewer(updatedInterview.getInterviewer());
        interview.setNotes(updatedInterview.getNotes());
        return interviewRepository.save(interview);
    }

    public void deleteInterview(Long applicationId, Long interviewId, UserAccount owner) {
        interviewRepository.delete(findInterview(interviewId, findApplication(applicationId, owner)));
    }

    private Application findApplication(Long applicationId, UserAccount owner) {
        return applicationService.getApplicationById(applicationId, owner);
    }

    private Interview findInterview(Long interviewId, Application application) {
        return interviewRepository.findByIdAndApplication(interviewId, application)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview was not found"));
    }

    private void normalizeAndValidate(Interview interview) {
        if (interview.getScheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview date and time are required");
        }
        if (interview.getType() == null) {
            interview.setType(InterviewType.OTHER);
        }
        interview.setInterviewer(normalizeOptionalText(interview.getInterviewer()));
        interview.setNotes(normalizeOptionalText(interview.getNotes()));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
