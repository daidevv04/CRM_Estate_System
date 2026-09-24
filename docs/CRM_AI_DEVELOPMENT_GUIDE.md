# CRM Bất Động Sản — Development Guide cho AI Code

> Tài liệu này là **nguồn quy chuẩn phát triển** để AI coding assistant sử dụng khi phân tích, thiết kế, sinh code, refactor, viết API, test và mở rộng hệ thống CRM bất động sản.
>
> Nội dung được xây dựng từ hai tài liệu dự án được cung cấp: **TÀI LIỆU PHÁT TRIỂN PHẦN MỀM HỆ THỐNG CRM BẤT ĐỘNG SẢN** và **BÁO CÁO THIẾT KẾ DATABASE + API + PHÂN QUYỀN CRM**. Không tự ý thay đổi nghiệp vụ hoặc kiến trúc nếu chưa có yêu cầu rõ ràng.

---

## 1. Mục tiêu hệ thống

CRM dùng để quản lý toàn bộ quá trình kinh doanh bất động sản:

- Quản lý người dùng và phân quyền.
- Quản lý khách hàng.
- Quản lý dự án bất động sản.
- Quản lý sản phẩm: căn hộ, đất, nhà phố.
- Quản lý lead và pipeline bán hàng.
- Theo dõi lịch sử chăm sóc khách hàng.
- Quản lý lịch hẹn.
- Quản lý deal/hợp đồng.
- Báo cáo và thống kê hiệu suất kinh doanh.
- Chuẩn bị khả năng tích hợp email/SMS/call.
- AI và automation chỉ thuộc **Phase 3**, không tự ý đưa AI vào CRM core.

Mục tiêu tổng thể ban đầu là quy trình:

```text
Customer
   ↓
Lead
   ↓
Sales chăm sóc
   ↓
Pipeline
   ↓
Won
   ↓
Deal / Contract
```

Tài liệu gốc xác định hệ thống phục vụ Admin, Manager, Sales/Môi giới và Marketing; CRM hỗ trợ quy trình `lead → deal → hợp đồng` và theo dõi lịch sử tương tác. 

---

# 2. Kiến trúc tổng thể

## 2.1 Kiến trúc

Hệ thống sử dụng:

- **Microservices**
- **REST API**

Các service chính:

```text
                    ┌───────────────────┐
                    │      Frontend     │
                    │ ReactJS / VueJS   │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │      Nginx        │
                    │   API Gateway     │
                    └─────────┬─────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ user-service │      │customer-     │      │ crm-service  │
│              │      │service       │      │              │
│ Auth         │      │ Customer     │      │ Project      │
│ JWT          │      │ Care         │      │ Product      │
│ 2FA          │      │ Appointment  │      │ Lead         │
│ User         │      │ Email        │      │ Deal         │
└──────────────┘      └──────────────┘      └──────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ analytic-service  │
                    │ Reports /         │
                    │ Dashboard        │
                    └───────────────────┘

Infrastructure:
- PostgreSQL
- Redis
- Kafka
- Config Service
- Discovery Service
```

### AI phải tuân thủ

- Không tự chuyển sang monolith nếu task hiện tại thuộc microservice.
- Không gộp các service chỉ vì thấy code đơn giản hơn.
- Không tạo thêm service mới nếu chưa có yêu cầu hoặc lý do kiến trúc rõ ràng.
- Giao tiếp giữa service theo REST API.
- Kafka được dành cho event-driven communication khi cần.
- Redis dùng cho cache.
- Config phải tách khỏi code, không hardcode.

---

# 3. Tech Stack chuẩn

## Backend

- Java 17
- Spring Boot
- Spring Security
- JWT
- Refresh Token
- JPA / Hibernate
- PostgreSQL
- Redis
- Kafka

## Frontend

- ReactJS hoặc VueJS
- Ant Design hoặc Material UI

## DevOps

- GitHub
- Docker
- GitHub Actions
- Nginx

## Optional

- ELK Stack — logging
- Prometheus + Grafana — monitoring

---

# 4. Service Boundary

## 4.1 user-service

Chịu trách nhiệm:

- Authentication
- Authorization
- User management
- JWT
- Refresh Token
- OTP
- 2FA

Không đặt nghiệp vụ Customer, Lead, Product hoặc Deal vào service này.

---

## 4.2 customer-service

Chịu trách nhiệm:

- Customers
- Customer care history
- Appointments
- Email templates
- Email care

---

## 4.3 crm-service

Chịu trách nhiệm:

- Projects
- Products
- Leads
- Deals
- Contract detail / deal items

---

## 4.4 analytic-service

Chịu trách nhiệm:

