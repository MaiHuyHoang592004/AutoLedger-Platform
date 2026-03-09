# AGENTS.md

## 1. Project Identity

This repository contains a portfolio platform designed to demonstrate realistic **DevOps / DevSecOps capabilities** using a fintech-style architecture.

The platform consists of:

1. **Car Rental Platform**
   - existing business application
   - implemented in **Spring Boot MVC**
   - currently the main business system
   - already contains most booking / rental business logic

2. **MiniBank**
   - an **internal payment service**
   - not a real bank
   - not a digital wallet for end users
   - not a merchant-facing payment product like Stripe
   - used as a **payment core / money movement service** for the platform

3. **PSP Sandbox**
   - payment gateway simulator
   - used for testing payment flows and reliability scenarios
   - intentionally injects duplicate / delayed / invalid / out-of-order payment events

The main goal of this repository is **not** to build a full banking product.

The goal is to build a **realistic architecture** that showcases:
- service boundaries
- payment reliability
- DevOps / DevSecOps workflows
- CI/CD
- observability
- operational readiness
- secure delivery practices
- production-like integration patterns


---

## 2. Primary Goal of the AI Agent

When working in this repository, your primary responsibility is:

> Help evolve the existing Car Rental Platform by integrating MiniBank as a separate internal payment service, while preserving the current booking business flow and enabling a strong DevOps / DevSecOps showcase.

You must optimize for:
- practicality
- clarity
- incremental evolution
- minimal disruption to existing business logic
- demoability
- architectural correctness for a personal portfolio project

You must **not** optimize for:
- maximum theoretical purity
- building a full-scale bank
- unnecessary microservice decomposition
- rewriting working business logic from scratch


---

## 3. System Architecture Principles

### 3.1 Current architectural direction

The target architecture is:

- **Car Rental Platform remains a Spring Boot MVC monolith**
- **MiniBank is a separate service**
- **PSP Sandbox is a separate service**
- each service owns its own database
- services communicate by:
  - **REST for commands**
  - **Kafka for events**
- architecture should evolve incrementally from the current codebase

### 3.2 What this is NOT

Do not redesign the platform into:
- a full microservice ecosystem for all car rental domains
- a shared database system
- a full banking core platform
- an end-user wallet app
- a marketplace-grade PSP platform

### 3.3 Architecture style to prefer

Prefer:
- modular monolith for existing Car Rental
- external payment service for MiniBank
- bounded contexts
- incremental decomposition
- eventual consistency
- outbox-based event publishing
- idempotent integration
- operational simplicity

Avoid:
- distributed transactions across services
- cross-database queries between services
- unnecessary service explosion
- introducing infrastructure that cannot realistically be completed


---

## 4. Business Context

### 4.1 Car Rental Platform

The Car Rental Platform is the real business domain.

It is responsible for:
- vehicle inventory
- booking
- availability calendar
- trip lifecycle
- trip inspections
- host / guest interactions
- surcharge business logic
- reviews

It is **not** responsible for deep payment infrastructure concerns such as:
- payment authorization internals
- payment event deduplication
- refund workflow internals
- ledger accounting
- settlement reconciliation
- payment audit trail

Those should move into MiniBank.

### 4.2 MiniBank

MiniBank is an **internal payment core service**.

It is responsible for:
- payment creation / authorization
- idempotency
- payment state machine
- webhook verification and deduplication
- ledger double-entry
- refund request / approval
- audit logging for payment actions
- outbox and event publishing
- settlement import and reconciliation

MiniBank is **not** responsible for:
- booking creation
- host approval logic
- availability calendar ownership
- trip workflow
- rental pricing business rules
- reviews

### 4.3 PSP Sandbox

PSP Sandbox exists to simulate realistic integration behavior.

It is responsible for:
- payment success / failure simulation
- duplicate webhook events
- delayed webhook events
- invalid signature events
- out-of-order delivery
- settlement CSV generation

Its purpose is to support:
- reliability testing
- operational scenarios
- DevSecOps demonstrations
- failure scenario testing


---

## 5. Existing Booking Flow Constraints

The AI agent must respect the real booking flow already implemented in the codebase.

### 5.1 Current booking flow (important)

Current high-level flow:

