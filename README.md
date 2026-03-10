# AutoLedger Platform

AutoLedger Platform is a portfolio project that evolves a **Car Rental marketplace** into a more realistic fintech-style platform by integrating an internal payment core called **MiniBank**.

The goal of this repository is not to build a full banking product. Instead, it is designed to demonstrate:
- clear service boundaries
- reliable payment orchestration
- idempotent cross-service flows
- operational thinking for DevOps / DevSecOps portfolios
- production-like integration patterns that remain feasible for a personal project

## 📌 Project Overview

The platform currently centers around an existing **Spring Boot MVC Car Rental application**. Instead of keeping payment authorization as mock or in-process business logic, the system is being evolved so that payment responsibilities move into a separate **MiniBank** service.

This allows the repository to showcase a more realistic architecture where:
- **Car Rental** owns booking, calendar, trip, surcharge, and review logic
- **MiniBank** owns payment initialization, authorization hold, void, idempotency, ledger, and audit behavior
- **PSP Sandbox** simulates real-world gateway behavior such as duplicates, delays, invalid signatures, and out-of-order events

## 🧩 Main Components

### 1. Car Rental Platform
Main business application implemented in **Java / Spring Boot MVC**.

Responsibilities:
- listing and vehicle inventory
- booking lifecycle
- availability calendar management
- host / guest actions
- trip flow and inspections
- surcharge and review logic

### 2. MiniBank
Internal payment core implemented in **.NET + SQL Server + Dapper**.

Responsibilities:
- payment creation
- authorization hold
- void hold
- payment lookup
- idempotent payment commands
- ledger-oriented payment design
- auditability and reliability patterns

### 3. PSP Sandbox
Simulated payment gateway used for future reliability and failure scenario demos.

Planned responsibilities:
- payment success / failure simulation
- duplicate webhook events
- delayed events
- invalid signatures
- settlement and reconciliation scenarios

## 🏗️ Architecture Direction

The target architecture intentionally stays practical:

- **Car Rental remains a Spring Boot MVC monolith**
- **MiniBank is a separate service**
- **PSP Sandbox is a separate service**
- **REST** is used for command-style integration
- **Kafka** is the future direction for asynchronous payment events
- each service owns its own database

This repository prefers **incremental evolution** over a full rewrite.

## 🔄 Booking and Payment Flow

Current business flow is preserved as follows:

1. Guest selects vehicle and rental time
2. Car Rental calls `holdSlot()`
3. Calendar state becomes `HOLD`
4. No booking row exists yet
5. `holdToken` is valid for 15 minutes
6. Guest enters payment step via `POST /bookings/payment/{holdToken}`
7. Car Rental creates the booking row
8. Car Rental authorizes payment through MiniBank
9. Booking becomes `PENDING_HOST`
10. Host may later confirm the booking
11. Calendar moves from `HOLD` to `BOOKED`

This separation is important:
- **calendar state** belongs to Car Rental
- **booking state** belongs to Car Rental
- **payment state** belongs to MiniBank

## ✅ Current MVP Scope

The repository is currently focused on a thin but realistic MVP slice.

### Car Rental side
- hold-slot booking flow
- booking creation at payment step
- MiniBank client integration for payment authorization
- persistence of `payment_id`, `hold_id`, and `payment_provider`
- compensating void path if Java persistence fails after successful hold

### MiniBank side
- `POST /api/payments`
- `POST /api/payments/{paymentId}/authorize-hold`
- `GET /api/payments/{paymentId}`
- `POST /api/holds/{holdId}/void`
- idempotent payment commands
- ledger-oriented SQL design

## 🛡️ Reliability and DevSecOps Themes

This project is structured to support demonstrations of:
- idempotent APIs
- retry-safe financial commands
- compensating actions instead of distributed transactions
- immutable / append-only financial history
- traceable audit behavior
- future outbox + Kafka event publishing
- secure delivery and operational readiness

Planned future themes include:
- observability dashboards
- CI/CD hardening
- containerization and Kubernetes deployment
- settlement and reconciliation demos
- PSP failure scenario testing

## 🛠️ Technology Stack

- **Java 17**
- **Spring Boot 3**
- **Spring MVC / Spring Data JPA / Thymeleaf**
- **.NET 9**
- **C# + Dapper**
- **SQL Server**
- **Flyway**
- **REST integration**
- planned: **Kafka, Docker, Kubernetes, observability tooling**

## 📁 Repository Structure

```text
.
├── docs/                 # architecture, payment, roadmap, reliability docs
├── minibank/             # .NET MiniBank payment service
├── src/                  # Java Spring Boot Car Rental application
├── pom.xml               # Maven build for Car Rental
└── README.md
```

## 📚 Key Documentation

- `AGENTS.md`
- `docs/system-design-overview.md`
- `docs/target-architecture.md`
- `docs/payment-domain-model.md`
- `docs/integration-contract.md`
- `docs/mvp-integration-contract.md`
- `docs/devsecops-roadmap.md`
- `docs/reliability-scenarios.md`
- `docs/ledger-money-flow.md`

## 🗺️ Roadmap

Recommended implementation direction:

1. analyze booking / payment coupling
2. define integration contract
3. implement MiniBank core locally
4. integrate MiniBank into booking payment step
5. add PSP Sandbox
6. add outbox + Kafka
7. add refund flow
8. add settlement import
9. add Docker / CI/CD / observability / Kubernetes hardening

## 🚀 Milestone: Phase 1 MVP Integrated (Java ↔ .NET)
**Status:** ✅ Completed (2026-03-10)

Hệ thống đã thông luồng thanh toán thực tế giữa Car Rental (Java) và MiniBank (.NET).

### Các tính năng cốt lõi:
- **Ledger-based Payment:** Thay thế mock-payment bằng hệ thống sổ cái tài chính thực thụ.
- **Distributed Idempotency:** Sử dụng `booking-{id}-{action}` làm key để đảm bảo an toàn giao dịch khi có retry.
- **Fault Tolerance:** Triển khai cơ chế **Compensating Void** - tự động hoàn tiền nếu hệ thống Java gặp lỗi sau khi đã giữ tiền thành công trên .NET.
- **Cross-Service Tracking:** Đồng bộ hóa `payment_id` và `hold_id` giữa hai cơ sở dữ liệu để phục vụ đối soát.

> **Technical Stack:** Spring Boot 3 (Java), .NET 9 (C#), SQL Server, Dapper, REST Integration.