package com.ovengers.slotkey.credit.pg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PgClientTestProfileIntegrationTest {

    @Test
    @DisplayName("test 프로필은 .env 없이 실제 구현체를 제외하고 FakePgClient 하나만 로딩한다")
    void testProfile_loadsOnlyFakePgClientWithoutDotenv() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("test");
            context.register(TossPaymentsPgConfiguration.class, FakePgClient.class);
            context.refresh();

            assertThat(Arrays.asList(context.getBeanNamesForType(PgClient.class)))
                    .singleElement()
                    .satisfies(beanName -> assertThat(context.getBean(beanName))
                            .isInstanceOf(FakePgClient.class));
        }
    }
}