- Sales performance
- Pipeline summary
- Revenue
- Project performance
- Dashboard overview

---

## 4.5 config-service

Quản lý configuration tập trung.

Không hardcode:

- Database credentials
- JWT secret
- API keys
- Email credentials
- Kafka configuration
- Redis configuration
- Các giá trị môi trường khác

---

## 4.6 discovery-service

Dùng cho service discovery và hỗ trợ khả năng phân phối request giữa các service.

---

# 5. Database Model

## 5.1 User

```text
users
├── id UUID PK
├── username
├── password
├── email
├── full_name
├── phone UNIQUE
├── address
├── role
├── status
├── is_2fa_enabled
├── two_factor_secret
├── created_at
├── updated_at
├── created_by
└── updated_by
```

Role:

```text
ADMIN
MANAGER
SALES
```

Status:

```text
ACTIVE
INACTIVE
LOCKED
```

---

# 6. Authentication Data

## refresh_tokens

```text
id
user_id
token_hash
expires_at
revoked_at
created_at
```

## otp_verifications

```text
id
user_id
otp_code
purpose
expires_at
is_used
created_at
updated_at
```

OTP purpose:

```text
LOGIN
RESET_PASSWORD
VERIFY_EMAIL
```

---

# 7. Customer Model

## customers

```text
id UUID PK
full_name
phone UNIQUE
email
demand_type
source
owner_id
status
created_at
updated_at
created_by
updated_by
```

Demand type:

```text
BUY
SELL
RENT
INVEST
```

Source ví dụ:

```text
Facebook
Zalo
Website
...
```

Status:

```text
NEW
POTENTIAL
CUSTOMER
INACTIVE
```

### Quy tắc nghiệp vụ

- Customer có Sales phụ trách thông qua `owner_id`.
- SALES chỉ được quản lý khách hàng được giao.
- MANAGER quản lý khách hàng của team.
- ADMIN quản lý toàn bộ.

---

# 8. Customer Care

## customer_cares

```text
id
customer_id
type
content
created_at
updated_at
created_by
updated_by
```

Type:

```text
CALL
MEETING
EMAIL
NOTE
```

Đây là lịch sử chăm sóc khách hàng.

AI không được tự tạo một bảng interaction khác nếu chưa có yêu cầu.

---

# 9. Email

## email_templates

Dùng để lưu mẫu email tái sử dụng.

```text
id
name
subject
body
category
status
created_at
updated_at
created_by
updated_by
```

Category:

```text
WELCOME
FOLLOW_UP
PROMOTION
CONTRACT
```

Status:

```text
ACTIVE
INACTIVE
```

## email_cares

Lưu thông tin một email cụ thể đã gửi.

```text
id
customer_care_id
template_id
to_email
subject
body
status
sent_at
opened_at
created_at
updated_at
created_by
updated_by
```

Status:

```text
SENT
FAILED
OPENED
CLICKED
```

---

# 10. Project

## projects

```text
id
name
location
investor
description
status
created_at
updated_at
created_by
updated_by
```

Status:

```text
PLANNING
SELLING
SOLD_OUT
CLOSED
```

---

# 11. Product

## products

```text
id
project_id
code
type
area
block
price
bedroom
direction
status
created_at
updated_at
created_by
updated_by
```

Type:

```text
APARTMENT
LAND
TOWNHOUSE
```

Status:

```text
AVAILABLE
RESERVED
SOLD
```

Quan hệ:

```text
Project 1 ───── N Product
```

---

# 12. Lead / Sales Pipeline

## leads

```text
id
customer_id
product_id
stage
expected_value
close_date
assigned_to
created_at
updated_at
created_by
updated_by
```

Pipeline chuẩn:

```text
NEW
  ↓
CONTACTED
  ↓
INTERESTED
  ↓
PROPOSAL_SENT
  ↓
NEGOTIATION
  ↓
INTERNAL_REVIEW
  ↓
WON / LOST
```

Đây là luồng nghiệp vụ quan trọng nhất của CRM.

AI khi implement Lead phải giữ nguyên stage đã định nghĩa, không tự đổi tên stage.

---

# 13. Deal / Contract

Deal được tạo khi Lead chốt thành công.

## deals

```text
id
lead_id
sales_id
contract_code
contract_value
deposit_amount
deposit_date
signed_date
payment_status
payment_method
approval_status
approved_by
approved_at
file_url
status
note
created_at
updated_at
created_by
updated_by
```

Payment status:

```text
UNPAID
PARTIAL
PAID
```

Approval status:

```text
PENDING
APPROVED
REJECTED
NOT_REQUIRED
```

