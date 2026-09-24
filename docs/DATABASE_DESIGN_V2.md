# Thiết kế Database CRM Bất Động Sản — V2 (bản chốt)

> Tài liệu này là **nguồn chuẩn cho database** sau khi rà soát. Viết theo các quyết định đã chốt:
>
> | # | Quyết định | Giá trị chốt |
> |---|---|---|
> | 1 | Mô hình database | **1A** — giữ 3 database riêng: `user_db`, `customer_db`, `crm_db`; FK xuyên database là logical FK |
> | 2 | Hình thức bàn giao | Cấu trúc bảng dạng văn bản trong tài liệu này + script SQL tạo DB mới ở `docs/db/` |
> | 3 | Mã OTP | **3B** — lưu hash SHA-256 (`VARCHAR(64)`), không lưu plaintext |
> | 4 | Bảng làm luôn | `lead_stage_history`, `deal_payments`, `notifications` |
> | 5 | Cột thêm ngay | `users.email_verified_at`, `otp_verifications.attempts`, `appointments.reminder_sent_at` |
> | 6 | Kiểu thời gian | **6A** — giữ `TIMESTAMP`, bắt buộc lưu UTC |
> | 7 | Chuẩn hoá cột | **7A** — CHECK cho `products.direction` và `deals.payment_method` |
> | 8 | Trạng thái dữ liệu | Xoá toàn bộ dữ liệu cũ, tạo DB mới hoàn chỉnh từ script trong `docs/db/` |
>
> **Chưa đụng vào code**: không sửa entity, không sửa `services/*/src/main/resources/db/migration/*`, không đổi `application.properties`. Database làm trước, code cập nhật sau (mục 10).

---

## 1. Quy ước chung

- Khóa chính: `id UUID NOT NULL DEFAULT gen_random_uuid()` (cần extension `pgcrypto`).
- Thời gian: `TIMESTAMP` (không dùng `TIMESTAMPTZ`). Toàn hệ thống lưu **UTC**; JVM chạy `TZ=UTC` (đã set trong Dockerfile), DB session đặt UTC.
- Audit: mọi bảng nghiệp vụ có `created_at`, `updated_at`, `created_by`, `updated_by`.
  - `created_at` NOT NULL DEFAULT CURRENT_TIMESTAMP.
  - `updated_at` NOT NULL DEFAULT CURRENT_TIMESTAMP + trigger `trg_<bảng>_updated_at` gọi hàm `set_updated_at()`.
  - `created_by`, `updated_by`: UUID NULL, là **logical FK** trỏ `user_db.users.id` (khác database nên không tạo FK vật lý).
- Enum: lưu `VARCHAR` + `CHECK` constraint (không dùng kiểu ENUM của Postgres để thêm giá trị không phải `ALTER TYPE`).
- Xóa: hard delete. Xóa cha bị chặn bởi FK `RESTRICT` (cùng DB) hoặc bởi service (khác DB).
- Row Level Security: bật cho mọi bảng, **không đặt policy** — backend kết nối bằng role sở hữu bảng nên vẫn đọc/ghi bình thường, còn `anon key` của Supabase không vào được.
- Tên bảng/cột giữ nguyên như thiết kế gốc (kể cả `contact_detail` số ít) — không đổi DB contract.

---

## 2. Phân bổ bảng theo database

| Database | Service sở hữu | Bảng |
|---|---|---|
| `user_db` | user-service (8081) | `users`, `refresh_tokens`, `otp_verifications`, `notifications` |
| `customer_db` | customer-service (8082) | `customers`, `customer_cares`, `appointments`, `email_templates`, `email_cares` |
| `crm_db` | crm-service (8083) | `projects`, `products`, `leads`, `lead_stage_history`, `deals`, `deal_payments`, `contact_detail` |

Tổng 16 bảng (13 bảng gốc + 3 bảng mới), không bảng nào nằm ngoài 3 database này.

