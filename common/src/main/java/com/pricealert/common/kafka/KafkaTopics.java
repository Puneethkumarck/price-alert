package com.pricealert.common.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    public static final String MARKET_TICKS = "market-ticks";
    public static final String ALERT_CHANGES = "alert-changes";
    public static final String ALERT_TRIGGERS = "alert-triggers";
}
