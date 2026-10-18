package com.postiva.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(max = 72)
        String password,
        @Size(max = 64) String mfaCode
) {
    public LoginRequest(String email, String password) { this(email, password, null); }
}
