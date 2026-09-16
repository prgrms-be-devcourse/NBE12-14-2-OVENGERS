package com.ovengers.slotkey.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.security.CustomAuthenticationFilter;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // JWT 인증을 처리할 필터 생성
        CustomAuthenticationFilter customAuthenticationFilter =
                new CustomAuthenticationFilter(
                        jwtProvider,
                        memberRepository,
                        objectMapper
                );

        http
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/favicon.ico").permitAll()

                        // 액세스 토큰 없이 접근 가능한 API
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        // 관리자 권한 필요
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        // 나머지 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                .csrf(csrf -> csrf.disable())

                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())

                // 인증 정보를 HTTP 세션에 저장하지 않음
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // JWT 인증 필터를 시큐리티 필터 체인에 추가
                .addFilterBefore(
                        customAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .exceptionHandling(exception -> exception

                        // 인증되지 않은 사용자가 보호된 API에 접근
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(
                                    ErrorCode.AUTHENTICATION_REQUIRED
                                            .getHttpStatus().value()
                            );
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            objectMapper.writeValue(
                                    response.getWriter(),
                                    ApiResponse.fail(
                                            ErrorCode.AUTHENTICATION_REQUIRED
                                    )
                            );
                        })

                        // 인증됐지만 필요한 권한이 없음
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(
                                    ErrorCode.ACCESS_DENIED
                                            .getHttpStatus().value()
                            );
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            objectMapper.writeValue(
                                    response.getWriter(),
                                    ApiResponse.fail(ErrorCode.ACCESS_DENIED)
                            );
                        })
                );

        return http.build();
    }
}