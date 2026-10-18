package com.postiva.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyRegisterRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Mã xác thực không được để trống")
        @Pattern(regexp = "\\d{6}", message = "Mã xác thực phải gồm 6 chữ số")
        String code
) {
}
