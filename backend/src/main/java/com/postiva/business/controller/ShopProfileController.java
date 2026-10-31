package com.postiva.business.controller;

import com.postiva.business.dto.ShopProfileRequest;
import com.postiva.business.dto.ShopProfileResponse;
import com.postiva.business.service.ShopProfileService;
import com.postiva.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop-profile")
public class ShopProfileController {
    private final ShopProfileService shopProfileService;

    public ShopProfileController(ShopProfileService shopProfileService) {
        this.shopProfileService = shopProfileService;
    }

    /** data is null until the shop saves a profile for the first time. */
    @GetMapping
    public ApiResponse<ShopProfileResponse> get() {
        return ApiResponse.ok(shopProfileService.current().orElse(null));
    }

    @PutMapping
    public ApiResponse<ShopProfileResponse> save(@Valid @RequestBody ShopProfileRequest request) {
        return ApiResponse.ok(shopProfileService.save(request));
    }
}
