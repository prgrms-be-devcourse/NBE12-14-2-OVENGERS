package com.ovengers.slotkey.global.security;

import com.ovengers.slotkey.global.config.SecurityConfig;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import com.ovengers.slotkey.global.security.jwt.JwtUtil;
import com.ovengers.slotkey.member.entity.MemberRole;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ErrorDispatchSecurityIntegrationTest.TestAppConfig.class
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "custom.jwt.secret-key=slotkey-test-only-secret-key-0123456789-abcdefghijklmnopqrstuvwxyz",
        "custom.jwt.expire-millis=300000",
        "server.error.include-message=never",
        "server.error.include-binding-errors=never",
        "server.error.include-stacktrace=never",
        "server.error.include-exception=false"
})
@DisplayName("실제 서블릿 컨테이너 ERROR 디스패치 보안 통합 테스트")
class ErrorDispatchSecurityIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtProvider.class})
    static class TestAppConfig {

        @Bean
        public TestErrorDispatchController testErrorDispatchController() {
            return new TestErrorDispatchController();
        }

        @Bean
        public DispatchRecordingFilter dispatchRecordingFilter() {
            return new DispatchRecordingFilter();
        }
    }

    @RestController
    @RequestMapping("/api/v1/admin/test-error-dispatch")
    static class TestErrorDispatchController {

        @GetMapping("/ok")
        public ResponseEntity<Map<String, String>> ok() {
            return ResponseEntity.ok(Map.of("message", "ok"));
        }

        @GetMapping("/throw")
        public ResponseEntity<Void> throwException() {
            throw new IllegalArgumentException("test-dispatch-error-identifier");
        }
    }

    static class DispatchRecordingFilter extends OncePerRequestFilter {
        static final List<RecordedDispatch> records = new CopyOnWriteArrayList<>();

        record RecordedDispatch(
                DispatcherType dispatcherType,
                String requestUri,
                int status
        ) {}

        @Override
        protected boolean shouldNotFilterAsyncDispatch() {
            return false;
        }

        @Override
        protected boolean shouldNotFilterErrorDispatch() {
            return false;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            try {
                filterChain.doFilter(request, response);
            } finally {
                records.add(new RecordedDispatch(
                        request.getDispatcherType(),
                        request.getRequestURI(),
                        response.getStatus()
                ));
            }
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    @BeforeEach
    void setUp() {
        DispatchRecordingFilter.records.clear();
    }

    private String createToken(Long id, String email, MemberRole role) {
        return JwtUtil.createToken(
                secretKey,
                300_000L,
                Map.of(
                        "id", id,
                        "email", email,
                        "role", role.name()
                )
        );
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @DisplayName("ADMIN 정상 요청은 200 OK를 반환한다")
    void adminOk_returns200() {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders(adminToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/test-error-dispatch/ok",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"message\":\"ok\"");
    }

    @Test
    @DisplayName("ADMIN 요청 중 미처리 예외 발생 시 컨테이너 ERROR 디스패치를 거쳐 500을 반환하며 401로 변환되지 않는다")
    void adminThrow_triggersErrorDispatchAndReturns500() {
        String adminToken = createToken(1L, "admin@test.com", MemberRole.ADMIN);
        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders(adminToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/test-error-dispatch/throw",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).doesNotContain("AUTHENTICATION_REQUIRED");
        assertThat(body).doesNotContain("test-dispatch-error-identifier");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("at com.ovengers");
        assertThat(body).doesNotContain(adminToken);

        // 컨테이너 ERROR 디스패치가 관찰 필터에 기록되었는지 확인
        boolean errorDispatched = DispatchRecordingFilter.records.stream()
                .anyMatch(r -> r.dispatcherType() == DispatcherType.ERROR);
        assertThat(errorDispatched).isTrue();
    }

    @Test
    @DisplayName("익명 요청 /ok는 401을 반환한다")
    void anonymousOk_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/admin/test-error-dispatch/ok",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("익명 요청 /throw는 401을 반환하고 내부 예외가 실행되지 않는다")
    void anonymousThrow_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/admin/test-error-dispatch/throw",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("USER 권한 요청 /ok는 403 ACCESS_DENIED를 반환한다")
    void userRoleOk_returns403() {
        String userToken = createToken(2L, "user@test.com", MemberRole.USER);
        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders(userToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/test-error-dispatch/ok",
                HttpMethod.GET,
                request,
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED");
    }

    @Test
    @DisplayName("USER 권한 요청 /throw는 403 ACCESS_DENIED를 반환한다")
    void userRoleThrow_returns403() {
        String userToken = createToken(2L, "user@test.com", MemberRole.USER);
        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders(userToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/test-error-dispatch/throw",
                HttpMethod.GET,
                request,
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED");
    }

    @Test
    @DisplayName("잘못된 토큰 요청은 401 INVALID_ACCESS_TOKEN을 반환한다")
    void invalidToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid.jwt.token");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/test-error-dispatch/ok",
                HttpMethod.GET,
                request,
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("INVALID_ACCESS_TOKEN");
    }

    @Test
    @DisplayName("익명의 일반 REQUEST /error 직접 요청은 401 AUTHENTICATION_REQUIRED를 반환한다")
    void anonymousDirectGetError_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/error",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("AUTHENTICATION_REQUIRED");
    }
}
