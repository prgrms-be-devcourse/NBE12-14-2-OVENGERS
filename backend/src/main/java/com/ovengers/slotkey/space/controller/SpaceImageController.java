package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.space.image.ImageResource;
import com.ovengers.slotkey.space.image.SpaceImageStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Tag(name = "공간 이미지", description = "공간 대표 이미지 서빙 API")
@RestController
@RequestMapping("/api/v1/space-images")
@RequiredArgsConstructor
public class SpaceImageController {

    private final SpaceImageStorage spaceImageStorage;

    @Operation(summary = "공간 이미지 조회 (공개)")
    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable("fileName") String fileName) {
        ImageResource imageResource = spaceImageStorage.load(fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageResource.contentType()))
                .contentLength(imageResource.contentLength())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(imageResource.resource());
    }
}
