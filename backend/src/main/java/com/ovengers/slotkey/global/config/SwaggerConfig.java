package com.ovengers.slotkey.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI slotKeyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SlotKey API")
                        .description("공간 예약 및 크레딧 결제 서비스 API 문서")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("로그인 응답의 accessToken을 입력하세요. Bearer 접두사는 제외합니다.")
                        ));
    }
}