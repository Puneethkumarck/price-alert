# Evaluation Engine — How It Works

The evaluation engine is the brain of the price alert system. It decides, for every incoming market price tick, whether any user's alert should fire.

---

## The Core Idea

Every alert is essentially a rule:

> "When **AAPL** price goes **ABOVE $150**, notify me."

The engine stores these rules in memory, organised by symbol and direction. When a tick arrives for AAPL at $184.48, the engine checks the rules for AAPL and fires any that match — in microseconds, without touching the database.

---

## Components at a Glance

```
                     ┌────────────────────────────────────────┐
  Kafka              │           EVALUATOR SERVICE             │
  alert-changes ────▶│  AlertChangeConsumer                   │
                     │    └─▶ AlertIndexManager               │
  market-ticks  ────▶│  MarketTickConsumer                    │
                     │    └─▶ EvaluationEngine                │
                     │          └─▶ SymbolAlertIndex          │
                     │               ├── aboveAlerts TreeMap  │
                     │               ├── belowAlerts TreeMap  │
                     │               └── crossAlerts TreeMap  │
                     │                                        │
                     │    On fire:                            │
                     │    ├─▶ AlertTriggerProducer (outbox)   │
                     │    └─▶ AlertStatusUpdater (DB)         │
                     └────────────────────────────────────────┘
                                        │
                               alert-triggers (Kafka)
                                        │
                               notification-persister
```

---

## Class Responsibilities

| Class | Layer | Responsibility |
|---|---|---|
| `AlertEntry` | Domain | Immutable record holding one alert's data (id, userId, symbol, threshold, direction) |
| `SymbolAlertIndex` | Domain | In-memory index for one symbol — stores alerts in sorted TreeMaps, runs evaluation |
| `AlertIndexManager` | Domain | Map of `symbol → SymbolAlertIndex`; manages the full index across all symbols |
| `EvaluationEngine` | Domain | Orchestrates evaluation: calls the index, converts fired alerts into `AlertTrigger` events |
| `AlertChangeConsumer` | Infrastructure | Kafka consumer — keeps the in-memory index in sync when alerts are created/updated/deleted/reset |
| `MarketTickConsumer` | Infrastructure | Kafka consumer — receives price ticks, calls `EvaluationEngine`, dispatches triggers |
| `WarmUpService` | Infrastructure | On startup, loads all ACTIVE alerts from PostgreSQL into the index |
| `AlertStatusUpdater` | Infrastructure | After firing, updates `alerts.status = TRIGGERED_TODAY` in the DB (Layer 2 dedup) |
| `AlertTriggerProducer` | Infrastructure | Schedules the `AlertTrigger` event to the transactional outbox |

---

## The Data Structure — TreeMap per Direction

For each symbol, alerts are stored in **three sorted TreeMaps**:

```
SymbolAlertIndex for "AAPL"
│
├── aboveAlerts: TreeMap<BigDecimal, List<AlertEntry>>  (sorted ascending)
│     $120.00 → [Alice: ABOVE $120]
│     $150.00 → [Bob: ABOVE $150, Carol: ABOVE $150]
│     $200.00 → [Dave: ABOVE $200]
│
├── belowAlerts: TreeMap<BigDecimal, List<AlertEntry>>  (sorted ascending)
│     $100.00 → [Eve: BELOW $100]
│     $130.00 → [Frank: BELOW $130]
│
└── crossAlerts: TreeMap<BigDecimal, List<AlertEntry>>  (sorted ascending)
      $140.00 → [Grace: CROSS $140]
```

Using a `TreeMap` (a sorted binary tree) means range queries are O(log n) — far faster than scanning every alert.

---

## Evaluation Logic — Step by Step

When a tick arrives for AAPL at **$184.48**:

### ABOVE alerts — `headMap(newPrice, inclusive=true)`

`headMap($184.48, true)` returns all entries with key **≤ $184.48**.

