package com.postiva.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRuntimeSwitchRequest(boolean enabled, @NotBlank @Size(max = 1000) String reason) { }
