import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

fig, ax = plt.subplots(1, 1, figsize=(28, 20))
ax.set_xlim(0, 28)
ax.set_ylim(0, 20)
ax.axis('off')
fig.patch.set_facecolor('#0d1117')
ax.set_facecolor('#0d1117')

# ── Colour palette ──────────────────────────────────────────────────────────
C = {
    'bg':       '#0d1117',
    'panel':    '#161b22',
    'border':   '#30363d',
    'green':    '#238636',
    'green_l':  '#2ea043',
    'blue':     '#1f6feb',
    'blue_l':   '#388bfd',
    'purple':   '#6e40c9',
    'purple_l': '#8957e5',
    'orange':   '#d1782a',
    'orange_l': '#e8912d',
    'red':      '#b91c1c',
    'red_l':    '#dc2626',
    'teal':     '#0e7490',
    'teal_l':   '#0891b2',
    'gray':     '#21262d',
    'text':     '#e6edf3',
    'text_dim': '#8b949e',
    'text_hdr': '#f0f6fc',
    'yellow':   '#9e6a03',
    'yellow_l': '#d29922',
    'kafka_bg': '#1a2332',
    'infra_bg': '#141d26',
    'mon_bg':   '#1a1f2e',
}

def box(ax, x, y, w, h, fc, ec, lw=1.2, radius=0.25, alpha=1.0):
    rect = FancyBboxPatch((x, y), w, h,
                          boxstyle=f"round,pad=0,rounding_size={radius}",
                          facecolor=fc, edgecolor=ec, linewidth=lw, alpha=alpha, zorder=3)
    ax.add_patch(rect)
    return rect

def label(ax, x, y, txt, size=7.5, color=None, bold=False, ha='center', va='center', zorder=5):
    color = color or C['text']
    weight = 'bold' if bold else 'normal'
    ax.text(x, y, txt, fontsize=size, color=color, ha=ha, va=va,
            fontweight=weight, zorder=zorder,
            fontfamily='monospace')

def section_bg(ax, x, y, w, h, fc, ec, title, title_color, lw=1.5):
    rect = FancyBboxPatch((x, y), w, h,
                          boxstyle="round,pad=0,rounding_size=0.3",
                          facecolor=fc, edgecolor=ec, linewidth=lw, alpha=0.55, zorder=1)
    ax.add_patch(rect)
    ax.text(x + 0.18, y + h - 0.22, title, fontsize=7.5, color=title_color,
            ha='left', va='top', fontweight='bold', fontstyle='italic', zorder=2,
            fontfamily='monospace')

def arrow(ax, x1, y1, x2, y2, color, lw=1.2, style='->', head=8, zorder=4):
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle=f'->, head_width=0.18, head_length=0.15',
                                color=color, lw=lw, connectionstyle='arc3,rad=0.0'),
                zorder=zorder)

def dashed_arrow(ax, x1, y1, x2, y2, color, lw=1.0, zorder=4):
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle='->, head_width=0.15, head_length=0.13',
                                color=color, lw=lw, linestyle='dashed',
                                connectionstyle='arc3,rad=0.0'),
                zorder=zorder)

# ════════════════════════════════════════════════════════════════════════════
# TITLE
# ════════════════════════════════════════════════════════════════════════════
ax.text(14, 19.5, 'Price Alert System — Deployment Architecture',
        fontsize=15, color=C['text_hdr'], ha='center', va='center',
        fontweight='bold', fontfamily='monospace', zorder=10)
ax.text(14, 19.1, 'Docker · KRaft Kafka 3-Broker Cluster · Spring Boot 4 · Java 25 · Terraform-managed',
        fontsize=7.5, color=C['text_dim'], ha='center', va='center',
        fontfamily='monospace', zorder=10)

