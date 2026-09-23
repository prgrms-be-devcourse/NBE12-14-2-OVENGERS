package com.ovengers.slotkey.space.image;

public record StoredImage(
        String fileName,
        String relativeUrl,
        String contentType,
        long size,
        int width,
        int height
) {}
