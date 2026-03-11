# Integration Contract — Car Rental ↔ MiniBank

## 1. Purpose

Tài liệu này mô tả runtime integration hiện tại giữa Car Rental và MiniBank.
Nó không phải historical design doc. Nếu có mâu thuẫn với doc cũ, hãy ưu tiên file này và `current-status.md`.

## 2. Integration Style

- protocol: REST synchronous
- direction: Car Rental gọi MiniBank cho command operations
- Car Rental không tự ghi ledger
- MiniBank là service financial authority

## 3. Endpoints currently used

### 3.1 Init Payment
- Endpoint: `POST /api/payments`
- Purpose: tạo payment record tại MiniBank
- Backend contract: `sp_init_payment_with_idem`

### 3.2 Authorize Hold
- Endpoint: `POST /api/payments/{paymentId}/authorize-hold`
- Purpose: giữ tiền trên account customer
- Backend contract: `sp_authorize_hold` hoặc wrapper idempotent phù hợp

### 3.3 Get Payment
- Endpoint: `GET /api/payments/{paymentId}`
- Purpose: đồng bộ UI/backend state nếu cần

### 3.4 Void Hold
- Endpoint: `POST /api/holds/{holdId}/void`
- Purpose: hoàn lại available balance khi booking bị cancel hoặc reject
- Backend contract: `sp_void_hold_with_idem`

### 3.5 Capture Hold
- Endpoint: `POST /api/holds/{holdId}/capture`
- Purpose: capture payment khi trip hoàn thành
- Backend contract: `sp_capture_hold_partial_with_idem`

## 4. Current runtime capture behavior

Đây là phần rất quan trọng.

### Current runtime truth
- Car Rental hiện đã integrate capture thật với MiniBank
- Capture được gọi trong booking/trip completion flow
- Runtime hiện tại là full capture only
- Capture amount hiện lấy từ `remaining_amount_minor` của hold

### Important note
Database contract hỗ trợ partial capture.
Nhưng current runtime integration chưa expose partial capture như business behavior canonical.

Nói ngắn gọn:
- DB capability = partial capture supported
- Current runtime behavior = full capture only

## 5. Field mapping

| Car Rental field | MiniBank field | Meaning |
|---|---|---|
| `bookingId` | `order_ref` | correlation giữa 2 hệ thống |
| `totalPrice` | `amount_minor` | số tiền thanh toán |
| `Idempotency-Key` | `idem_key` | chống duplicate command |
| internal merchant mapping | `merchant_id` | merchant/payment owner |
| selected customer account | `account_id` | source account cho hold |

## 6. Local persistence in Car Rental

Car Rental không mirror đầy đủ bảng MiniBank.
Car Rental chỉ cần lưu đủ reference để gọi lại MiniBank:
- `payment_id`
- `hold_id`
- `payment_provider` hoặc equivalent field

## 7. Canonical business seams

### Booking payment creation seam
Booking/payment creation bên Car Rental phải tạo payment tại MiniBank trước khi đi tiếp authorize.

Runtime hiện tại:
- `BookingWebController.processPayment()` gọi `createBooking()` rồi `authorizePayment()`
- `authorizePayment()` dùng MiniBank để:
  - init payment
  - authorize hold

### Completion seam
Booking/trip completion là seam canonical để gọi capture.

Runtime hiện tại:
- canonical seam là `BookingService.completeTrip()`
- host checkout flow vẫn tồn tại, nhưng completion thực tế delegate về `completeTrip()`

### Cancellation seam
Booking cancel / host reject là seam canonical để gọi void hold.

Runtime clarification:
- pre-capture cancel/reject canonical runtime truth = release business state trong Car Rental + call `voidHold()` ở MiniBank
- post-capture refund/correction chưa phải canonical MiniBank runtime path trong thin slice hiện tại

### Approval seam
Host approval canonical seam hiện tại là `BookingService.confirmBooking()`.

Transitional path:
- `BookingService.approveBooking()` vẫn còn tồn tại để tránh phá call path cũ
- nhưng runtime behavior canonical được gom về `confirmBooking()`

## 8. Idempotency contract

Mọi write command quan trọng phải dùng idempotency key ổn định.

### Rule
- Cùng operation business → cùng idem key
- Retry network → reuse same idem key
- Không reuse idem key cho payload khác

### Example pattern
- init payment: `booking-{bookingId}-payment-init`
- authorize: `booking-{bookingId}-authorize`
- capture: `booking-{bookingId}-capture`
- void: `booking-{bookingId}-void`

Current runtime note:
- các MiniBank write operations ở Car Rental hiện đã dùng deterministic business keys theo pattern trên.
- local idempotency keys cho web/API request ở Car Rental vẫn có thể dùng UUID theo request boundary.

## 8.1 Verification assets currently available
- `minibank/scripts/test-init-payment.ps1`
- `minibank/scripts/test-authorize-hold.ps1`
- `minibank/scripts/test-void-hold.ps1`
- `docs/runbooks.md` canonical runtime verification checklist

## 9. Failure semantics

### Timeout or lost response
Nếu Car Rental timeout nhưng MiniBank đã commit thành công:
- Car Rental phải retry với cùng idem key
- MiniBank phải replay kết quả hoặc trả trạng thái phù hợp

### In-progress collision
Nếu request cùng idem key đang chạy dở:
- MiniBank có thể trả lỗi tương đương in-progress
- Car Rental nên backoff và retry

### Duplicate submit
Duplicate request không được tạo duplicate financial side effects.

## 10. Out of scope for this runtime contract

File này không mô tả chi tiết:
- full event-driven integration qua Kafka
- PSP webhook contract end-to-end
- settlement batch import
- chargeback/dispute

Và trong patch set hiện tại cũng chưa mở rộng:
- refund runtime authority hoàn chỉnh sang MiniBank
- surcharge payment redesign
- outbox/Kafka verification scenarios

Các phần đó sẽ được mô tả ở doc khác.
