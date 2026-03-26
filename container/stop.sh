#!/bin/bash
set -euo pipefail

echo "=== Stopping Observability Stack ==="

podman pod stop observability 2>/dev/null || true
podman pod rm observability 2>/dev/null || true

echo "Stack stopped."