# ════════════════════════════════════════════════════════════════════════════
# SECTION BACKGROUNDS
# ════════════════════════════════════════════════════════════════════════════
# Client zone
section_bg(ax, 0.3, 17.0, 4.2, 1.6, C['gray'], C['border'], '[EXTERNAL CLIENT]', C['text_dim'])
# Ingestion lane
section_bg(ax, 0.3, 13.8, 7.2, 3.0, C['infra_bg'], C['blue'], '[INGESTION]', C['blue_l'])
# Core services lane
section_bg(ax, 0.3, 8.8, 13.2, 4.8, C['infra_bg'], C['green'], '[APPLICATION SERVICES]', C['green_l'])
# Kafka cluster
section_bg(ax, 7.7, 13.8, 6.2, 3.0, C['kafka_bg'], C['orange'], '[KAFKA CLUSTER  KRaft · RF=3]', C['orange_l'])
# Infrastructure
section_bg(ax, 0.3, 5.6, 13.2, 2.9, C['infra_bg'], C['purple'], '[INFRASTRUCTURE]', C['purple_l'])
# Monitoring stack
section_bg(ax, 14.3, 5.6, 13.2, 12.9, C['mon_bg'], C['teal'], '[OBSERVABILITY STACK]', C['teal_l'])

# ════════════════════════════════════════════════════════════════════════════
# HELPER — microservice card
# ════════════════════════════════════════════════════════════════════════════
def svc_card(ax, x, y, w, h, title, subtitle, jvm_min, jvm_max, port, extra_lines,
             fc, ec, port_color=None):
    box(ax, x, y, w, h, fc, ec, lw=1.5)
    port_color = port_color or C['text_dim']
    # Title bar
    box(ax, x, y + h - 0.48, w, 0.48, ec, ec, lw=0, radius=0.0)
    label(ax, x + w/2, y + h - 0.24, title, size=7.5, color='#ffffff', bold=True)
    label(ax, x + w/2, y + h - 0.70, subtitle, size=6.2, color=C['text_dim'])
    # JVM row
    jvm_txt = f'JVM  Xms{jvm_min} / Xmx{jvm_max}  ZGC+Gen'
    label(ax, x + w/2, y + h - 1.02, jvm_txt, size=6.0, color=C['yellow_l'])
    # Port
    label(ax, x + w/2, y + h - 1.28, f'port {port}', size=6.0, color=port_color)
    # Extra lines
    for i, line in enumerate(extra_lines):
        label(ax, x + w/2, y + h - 1.56 - i * 0.26, line, size=5.8, color=C['text_dim'])

# ════════════════════════════════════════════════════════════════════════════
# EXTERNAL CLIENT
# ════════════════════════════════════════════════════════════════════════════
box(ax, 0.6, 17.25, 3.5, 1.1, C['gray'], C['border'], lw=1.2)
label(ax, 2.35, 18.05, 'REST Client / Browser', size=7.5, color=C['text'], bold=True)
label(ax, 2.35, 17.75, 'HTTP  Bearer JWT', size=6.5, color=C['text_dim'])
label(ax, 2.35, 17.47, 'GET/POST  /api/v1/alerts  |  /api/v1/notifications', size=6.0, color=C['text_dim'])

# ════════════════════════════════════════════════════════════════════════════
# ALERT-API
# ════════════════════════════════════════════════════════════════════════════
svc_card(ax, 0.55, 9.1, 3.8, 4.3,
         'alert-api',
         'Spring Boot 4 · Java 25 · eclipse-temurin',
         '128m', '256m', '8080 ✦ external',
         ['DB pool  primary 20 / replica 10',
          'Kafka producer  acks=all',
          'Redis cache  :6379',
          'Outbox poll 2000ms  batch 20',
          'Flyway migrations ON',
          'JWT HS256 auth'],
         C['panel'], C['green'], port_color=C['green_l'])

# ════════════════════════════════════════════════════════════════════════════
# MARKET-FEED-SIMULATOR
# ════════════════════════════════════════════════════════════════════════════
svc_card(ax, 0.55, 14.05, 3.5, 2.45,
         'market-feed-simulator',
         'Spring Boot 4 · WebSocket server',
         '64m', '128m', '8085 ✦ external',
         ['RANDOM_WALK  vol=0.001',
          'tick-interval 100ms (10/s)',
          'Symbols: US equities CSV'],
         C['panel'], C['blue'], port_color=C['blue_l'])

