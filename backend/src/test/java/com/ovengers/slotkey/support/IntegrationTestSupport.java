package com.ovengers.slotkey.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
public abstract class IntegrationTestSupport {
}
