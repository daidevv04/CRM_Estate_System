package com.estatecrm.user_service.repository;

import com.estatecrm.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Modifying
    @Query("update User u set u.createdBy = null where u.createdBy.id = :userId")
    int clearCreatedBy(@Param("userId") UUID userId);

    @Modifying
    @Query("update User u set u.updatedBy = null where u.updatedBy.id = :userId")
    int clearUpdatedBy(@Param("userId") UUID userId);
}