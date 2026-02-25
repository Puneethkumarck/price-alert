package com.pricealert.alertapi;

import com.pricealert.alertapi.infrastructure.db.alert.AlertJpaRepository;
import com.pricealert.alertapi.infrastructure.db.notification.NotificationJpaRepository;
import com.pricealert.alertapi.infrastructure.db.triggerlog.AlertTriggerLogJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    protected static final String JWT_SECRET = "test-secret-key-for-integration-tests";

    protected static final PostgreSQLContainer<?> postgres;
    protected static final KafkaContainer kafka;
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

    @Autowired
    private AlertTriggerLogJpaRepository triggerLogJpaRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private AlertJpaRepository alertJpaRepository;

    @BeforeEach
    void cleanDatabase() {
        triggerLogJpaRepository.deleteAll();
        notificationJpaRepository.deleteAll();
        alertJpaRepository.deleteAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.jdbc-url", postgres::getJdbcUrl);
        registry.add("spring.datasource.hikari.username", postgres::getUsername);
        registry.add("spring.datasource.hikari.password", postgres::getPassword);
        registry.add("spring.datasource.replica.hikari.jdbc-url", postgres::getJdbcUrl);
        registry.add("spring.datasource.replica.hikari.username", postgres::getUsername);
        registry.add("spring.datasource.replica.hikari.password", postgres::getPassword);
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
