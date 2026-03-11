# Security Architecture

## 1. Purpose

Tài liệu này mô tả trust boundaries, threat model ở mức hệ thống, và các controls chính cho MiniBank platform.

## 2. Assets cần bảo vệ
- payment records
- hold records
- ledger journals
- ledger postings
- account balances
- audit trail
- idempotency store
- webhook authenticity
- secrets/configuration

## 3. Trust boundaries

### Trusted components
- MiniBank database
- MiniBank internal stored procedures
- Car Rental service trong boundary nội bộ

### Partially trusted / externally simulated
- PSP Sandbox
- webhook payloads
- external-like event sources

### Untrusted inputs
- incoming API payloads
- webhook payloads
- duplicate/replayed network requests

## 4. Core security goals
- không cho mutate ledger history
- không cho duplicate request tạo duplicate financial effect
- không cho webhook giả mạo đi vào state transition hợp lệ
- mọi thay đổi tài chính quan trọng phải trace được

## 5. Security controls

### Immutable financial history
- ledger tables append-only
- audit tables append-only
- reversal instead of mutation

### Idempotency protection
- idem key + request hash
- in-progress / completed state tracking
- payload mismatch detection

### Webhook verification
Future/target behavior:
- signature verification
- replay window checks
- dedupe store cho processed webhook ids

### Secrets handling
- secrets không hardcode trong repo
- config per environment
- target state dùng secret store phù hợp

### Data minimization
Car Rental chỉ giữ reference cần thiết, không mirror full MiniBank state.

### Auditability
- `before_state_json` / `after_state_json`
- `correlation_id` / `trace_id`
- hash chain cho audit hoặc ledger

## 6. Threat scenarios
- duplicate API request
- replay request with altered payload
- forged webhook
- delayed or out-of-order webhook
- accidental mutation of financial history
- unauthorized direct DB manipulation
- secret leakage

## 7. Residual risks
- sandbox-first environments có thể bỏ qua vài production controls
- nếu chưa bật full webhook verification thì sandbox flow vẫn còn soft spot
- nếu chưa có K8s network policies hoặc secret rotation automation thì posture vẫn mới ở mức demo/devsecops roadmap