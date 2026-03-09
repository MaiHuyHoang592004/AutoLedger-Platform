# Target Architecture

The platform contains three systems.

Car Rental Service  
MiniBank Payment Service  
PSP Sandbox

---

# Architecture Overview

Car Rental manages booking lifecycle.

MiniBank manages financial ledger and balances.

PSP Sandbox simulates external payment gateway behaviour.

---

# Core Design Principles

Immutable financial ledger  
Double-entry accounting  
Idempotent APIs  
Append-only financial history  
Cryptographic tamper evidence

---

# Immutable Ledger

Financial history cannot be modified.

Tables:

ledger_journals  
ledger_postings

Mutations are prevented using triggers.

---

# Reversal Instead of Mutation

If a mistake occurs:

A reversal journal is created.

Procedure

sp_reverse_journal

This ensures accounting traceability.

---

# Hash Chained Ledger

Each journal row contains:

prev_row_hash  
row_hash

row_hash is computed as:

SHA256(payload + prev_row_hash)

This creates a **cryptographic chain**.

If a row is modified, the chain breaks.

---

# Append-only Enforcement

Triggers prevent mutation.

ledger_journals UPDATE blocked  
ledger_postings DELETE blocked  
audit_events immutable  

---

# Idempotent Financial APIs

Write procedures are protected using generalized idempotency.

State machine:

IN_PROGRESS  
COMPLETED  
FAILED

Concurrent requests are rejected with:

error 53001

---

# Event Delivery

MiniBank uses the **Transactional Outbox Pattern**.

Table

outbox_messages

Events are published asynchronously to Kafka.

---

# PSP Sandbox

Simulates external payment provider.

Features

authorization response  
webhook delivery  
duplicate events  
delayed events

---

# Service Boundaries

Car Rental

Booking  
Trip lifecycle  
Pricing

MiniBank

Payments  
Ledger  
Balances  
Reversals

PSP Sandbox

External gateway simulation
