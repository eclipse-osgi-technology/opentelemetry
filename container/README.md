# Observability Stack

Podman pod with OpenTelemetry Collector, Prometheus, Tempo, Loki, and Grafana.

## Start

### Linux / macOS

```bash
./deploy.sh
```

### Windows (PowerShell)

```powershell
podman pod stop observability 2>$null; podman pod rm observability 2>$null
podman kube play .\observability-stack.yaml
```

## Stop

### Linux / macOS

```bash
./stop.sh
```

### Windows (PowerShell)

```powershell
podman pod stop observability; podman pod rm observability
```

## Endpoints

| Service   | URL                   | Description              |
|-----------|-----------------------|--------------------------|
| Grafana   | http://localhost:3000  | UI (no login required)   |
| OTLP gRPC | localhost:4317       | Telemetry ingestion      |
| OTLP HTTP | localhost:4318       | Telemetry ingestion      |

Prometheus (9090), Loki (3100), and Tempo (3200) are pod-internal only.

## Files

| File                       | Purpose                                    |
|----------------------------|--------------------------------------------|
| `deploy.sh`                | Start the pod                              |
| `stop.sh`                  | Stop and remove the pod                    |
| `observability-stack.yaml` | Pod definition with all configs inline     |

## Data Flow

```
                               |-> [Tempo]      (traces)   ->|
[App] --OTLP--> [Collector] ---|-> [Prometheus] (metrics)  ->|---> [Grafana]
                               |-> [Loki]       (logs)     ->|

Grafana reads from all three backends.
```