Deal status:

```text
IN_PROGRESS
ACTIVE
COMPLETED
CANCELLED
```

Quy trình:

```text
Lead WON
   ↓
Create Deal
   ↓
Approval
   ├── REJECTED
   └── APPROVED
          ↓
       Deposit
          ↓
       Payment
          ↓
      Completed
```

---

# 14. Deal Items

Bảng chi tiết sản phẩm trong một deal.

```text
contact_detail
├── id
├── deal_id
├── product_id
├── unit_price
├── quantity
├── note
├── created_at
├── updated_at
├── created_by
└── updated_by
```

Quan hệ:

```text
Deal 1 ───── N Deal Items
Deal Item N ───── 1 Product
```

---

# 15. Appointment

## appointments

```text
id
customer_id
sales_id
title
start_time
end_time
color
reminder_minutes
status
created_at
updated_at
created_by
updated_by
```

Status:

```text
PENDING
DONE
CANCELLED
```

---

# 16. API Convention

API phải theo RESTful.

## Customer

```http
GET    /customers
GET    /customers/{id}
POST   /customers
PUT    /customers/{id}
PATCH  /customers/{id}/status
PATCH  /customers/{id}/assign
DELETE /customers/{id}
```

List hỗ trợ filter:

```text
owner_id
status
demand_type
source
keyword
page
size
```

---

## Customer Care

```http
GET    /customers/{id}/cares
POST   /customers/{id}/cares
DELETE /customer-cares/{id}
```

---

## Appointment

```http
GET    /appointments
GET    /appointments/{id}
POST   /appointments
PUT    /appointments/{id}
PATCH  /appointments/{id}/status
DELETE /appointments/{id}
```

---

## Projects

```http
GET    /projects
GET    /projects/{id}
POST   /projects
PUT    /projects/{id}
PATCH  /projects/{id}/status
DELETE /projects/{id}
```

---

## Products

```http
GET    /products
GET    /products/{id}
POST   /products
PUT    /products/{id}
PATCH  /products/{id}/status
DELETE /products/{id}
```

Filter:

```text
project_id
block
type
status
min_price
max_price
bedroom
page
size
```

---

## Leads

```http
GET    /leads
GET    /leads/{id}
POST   /leads
PUT    /leads/{id}
PATCH  /leads/{id}/stage
PATCH  /leads/{id}/assign
DELETE /leads/{id}
```

---

## Deals

```http
GET    /deals
GET    /deals/{id}
POST   /deals
PUT    /deals/{id}
PATCH  /deals/{id}/deposit
PATCH  /deals/{id}/payment-status
PATCH  /deals/{id}/status
PATCH  /deals/{id}/approve
PATCH  /deals/{id}/reject
POST   /deals/{id}/contract-file
DELETE /deals/{id}
```

---

# 17. Authentication API

```http
POST /auth/login
POST /auth/logout
POST /auth/refresh-token

POST /auth/otp/send
POST /auth/otp/verify

POST /auth/2fa/enable
POST /auth/2fa/verify
POST /auth/2fa/disable
```

JWT + Refresh Token là cơ chế authentication chuẩn.

---

# 18. Authorization Matrix

| Chức năng | ADMIN | MANAGER | SALES |
|---|---|---|---|
| User CRUD | Có | Không | Không |
| Gán role | Có | Không | Không |
| Reset password user | Có | Không | Không |
| Đổi mật khẩu | Có | Có | Có |
| Xem users | Toàn bộ | Team | Thông tin cơ bản |
| Project/Product | Toàn bộ | Toàn bộ | Xem |
| Customer | Toàn bộ | Team | Khách được giao |
| Tạo Customer | Có | Có | Có |
| Assign Customer | Có | Trong team | Không |
| Customer care | Toàn bộ | Team | Khách được giao |
| Lead | Toàn bộ | Team | Lead được giao |
| Assign Lead | Có | Trong team | Không |
| Deal | Toàn bộ | Team | Không |
| Approve Deal | Có | Có | Không |
| Report | Toàn công ty | Team | Cá nhân |
| Email Template | CRUD | CRUD | Chỉ dùng |
| System Config | Có | Không | Không |

### AI implementation rule

Authorization phải kiểm tra **role + ownership/team scope**.

Không được chỉ dựa vào frontend để bảo vệ dữ liệu.

Ví dụ:

```text
SALES request GET /customers
        ↓
Backend kiểm tra JWT
        ↓
role = SALES
        ↓
Chỉ query customer.owner_id = currentUser.id
```

---

# 19. Development Flow

Development sử dụng Agile / Scrum.

