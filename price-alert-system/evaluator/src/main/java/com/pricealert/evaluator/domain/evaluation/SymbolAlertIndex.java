package com.pricealert.evaluator.domain.evaluation;

import com.pricealert.common.event.Direction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Per-symbol alert index using TreeMap for efficient threshold matching.
 * Thread-safe via read-write lock.
 */
public class SymbolAlertIndex {

    private final TreeMap<BigDecimal, List<AlertEntry>> aboveAlerts = new TreeMap<>();
    private final TreeMap<BigDecimal, List<AlertEntry>> belowAlerts = new TreeMap<>();
    private final TreeMap<BigDecimal, List<AlertEntry>> crossAlerts = new TreeMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile BigDecimal lastPrice;

    public void addAlert(AlertEntry alert) {
        lock.writeLock().lock();
        try {
            var map = mapFor(alert.direction());
            map.computeIfAbsent(alert.thresholdPrice(), k -> new ArrayList<>()).add(alert);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeAlert(String alertId) {
        lock.writeLock().lock();
        try {
            removeFrom(aboveAlerts, alertId);
            removeFrom(belowAlerts, alertId);
            removeFrom(crossAlerts, alertId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Evaluate a new price tick against this symbol's alerts.
     * Returns fired alerts and removes them from the index.
     */
    public List<AlertEntry> evaluate(BigDecimal newPrice) {
        lock.writeLock().lock();
        try {
            var fired = new ArrayList<AlertEntry>();

            // ABOVE: fires when newPrice >= threshold
            var aboveFired = aboveAlerts.headMap(newPrice, true);
            for (var entries : aboveFired.values()) {
                fired.addAll(entries);
            }
            aboveFired.clear();

            // BELOW: fires when newPrice <= threshold
            var belowFired = belowAlerts.tailMap(newPrice, true);
            for (var entries : belowFired.values()) {
                fired.addAll(entries);
            }
            belowFired.clear();

            // CROSS: fires when price crosses threshold between lastPrice and newPrice
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
        } finally {
            lock.writeLock().unlock();
        }
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal price) {
        this.lastPrice = price;
    }

    public int size() {
        lock.readLock().lock();
        try {
            return countEntries(aboveAlerts) + countEntries(belowAlerts) + countEntries(crossAlerts);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return aboveAlerts.isEmpty() && belowAlerts.isEmpty() && crossAlerts.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
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
