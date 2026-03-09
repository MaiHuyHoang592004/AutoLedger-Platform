# Reliability Scenarios

This document describes operational scenarios.

---

# Duplicate API Requests

Scenario

Client retries the same request.

Mechanism

Idempotency store.

Table

idempotency_keys

---

# Idempotency State Machine

States

IN_PROGRESS  
COMPLETED  
FAILED

Procedure

sp_idem_begin

Possible outcomes

SUCCESS  
ALREADY_COMPLETED  
53001 IN_PROGRESS  
53002 PAYLOAD_MISMATCH

---

# Concurrent Request Handling

If request B arrives before request A finishes:

Database throws

53001

Client must retry with backoff.

---

# Duplicate Webhooks

Scenario

PSP sends webhook twice.

Detection

webhook_events_processed

Duplicate payload ignored.

---

# Worker Crash After Commit

Scenario

Event written to outbox  
worker crashes

Mechanism

outbox polling resumes publishing.

---

# Kafka Failure

Scenario

Kafka temporarily unavailable.

Mechanism

event remains pending in outbox.

Retry occurs.

---

# Hold Expiration

Expired holds are released by background job.

Procedure

sp_void_hold

Void status

EXPIRED

---

# Ledger Reversal

Incorrect financial journal.

Resolution

sp_reverse_journal

Creates counter-entries.

Ledger history remains immutable.
