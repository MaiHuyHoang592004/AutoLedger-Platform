# Implementation Roadmap

## Phase 1 — Thin Slice Integration

### Goal
Chứng minh luồng payment cơ bản hoạt động end-to-end giữa Car Rental và MiniBank.

### Scope
- init payment
- authorize hold
- get payment
- void hold
- capture on trip completion

### Done criteria
- Car Rental gọi MiniBank thành công qua REST
- capture được gọi khi complete trip
- duplicate request không tạo duplicate financial side effects cơ bản

### Demo value
- business service tách khỏi ledger
- hold/capture flow hoạt động thật

## Phase 2 — Runtime Truth and Contracts

### Goal
Đồng bộ docs với implementation để AI agent và contributor không hiểu sai.

### Scope
- rewrite docs
- archive historical docs
- establish source-of-truth hierarchy
- publish state machines và integration contract chuẩn

### Done criteria
- `current-status.md`, `integration-contract.md`, `source-of-truth.md` khớp runtime

### Demo value
- repo trở nên AI-friendly và reviewer-friendly

## Phase 3 — Outbox + Kafka + Sandbox Reliability

### Goal
Nâng hệ thống từ REST-only sang REST + async event-driven.

### Scope
- outbox publisher worker
- Kafka topics
- base consumer
- PSP Sandbox scenarios cho duplicate/delayed events

### Done criteria
- financial commit không phụ thuộc Kafka up/down
- duplicate/delayed event path được test

### Demo value
- project chuyển từ CRUD integration sang resilience-aware payment platform

## Phase 4 — Refund / Reversal / Reconciliation

### Goal
Mở rộng financial correctness story.

### Scope
- refund thin slice
- reversal workflows rõ ràng
- reconciliation reports
- settlement documentation

### Done criteria
- có thể chứng minh correction không cần sửa ledger history

### Demo value
- portfolio mang màu bank-grade mạnh hơn

## Phase 5 — Observability + DevSecOps Evidence

### Goal
Tạo operational evidence.

### Scope
- metrics
- dashboards
- traces
- structured logs
- CI gates
- secret handling baseline
- image/dependency scanning

### Done criteria
- trace xuyên Car Rental -> MiniBank -> Sandbox hoặc async pipeline
- dashboard cho payment latency, outbox lag, idempotency conflicts

### Demo value
- rất hợp JD DevOps/DevSecOps

## Phase 6 — Chaos / GameDay / Runtime Security

### Goal
Chứng minh hệ thống chịu lỗi và recover được.

### Scope
- fault injection
- kill service / Kafka / worker scenarios
- runbooks
- invariant verification
- game day demo

### Done criteria
- mỗi chaos scenario có expected invariant, evidence, recovery path

### Demo value
- project bật hẳn lên level production-style demo