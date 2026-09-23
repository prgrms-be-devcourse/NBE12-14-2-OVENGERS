package com.ovengers.slotkey.space.image;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceImageStorageTest {

    @TempDir
    Path tempStorageDir;

    private SpaceImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new SpaceImageStorage(tempStorageDir.toString());
    }

    private byte[] createSampleImageBytes(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("JPEG 이미지 정상 저장 및 조회 성공")
    void storeAndLoad_jpeg_success() throws IOException {
        byte[] bytes = createSampleImageBytes("jpg", 200, 150);
        MockMultipartFile file = new MockMultipartFile(
                "file", "room.jpg", "image/jpeg", bytes
        );

        StoredImage stored = storage.store(file);

        assertThat(stored.fileName()).endsWith(".jpg");
        assertThat(stored.relativeUrl()).isEqualTo("/api/v1/space-images/" + stored.fileName());
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.width()).isEqualTo(200);
        assertThat(stored.height()).isEqualTo(150);
        assertThat(stored.size()).isEqualTo(bytes.length);

        ImageResource loaded = storage.load(stored.fileName());
        assertThat(loaded.bytes()).isEqualTo(bytes);
        assertThat(loaded.contentType()).isEqualTo("image/jpeg");
        assertThat(loaded.contentLength()).isEqualTo(bytes.length);
    }

    @Test
    @DisplayName("PNG 이미지 정상 저장 및 조회 성공")
    void storeAndLoad_png_success() throws IOException {
        byte[] bytes = createSampleImageBytes("png", 320, 240);
        MockMultipartFile file = new MockMultipartFile(
                "file", "office.png", "image/png", bytes
        );

        StoredImage stored = storage.store(file);

        assertThat(stored.fileName()).endsWith(".png");
        assertThat(stored.relativeUrl()).isEqualTo("/api/v1/space-images/" + stored.fileName());
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.width()).isEqualTo(320);
        assertThat(stored.height()).isEqualTo(240);

        ImageResource loaded = storage.load(stored.fileName());
        assertThat(loaded.bytes()).isEqualTo(bytes);
        assertThat(loaded.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("WebP 이미지는 지원 형식에서 제외되어 INVALID_IMAGE_FORMAT 예외를 발생시킨다")
    void store_webpFormat_rejected() {
        byte[] webpBytes = new byte[] {
                'R', 'I', 'F', 'F', 22, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "space.webp", "image/webp", webpBytes
        );

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FORMAT);
    }

    @Test
    @DisplayName("빈 파일은 IMAGE_FILE_EMPTY 예외를 발생시키고 파일이 생성되지 않는다")
    void store_emptyFile_throwsException() throws IOException {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> storage.store(emptyFile))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_FILE_EMPTY);

        try (Stream<Path> stream = Files.list(tempStorageDir)) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    @DisplayName("5MB 초과 파일은 IMAGE_SIZE_EXCEEDED 예외를 발생시키고 파일이 생성되지 않는다")
    void store_exceedsMaxSize_throwsException() throws IOException {
        byte[] largeBytes = new byte[(int) SpaceImageStorage.MAX_FILE_SIZE + 1];
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", largeBytes);

        assertThatThrownBy(() -> storage.store(largeFile))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_SIZE_EXCEEDED);

        try (Stream<Path> stream = Files.list(tempStorageDir)) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    @DisplayName("용량은 5MB 이하이지만 해상도(4096px)를 초과하는 이미지는 디코딩 전 메타데이터 검사에서 IMAGE_DIMENSIONS_EXCEEDED 예외를 발생시킨다")
    void store_exceedsDimensions_throwsException() throws IOException {
        // 폭 4097px, 높이 10px의 작은 파일 크기 이미지 (압축 폭탄 시나리오)
        byte[] bytes = createSampleImageBytes("png", 4097, 10);
        assertThat(bytes.length).isLessThan((int) SpaceImageStorage.MAX_FILE_SIZE);

        MockMultipartFile file = new MockMultipartFile("file", "wide.png", "image/png", bytes);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);

        try (Stream<Path> stream = Files.list(tempStorageDir)) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    @DisplayName("해상도 경계값(4096x4096px) 이미지는 정상 저장된다")
    void store_boundaryDimensions_success() throws IOException {
        byte[] bytes = createSampleImageBytes("png", 4096, 1);
        MockMultipartFile file = new MockMultipartFile("file", "boundary.png", "image/png", bytes);

        StoredImage stored = storage.store(file);
        assertThat(stored.width()).isEqualTo(4096);
        assertThat(stored.height()).isEqualTo(1);
    }

    @Test
    @DisplayName("확장자와 실제 서명이 불일치(위조 형식)하면 INVALID_IMAGE_FORMAT 예외를 발생시킨다")
    void store_mismatchedSignature_throwsException() throws IOException {
        byte[] pngBytes = createSampleImageBytes("png", 100, 100);
        // PNG 바이트인데 확장자는 jpg
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", pngBytes);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FORMAT);

        try (Stream<Path> stream = Files.list(tempStorageDir)) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    @DisplayName("손상된 이미지 파일은 INVALID_IMAGE_FORMAT 예외를 발생시킨다")
    void store_corruptedImage_throwsException() throws IOException {
        // JPEG 시그니처만 있고 뒤에는 깨진 바이트
        byte[] corrupted = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.jpg", "image/jpeg", corrupted);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FORMAT);

        try (Stream<Path> stream = Files.list(tempStorageDir)) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    @DisplayName("허용되지 않은 확장자(예: txt, gif, svg)는 INVALID_IMAGE_FORMAT 예외를 발생시킨다")
    void store_unsupportedExtension_throwsException() throws IOException {
        byte[] bytes = "not an image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream", bytes);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FORMAT);

        try (Stream<Path> stream = Files.list(tempStorageDir)) {
            assertThat(stream.count()).isZero();
        }
    }

    @Test
    @DisplayName("경로 탈출(Path Traversal) 파일명 조회는 IMAGE_NOT_FOUND 예외를 발생시킨다")
    void load_pathTraversal_throwsException() {
        assertThatThrownBy(() -> storage.load("../secret.txt"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);

        assertThatThrownBy(() -> storage.load("/etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("심볼릭 링크 파일 조회는 거절되고 IMAGE_NOT_FOUND 예외를 발생시킨다")
    void load_symbolicLink_throwsException() throws IOException {
        Path realFile = Files.createFile(tempStorageDir.resolve("real-target.jpg"));
        String linkFileName = UUID.randomUUID().toString() + ".jpg";
        Path symlink = tempStorageDir.resolve(linkFileName);

        try {
            Files.createSymbolicLink(symlink, realFile);
        } catch (UnsupportedOperationException | SecurityException | IOException e) {
            // OS 또는 파일시스템이 심볼릭 링크 생성을 허용하지 않는 경우 테스트 스킵
            return;
        }

        assertThatThrownBy(() -> storage.load(linkFileName))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 파일 조회는 IMAGE_NOT_FOUND 예외를 발생시킨다")
    void load_nonExistentFile_throwsException() {
        String nonExistentFileName = UUID.randomUUID().toString() + ".jpg";

        assertThatThrownBy(() -> storage.load(nonExistentFileName))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("파일 삭제 정상 동작 및 비정상 요청 방어")
    void delete_successAndSecurity() throws IOException {
        byte[] bytes = createSampleImageBytes("jpg", 100, 100);
        StoredImage stored = storage.store("sample.jpg", "image/jpeg", bytes);

        assertThat(storage.delete(stored.fileName())).isTrue();
        assertThat(Files.exists(tempStorageDir.resolve(stored.fileName()))).isFalse();

        // 이미 삭제된 파일 재삭제 시 false
        assertThat(storage.delete(stored.fileName())).isFalse();

        // 경로 탈출 시도 삭제 시 false
        assertThat(storage.delete("../outside.jpg")).isFalse();
    }

    @Test
    @DisplayName("서버 관리 이미지 경로 판별")
    void isServerManagedImage_check() {
        String validPath = "/api/v1/space-images/" + UUID.randomUUID().toString() + ".png";
        assertThat(storage.isServerManagedImage(validPath)).isTrue();

        assertThat(storage.isServerManagedImage("/images/slotkey-test-data/sample.jpg")).isFalse();
        assertThat(storage.isServerManagedImage("https://example.com/photo.png")).isFalse();
        assertThat(storage.isServerManagedImage("/api/v1/space-images/not-a-uuid.jpg")).isFalse();
        assertThat(storage.isServerManagedImage(null)).isFalse();
    }
}
