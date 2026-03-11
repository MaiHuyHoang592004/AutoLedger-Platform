# Product Scope

## 1. Problem Statement

Hệ thống này mô phỏng một nền tảng thanh toán kiểu marketplace, trong đó một hệ thống nghiệp vụ cần giữ tiền, chốt tiền, hoàn giữ tiền, và ghi nhận ledger tài chính một cách an toàn, có thể audit, và chịu được retry/failure.

Bài toán chính:
- Tách business workflow khỏi financial correctness.
- Tránh để service nghiệp vụ tự sửa số dư hoặc tự ghi ledger.
- Mô phỏng các pattern quan trọng của payment system thực tế:
  - authorization hold
  - capture
  - reversal
  - idempotency
  - append-only ledger
  - event-driven integration
  - webhook deduplication

## 2. Why this matters

Trong hệ payment thật:
- retry có thể tạo double charge nếu không có idempotency
- update/delete ledger làm mất auditability
- external PSP có thể gửi duplicate webhook hoặc delayed webhook
- financial system phải ưu tiên correctness hơn convenience

MiniBank được thiết kế để mô phỏng đúng các ràng buộc này ở mức demo nhưng theo tư duy bank-grade.

## 3. Actors

### Customer / Guest
Người đặt xe và thanh toán.

### Host / Merchant
Người cung cấp xe và nhận tiền sau khi trip hoàn thành.

### Car Rental Service
Service nghiệp vụ quản lý booking, trip lifecycle, pricing, calendar, host confirmation.

### MiniBank Payment Service
Service tài chính quản lý payment lifecycle, holds, ledger, balances, reversals, outbox events.

### PSP Sandbox
Hệ thống giả lập external payment gateway để mô phỏng authorization responses, webhooks, duplicate events, delayed events, và fault scenarios.

## 4. Why there are 3 systems

### Car Rental
Giữ business logic. Không sở hữu ledger tài chính.

### MiniBank
Giữ money movement và financial invariants. Không sở hữu booking lifecycle.

### PSP Sandbox
Đóng vai external world giả lập. Giúp test retry, webhook, failure, reconciliation.

## 5. MVP Scope

MVP runtime scope hiện tại:
- init payment
- authorize hold
- get payment
- void hold
- capture when trip completes

MVP runtime hiện tại chưa phải full bank platform.

## 6. Non-goals

Ngoài phạm vi hiện tại:
- AML/KYC thực tế
- card network / ISO8583 / SWIFT
- interbank settlement đầy đủ
- dispute/chargeback engine hoàn chỉnh
- production-grade fraud engine
- true multi-tenant core banking

## 7. Success Criteria

Project thành công khi chứng minh được:
- business service không trực tiếp ghi ledger
- financial operations là idempotent
- ledger là immutable / append-only
- duplicate requests không tạo duplicate financial side effect
- state changes có audit trail
- system có thể mở rộng sang outbox/Kafka/reconciliation/chaos testing