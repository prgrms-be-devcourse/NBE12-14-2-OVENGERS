package com.ovengers.slotkey.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1단계 배포(hello world) 확인용 임시 엔드포인트.
 */
@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello World";
    }
}
