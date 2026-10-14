package com.ovengers.slotkey.credit.pg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsPgConfiguration {

    @Bean
    PgClient tossPaymentsPgClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            TossPaymentsProperties properties
    ) {
        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .build();
        return new TossPaymentsPgClient(
                restClient,
                objectMapper,
                properties.secretKey()
        );
    }

    static SimpleClientHttpRequestFactory requestFactory(
            TossPaymentsProperties properties
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