# ════════════════════════════════════════════════════════════════════════════
# TICK-INGESTOR
# ════════════════════════════════════════════════════════════════════════════
svc_card(ax, 4.35, 14.05, 3.0, 2.45,
         'tick-ingestor',
         'Spring Boot 4 · WS consumer',
         '128m', '256m', '8081 internal',
         ['WS → Kafka producer  acks=1',
          'batch 32KB  linger 20ms',
          'buf 64MB  Outbox 200ms/500'],
         C['panel'], C['blue'], port_color=C['blue_l'])

# ════════════════════════════════════════════════════════════════════════════
# EVALUATOR  ×2
# ════════════════════════════════════════════════════════════════════════════
svc_card(ax, 4.55, 9.1, 3.55, 4.3,
         'evaluator  ×2',
         'Spring Boot 4 · In-memory alert index',
         '256m', '512m', '8082 internal',
         ['Kafka consumer  evaluator-group',
          'CooperativeStickyAssignor',
          'market-ticks  16 parts → 8 each',
          'DB pool  primary 10 / replica 5',
          'Warmup batch 10000',
          'Outbox 1000ms / batch 50'],
         C['panel'], C['green'], port_color=C['green_l'])

# ════════════════════════════════════════════════════════════════════════════
# NOTIFICATION-PERSISTER
# ════════════════════════════════════════════════════════════════════════════
svc_card(ax, 8.55, 9.1, 3.5, 4.3,
         'notification-persister',
         'Spring Boot 4 · Kafka consumer',
         '128m', '256m', '8083 internal',
         ['Kafka consumer  notif-persister-grp',
          'CooperativeStickyAssignor',
          'Consumes  alert-triggers topic',
          'DB pool  primary 10',
          'Writes to notifications table'],
         C['panel'], C['green'], port_color=C['green_l'])

# ════════════════════════════════════════════════════════════════════════════
# KAFKA CLUSTER
# ════════════════════════════════════════════════════════════════════════════
def kafka_broker(ax, x, y, node_id, ext_port, controller_port):
    box(ax, x, y, 1.75, 2.45, C['kafka_bg'], C['orange'], lw=1.5)
    box(ax, x, y + 2.0, 1.75, 0.45, C['orange'], C['orange'], lw=0, radius=0.0)
    label(ax, x + 0.875, y + 2.22, f'kafka{"-"+str(node_id) if node_id>1 else ""}', size=7.0, color='#fff', bold=True)
    label(ax, x + 0.875, y + 1.73, f'apache/kafka:3.9.0', size=5.5, color=C['text_dim'])
    label(ax, x + 0.875, y + 1.47, f'KRaft broker+controller', size=5.7, color=C['orange_l'])
    label(ax, x + 0.875, y + 1.2,  f'Node ID: {node_id}', size=5.8, color=C['text_dim'])
    label(ax, x + 0.875, y + 0.95, f'INTERNAL :19092', size=5.5, color=C['text_dim'])
    label(ax, x + 0.875, y + 0.70, f'EXTERNAL :{ext_port}  ✦ host', size=5.5, color=C['orange_l'])
    label(ax, x + 0.875, y + 0.45, f'CONTROLLER :{controller_port}', size=5.5, color=C['text_dim'])
    label(ax, x + 0.875, y + 0.18, f'Healthcheck  10s/10r/30s', size=5.3, color=C['text_dim'])

kafka_broker(ax, 7.95, 14.05, 1, 9092, 9093)
kafka_broker(ax, 9.85, 14.05, 2, 9095, 9093)
kafka_broker(ax, 11.75, 14.05, 3, 9094, 9093)

# Kafka quorum voters label
label(ax, 10.72, 13.85, 'Quorum voters: 1@kafka:9093 · 2@kafka-2:9093 · 3@kafka-3:9093',
      size=5.5, color=C['orange_l'])

