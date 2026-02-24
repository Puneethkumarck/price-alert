package com.pricealert.alertapi.domain.alert;

import com.pricealert.common.event.AlertChange;

public interface AlertEventPublisher {

    void publish(AlertChange event);
}
