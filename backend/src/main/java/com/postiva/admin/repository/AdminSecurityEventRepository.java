package com.postiva.admin.repository;

import com.postiva.admin.entity.AdminSecurityEvent;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface AdminSecurityEventRepository extends Repository<AdminSecurityEvent, UUID> {
    AdminSecurityEvent save(AdminSecurityEvent event);
}