# Topics box
box(ax, 7.95, 13.0, 5.55, 0.72, '#1c2a1c', C['green'], lw=1.0)
label(ax, 10.725, 13.55, 'TOPICS', size=6.0, color=C['green_l'], bold=True)
label(ax, 10.725, 13.25, 'market-ticks  16p  RF3  4h  |  alert-changes  8p  RF3  1d  |  alert-triggers  8p  RF3  7d',
      size=5.5, color=C['text_dim'])

# kafka-init one-shot
box(ax, 7.95, 12.3, 5.55, 0.58, '#1a1a2e', C['purple'], lw=1.0, radius=0.15)
label(ax, 10.725, 12.62, 'kafka-init  (one-shot)  attach=true  must_run=false',
      size=5.8, color=C['purple_l'])
label(ax, 10.725, 12.38, 'Creates topics · Waits for all 3 brokers · Exits clean',
      size=5.5, color=C['text_dim'])

# ════════════════════════════════════════════════════════════════════════════
# INFRASTRUCTURE  — PostgreSQL + Redis
# ════════════════════════════════════════════════════════════════════════════
# PostgreSQL
box(ax, 0.6, 5.85, 5.8, 2.45, C['panel'], C['purple'], lw=1.5)
box(ax, 0.6, 7.83, 5.8, 0.47, C['purple'], C['purple'], lw=0, radius=0.0)
label(ax, 3.5, 8.06, 'PostgreSQL 17', size=8.0, color='#fff', bold=True)
label(ax, 3.5, 7.60, 'postgres:17  |  port 5432 ✦ external  |  prevent_destroy=true', size=6.0, color=C['text_dim'])

# DB pool summary table
cols = ['Service', 'Primary pool', 'Replica pool', 'Conn timeout']
rows = [
    ['alert-api',            'max=20 idle=5', 'max=10 idle=2', '3s'],
    ['tick-ingestor',        'max=10 idle=2', '—',             '3s'],
    ['evaluator ×2',         'max=10 idle=2', 'max=5 idle=1',  '3s'],
    ['notification-persister','max=10 idle=2','—',             '3s'],
]
cx = [0.85, 2.1, 3.5, 5.0]
label(ax, cx[0], 7.33, cols[0], size=5.5, color=C['purple_l'], bold=True, ha='left')
label(ax, cx[1], 7.33, cols[1], size=5.5, color=C['purple_l'], bold=True, ha='left')
label(ax, cx[2], 7.33, cols[2], size=5.5, color=C['purple_l'], bold=True, ha='left')
label(ax, cx[3], 7.33, cols[3], size=5.5, color=C['purple_l'], bold=True, ha='left')
for i, row in enumerate(rows):
    yy = 7.08 - i * 0.25
    for j, val in enumerate(row):
        label(ax, cx[j], yy, val, size=5.5, color=C['text_dim'], ha='left')

label(ax, 3.5, 6.05, 'Healthcheck: pg_isready  5s interval  10 retries  10s start_period', size=5.5, color=C['text_dim'])
label(ax, 3.5, 5.82, 'Volume: price-alert-pgdata → /var/lib/postgresql/data', size=5.5, color=C['text_dim'])

# Redis
box(ax, 6.7, 5.85, 6.6, 2.45, C['panel'], C['red'], lw=1.5)
box(ax, 6.7, 7.83, 6.6, 0.47, C['red'], C['red'], lw=0, radius=0.0)
label(ax, 10.0, 8.06, 'Redis 7', size=8.0, color='#fff', bold=True)
label(ax, 10.0, 7.60, 'redis:7-alpine  |  port 6379 ✦ external', size=6.0, color=C['text_dim'])
label(ax, 10.0, 7.33, 'Used by: alert-api  (dedup / rate-limit cache)', size=5.8, color=C['text_dim'])
label(ax, 10.0, 7.06, 'Healthcheck: redis-cli ping  5s interval  10 retries  5s start', size=5.5, color=C['text_dim'])
label(ax, 10.0, 6.78, 'SPRING_DATA_REDIS_HOST=redis  SPRING_DATA_REDIS_PORT=6379', size=5.5, color=C['text_dim'])
label(ax, 10.0, 6.50, 'No persistence volume (ephemeral)', size=5.5, color=C['text_dim'])
label(ax, 10.0, 6.0,  'Restart: unless-stopped', size=5.5, color=C['text_dim'])
label(ax, 10.0, 5.78,  'Network: price-alert-network (bridge)', size=5.5, color=C['text_dim'])

