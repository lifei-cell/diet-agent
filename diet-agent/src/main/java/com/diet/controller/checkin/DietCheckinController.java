package com.diet.controller.checkin;

import com.diet.model.CheckinConfirmRequest;
import com.diet.model.DailyCheckinSummary;
import com.diet.model.DietCheckinResponse;
import com.diet.model.ImageRecognitionResponse;
import com.diet.security.CurrentUser;
import com.diet.service.checkin.DietCheckinService;
import com.diet.service.storage.CheckinImageStorage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/diet/checkins")
public class DietCheckinController {
    private final DietCheckinService checkinService;

    public DietCheckinController(DietCheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping(value = "/recognitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageRecognitionResponse recognize(
            Authentication authentication,
            @RequestParam("image") MultipartFile image
    ) {
        return checkinService.recognize(CurrentUser.require(authentication).id(), image);
    }

    @PostMapping
    public DietCheckinResponse confirm(Authentication authentication, @RequestBody CheckinConfirmRequest request) {
        return checkinService.confirm(CurrentUser.require(authentication).id(), request);
    }

    @GetMapping
    public DailyCheckinSummary findDaily(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return checkinService.findDailySummary(CurrentUser.require(authentication).id(), date);
    }

    @DeleteMapping("/{checkinId}")
    public void delete(Authentication authentication, @PathVariable Long checkinId) {
        checkinService.delete(CurrentUser.require(authentication).id(), checkinId);
    }

    @GetMapping("/{checkinId}/image")
    public org.springframework.http.ResponseEntity<byte[]> image(Authentication authentication, @PathVariable Long checkinId) {
        CheckinImageStorage.StoredImage image = checkinService.loadImage(CurrentUser.require(authentication).id(), checkinId);
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .body(image.data());
    }
}
