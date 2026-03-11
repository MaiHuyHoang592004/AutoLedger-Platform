# Event Contracts

## 1. Purpose

Tài liệu này định nghĩa event names và contract cho outbox/Kafka integration.
Hiện một số event có thể mới ở mức design intent hoặc outbox-ready, chưa phải full runtime publish.

## 2. Event design rules
- event names phải ổn định
- payload có `event_id` và `event_type`
- consumers phải idempotent
- producer không được phụ thuộc consumer xử lý thành công mới commit financial transaction
- partition key nên bám aggregate gốc để giữ ordering hợp lý

## 3. Common event envelope

```json
{
  "event_id": "uuid",
  "event_type": "PaymentCreated",
  "event_version": 1,
  "occurred_at": "2026-03-11T10:00:00Z",
  "aggregate_type": "Payment",
  "aggregate_id": "uuid",
  "correlation_id": "uuid",
  "payload": {}
}
```

## 4. Events

### PaymentCreated
- Producer: MiniBank
- Trigger: init payment committed
- Partition key: `payment_id`
- Idempotency expectation: same `event_id` published at-least-once; consumers must dedupe by `event_id`

Example payload:

```json
{
  "payment_id": "uuid",
  "merchant_id": "uuid",
  "order_ref": "BOOKING-123",
  "amount_minor": 100000,
  "currency": "VND",
  "status": "created"
}
```

### HoldAuthorized
- Producer: MiniBank
- Trigger: authorize hold success
- Partition key: `hold_id`
- Consumer concern: update read model / notify business workflow

### HoldVoided
- Producer: MiniBank
- Trigger: void hold success
- Partition key: `hold_id`

### HoldCaptured
- Producer: MiniBank
- Trigger: capture success
- Partition key: `hold_id` or `payment_id`
- Important: current runtime may still be REST-first even if event is defined

### RefundRequested
- Producer: MiniBank
- Trigger: refund request created
- Partition key: `refund_id`

### JournalReversed
- Producer: MiniBank
- Trigger: reversal journal created
- Partition key: `reference_id` or `journal_id`

## 5. Consumer responsibilities

Consumers phải:
- idempotent
- retry-safe
- không giả định exactly-once delivery
- chịu được delayed events
- chịu được duplicate events
- chịu được out-of-order events nếu business design cho phép

## 6. Retry and dedupe expectations
- outbox publisher dùng at-least-once delivery
- duplicate delivery là chấp nhận được
- consumer phải dedupe theo `event_id`
- nếu cần semantic dedupe sâu hơn thì dùng business key phù hợp