# ════════════════════════════════════════════════════════════════════════════
# MONITORING STACK
# ════════════════════════════════════════════════════════════════════════════
def mon_card(ax, x, y, w, h, title, image, port_line, detail_lines, fc, ec):
    box(ax, x, y, w, h, fc, ec, lw=1.3)
    box(ax, x, y + h - 0.38, w, 0.38, ec, ec, lw=0, radius=0.0)
    label(ax, x + w/2, y + h - 0.19, title, size=7.0, color='#fff', bold=True)
    label(ax, x + w/2, y + h - 0.55, image, size=5.5, color=C['text_dim'])
    label(ax, x + w/2, y + h - 0.78, port_line, size=5.8, color=C['teal_l'])
    for i, d in enumerate(detail_lines):
        label(ax, x + w/2, y + h - 1.03 - i * 0.24, d, size=5.5, color=C['text_dim'])

# Prometheus
mon_card(ax, 14.5, 15.5, 4.0, 2.9,
         'Prometheus', 'prom/prometheus:latest', 'port 9090 ✦ external',
         ['Scrapes /actuator/prometheus',
          'from all 6 Spring services',
          'Config: prometheus.yml (ro)',
          'Restart: unless-stopped'],
         C['panel'], C['teal'])

# Grafana
mon_card(ax, 19.0, 15.5, 4.0, 2.9,
         'Grafana', 'grafana/grafana:latest', 'port 3000 ✦ external',
         ['Datasources: Prometheus, Loki, Tempo',
          'Dashboards provisioned from volume',
          'Admin: admin / (secrets.tfvars)',
          'Depends on: Prometheus, Loki, Tempo'],
         C['panel'], C['teal'])

# Loki
mon_card(ax, 14.5, 11.9, 4.0, 2.9,
         'Loki', 'grafana/loki:latest', 'port 3100 ✦ external',
         ['Log aggregation backend',
          'Config: loki.yml (ro mount)',
          'Push endpoint: /loki/api/v1/push',
          'Restart: unless-stopped'],
         C['panel'], C['teal'])

# Promtail
mon_card(ax, 19.0, 11.9, 4.0, 2.9,
         'Promtail', 'grafana/promtail:latest', 'no external port',
         ['Tails Docker container logs',
          'Mounts /var/run/docker.sock (ro)',
          'Ships logs → Loki :3100',
          'Depends on: Loki'],
         C['panel'], C['teal'])

# Tempo
mon_card(ax, 16.75, 8.2, 4.0, 2.9,
         'Tempo', 'grafana/tempo:latest', 'port 3200 + OTLP 4318 ✦ external',
         ['Distributed tracing backend',
          'OTLP endpoint :4318/v1/traces',
          'All services → 100% sampling',
          'Production → 0.01 sampling'],
         C['panel'], C['teal'])

