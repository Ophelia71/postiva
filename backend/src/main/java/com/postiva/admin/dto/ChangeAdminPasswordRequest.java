package com.postiva.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeAdminPasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword
) {
}