**Vì sao `notifications` nằm ở `user_db`**: notification là hộp thư của một nhân viên, khóa tự nhiên là `user_id`; đặt cùng DB với `users` thì FK vật lý được (`ON DELETE CASCADE`) và không phải nhân bản bảng sang 3 DB. Service khác muốn tạo notification thì gọi user-service (mục 10.6 — chưa có endpoint).

---

## 3. user_db

### 3.1 Bảng users (Tài khoản nhân viên)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính, default gen_random_uuid() |
| username | VARCHAR(50) | NOT NULL, UNIQUE |
| password | VARCHAR(255) | NOT NULL, mật khẩu đã hash bcrypt |
| email | VARCHAR(100) | NULL, UNIQUE không phân biệt hoa/thường (index trên lower(email)) |
| full_name | VARCHAR(100) | NULL |
| phone | VARCHAR(15) | NULL, UNIQUE partial (nhiều NULL vẫn hợp lệ) |
| address | VARCHAR(255) | NULL |
| role | VARCHAR(15) | NOT NULL, default SALES, CHECK: ADMIN / MANAGER / SALES |
| status | VARCHAR(20) | NOT NULL, default ACTIVE, CHECK: ACTIVE / INACTIVE / LOCKED |
| is_2fa_enabled | BOOLEAN | NOT NULL, default false |
| two_factor_secret | VARCHAR(255) | NULL khi chưa bật 2FA |
| email_verified_at | TIMESTAMP | **[MỚI]** NULL nếu chưa xác thực email; nơi ghi kết quả OTP purpose VERIFY_EMAIL |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, FK → users.id ON DELETE SET NULL |
| updated_by | UUID | NULL, FK → users.id ON DELETE SET NULL |

Ràng buộc: `chk_users_2fa_secret` — (is_2fa_enabled = false AND two_factor_secret IS NULL) OR (is_2fa_enabled = true AND two_factor_secret IS NOT NULL).
Index: `uq_users_username`, `uq_users_email_lower`, `uq_users_phone`, `idx_users_role_status (role, status)`.
Ghi chú: đăng nhập bằng cả username và email nên email bắt buộc unique; phone unique nhưng cho phép nhiều NULL.

### 3.2 Bảng refresh_tokens (Refresh token đã hash)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| user_id | UUID | NOT NULL, FK → users.id ON DELETE CASCADE |
| token_hash | VARCHAR(64) | NOT NULL, UNIQUE — **[SỬA]** bản gốc ghi 70, thực tế là SHA-256 hex 64 ký tự |
| expires_at | TIMESTAMP | NOT NULL, CHECK (expires_at > created_at) |
| revoked_at | TIMESTAMP | NULL = còn hiệu lực; có giá trị = đã thu hồi |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |

Index: `uq_refresh_tokens_token_hash`, `idx_refresh_tokens_user_id`, `idx_refresh_tokens_expires_at`, `idx_refresh_tokens_active (user_id, expires_at) WHERE revoked_at IS NULL`.
Ghi chú: chưa có job dọn token hết hạn — nên thêm cron `DELETE FROM refresh_tokens WHERE expires_at < now() - interval '30 days'`.

### 3.3 Bảng otp_verifications (OTP dùng một lần)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| user_id | UUID | NOT NULL, FK → users.id ON DELETE CASCADE |
| otp_code | VARCHAR(64) | NOT NULL — **[SỬA]** hash SHA-256 hex của mã 6 số (bản gốc là VARCHAR(6) plaintext) |
| purpose | VARCHAR(20) | NOT NULL, CHECK: LOGIN / RESET_PASSWORD / VERIFY_EMAIL |
| attempts | INT | **[MỚI]** NOT NULL default 0 — số lần nhập sai, service huỷ OTP khi >= 5 (hàng rào thật thay cho bộ đếm in-memory) |
| expires_at | TIMESTAMP | NOT NULL, CHECK (expires_at > created_at) |
| is_used | BOOLEAN | NOT NULL, default false |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |

Index: `idx_otp_verifications_lookup (user_id, purpose, is_used, expires_at)`, `idx_otp_verifications_expires_at`.
Quy tắc: mỗi user + purpose chỉ có 1 OTP còn sống; service vô hiệu mã cũ khi phát hành mã mới.