# Healthcheck legend box (monitoring)
box(ax, 14.5, 6.05, 8.5, 1.75, C['gray'], C['border'], lw=1.0)
label(ax, 18.75, 7.6,  'HEALTHCHECK SUMMARY', size=6.5, color=C['teal_l'], bold=True)
hc_data = [
    ('kafka / kafka-2 / kafka-3', 'broker-api-versions.sh', '10s', '10s', '10×', '30s'),
    ('postgres',                  'pg_isready',              '5s',  '5s',  '10×', '10s'),
    ('redis',                     'redis-cli ping',          '5s',  '5s',  '10×',  '5s'),
    ('alert-api',                 'wget actuator/health',    '10s', '5s',  '10×', '30s'),
    ('tick-ingestor',             'wget actuator/health',    '10s', '5s',  '10×', '20s'),
    ('evaluator ×2',              'wget actuator/health',    '10s', '5s',  '10×', '20s'),
    ('notification-persister',    'wget actuator/health',    '10s', '5s',  '10×', '20s'),
    ('market-feed-simulator',     'wget actuator/health',    '10s', '5s',  '10×', '20s'),
]
hdr_x = [14.65, 17.05, 19.4, 20.1, 20.8, 21.5]
hdrs  = ['Container', 'Probe', 'Interval', 'Timeout', 'Retries', 'Start']
for i, (hx, hh) in enumerate(zip(hdr_x, hdrs)):
    label(ax, hx, 7.37, hh, size=5.3, color=C['teal_l'], bold=True, ha='left')
for ri, row in enumerate(hc_data):
    yy = 7.17 - ri * 0.155
    xs = hdr_x
    for ci, val in enumerate(row):
        label(ax, xs[ci], yy, val, size=5.0, color=C['text_dim'], ha='left')

# ════════════════════════════════════════════════════════════════════════════
# JVM LEGEND
# ════════════════════════════════════════════════════════════════════════════
box(ax, 14.5, 5.6, 8.5, 0.95, C['gray'], C['border'], lw=1.0)
label(ax, 18.75, 6.3, 'JVM CONFIG  (all services — eclipse-temurin:25-jre-alpine  --enable-preview  ZGC+Generational)',
      size=6.0, color=C['yellow_l'], bold=True)
jvm_rows = [
    ('market-feed-simulator', 'Xms64m',  'Xmx128m'),
    ('alert-api',             'Xms128m', 'Xmx256m'),
    ('tick-ingestor',         'Xms128m', 'Xmx256m'),
    ('notification-persister','Xms128m', 'Xmx256m'),
    ('evaluator ×2',          'Xms256m', 'Xmx512m'),
]
xs_jvm = [14.65, 17.1, 18.4]
label(ax, xs_jvm[0], 6.05, 'Service', size=5.3, color=C['yellow_l'], bold=True, ha='left')
label(ax, xs_jvm[1], 6.05, 'Xms',     size=5.3, color=C['yellow_l'], bold=True, ha='left')
label(ax, xs_jvm[2], 6.05, 'Xmx',     size=5.3, color=C['yellow_l'], bold=True, ha='left')
for ri, (svc, xms, xmx) in enumerate(jvm_rows):
    yy = 5.85 - ri * 0  # horizontal layout
label(ax, 14.65, 5.78, 'simulator 64m→128m  |  alert-api 128m→256m  |  tick-ingestor 128m→256m  |  notification-persister 128m→256m  |  evaluator×2 256m→512m',
      size=5.5, color=C['text_dim'], ha='left', va='center')

# ════════════════════════════════════════════════════════════════════════════
# ARROWS  (data flow)
# ════════════════════════════════════════════════════════════════════════════

# Client → alert-api
arrow(ax, 2.35, 17.25, 2.35, 13.4, C['green_l'], lw=1.5)

# market-feed-simulator → tick-ingestor (WS)
arrow(ax, 4.05, 15.28, 4.35, 15.28, C['blue_l'], lw=1.5)
label(ax, 4.22, 15.44, 'WS', size=5.5, color=C['blue_l'])

# tick-ingestor → kafka (Produce market-ticks)
arrow(ax, 5.85, 15.28, 7.95, 15.85, C['orange_l'], lw=1.5)
label(ax, 6.8, 15.75, 'market-ticks', size=5.5, color=C['orange_l'])

# alert-api → kafka (Produce alert-changes)
arrow(ax, 2.45, 13.4, 8.95, 15.6, C['orange_l'], lw=1.3)
label(ax, 5.3, 14.7, 'alert-changes', size=5.5, color=C['orange_l'])

# kafka → evaluator (Consume market-ticks + alert-changes)
arrow(ax, 9.85, 13.85, 7.05, 12.7, C['green_l'], lw=1.5)
label(ax, 9.0, 13.5, 'market-ticks', size=5.5, color=C['green_l'])

