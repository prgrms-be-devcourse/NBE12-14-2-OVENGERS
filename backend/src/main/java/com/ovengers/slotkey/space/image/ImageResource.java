package com.ovengers.slotkey.space.image;

import org.springframework.core.io.Resource;

public record ImageResource(
        Resource resource,
        byte[] bytes,
        String contentType,
        long contentLength
) {}
