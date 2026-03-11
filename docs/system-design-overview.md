# System Design Overview

## 1. System Summary

Hệ thống gồm 3 thành phần chính:
- Car Rental Service
- MiniBank Payment Service
- PSP Sandbox

Mục tiêu của hệ thống là mô phỏng một payment platform kiểu marketplace, trong đó service nghiệp vụ gọi một service tài chính riêng để thực hiện giữ tiền, chốt tiền, void hold, và ghi nhận ledger.

## 2. Core Responsibilities

### Car Rental Service
Phụ trách:
- booking lifecycle
- trip lifecycle
- calendar availability
- pricing
- host confirmation
- complete trip business flow

Không phụ trách:
- ledger posting
- account balances
- financial reversals

### MiniBank Payment Service
Phụ trách:
- payment initialization
- authorization hold
- hold void / expiration
- capture
- ledger accounting
- dual balance management
- reversal operations
- audit events
- idempotent write procedures
- outbox event generation

### PSP Sandbox
Phụ trách:
- authorization simulation
- webhook delivery simulation
- duplicate/delayed webhook scenarios
- fault injection cho payment gateway behavior

## 3. Core Design Principles

- immutable financial ledger
- double-entry accounting
- append-only financial history
- generalized idempotency
- reversal instead of mutation
- dual balance model
- transactional outbox
- cryptographic tamper evidence

## 4. Current command integration model

Hiện tại luồng command giữa Car Rental và MiniBank là synchronous REST.

Canonical command path:
- `POST /api/payments`
- `POST /api/payments/{paymentId}/authorize-hold`
- `GET /api/payments/{paymentId}`
- `POST /api/holds/{holdId}/void`
- `POST /api/holds/{holdId}/capture`

## 5. Runtime vs Target Architecture

### Current runtime
- Car Rental là Spring Boot monolith
- MiniBank là service .NET riêng
- command path là REST sync
- capture đã được integrate vào booking completion flow
- outbox/Kafka nằm ở mức architectural intent hoặc next phase
- PSP Sandbox có trong design intent nhưng chưa phải canonical runtime path chính của thin slice hiện tại

### Target architecture
- MiniBank publish events từ transactional outbox
- events đi qua Kafka
- PSP Sandbox tham gia đầy đủ vào gateway simulation và webhook scenarios
- observability và resilience được nâng lên production-style

## 6. High-level Flow

1. Car Rental tạo payment tại MiniBank
2. MiniBank authorize hold để giữ tiền
3. Nếu booking bị cancel hoặc host reject, hold bị void
4. Nếu trip hoàn thành, hold được capture
5. Capture tạo ledger journal và postings
6. Sai financial record thì reverse bằng reversal journal, không sửa lịch sử
