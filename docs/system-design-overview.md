# System Design Overview

This document describes the complete architecture of the platform.

The system consists of three main components:
MiniBank: .NET 8/9 Core Web API (C#) handles idempotency and SQL procedure orchestration.
Car Rental Service  
MiniBank Payment Service  
PSP Sandbox

The platform simulates a real-world payment ecosystem where a marketplace integrates with a financial ledger service and an external payment gateway.

---

# High Level Architecture

Car Rental manages the booking lifecycle.

MiniBank manages financial transactions and ledger balances.

PSP Sandbox simulates external payment gateway behavior.

User
 │
 │ Booking / Payment
 ▼
Car Rental Service
 │
 │ Payment Request
 ▼
MiniBank
 │
 │ Payment Authorization
 ▼
PSP Sandbox
Service Responsibilities
Car Rental Service

Handles business logic related to vehicle rental.

Responsibilities

Booking lifecycle
Trip lifecycle
Calendar availability
Pricing
Host confirmation
Trip completion

Car Rental does not handle money movement directly.

All financial operations are delegated to MiniBank.

MiniBank Payment Service

MiniBank is responsible for financial operations.

Responsibilities

Payment lifecycle
Authorization (hold)
Capture (ledger posting)
Balance management
Ledger accounting
Reversal operations
Event publishing

MiniBank acts as a financial ledger service.

PSP Sandbox

PSP Sandbox simulates a third-party payment provider.

Responsibilities

Payment authorization simulation
Webhook delivery
Failure scenario simulation
Duplicate event simulation
Delayed webhook simulation

The sandbox allows realistic testing of payment flows.

Payment Lifecycle

The system implements a two-phase payment model.

Authorization (Hold)
Capture (Ledger Posting)

Step 1 — Payment Initialization

Car Rental creates a payment.

Procedure

sp_init_payment_with_idem

Effects

Payment record created
Outbox event generated

Step 2 — Authorization (Hold)

Funds are reserved.

Procedure

sp_authorize_hold

Effects

available_balance_minor decreases

posted_balance_minor unchanged

Hold state

AUTHORIZED

Step 3 — Partial Capture

Funds are captured from the hold.

Procedure

sp_capture_hold_partial

Effects

Ledger journal created

posted_balance_minor updated

Example

Authorized: 100000
Capture: 70000
Remaining Hold: 30000

Step 4 — Final Settlement

Ledger entries move funds between accounts.

Example

Debit CUSTOMER_LIAB
Credit MERCHANT_LIAB

Balances updated.

Step 5 — Merchant Settlement

Funds transferred to merchant payout account.

Example

Debit MERCHANT_LIAB
Credit BANK_CASH

Reversal Model

Financial history cannot be modified.

Incorrect transactions are reversed using counter entries.

Procedure

sp_reverse_journal

Mechanism

Original journal identified
Debit/Credit swapped
Reversal journal created

Ledger history remains immutable.

Ledger Architecture

MiniBank implements double-entry accounting.

Tables

ledger_journals
ledger_postings

Invariant

sum(debit) = sum(credit)

Dual Balance Model

Each account maintains two balances.

posted_balance_minor
available_balance_minor

Posted Balance

Represents settled ledger balance.

Changes only when journals are posted.

Available Balance

Represents spendable balance.

Affected by

authorization holds
voided holds
ledger postings

Immutable Ledger

Financial history cannot be modified.

Tables are append-only.

ledger_journals
ledger_postings
audit_events

Triggers prevent update or delete operations.

Cryptographic Hash Chain

Ledger rows are cryptographically chained.

Fields

prev_row_hash
row_hash

Hash formula

row_hash = SHA256(payload + prev_row_hash)

If a historical record is altered, the chain becomes invalid.

Idempotent Financial Operations

All write procedures use idempotency protection.

Table

idempotency_keys

States

IN_PROGRESS
COMPLETED
FAILED

Duplicate requests are safely handled.

Error

53001

Indicates request already in progress.

Event Driven Integration

MiniBank publishes events using the Transactional Outbox Pattern.

Table

outbox_messages

Process

Transaction commits
Event written to outbox
Background worker publishes event

Failure Resilience

The architecture handles multiple failure scenarios.

Duplicate API requests
Duplicate webhooks
Worker crashes
Kafka failures
Network retries

All financial operations remain consistent.

Observability

System observability includes

structured logs
trace_id propagation
audit event snapshots

Audit events include

before_state_json
after_state_json

These snapshots allow forensic analysis of financial operations.

Security Design

Security measures include

immutable financial tables
hash chained ledger
idempotent APIs
append-only audit history

Sensitive operations are fully traceable.

Infrastructure

The system is designed to run on modern cloud infrastructure.

Docker containers
Kubernetes orchestration
Kafka event streaming
SQL Server ledger database

CI/CD pipelines automate deployment.

Repository Layout

```text
repo
│
├── docs
│   ├── system-design-overview.md
│   ├── payment-domain-model.md
│   ├── integration-contract.md
│   ├── target-architecture.md
│   ├── reliability-scenarios.md
│   ├── ledger-money-flow.md
│   └── devsecops-roadmap.md
│
├── car-rental
├── minibank
├── psp-sandbox
└── infra
```

Design Philosophy

The system models real financial systems using proven patterns.

Double-entry accounting
Immutable ledger
Hash chained audit
Idempotent APIs
Transactional outbox

These principles ensure financial correctness, traceability, and operational resilience.


---