```
aboveAlerts before tick:
  $120.00 → [Alice]
  $150.00 → [Bob, Carol]
  $200.00 → [Dave]

headMap($184.48, inclusive) → { $120.00, $150.00 }  ← these fire
                              { $200.00 }             ← stays (price not reached yet)

fired: Alice ($120), Bob ($150), Carol ($150)
aboveAlerts after:
  $200.00 → [Dave]   ← only Dave remains
```

Alice, Bob and Carol all had thresholds ≤ $184.48, so AAPL being above their thresholds means their alerts fire. Dave's $200 threshold hasn't been reached yet.

**The `clear()` on the view is the key**: fired alerts are removed from the TreeMap immediately, so they cannot fire again today (Layer 1 dedup).

### BELOW alerts — `tailMap(newPrice, inclusive=true)`

`tailMap($184.48, true)` returns all entries with key **≥ $184.48**.

```
belowAlerts before tick:
  $100.00 → [Eve]
  $130.00 → [Frank]

tailMap($184.48, inclusive) → { }  ← nothing fires (no thresholds ≥ $184.48)
```

AAPL is at $184.48 — no BELOW threshold is ≥ that price, so nothing fires. If the price were $95.00, both Eve and Frank would fire.

### CROSS alerts — `subMap(low, exclusive, high, exclusive)`

CROSS fires when the price **passes through** a threshold between the previous tick and the current tick.

```
lastPrice = $145.00
newPrice  = $184.48

low  = min($145, $184.48) = $145.00
high = max($145, $184.48) = $184.48

crossAlerts:
  $140.00 → [Grace]

subMap($145.00 exclusive, $184.48 exclusive) → { }
   ← $140 is outside the range [145, 184.48], so Grace does not fire

If lastPrice were $135.00:
subMap($135.00 exclusive, $184.48 exclusive) → { $140.00 → [Grace] }  ← Grace fires!
```

The exclusive bounds ensure an alert only fires when the price actually crosses through the threshold, not merely sits on it.

---

## Worked Example — Full Lifecycle

**Setup**: Three users create alerts.

```
User A: AAPL ABOVE $150   → alertId: A1
User B: AAPL BELOW $130   → alertId: A2
User C: AAPL CROSS $140   → alertId: A3
```

**Step 1 — Startup warm-up**

`WarmUpService` runs on `ApplicationReadyEvent`, queries PostgreSQL for all ACTIVE alerts, and populates the index:

```
AlertIndexManager
└── "AAPL" → SymbolAlertIndex
      aboveAlerts: { $150.00 → [A1 (User A)] }
      belowAlerts: { $130.00 → [A2 (User B)] }
      crossAlerts: { $140.00 → [A3 (User C)] }
      lastPrice: null
```

**Step 2 — First tick: AAPL = $145.00**

`MarketTickConsumer` receives the tick → calls `EvaluationEngine.evaluate("AAPL", 145.00)`:

```
ABOVE check: headMap($145, inclusive) → {} (no threshold ≤ $145 in aboveAlerts)  → 0 fired
BELOW check: tailMap($145, inclusive) → {} ($130 < $145, not ≥ $145)             → 0 fired
CROSS check: lastPrice=null → skip

lastPrice = $145.00
```

Nothing fires. Index unchanged.

**Step 3 — Second tick: AAPL = $125.00**

```
ABOVE check: headMap($125, inclusive) → {}   → 0 fired
BELOW check: tailMap($125, inclusive) → { $130.00 → [A2] }  ← $130 ≥ $125  → A2 FIRES
CROSS check: lastPrice=$145, newPrice=$125
             low=$125, high=$145
             subMap($125 excl, $145 excl) → { $140.00 → [A3] }  ← $140 in (125,145) → A3 FIRES

lastPrice = $125.00
```

**A2 and A3 fire.** Both are removed from the index.

Index after:
```
aboveAlerts: { $150.00 → [A1] }
belowAlerts: { }    ← A2 removed
crossAlerts: { }    ← A3 removed
```

**Step 4 — For each fired alert:**

