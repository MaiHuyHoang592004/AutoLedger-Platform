# State Machines

## 1. Purpose

Tài liệu này gom state transitions chính thức của domain vào một chỗ.
Mục tiêu là tránh việc lifecycle bị rải rác qua nhiều file.

## 2. Payment State Machine

### States
- created
- authorized
- captured
- partially_refunded
- refunded

### Transition table

| From | Trigger | To | Triggered by | Side effects |
|---|---|---|---|---|
| none | init payment | created | Car Rental -> MiniBank | create payment record, audit, outbox PaymentCreated |
| created | authorize hold success | authorized | Car Rental -> MiniBank | create hold, reduce available balance |
| authorized | full capture success | captured | Car Rental -> MiniBank | ledger journal created |
| captured | partial refund approved | partially_refunded | refund workflow | refund request + reversal/correction flow |
| partially_refunded | refund remaining | refunded | refund workflow | full refund completed |

### Notes
- current runtime has init/authorize/capture
- refund states may exist in domain model before full runtime implementation is complete

## 3. Hold State Machine

### States
- AUTHORIZED
- CAPTURED
- VOIDED
- EXPIRED

### Transition table

| From | Trigger | To | Triggered by | Side effects |
|---|---|---|---|---|
| none | authorize hold | AUTHORIZED | Car Rental -> MiniBank | available_balance_minor decreases, hold event appended |
| AUTHORIZED | full capture of remaining amount | CAPTURED | Car Rental -> MiniBank | ledger journal posted, remaining amount becomes 0 |
| AUTHORIZED | void request | VOIDED | Car Rental -> MiniBank | available balance restored |
| AUTHORIZED | expiration job | EXPIRED | background job | available balance restored |

### Runtime note
Current runtime capture behavior is full capture only, though DB contract supports partial capture.

## 4. Refund Request State Machine

### States
- requested
- approved
- rejected
- processed

### Transition table

| From | Trigger | To | Triggered by | Side effects |
|---|---|---|---|---|
| none | refund request create | requested | operator/workflow | refund request row created |
| requested | maker-checker approval | approved | checker | reversal/correction becomes allowed |
| requested | reject | rejected | checker | no financial correction |
| approved | financial correction applied | processed | MiniBank flow | reversal or refund posting completed |

### Note
Refund concept exists in schema/domain model, but runtime thin slice may not yet expose full end-to-end refund flow.