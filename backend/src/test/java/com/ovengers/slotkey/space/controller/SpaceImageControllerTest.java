package com.ovengers.slotkey.space.controller;

import com.ovengers.slotkey.space.image.SpaceImageStorage;
import com.ovengers.slotkey.space.image.StoredImage;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SpaceImageControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpaceImageStorage spaceImageStorage;

    @BeforeEach
    void setUp() throws IOException {
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

    private byte[] createSampleJpegBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.CYAN);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("비인가 사용자도 GET /api/v1/space-images/{fileName}을 통해 이미지를 조회할 수 있다")
    void getImage_unauthenticated_success() throws Exception {
        byte[] bytes = createSampleJpegBytes(100, 100);
        MockMultipartFile file = new MockMultipartFile("file", "office.jpg", "image/jpeg", bytes);
        StoredImage stored = spaceImageStorage.store(file);

        mockMvc.perform(get("/api/v1/space-images/{fileName}", stored.fileName()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    @DisplayName("존재하지 않는 이미지 파일 조회 시 404 NOT_FOUND를 반환한다")
    void getImage_notFound_returns404() throws Exception {
        String nonExistentFileName = UUID.randomUUID().toString() + ".jpg";

        mockMvc.perform(get("/api/v1/space-images/{fileName}", nonExistentFileName))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("경로 탈출 시도 파일명 요청 시 404 NOT_FOUND를 반환한다")
    void getImage_pathTraversal_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/space-images/{fileName}", "malicious-name.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("비인가 사용자의 비-GET 메서드(POST, DELETE 등)는 거부(401 Unauthorized)된다")
    void nonGetMethods_unauthenticated_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/space-images/test.jpg"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/space-images/test.jpg"))
                .andExpect(status().isUnauthorized());
    }
}