### 3.4 Bảng notifications (Thông báo cho nhân viên) — MỚI

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| user_id | UUID | NOT NULL, FK → users.id ON DELETE CASCADE |
| type | VARCHAR(30) | NOT NULL, CHECK: APPOINTMENT_REMINDER / LEAD_DUE / DEAL_APPROVAL / EMAIL_FAILED |
| title | VARCHAR(200) | NOT NULL |
| body | TEXT | NULL |
| ref_type | VARCHAR(30) | NULL — APPOINTMENT / LEAD / DEAL / CUSTOMER (trỏ entity ở DB khác nên không FK) |
| ref_id | UUID | NULL |
| read_at | TIMESTAMP | NULL = chưa đọc |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |

Index: `idx_notifications_user_unread (user_id, read_at)`, `idx_notifications_created_at`.
Mục đích: nhắc lịch hẹn, lead quá `close_date`, hợp đồng chờ duyệt. mail-service đã gửi được email nên chỉ thiếu chỗ lưu trạng thái đã đọc/chưa đọc.

---

## 4. customer_db

### 4.1 Bảng customers (Khách hàng)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| full_name | VARCHAR(100) | NOT NULL |
| phone | VARCHAR(15) | NULL, UNIQUE partial (cho phép nhiều NULL) — **[SỬA]** ghi rõ partial |
| email | VARCHAR(100) | NULL, UNIQUE trên lower(email) |
| demand_type | VARCHAR(20) | NOT NULL, default BUY, CHECK: BUY / SELL / RENT / INVEST |
| source | VARCHAR(50) | NULL — Facebook / Zalo / Website... (để tự do, không CHECK) |
| owner_id | UUID | NOT NULL — **logical FK** → user_db.users.id (sales phụ trách) |
| status | VARCHAR(20) | NOT NULL, default NEW, CHECK: NEW / POTENTIAL / CUSTOMER / INACTIVE |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `idx_customers_owner_id`, `idx_customers_status`, `idx_customers_demand_type`.
Ghi chú: không xoá được khách khi còn `customer_cares` hoặc `appointments` (service chặn trước, FK RESTRICT là hàng rào cuối).

### 4.2 Bảng customer_cares (Nhật ký chăm sóc)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| customer_id | UUID | NOT NULL, FK → customers.id **ON DELETE RESTRICT** |
| type | VARCHAR(20) | NOT NULL, CHECK: CALL / MEETING / EMAIL / NOTE |
| content | TEXT | NOT NULL |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `idx_customer_cares_customer_id`, **[MỚI]** `idx_customer_cares_timeline (customer_id, created_at DESC)`.
Ghi chú: đây là bảng lịch sử tương tác **duy nhất** — không tạo bảng interactions khác.

### 4.3 Bảng appointments (Lịch hẹn)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| customer_id | UUID | NOT NULL, FK → customers.id ON DELETE RESTRICT |
| sales_id | UUID | NOT NULL — logical FK → users.id |
| title | VARCHAR(200) | NOT NULL |
| start_time | TIMESTAMP | NOT NULL |
| end_time | TIMESTAMP | NOT NULL, **[SỬA]** thêm CHECK (end_time > start_time) |
| color | VARCHAR(10) | NULL (để tự do) |
| reminder_minutes | INT | NULL, CHECK >= 0 |
| reminder_sent_at | TIMESTAMP | **[MỚI]** NULL — chống gửi nhắc trùng; trước đây `reminder_minutes` là cột chết |
| status | VARCHAR(20) | NOT NULL, default PENDING, CHECK: PENDING / DONE / CANCELLED |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Ràng buộc thêm (cần extension `btree_gist`): `ex_appointments_no_overlap` EXCLUDE USING gist (sales_id WITH =, tsrange(start_time, end_time) WITH &&) WHERE (status <> 'CANCELLED') — hàng rào thật chống trùng lịch, vì check ở service có thể lọt khi 2 request cùng lúc.
Index: `idx_appointments_customer_id`, `idx_appointments_sales_id`, `idx_appointments_start_time`, **[MỚI]** `idx_appointments_sales_start (sales_id, start_time)`.
Ghi chú: ràng buộc "start_time phải ở tương lai" không đặt được ở DB (`now()` không immutable) — chặn ở service.

