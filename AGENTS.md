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
- payment creation
- payment authorization / hold
- payment capture
- payment query
- idempotency
- payment state machine
- ledger double-entry
- audit logging for payment actions
- reversal-based financial correction
- outbox-ready event generation
- future webhook verification and deduplication
- future settlement import and reconciliation

Important interpretation:
- some capabilities already exist in the DB/domain contract
- some capabilities are already implemented in current runtime
- some capabilities are still target architecture or next-phase work

Current runtime emphasis:
- payment creation
- authorization hold
- payment query
- void authorization
- capture on trip completion

Not yet primary runtime emphasis:
- refund end-to-end workflow
- Kafka publishing in production-like flow
- settlement import
- full PSP webhook-driven runtime path

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
- future settlement CSV generation

Its purpose is to support:
- reliability testing
- operational scenarios
- DevSecOps demonstrations
- failure scenario testing

Important runtime note:
- PSP Sandbox is a **target architecture component**
- it is important to the long-term platform design
- but it is **not yet the primary canonical runtime path** for the current thin-slice integration


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

Current supported / expected values:
- `CREATED`
- `AUTHORIZED`
- `CAPTURED`
- `FAILED`
- `VOIDED`
- `REFUNDED`

Possible future values:
- `SETTLED`

Important runtime note:
- current runtime already includes capture as part of the implemented payment lifecycle
- current capture behavior is **full capture only**
- the DB/domain model may support more generalized future behavior such as partial capture or richer settlement states

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
- create payment
- authorize payment / hold
- capture payment
- void payment / hold
- query payment
- future refund request operations

Important runtime note:
- capture is already part of the current runtime integration
- current runtime capture is full-capture-only from the remaining authorized hold amount

### 7.3 Kafka for events
Use Kafka for asynchronous events such as:
- `PaymentCreated`
- `PaymentAuthorized`
- `PaymentAuthorizationFailed`
- `PaymentCaptured`
- `PaymentVoided`
- future refund and settlement events

Car Rental or other downstream consumers may later consume these events to update business state, read models, and timelines.

Important interpretation:
- Kafka is part of the preferred target architecture
- but the current runtime integration is still primarily **REST for commands**
- event-driven publishing should be added incrementally through the outbox pattern

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

For booking completion:
- preserve the business completion flow in Car Rental
- use MiniBank capture at the completion seam
- treat the current runtime capture path as canonical unless the codebase proves otherwise

You must validate the exact classes/controllers/services involved from the codebase.


---

## 8. MVP Scope

The MVP must be intentionally narrow.

### 8.1 Car Rental MVP responsibilities
- keep hold-slot flow
- create booking at payment step
- store `payment_id`
- store `hold_id` when needed for later financial actions
- update booking state based on payment outcomes
- handle cancel flows through MiniBank
- trigger capture through MiniBank on the trip completion path

### 8.2 MiniBank MVP responsibilities
- create payment
- authorize payment
- query payment
- void authorization
- capture payment
- idempotency
- audit events
- minimal double-entry ledger

Near-term next phase, but not required to be fully runtime-complete in the current thin slice:
- refund request workflow
- refund approval workflow
- outbox publisher
- Kafka events
- webhook verification and deduplication

### 8.3 PSP Sandbox MVP responsibilities
- simulate successful payment
- simulate failed payment
- simulate duplicate webhook
- simulate delayed webhook
- simulate invalid signature
- optionally generate simple settlement CSV

Important interpretation:
- PSP Sandbox remains in MVP architecture scope
- but it does not have to be the primary end-to-end runtime path before the MiniBank thin slice is stable

### 8.4 Explicitly out of scope for now
Do not introduce in the first implementation:
- full banking core features
- loans
- KYC / AML
- chargeback workflow
- partial refund as a required business flow in the first slice
- multi-currency
- multi-tenant PSP routing
- complex authorization systems
- rewriting the entire car rental architecture
- service mesh complexity unless clearly needed later

### 8.5 Current runtime truth vs target architecture

The AI agent must distinguish between:
- **current runtime truth**
- **database/domain capability**
- **target architecture**
- **historical design docs**

Current runtime truth currently includes:
- create payment
- authorize hold
- query payment
- void hold
- capture on trip completion

Current runtime capture behavior:
- capture is already implemented
- capture is currently **full capture only**
- capture is taken from the remaining authorized hold amount

Target architecture still includes:
- PSP Sandbox-driven reliability scenarios
- outbox publisher
- Kafka event publishing
- refund expansion
- settlement and reconciliation workflows


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

## 10.1 Documentation precedence

When documentation appears inconsistent, use this order of precedence:

1. `docs/current-status.md` = current runtime truth
2. `docs/MiniBank.sql` = DB and financial contract truth
3. `docs/integration-contract.md` = runtime integration contract
4. `docs/system-design-overview.md` and `docs/target-architecture.md` = architecture intent
5. `docs/archive/*` = historical reference only

AI agents must not infer current runtime behavior from archived or outdated docs when `current-status.md` says otherwise.


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
2. define or sync runtime documentation and source-of-truth hierarchy
3. implement or stabilize MiniBank core locally
4. integrate MiniBank into the booking payment step
5. stabilize capture path in current runtime
6. add PSP Sandbox more explicitly into reliability flows
7. add outbox + Kafka
8. add refund flows
9. add settlement import / reconciliation
10. add thin UI / dashboard
11. add Docker Compose
12. add CI/CD and security scanning
13. add Kubernetes + observability + hardening

Do not jump straight to:
- Kubernetes
- service mesh
- complex infra

before the core business/payment integration and runtime truth are stable end-to-end.


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

## 13.1 Runtime truth reminder

At the time of writing:
- MiniBank current thin slice already includes capture
- Car Rental already calls MiniBank for authorize, void, and capture
- current capture behavior is full-capture-only
- some older docs may still describe capture as a later phase

Always validate against `docs/current-status.md` before planning further changes.


---

## 14. Final Guiding Principle

This project should feel like:

> a realistic platform evolution where an existing business application is integrated with an internal payment core and operational tooling

It should **not** feel like:
- a fake toy bank
- an overbuilt distributed system
- a full enterprise rewrite
- a random collection of unrelated services