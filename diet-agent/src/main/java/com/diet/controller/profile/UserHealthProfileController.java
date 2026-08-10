package com.diet.controller.profile;

import com.diet.model.UserHealthProfileRequest;
import com.diet.model.UserHealthProfileResponse;
import com.diet.security.CurrentUser;
import com.diet.service.profile.UserHealthProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/profile")
public class UserHealthProfileController {

    private final UserHealthProfileService profileService;

    public UserHealthProfileController(UserHealthProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserHealthProfileResponse get(Authentication authentication) {
        return profileService.findByUserId(CurrentUser.require(authentication).id());
    }

    @PutMapping
    public UserHealthProfileResponse save(
            Authentication authentication,
            @RequestBody UserHealthProfileRequest request
    ) {
        return profileService.save(CurrentUser.require(authentication).id(), request);
    }
}
