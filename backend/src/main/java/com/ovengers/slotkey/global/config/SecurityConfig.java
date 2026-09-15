package com.ovengers.slotkey.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorizeHttpRequests) ->
                        authorizeHttpRequests
                                .requestMatchers("/favicon.ico").permitAll()

                                // 인증 없이 접근가능한 api 모음
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/auth/signup",
                                        "/api/v1/auth/login",
                                        "/api/v1/auth/refresh"
                                ).permitAll()

                                // 관리자 API는 관리자 권한 필요
                                .requestMatchers("/api/v1/admin/**")
                                .hasRole("ADMIN")

                                // 나머지 요청은 인증 필요
                                .anyRequest().authenticated()
                )

                // Authorization 헤더 기반 인증을 전제로 설정
                .csrf((csrf) -> csrf.disable())

                .formLogin((formLogin) -> formLogin.disable())
                .httpBasic((httpBasic) -> httpBasic.disable())

                // 인증 정보를 HTTP 세션에 저장하지 않음
                .sessionManagement((sessionManagement) ->
                        sessionManagement.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling((exceptionHandling) ->
                        exceptionHandling
                                // 인증되지 않은 사용자의 접근
                                .authenticationEntryPoint(
                                        (request, response, authenticationException) -> {
                                            response.setContentType(
                                                    "application/json; charset=UTF-8"
                                            );
                                            response.setStatus(401);
                                            response.getWriter().write(
                                                    """
                                                    {
                                                        "resultCode": "401-1",
                                                        "msg": "로그인 후 이용해주세요."
                                                    }
                                                    """
                                            );
                                        }
                                )

                                // 인증됐지만 필요한 권한이 없는 사용자의 접근
                                .accessDeniedHandler(
                                        (request, response, accessDeniedException) -> {
                                            response.setContentType(
                                                    "application/json; charset=UTF-8"
                                            );
                                            response.setStatus(403);
                                            response.getWriter().write(
                                                    """
                                                    {
                                                        "resultCode": "403-1",
                                                        "msg": "권한이 없습니다."
                                                    }
                                                    """
                                            );
                                        }
                                )
                );

        return http.build();
    }
}