# evaluator → kafka (Produce alert-triggers)
arrow(ax, 7.3, 11.8, 9.5, 13.0, C['orange_l'], lw=1.3)
label(ax, 7.8, 12.2, 'alert-triggers', size=5.5, color=C['orange_l'])

# kafka → notification-persister (Consume alert-triggers)
arrow(ax, 11.2, 13.85, 10.3, 13.4, C['green_l'], lw=1.3)
label(ax, 11.5, 13.5, 'alert-triggers', size=5.5, color=C['green_l'])

# alert-api → postgres
arrow(ax, 2.45, 9.1, 2.45, 8.3, C['purple_l'], lw=1.3)

# evaluator → postgres
arrow(ax, 6.32, 9.1, 4.0, 8.3, C['purple_l'], lw=1.3)

# notification-persister → postgres
arrow(ax, 10.3, 9.1, 5.2, 8.3, C['purple_l'], lw=1.3)

# tick-ingestor → postgres
arrow(ax, 5.85, 14.28, 5.85, 8.3, C['purple_l'], lw=1.0)

# alert-api → redis
arrow(ax, 3.55, 9.1, 9.0, 8.3, C['red_l'], lw=1.3)
label(ax, 5.8, 8.7, 'cache', size=5.5, color=C['red_l'])

# Services → Prometheus (dashed)
for px, py in [(4.45, 9.1), (8.12, 9.1), (10.5, 9.1)]:
    dashed_arrow(ax, px, py, 16.5, 15.5, C['teal_l'], lw=0.8)
label(ax, 13.5, 11.2, '/actuator\n/prometheus', size=5.5, color=C['teal_l'])

# Services → Tempo via OTLP (dashed)
dashed_arrow(ax, 4.45, 9.1, 18.75, 8.2, C['teal'], lw=0.7)
label(ax, 13.0, 9.0, 'OTLP :4318', size=5.5, color=C['teal'])

# Promtail → Loki
arrow(ax, 19.5, 11.9, 16.5, 14.8, C['teal_l'], lw=1.0)
label(ax, 17.5, 13.5, 'push logs', size=5.5, color=C['teal_l'])

# Grafana → datasources
dashed_arrow(ax, 21.0, 15.5, 18.5, 14.8, C['teal'], lw=0.8)
dashed_arrow(ax, 21.0, 15.5, 21.0, 15.5, C['teal'], lw=0.8)
dashed_arrow(ax, 21.0, 15.5, 18.75, 11.1, C['teal'], lw=0.8)

# ════════════════════════════════════════════════════════════════════════════
# LEGEND
# ════════════════════════════════════════════════════════════════════════════
box(ax, 23.2, 8.2, 4.5, 10.15, C['gray'], C['border'], lw=1.0, radius=0.2)
label(ax, 25.45, 18.15, 'LEGEND', size=7.0, color=C['text_hdr'], bold=True)

legend_items = [
    (C['green_l'],   '─→', 'Data flow / API call'),
    (C['orange_l'],  '─→', 'Kafka produce/consume'),
    (C['purple_l'],  '─→', 'DB connection (HikariCP)'),
    (C['red_l'],     '─→', 'Redis cache'),
    (C['blue_l'],    '─→', 'WebSocket stream'),
    (C['teal_l'],    '- →','Metrics / traces (async)'),
]
for i, (col, sym, desc) in enumerate(legend_items):
    yy = 17.75 - i * 0.42
    ax.plot([23.4, 23.9], [yy, yy], color=col, lw=1.8)
    ax.annotate('', xy=(23.9, yy), xytext=(23.75, yy),
                arrowprops=dict(arrowstyle='->', color=col, lw=1.4))
    label(ax, 24.05, yy, f'{desc}', size=6.0, color=C['text_dim'], ha='left')

