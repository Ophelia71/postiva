package com.postiva.user.repository;

import com.postiva.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndRole(UUID id, User.Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from User account where account.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