### 4.4 Bảng email_templates (Mẫu email)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| name | VARCHAR(100) | NOT NULL, **[SỬA]** UNIQUE (service đang chặn trùng tên) |
| subject | VARCHAR(255) | NOT NULL |
| body | TEXT | NOT NULL |
| category | VARCHAR(50) | NOT NULL, CHECK: WELCOME / FOLLOW_UP / PROMOTION / CONTRACT |
| status | VARCHAR(20) | NOT NULL, default ACTIVE, CHECK: ACTIVE / INACTIVE |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `idx_email_templates_category`, `idx_email_templates_status`.

### 4.5 Bảng email_cares (Email đã gửi)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| customer_care_id | UUID | NOT NULL, FK → customer_cares.id **ON DELETE CASCADE** |
| template_id | UUID | NULL, FK → email_templates.id **ON DELETE SET NULL** (xoá mẫu không xoá lịch sử) |
| to_email | VARCHAR(100) | NOT NULL |
| subject | VARCHAR(255) | NOT NULL — bản sao tại thời điểm gửi |
| body | TEXT | NOT NULL — bản sao tại thời điểm gửi |
| status | VARCHAR(20) | NOT NULL, default SENT, CHECK: SENT / FAILED / OPENED / CLICKED |
| sent_at | TIMESTAMP | NULL — chỉ điền khi SMTP đã nhận gửi thật |
| opened_at | TIMESTAMP | NULL |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `idx_email_cares_customer_care_id`, `idx_email_cares_template_id`, `idx_email_cares_status`, **[MỚI]** `idx_email_cares_to_email`.

---

## 5. crm_db

### 5.1 Bảng projects (Dự án bất động sản)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| name | VARCHAR(200) | NOT NULL — **không UNIQUE** (2 chủ đầu tư có thể trùng tên dự án) |
| location | VARCHAR(255) | NULL |
| investor | VARCHAR(150) | NULL |
| description | TEXT | NULL |
| status | VARCHAR(20) | NOT NULL, default PLANNING, CHECK: PLANNING / SELLING / SOLD_OUT / CLOSED |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `idx_projects_status`, `idx_projects_investor`.

### 5.2 Bảng products (Sản phẩm bất động sản)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| project_id | UUID | NOT NULL, FK → projects.id ON DELETE RESTRICT |
| code | VARCHAR(50) | NOT NULL — **[SỬA]** UNIQUE trong phạm vi dự án: `uq_products_project_code (project_id, code)` |
| type | VARCHAR(20) | NOT NULL, CHECK: APARTMENT / LAND / TOWNHOUSE |
| area | NUMERIC(10,2) | NULL, CHECK (area IS NULL OR area > 0) |
| block | VARCHAR(20) | NULL — tòa/phân khu |
| price | NUMERIC(15,2) | NULL, CHECK (price IS NULL OR price >= 0) |
| bedroom | INT | NULL, CHECK (bedroom IS NULL OR bedroom >= 0) |
| direction | VARCHAR(20) | NULL — **[SỬA 7A]** CHECK: N / S / E / W / NE / NW / SE / SW |
| status | VARCHAR(20) | NOT NULL, default AVAILABLE, CHECK: AVAILABLE / RESERVED / SOLD |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `uq_products_project_code`, `idx_products_project_id`, `idx_products_status`, `idx_products_type`, `idx_products_block (partial, WHERE block IS NOT NULL)`.
Ghi chú: không xoá được sản phẩm khi còn `leads` hoặc `contact_detail` (FK RESTRICT + service chặn).

