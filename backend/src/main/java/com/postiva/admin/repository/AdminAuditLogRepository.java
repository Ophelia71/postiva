package com.postiva.admin.repository;

import com.postiva.admin.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface AdminAuditLogRepository extends Repository<AdminAuditLog, UUID> {
    AdminAuditLog save(AdminAuditLog entry);

    Page<AdminAuditLog> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId, Pageable pageable);
}
