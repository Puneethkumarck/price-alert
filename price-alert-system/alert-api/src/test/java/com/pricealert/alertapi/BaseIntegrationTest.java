package com.pricealert.alertapi;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests using singleton Testcontainers (shared across all test classes).
 * Containers start once on first use and stay alive for the entire JVM lifecycle,
 * avoiding the issue where Spring context caches a datasource URL that becomes stale.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    static final String JWT_SECRET = "test-secret-key-for-integration-tests";

    static final PostgreSQLContainer<?> postgres;
    static final KafkaContainer kafka;
    @SuppressWarnings("resource")
    static final GenericContainer<?> redis;

    static {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName("price_alerts_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))
                .withKraft();
        kafka.start();

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("alert.daily-reset.cron", () -> "-");
        registry.add("namastack.outbox.poll-interval", () -> "500");
        registry.add("namastack.outbox.batch-size", () -> "50");
        registry.add("namastack.outbox.jdbc.table-prefix", () -> "alertapi_");
        registry.add("namastack.outbox.jdbc.schema-initialization.enabled", () -> "false");
    }
}