# Kafka topic details
label(ax, 25.45, 15.2, 'KAFKA TOPICS', size=6.5, color=C['orange_l'], bold=True)
kafka_legend = [
    ('market-ticks',   '16p · RF3 · 4h  ret'),
    ('alert-changes',  ' 8p · RF3 · 1d  ret'),
    ('alert-triggers', ' 8p · RF3 · 7d  ret'),
]
for i, (t, d) in enumerate(kafka_legend):
    yy = 14.85 - i * 0.38
    label(ax, 23.4, yy, t, size=5.8, color=C['orange_l'], ha='left', bold=True)
    label(ax, 23.4, yy - 0.18, d, size=5.4, color=C['text_dim'], ha='left')

# Profiles
label(ax, 25.45, 13.15, 'SPRING PROFILES', size=6.5, color=C['green_l'], bold=True)
label(ax, 23.4, 12.88, 'local dev: localhost:* defaults', size=5.5, color=C['text_dim'], ha='left')
label(ax, 23.4, 12.65, 'docker:    container DNS names', size=5.5, color=C['text_dim'], ha='left')
label(ax, 23.4, 12.42, 'production:replica DB, tracing 1%', size=5.5, color=C['text_dim'], ha='left')

# Outbox
label(ax, 25.45, 12.05, 'OUTBOX (namastack)', size=6.5, color=C['purple_l'], bold=True)
outbox_rows = [
    ('alert-api',       '2000ms / batch 20 / 5 retries'),
    ('tick-ingestor',   ' 200ms / batch 500 / 3 retries'),
    ('evaluator',       '1000ms / batch 50 / 3 retries'),
]
for i, (svc, cfg) in enumerate(outbox_rows):
    yy = 11.70 - i * 0.38
    label(ax, 23.4, yy, svc, size=5.5, color=C['purple_l'], ha='left', bold=True)
    label(ax, 23.4, yy - 0.18, cfg, size=5.2, color=C['text_dim'], ha='left')

# Startup order
label(ax, 25.45, 10.35, 'STARTUP ORDER', size=6.5, color=C['blue_l'], bold=True)
order = [
    '1. network',
    '2. kafka × 3  +  postgres  +  redis',
    '3. kafka-init (one-shot, blocks)',
    '4. market-feed-simulator',
    '5. alert-api  (Flyway, tick-ingestor dep)',
    '6. tick-ingestor  |  evaluator × 2',
    '7. notification-persister',
    '8. monitoring stack',
]
for i, step in enumerate(order):
    label(ax, 23.4, 10.05 - i * 0.3, step, size=5.5, color=C['text_dim'], ha='left')

# Endpoints
label(ax, 25.45, 7.7, 'EXPOSED ENDPOINTS', size=6.5, color=C['teal_l'], bold=True)
endpoints = [
    'alert-api REST   :8080',
    'market-feed-sim WS   :8085',
    'Kafka (broker 1)   :9092',
    'Kafka (broker 3)   :9094',
    'Kafka (broker 2)   :9095',
    'PostgreSQL   :5432',
    'Redis   :6379',
    'Grafana   :3000',
    'Prometheus   :9090',
    'Loki   :3100',
    'Tempo query   :3200',
    'Tempo OTLP   :4318',
]
for i, ep in enumerate(endpoints):
    label(ax, 23.4, 7.42 - i * 0.28, ep, size=5.3, color=C['text_dim'], ha='left')

# ════════════════════════════════════════════════════════════════════════════
# NETWORK LABEL
# ════════════════════════════════════════════════════════════════════════════
box(ax, 0.3, 5.3, 22.5, 0.22, '#161b22', C['border'], lw=0.8, radius=0.1, alpha=0.8)
label(ax, 11.55, 5.41, '[price-alert-network  Docker bridge  — all containers share this network]',
      size=6.0, color=C['text_dim'], bold=False)

plt.tight_layout(pad=0.1)
out = '/Users/pchikkakalya-kempanna/Documents/AI/design_price_alert/price-alert-system/docs/deployment_architecture.png'
plt.savefig(out, dpi=160, bbox_inches='tight', facecolor=C['bg'], edgecolor='none')
print(f'Saved: {out}')
