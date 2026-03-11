# Runbooks

## 0. Canonical runtime verification checklist

Mục tiêu của checklist này là khóa runtime truth hiện tại trước khi làm Outbox/Kafka.

### Local run configuration note
- Car Rental hiện kỳ vọng MiniBank ở `http://localhost:5099` theo `application.yml`.
- MiniBank `launchSettings.json` mặc định có thể chạy ở `http://localhost:5169`.
- Khi verify end-to-end local, cần đảm bảo MiniBank được start ở `5099` hoặc cập nhật cấu hình tương ứng trước khi chạy scenario.

### Scenario 1 — Hold -> payment step -> booking create -> authorize
**Preconditions**
- SQL Server đang chạy trên `localhost:1433`
- MiniBank đang chạy ở `http://localhost:5099`
- Car Rental đang chạy ở `http://localhost:8084`
- user có thể đăng nhập và listing hợp lệ đang `ACTIVE`

**Action**
- thực hiện flow giữ chỗ và đi tới `POST /bookings/payment/{holdToken}`

**Expected outcome**
- booking row được tạo
- booking state = `PENDING_HOST`
- Car Rental lưu `payment_id`, `hold_id`, `payment_provider`
- MiniBank có payment + authorized hold tương ứng

**Where to verify**
- UI/redirect sau payment step
- Car Rental logs
- MiniBank API/DB

### Scenario 2 — Host confirm -> calendar HOLD -> BOOKED
**Action**
- host confirm booking qua flow hiện tại

**Expected outcome**
- canonical seam chạy qua `confirmBooking()`
- booking state = `PAYMENT_AUTHORIZED`
- calendar transition: `HOLD -> BOOKED`

**Where to verify**
- Car Rental DB (`availability_calendar`, `bookings`)
- host/booking UI

### Scenario 3 — Pre-capture reject/cancel -> MiniBank void
**Action**
- reject booking khi còn `PENDING_HOST`, hoặc cancel trước khi capture

**Expected outcome**
- business state được cancel/reject trong Car Rental
- canonical external financial side effect = MiniBank `voidHold()`
- available balance phía MiniBank được restore

**Where to verify**
- Car Rental logs
- MiniBank hold status / DB / script `minibank/scripts/test-void-hold.ps1`

### Scenario 4 — Trip completion -> MiniBank capture
**Action**
- complete trip qua seam canonical `completeTrip()`

**Expected outcome**
- MiniBank `captureHold()` được gọi
- booking state = `COMPLETED`
- local CAPTURE payment row tồn tại
- payout không bị tạo duplicate

**Where to verify**
- Car Rental logs
- MiniBank DB / API

### Scenario 5 — Duplicate completeTrip protection
**Action**
- gọi lặp lại completion cho cùng booking sau khi capture đã thành công

**Expected outcome**
- không tạo duplicate financial side effect
- không tạo duplicate local CAPTURE row
- booking vẫn ở `COMPLETED`

**Where to verify**
- Car Rental logs
- `payments` table
- MiniBank hold/payment state

### Deferred/non-canonical scenarios
- post-capture refund authoritative qua MiniBank: **deferred**
- surcharge payment qua MiniBank: **deferred**
- outbox/Kafka event verification: **deferred**

## 1. Outbox backlog tăng cao

### Symptom
- pending outbox messages tăng liên tục
- publish delay cao

### Check
- số row `outbox_messages` status pending
- worker logs
- Kafka availability

### Likely causes
- Kafka down
- publisher worker crash
- lock contention hoặc stuck lock

### Immediate actions
- xác minh worker còn sống
- xác minh Kafka reachable
- kiểm tra lock expiration
- restart publisher nếu cần

### Verify recovery
- pending count giảm dần
- new events vẫn được claim/publish

## 2. Kafka unavailable

### Symptom
- event publish fail
- outbox pending tăng

### Expected system behavior
- financial transaction đã commit vẫn giữ nguyên
- event không mất, chỉ delayed

### Immediate actions
- kiểm tra broker health
- kiểm tra network path
- tạm dừng escalate nếu outbox vẫn buffering đúng

### Verify recovery
- sau khi Kafka lên lại, outbox được drain

## 3. Duplicate webhook surge

### Symptom
- số request webhook tăng bất thường
- duplicate processing suspicion

### Check
- processed webhook dedupe store
- app logs theo event id
- PSP Sandbox scenario hiện hành

### Immediate actions
- xác minh dedupe vẫn đang hoạt động
- xác minh không có duplicate side effect business/financial

### Verify recovery
- cùng webhook id không tạo thêm state change mới

## 4. Hold expiration job failure

### Symptom
- holds quá hạn nhưng vẫn ở trạng thái active

### Check
- expired holds count
- background job logs
- scheduler health

### Immediate actions
- xác minh job runner
- re-run job theo cách an toàn, idempotent

### Verify recovery
- expired holds được move sang EXPIRED
- available balance được restore đúng

## 5. Graceful shutdown / deployment drain

### Goal
Tránh kill service giữa lúc đang xử lý request hoặc publish batch.

### Required behavior
- stop nhận request mới
- cho request đang chạy hoàn thành trong timeout hợp lý
- publisher worker finish/park batch safely
- release locks đúng cách nếu process chết

### Verify
- không có duplicate side effects sau redeploy
- outbox worker recover được từ pending/expired lock