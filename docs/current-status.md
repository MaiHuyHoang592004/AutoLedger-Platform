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

## 4. Known documentation lag

### Outdated
- `docs/archive/mvp-integration-contract.md`
  - là historical phase-1 doc
  - không còn phản ánh runtime truth hiện tại

### Needs to match runtime closely
- `docs/integration-contract.md`
  - phải phản ánh capture đã là một phần của current flow
  - phải nói rõ current runtime = full capture only

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