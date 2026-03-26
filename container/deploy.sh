#!/bin/bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== OpenTelemetry Demo - Deploy Observability Stack ==="

# --- Stop existing pod if running ---
echo "Stopping existing pod (if any)..."
podman pod stop observability 2>/dev/null || true
podman pod rm observability 2>/dev/null || true

# --- Start the pod ---
echo "Starting observability stack..."
podman kube play "$DIR/observability-stack.yaml"

echo ""
echo "=== Stack started ==="
echo "Grafana:     http://localhost:3000  (no login required)"
echo "OTLP gRPC:   localhost:4317"
echo "OTLP HTTP:   localhost:4318"
echo ""
echo "Internal only (not exposed): Prometheus :9090, Loki :3100, Tempo :3200"
echo ""
echo "Check status:  podman pod ps"
echo "View logs:     podman pod logs observability"
echo "Stop:          container/stop.sh"