1. guest selects vehicle and rental time
2. system calls `holdSlot()`
3. calendar moves to `HOLD`
4. no booking row exists yet
5. `holdToken` lives for 15 minutes
6. user goes to payment step:
   - `POST /bookings/payment/{holdToken}`
7. current implementation performs:
   - `createBooking()`
   - `authorizePayment()`
8. booking becomes `PENDING_HOST`
9. host may later confirm
10. booking transitions to `PAYMENT_AUTHORIZED`
11. calendar moves from `HOLD` to `BOOKED`

This means:

- booking is created **after** payment step
- payment logic is currently embedded in the booking flow
- current business behavior must be preserved

### 5.2 Important interpretation rules

You must not assume:
- booking is created at the initial hold step
- payment happens after host confirmation
- booking state and payment state are the same thing

You must treat these as different concepts:
- booking state
- calendar state
- payment state


---

## 6. State Separation Model

The design must clearly separate three kinds of state.

### 6.1 Calendar state
Calendar state belongs to Car Rental and should remain there.

Valid values:
- `FREE`
- `HOLD`
- `BOOKED`

Typical transitions:
- `FREE -> HOLD`
- `HOLD -> BOOKED`
- `HOLD -> FREE`
- `BOOKED -> FREE`

### 6.2 Booking state
Booking state belongs to Car Rental.

Known values from the codebase/business flow:
- `PENDING_HOST`
- `PAYMENT_AUTHORIZED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED_HOST`
- `CANCELLED_GUEST`
- `NO_SHOW_GUEST`

Important:
- side effects like surcharge payment or review do not necessarily create new booking states

### 6.3 Payment state
Payment state belongs to MiniBank.

Preferred initial values:
- `CREATED`
- `AUTHORIZED`
- `FAILED`
- `VOIDED`
- `REFUNDED`

Possible future values:
- `CAPTURED`
- `SETTLED`

### 6.4 State mapping principle
The AI agent must explicitly reason about mappings such as:

- `PENDING_HOST = booking created + payment authorized + calendar hold`
- `PAYMENT_AUTHORIZED = booking host-approved + payment authorized + calendar booked`

But you must validate these assumptions against the current codebase before proposing final mapping.


---

## 7. Integration Design Rules

### 7.1 Database ownership
Each service must own its own database.

- Car Rental owns `CarRentalDb`
- MiniBank owns `MiniBankDb`

Rules:
- Car Rental must not query MiniBank tables directly
- MiniBank must not query Car Rental tables directly
- data exchange must happen through APIs or events

### 7.2 REST for commands
Use REST for synchronous commands such as:
- create / authorize payment
- void payment
- create refund request
- approve refund
- query payment

### 7.3 Kafka for events
Use Kafka for asynchronous events such as:
- `PaymentAuthorized`
- `PaymentAuthorizationFailed`
- `PaymentVoided`
- `RefundApproved`
- future settlement events

Car Rental should consume these events to update business state and timeline.

### 7.4 Eventual consistency
Do not design for cross-service ACID transactions.

Prefer:
- local transaction per service
- outbox pattern
- idempotent consumers
- retry-safe integration
- compensating actions when needed

### 7.5 Integration placement
The payment integration point should align with the existing booking flow.

Preferred direction:
- keep `holdSlot()` in Car Rental
- keep `createBooking()` in Car Rental
- replace in-process payment authorization with a call to MiniBank

The likely integration point is the existing payment step around:
- `POST /bookings/payment/{holdToken}`

You must validate the exact classes/controllers/services involved from the codebase.


---

## 8. MVP Scope

The MVP must be intentionally narrow.

### 8.1 Car Rental MVP responsibilities
- keep hold-slot flow
- create booking at payment step
- store `payment_id`
- update booking state based on payment outcomes
- handle cancel flows through MiniBank

### 8.2 MiniBank MVP responsibilities
- authorize payment
- query payment
- void authorization
- create refund request
- approve refund
- idempotency
- audit events
- minimal double-entry ledger
- outbox + Kafka events

### 8.3 PSP Sandbox MVP responsibilities
- simulate successful payment
- simulate failed payment
- simulate duplicate webhook
- simulate delayed webhook
- simulate invalid signature
- optionally generate simple settlement CSV

