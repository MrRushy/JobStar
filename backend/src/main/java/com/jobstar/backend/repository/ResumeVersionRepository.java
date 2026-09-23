package com.jobstar.backend.repository;

import java.util.List;
import java.util.Optional;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {

    List<ResumeVersion> findAllByApplicationOrderByIdDesc(Application application);

    Optional<ResumeVersion> findByIdAndApplication(Long id, Application application);
}
