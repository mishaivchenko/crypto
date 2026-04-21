package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EngineApplication.class, properties = {
    "engine.internal-token=test-internal-token",
    "engine.metrics-publish.enabled=false"
})
class EngineMetricsPublisherDisabledIntegrationTest
{
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void doesNotCreateMetricsPublisherByDefault()
    {
        assertThat( applicationContext.getBeansOfType( EngineMetricsPublisher.class ) ).isEmpty();
    }
}
