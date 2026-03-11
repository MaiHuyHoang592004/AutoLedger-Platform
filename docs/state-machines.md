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
- current canonical runtime seam for capture is `BookingService.completeTrip()` in Car Rental

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

Current runtime command seams in Car Rental:
- host approval canonical path: `confirmBooking()`
- completion/capture canonical path: `completeTrip()`
- legacy/transitional wrappers may still exist, but should delegate to the canonical seams

## 3.1 Runtime booking/calendar/payment mapping

Current runtime should be interpreted as:

- `PENDING_HOST` = booking created + payment authorized + calendar still `HOLD`
- `PAYMENT_AUTHORIZED` = host confirmed + payment still authorized + calendar moved to `BOOKED`
- `IN_PROGRESS` = trip started after booking approval/authorization state is satisfied
- `COMPLETED` = MiniBank capture succeeded on canonical completion seam + Car Rental completion persisted

Important note:
- booking state, calendar state, and payment state remain separate concepts.
- refund/surcharge placeholder paths that still exist in Car Rental do not redefine the canonical runtime mapping above.
- pre-capture cancel/reject maps to MiniBank void as canonical runtime truth.
- post-capture refund and surcharge payment remain deferred/non-canonical runtime paths for now.

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