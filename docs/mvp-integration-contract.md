# MVP Integration Contract: Car Rental ↔ MiniBank

Tài liệu này định nghĩa hợp đồng tích hợp MVP (Phase 1) giữa hệ thống Car Rental (Nghiệp vụ) và MiniBank (Lõi thanh toán). 
Trọng tâm của Phase 1: **Chỉ thực hiện luồng Authorize Hold & Void Hold**. Bỏ qua Capture/Refund cho đến khi luồng Authorize ổn định.

---

## 1. API Contract MVP (MiniBank Interfaces)

Lớp Web API (.NET) của MiniBank sẽ phơi bày 4 endpoints đồng bộ (REST), đóng vai trò bọc ngoài (wrapper) các Stored Procedure của SQL v6.

### 1.1. Khởi tạo thanh toán (Init Payment)
- **Endpoint:** `POST /api/payments`
- **Backend Procedure:** Gọi `sp_init_payment_with_idem`.
- **Mục đích:** Tạo record `payments` ban đầu và lấy `payment_id` (GUID).

### 1.2. Giữ tiền (Authorize Hold)
- **Endpoint:** `POST /api/payments/{paymentId}/authorize-hold`
- **Backend Procedure:** Gọi `sp_authorize_hold` (Sử dụng wrapper Idempotent ở tầng C# hoặc gọi thêm `sp_idem_begin/complete`).
- **Mục đích:** Khóa tiền của khách (`available_balance` giảm). Trả về `hold_id` (GUID).

### 1.3. Lấy thông tin thanh toán (Get Payment Info)
- **Endpoint:** `GET /api/payments/{paymentId}`
- **Mục đích:** Car Rental lấy trạng thái thanh toán hiện tại để đồng bộ UI/Backend nếu cần.

### 1.4. Hủy giữ tiền (Void Hold)
- **Endpoint:** `POST /api/holds/{holdId}/void`
- **Backend Procedure:** Gọi `sp_void_hold_with_idem` (truyền `void_status = 3`).
- **Mục đích:** Hoàn lại `available_balance` ngay lập tức khi Host từ chối hoặc Guest hủy chuyến trước khi bắt đầu.

---

## 2. Field Mapping & Hardcoded Boundaries

Để giảm coupling trong Phase 1, Car Rental gửi payload tối giản nhất có thể. MiniBank API tự phân giải/hardcode các thông tin tài chính lõi.

| Car Rental Field (Client) | MiniBank Field (SQL) | Quy tắc Mapping (MVP) |
| :--- | :--- | :--- |
| `bookingId` (String) | `order_ref` (NVARCHAR) | Định danh chéo giữa 2 hệ thống. Dùng để đối soát sau này. |
| `totalPrice` (Long) | `amount_minor` (BIGINT) | Giá trị thanh toán (quy đổi ra đơn vị nhỏ nhất, VD: VND -> đồng). |
| Header: `Idempotency-Key`| `idem_key` (NVARCHAR) | Sinh ra từ phiên thanh toán của Car Rental. Chống trùng lặp. |
| **Không gửi** | `currency` | **Hardcode:** `'VND'` tại MiniBank C# API. |
| **Không gửi** | `merchant_id` (GUID) | **Hardcode:** Dùng 1 Merchant GUID cố định (Demo) cho toàn bộ Phase 1. |
| **Không gửi** | `account_id` (INT) | **Hardcode:** Dùng ID của tài khoản `CUSTOMER_LIAB` (ví dụ: ID = 4) tại MiniBank C# API. |

### Local Persistence tại Car Rental:
Hệ thống Car Rental không mirror bảng của MiniBank. Bảng `Bookings` (hoặc `Payments` cục bộ) chỉ lưu thêm:
1. `payment_id` (GUID - nhận từ MiniBank)
2. `hold_id` (GUID - nhận từ MiniBank)
3. `payment_provider` (String - mặc định là `'MINIBANK'`)

---

## 3. State Mapping (Tách bạch Business & Ledger)

Bảng dưới đây quy định cách hai hệ thống đồng bộ trạng thái mà không giẫm chân lên nhau. Đã đổi tên trạng thái Booking để tránh nhầm lẫn ngữ nghĩa.

| Hành động thực tế | Calendar State (Car Rental) | Booking State (Car Rental) | Hold State (MiniBank) |
| :--- | :--- | :--- | :--- |
| **1. Khách bấm thanh toán** | `HOLD` (15 phút) | `PENDING_HOST` | `AUTHORIZED` |
| **2. Host chấp nhận (Confirm)** | `BOOKED` | **`CONFIRMED_BY_HOST`** | `AUTHORIZED` (Vẫn đang giữ tiền) |
| **3. Host từ chối / Khách hủy** | `FREE` (Giải phóng) | `CANCELLED` | `VOIDED` (Hoàn lại available balance) |
| **4. Trip kết thúc (Hoàn thành)** | `FREE` (Đã qua) | `COMPLETED` | *Chờ Capture (Phase 2)* |

---

## 4. Failure Semantics & Idempotency Rules

Quản lý lỗi trong mô hình phân tán giữa Java và .NET:

### Quy tắc "Fail-Fast"
Nếu bước Init thành công nhưng bước Authorize Hold thất bại (ví dụ: khách không đủ `available_balance` trong DB MiniBank):
- MiniBank trả về lỗi `HTTP 400` hoặc `422`.
- Car Rental lập tức đánh dấu Booking là `PAYMENT_FAILED` hoặc `CANCELLED`, giải phóng Calendar, không đi tiếp luồng Host Confirm.

### Quy tắc Retry (Mã lỗi 53001)
Nếu Car Rental gặp lỗi Timeout mạng và gọi lại `POST /api/payments` với cùng `Idempotency-Key`:
- MiniBank chặn lại ở DB bằng lỗi SQL `53001` (In progress).
- C# API bắt exception này, dịch thành `HTTP 409 Conflict` (hoặc 429).
- Car Rental (Java) thực hiện Exponential Backoff và retry sau 2-3 giây.

### Hash Integrity (Chống giả mạo)
- C# API của MiniBank tự động tính toán `request_hash = SHA256(payload_json)` để truyền vào các SP có hậu tố `_with_idem`.
- Hệ thống Car Rental hoàn toàn "mù" về Hash này, không cần quan tâm.