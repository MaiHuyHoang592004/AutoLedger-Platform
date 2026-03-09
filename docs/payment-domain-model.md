# Payment Domain Model

MiniBank implements a **ledger-based payment system**.

The system separates:

Payment lifecycle  
Authorization (Hold)  
Ledger settlement (Capture)  
Reversal operations  

All financial state changes are recorded using **double-entry accounting**.

---

# Core Entities

## Merchant

Represents a business accepting payments.

Table:

merchants

Fields:

merchant_id  
merchant_code  
display_name  
status

---

## Payment

Represents a merchant order.

Table:

payments

Fields:

payment_id  
merchant_id  
order_ref  
amount_minor  
currency  
status  

Payment lifecycle:

created  
authorized  
captured  
partially_refunded  
refunded

---

## Hold (Authorization)

Authorization reserves funds before settlement.

Table:

holds

Fields:

hold_id  
payment_id  
merchant_id  
account_id  
original_amount_minor  
remaining_amount_minor  
status  

States:

AUTHORIZED  
CAPTURED  
VOIDED  
EXPIRED

---

## Hold Events

Every hold transition is recorded.

Table:

hold_events

Event types:

AUTHORIZED  
CAPTURED  
VOIDED  
EXPIRED

---

## Ledger Journal

Financial transactions are recorded as journals.

Table:

ledger_journals

Fields:

journal_id  
journal_type  
reference_id  
currency  
reversal_of_journal_id  

Each journal contains:

prev_row_hash  
row_hash

This forms a **cryptographic hash chain**.

---

## Ledger Postings

Journal entries are implemented using **double entry accounting**.

Table:

ledger_postings

Fields:

journal_id  
account_id  
direction  
amount_minor  

Invariant:

sum(debit) = sum(credit)

---

## Account

Represents ledger accounts.

Table:

accounts

Examples:

BANK_CASH  
PSP_CLEARING  
MERCHANT_LIAB  
CUSTOMER_LIAB  

---

## Account Balances

Table:

account_balances_current

Two balances exist.

posted_balance_minor  
available_balance_minor

---

## Posted Balance

Represents settled balance.

Changes only when **ledger journals are posted**.

---

## Available Balance

Represents spendable balance.

Affected by:

authorization holds  
voided holds  
ledger postings

---

## Balance History

Table:

account_balance_history

Stores point-in-time balance snapshots after each journal.

---

## Refund Request

Refunds are initiated through request workflow.

Table:

refund_requests

Fields:

refund_id  
payment_id  
merchant_id  
amount_minor  
status  

However financial correction is applied using **ledger reversal**.

---

## Outbox

Table:

outbox_messages

Used for **transactional event publishing**.

Pattern:

Transactional Outbox.

---

## Audit Events

Table:

audit_events

Fields:

before_state_json  
after_state_json  
prev_row_hash  
row_hash

Audit events also form a **hash chain**.
