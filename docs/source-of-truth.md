# Source of Truth

Tài liệu này định nghĩa thứ tự ưu tiên của bộ docs trong repo.

## Canonical truth hierarchy

### 1. Current runtime behavior
File:
- `docs/current-status.md`

Dùng để trả lời câu hỏi:
- Hiện tại hệ thống đã implement đến đâu?
- Flow nào đang chạy thật?
- Flow nào mới chỉ là design intent?

### 2. Financial and database contract
File:
- `docs/MiniBank.sql`
- `docs/payment-domain-model.md`
- `docs/ledger-money-flow.md`

Dùng để trả lời câu hỏi:
- Ledger vận hành thế nào?
- Balance invariants là gì?
- Stored procedures và bảng canonical là gì?

### 3. Runtime integration contract
File:
- `docs/integration-contract.md`

Dùng để trả lời câu hỏi:
- Car Rental gọi MiniBank qua endpoint nào?
- Idempotency keys dùng ra sao?
- Side effects thực tế hiện tại là gì?

### 4. Architecture intent
File:
- `docs/product-scope.md`
- `docs/system-design-overview.md`
- `docs/target-architecture.md`

Dùng để trả lời câu hỏi:
- Hệ thống giải quyết bài toán gì?
- Vì sao có 3 hệ thống?
- Định hướng dài hạn là gì?

### 5. Operational guidance
File:
- `docs/reliability-scenarios.md`
- `docs/security-architecture.md`
- `docs/runbooks.md`
- `docs/implementation-roadmap.md`

### 6. Historical documents
File:
- `docs/archive/*`

Các file trong archive chỉ dùng để tham khảo lịch sử, không dùng làm runtime truth.

## Rule for AI agents

Nếu phát hiện mâu thuẫn:
- Ưu tiên `docs/current-status.md`
- Sau đó kiểm tra `docs/MiniBank.sql`
- Sau đó mới đọc `docs/integration-contract.md`
- Không suy luận theo doc archived nếu runtime truth nói khác