- Sprint: 1 tuần.
- Code review bắt buộc.
- Unit test cho service layer.

AI phải phát triển theo task nhỏ, không tự ý rewrite toàn bộ project.

Quy trình đề xuất cho mỗi task:

```text
1. Đọc requirement
       ↓
2. Xác định service
       ↓
3. Xác định entity / database
       ↓
4. Xác định API
       ↓
5. Implement service layer
       ↓
6. Implement controller
       ↓
7. Validate + exception handling
       ↓
8. Unit test
       ↓
9. Review authorization
       ↓
10. Review REST convention
```

---

# 20. Coding Convention

## Clean Code

Code phải:

- Dễ đọc.
- Tên biến/hàm rõ nghĩa.
- Method không quá lớn.
- Không duplicate logic.
- Không tạo abstraction vô nghĩa.
- Không thêm dependency không cần thiết.

## RESTful

API phải tuân theo REST convention.

Không tạo endpoint tùy tiện nếu đã có endpoint chuẩn.

## Validation

Input phải được validate ở backend.

Ví dụ:

```text
@NotBlank
@Email
@Size
@NotNull
@Positive
```

Tùy trường dữ liệu mà lựa chọn validation phù hợp.

## Configuration

Không hardcode:

```text
password
JWT secret
API key
database URL
email credential
Kafka credential
Redis credential
```

Sử dụng configuration/environment variables.

---

# 21. Error Handling

AI khi tạo API phải thiết kế error response nhất quán.

Các nhóm lỗi cần phân biệt:

```text
400 - Validation / Bad Request
401 - Unauthenticated
403 - Forbidden
404 - Resource Not Found
409 - Business Conflict
500 - Internal Server Error
```

Không trả stack trace hoặc secret ra client.

---

# 22. Pagination & Filtering

Các API list nên hỗ trợ:

```text
page
size
```

Khi requirement có filter thì hỗ trợ đúng filter đã định nghĩa.

Ví dụ:

```http
GET /customers?owner_id=...&status=NEW&page=0&size=20
```

AI không tự bỏ pagination chỉ vì dataset hiện tại nhỏ.

---

# 23. Reporting

analytic-service có các API:

```http
GET /reports/sales-performance
GET /reports/pipeline-summary
GET /reports/revenue
GET /reports/project-performance
GET /dashboard/overview
```

Mục đích:

- Hiệu suất từng Sales.
- Tổng quan pipeline.
- Doanh thu theo thời gian.
- Hiệu suất theo dự án.
- Dashboard tổng quan.

---

# 24. Logging & Monitoring

Optional:

```text
ELK Stack
Prometheus
Grafana
```

Có thể monitor:

- Service down.
- API error.
- Request/response metrics.
- Application health.

Không log:

- Password.
- JWT secret.
- Refresh token plaintext.
- OTP nhạy cảm.
- Secret key.

---

# 25. Roadmap

## Phase 1 — CRM Core

Ưu tiên triển khai:

```text
Authentication
    ↓
User & Role
    ↓
Customer
    ↓
Project
    ↓
Product
    ↓
Lead
    ↓
Pipeline
    ↓
Deal
    ↓
Contract
```

Kèm:

- Customer care.
- Appointment.
- Basic dashboard/report.

---

## Phase 2 — Advanced Business Features

- Báo cáo nâng cao.
- Email.
- SMS.
- Các tích hợp giao tiếp khác.

---

## Phase 3 — AI & Automation

AI chỉ được đưa vào sau CRM core.

Định hướng:

```text
CRM Data
   ↓
Automation
   ↓
AI
   ├── Lead assistance
   ├── Customer suggestion
   ├── Care suggestion
   └── Other approved AI features
```

Không tự ý biến AI thành dependency bắt buộc của CRM core.

---

# 26. Quy tắc đặc biệt cho AI Coding Assistant

## 26.1 Trước khi code

AI phải xác định:

1. Task thuộc service nào?
2. Entity/table nào liên quan?
3. API nào liên quan?
4. Role nào được phép?
5. Ownership/team scope có ảnh hưởng không?
6. Có thay đổi database không?
7. Có ảnh hưởng service khác không?
8. Có cần event Kafka không?
9. Có cần Redis không?
10. Có cần unit test không?

Nếu chưa xác định được thì không nên tự đoán.

---

## 26.2 Không tự ý thay đổi architecture

AI KHÔNG được tự ý:

- Đổi microservices thành monolith.
- Gộp service.
- Đổi PostgreSQL sang database khác.
- Bỏ Redis/Kafka nếu chúng đã được sử dụng.
- Đổi REST API thành GraphQL.
- Đổi JWT sang cơ chế auth khác.
- Tạo service mới không có requirement.

