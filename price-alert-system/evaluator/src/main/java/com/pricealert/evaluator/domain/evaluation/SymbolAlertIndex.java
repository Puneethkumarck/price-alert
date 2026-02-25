package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class SymbolAlertIndex {

    private final TreeMap<BigDecimal, List<AlertEntry>> aboveAlerts = new TreeMap<>();
    private final TreeMap<BigDecimal, List<AlertEntry>> belowAlerts = new TreeMap<>();
    private final TreeMap<BigDecimal, List<AlertEntry>> crossAlerts = new TreeMap<>();

    private BigDecimal lastPrice;

    public void addAlert(AlertEntry alert) {
        mapFor(alert.direction())
                .computeIfAbsent(alert.thresholdPrice(), k -> new ArrayList<>())
                .add(alert);
    }

    public void removeAlert(String alertId) {
        removeFrom(aboveAlerts, alertId);
        removeFrom(belowAlerts, alertId);
        removeFrom(crossAlerts, alertId);
    }

    public List<AlertEntry> evaluate(BigDecimal newPrice) {
        var fired = new ArrayList<AlertEntry>();

        var aboveFired = aboveAlerts.headMap(newPrice, true);
        for (var entries : aboveFired.values()) {
            fired.addAll(entries);
        }
        aboveFired.clear();

        var belowFired = belowAlerts.tailMap(newPrice, true);
        for (var entries : belowFired.values()) {
            fired.addAll(entries);
        }
        belowFired.clear();

        var previousPrice = lastPrice;
        if (previousPrice != null && previousPrice.compareTo(newPrice) != 0) {
            var low = previousPrice.min(newPrice);
            var high = previousPrice.max(newPrice);
            var crossRange = crossAlerts.subMap(low, false, high, false);
            for (var entries : crossRange.values()) {
                fired.addAll(entries);
            }
            crossRange.clear();
        }

        lastPrice = newPrice;
        return fired;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal price) {
        this.lastPrice = price;
    }

    public int size() {
        return countEntries(aboveAlerts) + countEntries(belowAlerts) + countEntries(crossAlerts);
    }

    public boolean isEmpty() {
        return aboveAlerts.isEmpty() && belowAlerts.isEmpty() && crossAlerts.isEmpty();
    }

    private TreeMap<BigDecimal, List<AlertEntry>> mapFor(Direction direction) {
        return switch (direction) {
            case ABOVE -> aboveAlerts;
            case BELOW -> belowAlerts;
            case CROSS -> crossAlerts;
        };
    }

    private void removeFrom(TreeMap<BigDecimal, List<AlertEntry>> map, String alertId) {
        var iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            entry.getValue().removeIf(a -> a.alertId().equals(alertId));
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private int countEntries(NavigableMap<BigDecimal, List<AlertEntry>> map) {
        return map.values().stream().mapToInt(List::size).sum();
    }
}
