package com.postiva.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrantProRequest(
        @Min(value = 1, message = "Số ngày Pro phải từ 1 đến 366")
        @Max(value = 366, message = "Số ngày Pro phải từ 1 đến 366") int days,
        @NotBlank(message = "Vui lòng nhập lý do")
        @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự") String reason
) {
}