### 8.4 Explicitly out of scope for now
Do not introduce in the first implementation:
- full banking core features
- loans
- KYC / AML
- chargeback workflow
- partial refund
- multi-currency
- multi-tenant PSP routing
- complex authorization systems
- rewriting the entire car rental architecture
- service mesh complexity unless clearly needed later


---

## 9. DevOps / DevSecOps Showcase Goals

This repository is intended to support job applications for DevOps / DevSecOps roles in a banking environment.

That means the architecture and implementation should enable demonstration of the following capabilities:

### 9.1 CI/CD
The system should eventually support:
- build
- unit tests
- integration tests
- secret scanning
- dependency scanning
- container scanning
- SBOM generation
- image signing
- push to Nexus
- deploy to staging

### 9.2 Runtime platform
The system should eventually support:
- Docker
- Docker Compose
- Kubernetes
- Helm
- RBAC
- NetworkPolicy
- secret handling
- readiness/liveness probes

### 9.3 Observability
The system should eventually support:
- Prometheus metrics
- Grafana dashboards
- centralized logs
- tracing
- payment metrics
- outbox metrics
- duplicate webhook metrics
- refund metrics

### 9.4 Reliability patterns
The implementation must be designed so that later demos can show:
- idempotent payment creation
- webhook deduplication
- retry-safe processing
- outbox recovery
- eventual consistency
- settlement mismatch handling

### 9.5 Failure scenario demos
The architecture should support realistic demo scenarios such as:
- duplicate webhook
- delayed webhook
- invalid signature
- settlement mismatch
- worker crash after DB commit
- replay-safe processing


---

## 10. Coding and Design Principles

When making changes, always prefer:

1. **Extend existing code before replacing it**
2. **Preserve current business flow**
3. **Introduce the smallest viable architectural boundary**
4. **Keep the design demoable**
5. **Keep the system realistic for a personal project**
6. **Make failure handling explicit**
7. **Prefer explicit state transitions**
8. **Prefer auditable actions**
9. **Prefer idempotent APIs and consumers**
10. **Prefer thin-slice end-to-end implementation before polishing**

Avoid:
- abstract frameworks without immediate value
- premature generalization
- hidden business logic
- direct DB coupling across services
- changes that make the booking flow harder to reason about


---

## 11. What the AI Agent Should Produce

When asked to analyze or plan work in this repository, your answers should typically contain:

### A. Current state analysis
- what the codebase currently does
- where booking and payment logic are coupled
- where the best integration point is

### B. Target architecture
- service boundaries
- DB boundaries
- sync vs async integration
- payment / booking / calendar state separation

### C. State mapping
- current booking state
- proposed payment state
- current calendar state
- transition mapping

### D. Implementation plan
Phase-by-phase plan that includes:
- objective
- code changes
- dependencies
- deliverables
- risks

### E. Practical next steps
- exactly what should be done in the next 1–2 weeks
- what to postpone
- what can break the existing system
- how to reduce risk

### F. Scope control
- what must remain out of scope for now


---

## 12. Recommended Initial Roadmap

When proposing a plan, prefer this order:

1. analyze current booking/payment coupling
2. define integration contract
3. implement MiniBank core locally
4. integrate MiniBank into the booking payment step
5. add PSP Sandbox
6. add outbox + Kafka
7. add refund flows
8. add settlement import
9. add thin UI / dashboard
10. add Docker Compose
11. add CI/CD and security scanning
12. add Kubernetes + observability + hardening

Do not jump straight to:
- Kubernetes
- service mesh
- complex infra
before the core business/payment integration works end-to-end.


---

## 13. Definition of Success

A good plan or implementation should satisfy all of the following:

- does not break the existing booking flow
- introduces MiniBank in the correct place
- keeps Car Rental mostly intact
- creates a realistic internal payment architecture
- enables end-to-end demo
- enables later DevOps / DevSecOps showcase work
- is actually feasible for a personal portfolio project


---

## 14. Final Guiding Principle

This project should feel like:

> a realistic platform evolution where an existing business application is integrated with an internal payment core and operational tooling

It should **not** feel like:
- a fake toy bank
- an overbuilt distributed system
- a full enterprise rewrite
- a random collection of unrelated services
