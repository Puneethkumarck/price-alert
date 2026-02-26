package com.pricealert.evaluator.application.config;

import com.pricealert.common.event.AlertChange;
import com.pricealert.common.event.MarketTick;
import java.util.HashMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MarketTick>
            marketTickListenerContainerFactory(KafkaProperties kafkaProperties) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, MarketTick>();
        factory.setConsumerFactory(
                consumerFactory(kafkaProperties, MarketTick.class, "evaluator-ticks"));
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setConcurrency(16);
        // Do not start automatically — WarmUpService starts all listeners after the
        // index is fully built, preventing ConcurrentModificationException on TreeMap
        // when Kafka consumer threads and the warm-up main thread both call addAlert()
        // on the same SymbolAlertIndex concurrently.
        factory.setAutoStartup(false);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AlertChange>
            alertChangeListenerContainerFactory(KafkaProperties kafkaProperties) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, AlertChange>();
        factory.setConsumerFactory(
                consumerFactory(kafkaProperties, AlertChange.class, "evaluator-changes"));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(8);
        factory.setAutoStartup(false);
        return factory;
    }

    private <T> ConsumerFactory<String, T> consumerFactory(
            KafkaProperties kafkaProperties, Class<T> valueType, String groupId) {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                kafkaProperties.getConsumer().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        var assignmentStrategy =
                kafkaProperties.getConsumer().getProperties().get("partition.assignment.strategy");
        if (assignmentStrategy != null) {
            props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, assignmentStrategy);
        }

        var deserializer = new JacksonJsonDeserializer<>(valueType);
        deserializer.addTrustedPackages("com.pricealert.common.*");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }
}