### 5.3 Bảng leads (Khách hàng tiềm năng)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| customer_id | UUID | NOT NULL — **logical FK** → customer_db.customers.id (khác database) |
| product_id | UUID | NOT NULL, FK → products.id ON DELETE RESTRICT |
| stage | VARCHAR(20) | NOT NULL, default NEW, CHECK: NEW / CONTACTED / INTERESTED / PROPOSAL_SENT / NEGOTIATION / INTERNAL_REVIEW / WON / LOST |
| expected_value | NUMERIC(15,2) | NULL, CHECK >= 0 |
| close_date | DATE | NULL |
| assigned_to | UUID | NOT NULL — logical FK → users.id |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `idx_leads_customer_id`, `idx_leads_product_id`, `idx_leads_assigned_to`, `idx_leads_stage`, `idx_leads_close_date (partial)`, **[MỚI]** `idx_leads_assign_stage (assigned_to, stage)`.
Ghi chú: DB không kiểm tra được `customer_id` tồn tại (khác database) — service hiện chỉ check `product_id`.

### 5.4 Bảng lead_stage_history (Lịch sử đổi stage) — MỚI

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| lead_id | UUID | NOT NULL, FK → leads.id ON DELETE CASCADE |
| from_stage | VARCHAR(20) | NULL cho bản ghi đầu tiên (lúc tạo lead); CHECK cùng danh sách stage của leads |
| to_stage | VARCHAR(20) | NOT NULL, CHECK cùng danh sách stage của leads |
| changed_by | UUID | NULL, logical FK → users.id |
| changed_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |

Index: `idx_lead_stage_history_lead (lead_id, changed_at)`, `idx_lead_stage_history_to_stage`.
Bảng chỉ ghi thêm (append-only), không có `updated_at`.
Mục đích: `leads` chỉ giữ stage hiện tại nên không làm được báo cáo funnel và thời gian nằm ở mỗi stage.

### 5.5 Bảng deals (Hợp đồng)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| lead_id | UUID | NOT NULL, **[SỬA]** thêm UNIQUE — 1 lead chỉ ra 1 hợp đồng; FK → leads.id ON DELETE RESTRICT |
| sales_id | UUID | NOT NULL — logical FK → users.id |
| contract_code | VARCHAR(50) | NOT NULL, UNIQUE |
| contract_value | NUMERIC(15,2) | NULL, CHECK >= 0 |
| deposit_amount | NUMERIC(15,2) | NULL, CHECK >= 0 và CHECK (deposit_amount <= contract_value) |
| deposit_date | DATE | NULL |
| signed_date | DATE | NULL |
| payment_status | VARCHAR(20) | NOT NULL, default UNPAID, CHECK: UNPAID / PARTIAL / PAID |
| payment_method | VARCHAR(30) | NULL — **[SỬA 7A]** CHECK: CASH / BANK_TRANSFER / LOAN |
| approval_status | VARCHAR(20) | NOT NULL, default PENDING, CHECK: PENDING / APPROVED / REJECTED / NOT_REQUIRED |
| approved_by | UUID | NULL — logical FK → users.id, bắt buộc khi APPROVED |
| approved_at | TIMESTAMP | NULL — bắt buộc khi APPROVED |
| file_url | VARCHAR(500) | NULL — link file scan (S3/Drive); chưa có upload nội bộ nên chỉ nhận URL |
| status | VARCHAR(20) | NOT NULL, default IN_PROGRESS, CHECK: IN_PROGRESS / ACTIVE / COMPLETED / CANCELLED |
| note | TEXT | NULL |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Ràng buộc thêm: `chk_deals_approved_fields` — (approval_status = 'APPROVED') ⇔ (approved_by IS NOT NULL AND approved_at IS NOT NULL).
Index: `uq_deals_contract_code`, `uq_deals_lead_id`, `idx_deals_sales_id`, `idx_deals_status`, `idx_deals_payment_status`, `idx_deals_approval_status`, `idx_deals_created_at`.
Ghi chú: "một sản phẩm chỉ nằm trong 1 hợp đồng đang hiệu lực" không đặt được ở DB (phụ thuộc status của deals) — service chặn khi thêm `contact_detail`.

