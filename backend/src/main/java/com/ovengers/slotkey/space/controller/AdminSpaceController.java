package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminSpaceController {

    @PostMapping("/spaces")
    public ResponseEntity<?> createSpace(
            @Valid @RequestBody SpaceCreateRequest request) {
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/spaces/{spaceId}")
    public ResponseEntity<?> updateSpace(
            @PathVariable Long spaceId,
            @Valid @RequestBody SpaceUpdateRequest request) {
        return ResponseEntity.ok().build();
    }
}
