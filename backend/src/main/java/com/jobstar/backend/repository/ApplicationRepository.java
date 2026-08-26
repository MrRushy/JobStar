package com.jobstar.backend.repository;

import java.util.List;
import java.util.Optional;

import com.jobstar.backend.model.Application;
import com.jobstar.backend.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByOwnerOrderByIdDesc(UserAccount owner);

    List<Application> findAllByOwnerIsNull();

    Optional<Application> findByIdAndOwner(Long id, UserAccount owner);
}
