package com.pricealert.evaluator.domain.evaluation;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AlertIndexManager {

    private final ConcurrentHashMap<String, SymbolAlertIndex> indices = new ConcurrentHashMap<>();

    public SymbolAlertIndex getOrCreate(String symbol) {
        return indices.computeIfAbsent(symbol, k -> new SymbolAlertIndex());
    }

    public SymbolAlertIndex get(String symbol) {
        return indices.get(symbol);
    }

    public void addAlert(AlertEntry alert) {
        getOrCreate(alert.symbol()).addAlert(alert);
    }

    public void removeAlert(String alertId, String symbol) {
        var index = indices.get(symbol);
        if (index != null) {
            index.removeAlert(alertId);
        }
    }

    public int totalAlerts() {
        return indices.values().stream().mapToInt(SymbolAlertIndex::size).sum();
    }

    public int symbolCount() {
        return indices.size();
    }

    public void clear() {
        indices.clear();
    }
}
