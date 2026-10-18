package com.postiva.admin.repository;

import com.postiva.admin.entity.AdminMfaState;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;

public interface AdminMfaRepository extends Repository<AdminMfaState, UUID> {
    Optional<AdminMfaState> findById(UUID id);
    AdminMfaState save(AdminMfaState state);
}
