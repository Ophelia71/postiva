package com.postiva.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUserStatusRequest(
        @NotBlank(message = "Vui lòng nhập lý do")
        @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự") String reason
) {
}