---

## 26.3 Không tự ý thay đổi database contract

Không tự đổi:

```text
table name
column name
enum
relationship
endpoint
request format
response contract
```

Nếu thay đổi cần:

```text
1. Giải thích lý do.
2. Xác định impact.
3. Cập nhật migration.
4. Cập nhật entity.
5. Cập nhật repository/service/controller.
6. Cập nhật API documentation.
7. Cập nhật test.
```

---

# 27. Quy tắc khi AI sinh code

AI nên ưu tiên:

```text
Existing architecture
        ↓
Existing coding style
        ↓
Existing API contract
        ↓
Existing database model
        ↓
Requirement mới
```

Không được vì requirement nhỏ mà tạo một kiến trúc hoàn toàn mới.

---

# 28. Quy tắc khi sửa code

Trước khi sửa:

```text
Read related files
        ↓
Understand current flow
        ↓
Find dependency
        ↓
Make smallest safe change
        ↓
Run/test affected module
```

Ưu tiên **minimal change**.

Không refactor unrelated code trong cùng task.

---

# 29. Quy tắc Security

Luôn kiểm tra:

- Authentication.
- Authorization.
- Ownership.
- Input validation.
- SQL/JPA safety.
- Password hashing.
- JWT validation.
- Refresh token revocation.
- OTP expiry.
- 2FA.
- File upload validation.
- Sensitive data exposure.

Đặc biệt:

```text
Frontend authorization ≠ Backend authorization
```

Backend luôn phải tự kiểm tra quyền.

---

# 30. Quy tắc Business Flow

## Customer → Lead

```text
Create Customer
      ↓
Assign Sales
      ↓
Create Lead
      ↓
Assign Sales
```

## Lead Pipeline

```text
NEW
 ↓
CONTACTED
 ↓
INTERESTED
 ↓
PROPOSAL_SENT
 ↓
NEGOTIATION
 ↓
INTERNAL_REVIEW
 ↓
WON / LOST
```

## Won → Deal

```text
Lead = WON
      ↓
Create Deal
      ↓
Approval
      ↓
Deposit
      ↓
Payment
      ↓
Contract Completed
```

AI không được cho phép tạo Deal hợp lệ từ Lead đang `NEW`, `CONTACTED`, `INTERESTED`... nếu business rule chưa được thay đổi.

---

# 31. Definition of Done cho mỗi Backend Task

Một task backend chỉ nên được xem là hoàn thành khi:

- [ ] Đúng service.
- [ ] Đúng entity/database.
- [ ] Đúng API contract.
- [ ] Có validation.
- [ ] Có authentication nếu endpoint yêu cầu.
- [ ] Có authorization.
- [ ] Kiểm tra ownership/team scope.
- [ ] Có exception handling.
- [ ] Không hardcode config/secret.
- [ ] Không phá API cũ.
- [ ] Unit test service layer.
- [ ] Code compile.
- [ ] API hoạt động theo requirement.
- [ ] Swagger/API documentation được cập nhật nếu cần.

---

# 32. Definition of Done cho AI Coding

Trước khi kết thúc task, AI nên tự kiểm tra:

```text
[ ] Tôi có hiểu đúng requirement không?
[ ] Tôi có sửa đúng service không?
[ ] Tôi có làm thay đổi API contract không?
[ ] Tôi có làm thay đổi database không?
[ ] Authorization đã đúng role chưa?
[ ] Ownership/team scope đã đúng chưa?
[ ] Validation đã đủ chưa?
[ ] Error handling đã có chưa?
[ ] Có hardcode secret/config không?
[ ] Có unit test chưa?
[ ] Có sửa file ngoài scope không?
[ ] Có phá flow hiện tại không?
```

Nếu một câu trả lời là "có" đối với thay đổi ngoài scope, AI phải review lại trước khi hoàn tất.

---

# 33. Nguyên tắc ưu tiên

Khi có xung đột giữa các lựa chọn implementation, ưu tiên:

```text
1. Requirement nghiệp vụ
2. API contract hiện tại
3. Database design hiện tại
4. Security / Authorization
5. Existing architecture
6. Existing coding convention
7. Testability
8. Performance optimization
9. Refactoring / beautification
```

Không tối ưu premature.

---

# 34. Nguồn chuẩn

Hai tài liệu nguồn xác định:

- Mục tiêu, phạm vi, kiến trúc microservices, technology stack, workflow và roadmap.
- Database design chi tiết.
- API design.
- Role/permission matrix.
- Các endpoint chính.

Các thay đổi tương lai phải được đánh giá dựa trên các contract này.

