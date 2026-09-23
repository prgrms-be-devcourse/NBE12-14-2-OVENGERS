package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditLog;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.repository.AuditLogRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.image.SpaceImageStorage;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

class AdminSpaceImageServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SpaceImageService spaceImageService;

    @SpyBean
    private AdminSpaceImageUpdateService adminSpaceImageUpdateService;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SpaceImageStorage spaceImageStorage;

    private Long testAdminMemberId = 1L;

    @BeforeEach
    void setUp() throws IOException {
        auditLogRepository.deleteAll();
        spaceRepository.deleteAll();
        Path rootDir = spaceImageStorage.getStorageRootDir();
        if (Files.exists(rootDir)) {
            try (var stream = Files.list(rootDir)) {
                stream.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    private Space createTestSpace(String initialImagePath) {
        Space space = Space.builder()
                .name("공간 이미지 테스트 회의실")
                .location("서울시 강남구 테헤란로 123")
                .description("테스트용 회의실")
                .capacity(8)
                .pricePerSlot(15000L)
                .imagePath(initialImagePath)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(SpaceStatus.ACTIVE)
                .build();
        return spaceRepository.save(space);
    }

    private byte[] createSamplePngBytes(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("새 이미지 업로드 시 Space.imagePath가 갱신되고 MODIFY_SPACE 감사 로그가 원자적으로 기록된다 (version 불변)")
    void uploadAndAttach_success() throws IOException {
        Space space = createTestSpace("/images/slotkey-test-data/sample1.jpg");
        int initialVersion = space.getVersion();

        byte[] imageBytes = createSamplePngBytes(200, 200, Color.GREEN);
        MockMultipartFile file = new MockMultipartFile(
                "file", "new-space.png", "image/png", imageBytes
        );

        SpaceDetailResponse response = spaceImageService.uploadAndAttachSpaceImage(space.getId(), file, testAdminMemberId);

        assertThat(response.imagePath()).startsWith("/api/v1/space-images/");
        assertThat(response.imagePath()).endsWith(".png");

        // DB 검증
        Space updatedSpace = spaceRepository.findById(space.getId()).orElseThrow();
        assertThat(updatedSpace.getImagePath()).isEqualTo(response.imagePath());
        // 사진 변경만으로 version은 증가하지 않아야 함
        assertThat(updatedSpace.getVersion()).isEqualTo(initialVersion);

        // 감사 로그 검증
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.MODIFY_SPACE);
        assertThat(auditLog.getTargetType()).isEqualTo(AuditTargetType.SPACE);
        assertThat(auditLog.getTargetId()).isEqualTo(space.getId());
        assertThat(auditLog.getActorMemberId()).isEqualTo(testAdminMemberId);
        assertThat(auditLog.getBeforeValue()).contains("/images/slotkey-test-data/sample1.jpg");
        assertThat(auditLog.getAfterValue()).contains(response.imagePath());

        // 실제 파일 존재 검증
        String newFileName = spaceImageStorage.extractFileNameFromPath(response.imagePath());
        Path physicalFile = spaceImageStorage.getStorageRootDir().resolve(newFileName);
        assertThat(Files.exists(physicalFile)).isTrue();
    }

    @Test
    @DisplayName("DB 트랜잭션 실패 시 저장된 새 파일이 물리적으로 롤백/제거된다")
    void uploadAndAttach_dbFailure_cleansUpNewFile() throws IOException {
        Space space = createTestSpace("/images/slotkey-test-data/sample1.jpg");

        byte[] imageBytes = createSamplePngBytes(150, 150, Color.RED);
        MockMultipartFile file = new MockMultipartFile(
                "file", "will-fail.png", "image/png", imageBytes
        );

        // DB 트랜잭션에서 예외 강제 발생
        doThrow(new RuntimeException("DB Connection Timeout Simulation"))
                .when(adminSpaceImageUpdateService).updateSpaceImage(anyLong(), anyString(), anyLong());

        assertThatThrownBy(() -> spaceImageService.uploadAndAttachSpaceImage(space.getId(), file, testAdminMemberId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB Connection Timeout Simulation");

        // DB 상태 불변 검증
        Space unchangedSpace = spaceRepository.findById(space.getId()).orElseThrow();
        assertThat(unchangedSpace.getImagePath()).isEqualTo("/images/slotkey-test-data/sample1.jpg");

        // 감사 로그 없음 검증
        assertThat(auditLogRepository.count()).isZero();

        // 디렉터리에 새로 업로드된 파일이 남아있지 않은지 검증
        try (var stream = Files.list(spaceImageStorage.getStorageRootDir())) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    @DisplayName("기존 서버 관리 이미지가 교체되면 이전 파일은 삭제되고 샘플 경로는 삭제 대상에서 제외된다")
    void uploadAndAttach_replaceServerManagedImage_deletesOldFile() throws IOException {
        // 1. 처음엔 샘플 이미지
        Space space = createTestSpace("/images/slotkey-test-data/sample1.jpg");

        // 2. 첫 번째 서버 이미지 업로드
        byte[] firstBytes = createSamplePngBytes(100, 100, Color.MAGENTA);
        MockMultipartFile firstFile = new MockMultipartFile(
                "file", "first.png", "image/png", firstBytes
        );
        SpaceDetailResponse firstResponse = spaceImageService.uploadAndAttachSpaceImage(space.getId(), firstFile, testAdminMemberId);
        String firstFileName = spaceImageStorage.extractFileNameFromPath(firstResponse.imagePath());
        Path firstPhysicalFile = spaceImageStorage.getStorageRootDir().resolve(firstFileName);
        assertThat(Files.exists(firstPhysicalFile)).isTrue();

        // 3. 두 번째 서버 이미지로 교체 업로드
        byte[] secondBytes = createSamplePngBytes(120, 120, Color.ORANGE);
        MockMultipartFile secondFile = new MockMultipartFile(
                "file", "second.png", "image/png", secondBytes
        );
        SpaceDetailResponse secondResponse = spaceImageService.uploadAndAttachSpaceImage(space.getId(), secondFile, testAdminMemberId);
        String secondFileName = spaceImageStorage.extractFileNameFromPath(secondResponse.imagePath());
        Path secondPhysicalFile = spaceImageStorage.getStorageRootDir().resolve(secondFileName);

        // 새 파일은 존재하고, 이전 파일은 삭제되었어야 함
        assertThat(Files.exists(secondPhysicalFile)).isTrue();
        assertThat(Files.exists(firstPhysicalFile)).isFalse();

        // DB 상의 최종 이미지 경로 확인
        Space finalSpace = spaceRepository.findById(space.getId()).orElseThrow();
        assertThat(finalSpace.getImagePath()).isEqualTo(secondResponse.imagePath());
    }

    @Test
    @DisplayName("동시 다중 이미지 교체 요청 시 최종 Space.imagePath가 가리키는 실제 파일이 디스크에 반드시 존재한다")
    void uploadAndAttach_concurrency() throws Exception {
        Space space = createTestSpace("/images/slotkey-test-data/sample1.jpg");
        int threadCount = 4;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<SpaceDetailResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            byte[] bytes = createSamplePngBytes(50 + index * 10, 50 + index * 10, Color.DARK_GRAY);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "concurrent-" + index + ".png", "image/png", bytes
            );
            futures.add(executorService.submit(() -> {
                startLatch.await();
                try {
                    return spaceImageService.uploadAndAttachSpaceImage(space.getId(), file, testAdminMemberId);
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executorService.shutdown();

        // 최종 Space 조회
        Space finalSpace = spaceRepository.findById(space.getId()).orElseThrow();
        String finalImagePath = finalSpace.getImagePath();
        assertThat(finalImagePath).startsWith("/api/v1/space-images/");

        String finalFileName = spaceImageStorage.extractFileNameFromPath(finalImagePath);
        Path finalPhysicalFile = spaceImageStorage.getStorageRootDir().resolve(finalFileName);
        assertThat(Files.exists(finalPhysicalFile)).isTrue();

        // 모든 스레드가 감사 로그를 남겼는지 확인
        long auditCount = auditLogRepository.count();
        assertThat(auditCount).isEqualTo(threadCount);
    }
}
