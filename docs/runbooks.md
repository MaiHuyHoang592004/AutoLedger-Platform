# Runbooks

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