```
MarketTickConsumer (for A2):
  1. AlertTriggerProducer.send(trigger_A2)
       → outbox.schedule(trigger_A2, userId_B)     [atomic DB write]
  2. AlertStatusUpdater.markTriggeredToday("A2")
       → UPDATE alerts SET status='TRIGGERED_TODAY'
         WHERE id='A2' AND status='ACTIVE'          [Layer 2 dedup]

MarketTickConsumer (for A3):
  1. AlertTriggerProducer.send(trigger_A3)
  2. AlertStatusUpdater.markTriggeredToday("A3")
```

**Step 5 — Third tick: AAPL = $160.00**

```
ABOVE check: headMap($160, inclusive) → { $150.00 → [A1] }  ← $150 ≤ $160  → A1 FIRES
BELOW check: tailMap($160, inclusive) → {}
CROSS check: lastPrice=$125, newPrice=$160
             subMap($125 excl, $160 excl) → {} (crossAlerts is empty)

lastPrice = $160.00
```

**A1 fires.** Removed from index. Index is now empty for AAPL.

**Step 6 — Daily reset (09:30 ET next trading day)**

`DailyResetScheduler` (in alert-api) sets all `TRIGGERED_TODAY` alerts back to `ACTIVE` and publishes RESET events on `alert-changes`. `AlertChangeConsumer` handles them:

```java
case RESET -> {
    indexManager.removeAlert(change.alertId(), change.symbol());  // idempotent
    indexManager.addAlert(toEntry(change));                        // re-add to index
}
```

All three alerts (A1, A2, A3) are back in the index, ready for the next trading day.

---

## Concurrency Safety

Each `SymbolAlertIndex` uses a `ReentrantReadWriteLock`:

- `addAlert` / `removeAlert` / `evaluate` all acquire the **write lock** — only one thread at a time.
- `size` / `isEmpty` acquire the **read lock** — concurrent reads are safe.

Multiple symbols are evaluated independently — AAPL ticks don't block TSLA evaluation because each symbol has its own `SymbolAlertIndex` instance inside `AlertIndexManager`'s `ConcurrentHashMap`.

---

## Idempotency — Why `removeAlert` Before `addAlert`

On startup, `WarmUpService` loads alerts from DB **before** Kafka consumers start replaying the `alert-changes` topic. This creates a race:

```
Time 0:  WarmUpService adds A1 to index   (loaded from DB)
Time 1:  AlertChangeConsumer replays CREATED event for A1
         → if we just addAlert, A1 is now in the index TWICE
         → same tick would fire A1 twice → duplicate notification
```

The fix: every `CREATED` and `RESET` handler calls `removeAlert` first, then `addAlert`. The result is always exactly one copy, regardless of how many times the event is replayed.

---

## Why In-Memory? Why Not Query the DB Per Tick?

The simulator generates ~500 ticks/second (50 symbols × 100ms interval). Querying PostgreSQL for matching alerts on every tick would mean:

- 500 DB queries/second under normal load
- Each query with index scan on `(symbol, status, threshold, direction)`
- Latency: ~1-5ms per query → 500-2500ms of DB time per second

The in-memory TreeMap evaluates a tick in **microseconds** with zero DB I/O. The trade-off is that the index must be kept in sync via the `alert-changes` Kafka topic — which is the job of `AlertChangeConsumer`.

---

## Summary

```
User creates alert  →  alert-api writes to DB + outbox
                    →  alert-changes topic
                    →  AlertChangeConsumer.addAlert()
                    →  TreeMap entry inserted

Simulator tick      →  tick-ingestor writes to outbox
                    →  market-ticks topic
                    →  MarketTickConsumer.onMarketTick()
                    →  EvaluationEngine.evaluate()
                    →  SymbolAlertIndex.evaluate()
                         ABOVE: headMap  → fires if price ≥ threshold
                         BELOW: tailMap  → fires if price ≤ threshold
                         CROSS: subMap   → fires if price crosses threshold
                    →  fired alerts removed from TreeMap  (Layer 1 dedup)
                    →  AlertTrigger scheduled to outbox   (→ Kafka → notifier)
                    →  status = TRIGGERED_TODAY in DB     (Layer 2 dedup)

Next trading day    →  DailyResetScheduler resets status → ACTIVE
                    →  AlertChangeConsumer.handleReset()  → re-added to index
```
