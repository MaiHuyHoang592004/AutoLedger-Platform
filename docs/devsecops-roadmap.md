# DevSecOps Roadmap

## 1. Goal

Biến project từ “chạy được local” thành “có bằng chứng vận hành, bảo mật, và kiểm soát thay đổi”.

## 2. CI gates
- build
- unit tests
- integration tests
- lint/style checks
- fail pipeline nếu test đỏ

## 3. Artifact and supply-chain controls
- dependency scanning
- SBOM generation
- image scanning
- image signing nếu phase sau cần mạnh hơn

## 4. Runtime hardening
- non-root containers
- health checks
- graceful shutdown
- environment-based config
- secret injection thay vì hardcode

## 5. Kubernetes direction
- readiness/liveness probes
- ConfigMap/Secret
- resource requests/limits
- NetworkPolicy
- rollout strategy

## 6. Security visibility
- structured logs
- trace_id propagation
- audit events
- PII masking policy khi cần

## 7. Operations signals
- payment latency
- ledger throughput
- idempotency conflicts
- outbox lag
- duplicate webhook rate
- reversal frequency

## 8. Future controls
- policy-as-code
- admission controls
- secret rotation automation
- anomaly detection