### 5.6 Bảng deal_payments (Các đợt thanh toán) — MỚI

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| deal_id | UUID | NOT NULL, FK → deals.id ON DELETE CASCADE |
| amount | NUMERIC(15,2) | NOT NULL, CHECK > 0 |
| paid_at | TIMESTAMP | NOT NULL |
| method | VARCHAR(30) | NULL, CHECK cùng danh sách với `deals.payment_method` |
| receipt_url | VARCHAR(500) | NULL — link biên lai/ủy nhiệm chi |
| note | TEXT | NULL |
| created_by | UUID | NULL, logical FK → users.id |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |

Index: `idx_deal_payments_deal (deal_id, paid_at)`, `idx_deal_payments_paid_at`.
Bảng chỉ ghi thêm (append-only), không có `updated_at`/`updated_by`.
Mục đích: `deals.payment_status` chỉ là trạng thái tổng; báo cáo doanh thu theo thời gian cần từng đợt tiền.
Ghi chú: tổng `amount` không vượt `contract_value` — chặn ở service (DB không cộng được trong CHECK).

### 5.7 Bảng contact_detail (Chi tiết sản phẩm trong hợp đồng)

| Trường | Kiểu dữ liệu | Mô tả |
|---|---|---|
| id | UUID | Khóa chính |
| deal_id | UUID | NOT NULL, FK → deals.id ON DELETE CASCADE |
| product_id | UUID | NOT NULL, FK → products.id ON DELETE RESTRICT; **[SỬA]** thêm `uq_contact_detail_deal_product (deal_id, product_id)` |
| unit_price | NUMERIC(15,2) | NULL, CHECK >= 0 — giá thỏa thuận riêng cho dòng này |
| quantity | INT | NOT NULL, default 1, CHECK > 0 |
| note | TEXT | NULL |
| created_at | TIMESTAMP | NOT NULL, default CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, trigger tự cập nhật |
| created_by | UUID | NULL, logical FK → users.id |
| updated_by | UUID | NULL, logical FK → users.id |

Index: `uq_contact_detail_deal_product`, `idx_contact_detail_deal_id`, `idx_contact_detail_product_id`.
Ghi chú: giữ nguyên tên bảng số ít theo thiết kế gốc (không đổi thành `contact_details`).

---

## 6. FK thật và logical FK (mô hình 1A)

| FK thật (cùng database, DB tự chặn) | ON DELETE |
|---|---|
| users.created_by, users.updated_by → users.id | SET NULL |
| refresh_tokens.user_id → users.id | CASCADE |
| otp_verifications.user_id → users.id | CASCADE |
| notifications.user_id → users.id | CASCADE |
| customer_cares.customer_id → customers.id | RESTRICT (mặc định) |
| appointments.customer_id → customers.id | RESTRICT (mặc định) |
| email_cares.customer_care_id → customer_cares.id | CASCADE |
| email_cares.template_id → email_templates.id | SET NULL |
| products.project_id → projects.id | RESTRICT (mặc định) |
| leads.product_id → products.id | RESTRICT (mặc định) |
| lead_stage_history.lead_id → leads.id | CASCADE |
| deals.lead_id → leads.id | RESTRICT (mặc định) |
| deal_payments.deal_id → deals.id | CASCADE |
| contact_detail.deal_id → deals.id | CASCADE |
| contact_detail.product_id → products.id | RESTRICT (mặc định) |

| Logical FK (khác database — chỉ là UUID, không ràng buộc) | Trỏ tới |
|---|---|
| customers.owner_id, customers.created_by/updated_by | user_db.users.id |
| customer_cares.created_by/updated_by | user_db.users.id |
| appointments.sales_id, appointments.created_by/updated_by | user_db.users.id |
| email_templates / email_cares .created_by/updated_by | user_db.users.id |
| leads.customer_id | customer_db.customers.id |
| leads.assigned_to, leads.created_by/updated_by | user_db.users.id |
| lead_stage_history.changed_by | user_db.users.id |
| deals.sales_id, deals.approved_by, deals.created_by/updated_by | user_db.users.id |
| deal_payments.created_by | user_db.users.id |
| projects / products / contact_detail .created_by/updated_by | user_db.users.id |
| notifications.ref_id | tùy ref_type (appointment ở customer_db, lead/deal ở crm_db) |

