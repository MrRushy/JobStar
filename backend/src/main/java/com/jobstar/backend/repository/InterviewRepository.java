package com.jobstar.backend.repository;

import java.util.List;
import java.util.Optional;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findAllByApplicationOrderByScheduledAtAsc(Application application);

    Optional<Interview> findByIdAndApplication(Long id, Application application);
}
