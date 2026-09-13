package com.jobstar.backend.repository;

import java.util.List;
import java.util.Optional;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.Contact;
import com.jobstar.backend.model.FollowUpReminder;
import com.jobstar.backend.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpReminderRepository extends JpaRepository<FollowUpReminder, Long> {

    List<FollowUpReminder> findAllByApplicationOrderByCompletedAscDueDateAsc(Application application);

    List<FollowUpReminder> findAllByApplicationOwnerAndCompletedFalseOrderByDueDateAsc(UserAccount owner);

    List<FollowUpReminder> findAllByContact(Contact contact);

    Optional<FollowUpReminder> findByIdAndApplication(Long id, Application application);
}