Hệ quả phải chấp nhận: xóa user bên `user_db` không tự dọn dữ liệu bên `customer_db`/`crm_db`; service phải tự kiểm tra trước (hiện `UserService.delete` chỉ dọn `created_by/updated_by` trong chính `user_db`). Ghi rõ đây là **nợ kỹ thuật đã biết** của mô hình 1A.

---

## 7. Trigger / extension / RLS

| Database | Extension | Function | Trigger |
|---|---|---|---|
| user_db | pgcrypto | set_updated_at() | users, otp_verifications |
| customer_db | pgcrypto, btree_gist | set_updated_at() | customers, customer_cares, appointments, email_templates, email_cares |
| crm_db | pgcrypto | set_updated_at() | projects, products, leads, deals, contact_detail |

RLS bật cho toàn bộ 16 bảng, không policy. Bảng append-only (`lead_stage_history`, `deal_payments`) không có trigger vì không có `updated_at`.

---

## 8. Tổng hợp thay đổi so với bản thiết kế gốc

SỬA (13 điểm):
1. `users.email`: unique index trên `lower(email)` (đăng nhập bằng email).
2. `users.phone`: nêu rõ unique partial, cho phép nhiều NULL.
3. `users`: thêm CHECK `chk_users_2fa_secret`.
4. `refresh_tokens.token_hash`: 70 → **64**.
5. `refresh_tokens`: CHECK `expires_at > created_at`.
6. `otp_verifications`: CHECK `expires_at > created_at`.
7. `customers.phone` unique partial; `customers.email` unique trên lower.
8. `email_templates.name`: unique.
9. `products`: unique `(project_id, code)`.
10. `deals`: unique `lead_id`; CHECK `deposit_amount <= contract_value`; CHECK `approved_by/approved_at` khi APPROVED.
11. `contact_detail`: unique `(deal_id, product_id)`; CHECK `quantity > 0`.
12. `appointments`: CHECK `end_time > start_time` + EXCLUDE chống trùng lịch.
13. Ghi rõ `ON DELETE` cho mọi FK (bản gốc bỏ trống).

THÊM (3 bảng + 3 cột):
- Bảng: `lead_stage_history`, `deal_payments`, `notifications`.
- Cột: `users.email_verified_at`, `otp_verifications.attempts`, `appointments.reminder_sent_at`.
- CHECK chuẩn hoá: `products.direction`, `deals.payment_method`, `deal_payments.method`.
- Index mới: `idx_customer_cares_timeline`, `idx_appointments_sales_start`, `idx_email_cares_to_email`, `idx_leads_assign_stage`.

ĐỔI KIỂU: `otp_verifications.otp_code` VARCHAR(6) plaintext → **VARCHAR(64) hash SHA-256**.

---

## 9. Chưa làm (để Phase 2/3) và lý do

| Hạng mục | Trạng thái | Lý do |
|---|---|---|
| `mail_deliveries` (audit + retry email, gồm OTP) | để Phase 2 | mail-service hiện stateless; chưa cần retry/đối soát |
| `sms_logs`, `call_logs` | để Phase 2 | chưa tích hợp SMS/call |
| `system_configs` | để Phase 2 | cấu hình đang ở biến môi trường, đủ dùng |
| `audit_logs` | để Phase 2/3 | chưa cần truy vết chi tiết |
| `two_factor_recovery_codes` | để sau | hiện ADMIN reset được; bảng `users` cũng chưa cho trạng thái "secret chờ xác nhận" |
| Bảng `interactions` tổng hợp | **không làm** | tài liệu phát triển cấm tạo bảng tương tác khác; `customer_cares` + `email_cares` đã đủ |
| Bảng lưu file hợp đồng | **không làm** | `deals.file_url` chỉ lưu link S3/Drive, chưa có yêu cầu upload nội bộ |
| Bảng riêng cho analytic-service | **không làm** | dùng materialized view (`mv_sales_performance`, `mv_pipeline_summary`, `mv_revenue_by_month`) trên dữ liệu sẵn có |

