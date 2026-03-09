# Integration Contract

This document defines the integration between:

Car Rental Service  
MiniBank Payment Service

MiniBank exposes financial operations using **idempotent write procedures**.

---

# Payment Initialization

Creates a new payment record.

Procedure

sp_init_payment_with_idem

Parameters

merchant_id  
payment_id  
amount_minor  
currency  
order_ref  

Effect

Payment record created.

Outbox event generated:

PaymentCreated

---

# Authorization (Hold)

Reserves funds for the payment.

Procedure

sp_authorize_hold

Effects

available_balance_minor decreases

posted_balance_minor unchanged

Hold state

AUTHORIZED

---

# Partial Capture

Captures part of the authorized hold.

Procedure

sp_capture_hold_partial

Effects

Ledger journal posted

posted_balance_minor updated

remaining_hold_amount reduced

Example

Authorized: 100000  
Capture #1: 60000  
Capture #2: 40000

---

# Capture Ledger Posting

Ledger entries are created via:

sp_post_journal_posted_only

Rules

Debit = Credit

Balances update accordingly.

---

# Void Authorization

Cancels an authorization.

Procedure

sp_void_hold

Effect

available_balance_minor restored

Hold state becomes:

VOIDED  
or EXPIRED

---

# Reversal

MiniBank does not modify historical ledger entries.

Instead it creates **reversal journals**.

Procedure

sp_reverse_journal

Mechanism

Swap debit/credit directions.

Create new journal referencing original.

Field:

reversal_of_journal_id

This preserves immutable financial history.

---

# Refund Workflow

Refund requests are created using:

sp_init_refund_with_idem

Refund approval then results in ledger reversal.

---

# Idempotency

All write procedures require:

Idempotency Key  
Request Hash

Table:

idempotency_keys

This guarantees safe retries.

---

# Webhook Handling

Duplicate PSP events are detected using:

webhook_events_processed

Fields

psp_event_id  
payload_hash

Duplicate events are ignored.
