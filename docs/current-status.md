# Current Status

> Đây là runtime truth hiện tại. Nếu có mâu thuẫn với doc khác, ưu tiên file này.

## 1. Current Runtime State

### Implemented endpoints
MiniBank thin slice đang có:
- `POST /api/payments`
- `POST /api/payments/{paymentId}/authorize-hold`
- `GET /api/payments/{paymentId}`
- `POST /api/holds/{holdId}/void`
- `POST /api/holds/{holdId}/capture`

### Current integration behavior
- Car Rental đã gọi MiniBank cho authorize, void, và capture trong flow hiện tại.
- Command integration giữa Car Rental và MiniBank là synchronous REST.
- Capture hiện là full capture only từ `remaining_amount_minor` của hold.
- Canonical booking completion path hiện tại gọi MiniBank capture khi trip complete.

### Canonical runtime seams
- Canonical host approval path ở Car Rental là `BookingService.confirmBooking()`.
- `BookingService.approveBooking()` hiện được giữ lại như transitional wrapper, delegate về `confirmBooking()` để tránh phá các call path cũ.
- Canonical completion/capture path ở Car Rental là `BookingService.completeTrip()`.
- `BookingService.completeTripWithCharges()` hiện được giữ lại như transitional wrapper cho host checkout flow và delegate về `completeTrip()`.

### Current idempotency contract used for MiniBank write commands
- init payment: `booking-{bookingId}-payment-init`
- authorize hold: `booking-{bookingId}-authorize`
- capture hold: `booking-{bookingId}-capture`
- void hold: `booking-{bookingId}-void`

Lưu ý:
- deterministic MiniBank idempotency keys hiện được dùng cho cross-service write operations.
- local/UI idempotency keys trong Car Rental vẫn có thể dùng UUID theo request boundary hiện tại.

## 2. Runtime architecture
- Car Rental vẫn là Spring Boot MVC monolith.
- MiniBank là service .NET riêng.
- PSP Sandbox có trong target architecture nhưng chưa phải runtime canonical path chính trong thin slice hiện tại.

## 3. What is true now

### Payment flow hiện tại
- init payment: done
- authorize hold: done
- get payment: done
- void hold: done
- capture on completion: done

### Financial behavior hiện tại
- authorize làm giảm `available_balance_minor`
- authorize không đổi `posted_balance_minor`
- capture tạo ledger journal
- capture hiện runtime là full capture only

### Verified runtime scenarios
- local runtime đã được verify ở mức service availability:
  - Car Rental phản hồi trên `http://localhost:8084`
  - MiniBank phản hồi trên `http://localhost:5099`
- repo hiện có script/checklist để verify canonical payment core scenarios:
  - init payment
  - authorize hold
  - void hold
  - completeTrip duplicate-protection expectations
  - host confirm -> calendar transition expectations (runbook/manual checklist)

### Local verification status observed on 2026-03-11
- **Executed successfully**:
  - MiniBank `init payment`
  - service availability checks cho Car Rental và MiniBank
- **Blocked / failed in current local environment**:
  - MiniBank `authorize-hold` currently returns `500 Internal Server Error`
  - observed error points to Dapper materialization of `IdempotencyExecutionResult` in `PaymentRepository.BeginIdempotencyAsync`
  - because authorize-hold fails, local end-to-end verification of canonical `void hold` and `capture hold` flows is blocked
- **Observed local Car Rental DB mismatch**:
  - current local `dbo.bookings` table does not contain `payment_id`, `hold_id`, `payment_provider`
  - current local Car Rental data still shows `mock` / `MOCK_SURCHARGE` providers instead of `MINIBANK`
  - this means canonical MiniBank-backed booking persistence is not yet verified end-to-end in the current local DB state
- **Business-side state evidence still exists in local DB**:
  - `PENDING_HOST` bookings with calendar rows in `HOLD`
  - `PAYMENT_AUTHORIZED` bookings with calendar rows in `BOOKED`
  - `CANCELLED_HOST` bookings with calendar rows released back to `FREE`
  - `COMPLETED` bookings exist, but current local payment rows do not prove canonical MiniBank capture execution

### Deferred or non-canonical runtime paths
- pre-capture cancel/reject + MiniBank void = canonical runtime truth
- post-capture refund vẫn chưa là canonical MiniBank runtime path trong repo hiện tại
- `payOutstandingSurcharge()` vẫn là local/deferred placeholder path, chưa phải MiniBank-authoritative runtime behavior

### Current verification conclusion
- repo state is good enough to document canonical seams and blockers
- repo is **not yet ready** for confident Outbox + Kafka design work until:
  - MiniBank `authorize-hold` local runtime failure is fixed
  - Car Rental local DB schema is aligned with the `Bookings` entity / V2 MiniBank fields
  - canonical `completeTrip()` is re-verified with actual MiniBank-backed persistence

## 4. Known documentation lag

### Outdated
- `docs/archive/mvp-integration-contract.md`
  - là historical phase-1 doc
  - không còn phản ánh runtime truth hiện tại

### Needs to match runtime closely
- `docs/integration-contract.md`
  - phải phản ánh capture đã là một phần của current flow
  - phải nói rõ current runtime = full capture only
  - phải nói rõ approval/completion canonical seam và transitional wrapper paths

## 5. Current source of truth

### Architectural intent
- `docs/system-design-overview.md`
- `docs/target-architecture.md`

### Database contract
- `docs/MiniBank.sql`

### Runtime truth
- `docs/current-status.md`
- code hiện tại

## 6. Next planned phase
- sync lại docs theo runtime truth
- wire PSP Sandbox rõ hơn vào reliability flow
- outbox worker + Kafka publish
- observability metrics/traces
- chaos / replay / reconciliation

## 7. Explicitly deferred in this patch set
- chưa mở rộng refund thành MiniBank-authoritative runtime flow hoàn chỉnh
- chưa redesign surcharge collection path
- chưa implement outbox/Kafka/PSP Sandbox runtime wiring

## 8. Local run note
- Car Rental cấu hình `minibank.api.base-url=http://localhost:5099`.
- MiniBank `launchSettings.json` mặc định có thể dùng `http://localhost:5169`.
- Khi chạy local end-to-end cần ưu tiên khớp MiniBank về `5099` hoặc đổi Car Rental config tương ứng.