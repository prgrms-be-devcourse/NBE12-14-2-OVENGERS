package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;

public record ImageUpdateResult(
        String oldImagePath,
        SpaceDetailResponse spaceDetailResponse
) {}
