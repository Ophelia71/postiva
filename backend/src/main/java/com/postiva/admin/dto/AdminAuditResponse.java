package com.postiva.admin.dto;

import com.postiva.admin.entity.AdminAuditLog;
import com.postiva.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditResponse(UUID id, UUID actorUserId, String actorName, UUID targetUserId,
                                 AdminAuditLog.Action action, User.Status previousStatus, User.Status newStatus,
                                 String reason, Instant createdAt) {
    public static AdminAuditResponse from(AdminAuditLog entry, String actorName) {
        return new AdminAuditResponse(entry.getId(), entry.getActorUserId(), actorName, entry.getTargetUserId(),
                entry.getAction(), entry.getPreviousStatus(), entry.getNewStatus(), entry.getReason(), entry.getCreatedAt());
    }
}