---

## 10. TODO phía code sau khi chạy xong DB

Bắt buộc để DB và code không lệch:

Đã làm sau khi DB V2 được áp dụng:

1. `OtpVerification.otpCode` đã đổi thành `VARCHAR(64)` và `OtpService` lưu/so sánh SHA-256, không còn plaintext OTP.
2. `OtpService` tăng `attempts`, huỷ OTP khi đạt 5 lần sai; bucket throttle gửi OTP tách khỏi bucket verify để gửi mã không tự khoá mã vừa nhận.
3. `User.emailVerifiedAt` đã có trong entity/response; `VERIFY_EMAIL` gửi và xác thực được.
4. DTO đã dùng enum cho `direction` và `paymentMethod`, trả validation 400 thay vì để CHECK database ném lỗi khó hiểu.
5. `lead_stage_history` đã có entity/repository và tự ghi khi tạo/đổi stage; API đọc: `GET /leads/{leadId}/stage-history`.
6. `deal_payments` đã có entity/repository/service/API: `POST|GET /deals/{dealId}/payments`; chỉ ADMIN/MANAGER ghi được, tự cập nhật `payment_status` theo tổng đã thu.

Còn phải làm:

1. `Appointment` entity: thêm `reminderSentAt` + scheduler nhắc lịch qua mail-service và ghi `notifications`.
2. `notifications`: chưa có entity/service/endpoint. Muốn service khác tạo thông báo thì cần endpoint nội bộ ở user-service.
3. Vệ sinh định kỳ: cron xóa `refresh_tokens` hết hạn và `otp_verifications` hết hạn.
4. Cần chạy integration test với `IT_INFRA=up` và 3 Supabase database thật sau khi `.env` được cập nhật.

---

## 11. Cách chạy script

| Bước | Việc làm |
|---|---|
| 1 | Mở Supabase → project **user** → SQL Editor → dán toàn bộ `docs/db/user_db.sql` → Run |
| 2 | Project **customer** → dán `docs/db/customer_db.sql` → Run (cần quyền tạo extension `btree_gist`) |
| 3 | Project **CRM** → dán `docs/db/crm_db.sql` → Run |
| 4 | Tài khoản ADMIN đã được insert sẵn trong `user_db.sql`: user `anhdaidtr`, mật khẩu `Anhdaidtr04@` |
| 5 | Cập nhật `.env` (không đổi so với hiện tại, chỉ cần DB host/name/user/password của 3 project) rồi `docker compose up -d` |

Lưu ý: mỗi script **DROP toàn bộ bảng của database đó** trước khi tạo lại — chỉ chạy khi đã xác nhận dữ liệu cũ không cần giữ.

---

## 12. Kiểm tra sau khi chạy

| Kiểm tra | Kỳ vọng |
|---|---|
| `SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';` | user_db 4 · customer_db 5 · crm_db 7 |
| `SELECT conname FROM pg_constraint WHERE contype = 'c' ORDER BY conname;` | thấy đủ các CHECK liệt kê ở mục 4 và 5 |
| `SELECT indexname FROM pg_indexes WHERE schemaname = 'public';` | thấy đủ unique/partial index, gồm `uq_users_email_lower`, `uq_products_project_code`, `uq_deals_lead_id` |
| Thử insert lịch trùng giờ cùng 1 sales | báo lỗi vi phạm `ex_appointments_no_overlap` |
| Thử insert `deals` với `approval_status='APPROVED'` mà thiếu `approved_by` | báo lỗi vi phạm `chk_deals_approved_fields` |
| Thử insert `products.direction='Đông Nam'` | báo lỗi vi phạm `products_direction_check` (đúng theo 7A) |
| `SELECT * FROM pg_trigger WHERE tgname LIKE 'trg_%';` | mỗi bảng có `updated_at` có đúng 1 trigger |

