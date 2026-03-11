# Settlement and Reconciliation

## 1. Purpose

Tài liệu này tách riêng khái niệm settlement và reconciliation khỏi authorization/capture.
Đây là chỗ nối giữa payment system demo và tư duy bank-grade operations.

## 2. Key terms

### Authorized
Tiền đã bị giữ khỏi available balance nhưng chưa ghi ledger settlement.

### Captured
Tiền đã được chốt và ledger journal đã được ghi.

### Settled
Tiền đã được chuyển qua bước payout/settlement tương ứng trong mô hình tài chính.

### Reconciled
Dữ liệu giữa MiniBank, PSP Sandbox, outbox/events, và các report liên quan khớp nhau hoặc mismatch đã được ghi nhận.

## 3. Ledger view

### Authorization
- only `available_balance_minor` changes
- ledger not posted yet

### Capture
Example:
- Debit `CUSTOMER_LIAB`
- Credit `MERCHANT_LIAB`

### Settlement
Example:
- Debit `MERCHANT_LIAB`
- Credit `BANK_CASH`

## 4. Reconciliation goals

Phải phát hiện được các loại mismatch như:
- payment authorized nhưng chưa capture dù trip đã complete
- capture thành công nhưng thiếu outbox publish
- webhook processed nhưng state chưa đồng bộ
- settlement expected nhưng chưa ghi ledger tương ứng
- reversal expected nhưng chưa xuất hiện

## 5. Reports nên có

### Payment lifecycle reconciliation report
So sánh:
- payments
- holds
- ledger_journals
- outbox_messages
- webhook events

### Balance integrity report
Kiểm tra:
- double-entry invariant
- point-in-time balance consistency
- unusual drift giữa posted và available balance

### Settlement readiness report
Danh sách khoản đã capture nhưng chưa settle.

## 6. Future PSP settlement import

Nếu phase sau mô phỏng settlement file/import từ PSP Sandbox, nên làm rõ:
- file format hoặc event format
- import idempotency
- mismatch handling
- retry/replay model
- fee accounting

## 7. Fee handling direction

Future design có thể bổ sung:
- fee expense
- payment revenue
- net settlement
- merchant payout net of fee

Nhưng fee accounting chưa phải runtime canonical thin slice hiện tại.