# Ledger Money Flow

This document illustrates how money moves through the ledger.

---

# Accounts

CUSTOMER_LIAB  
MERCHANT_LIAB  
PSP_CLEARING  
BANK_CASH  

---

# Authorization Flow

Customer authorizes payment.

Effect

available_balance decreases.

Ledger unchanged.

---

# Capture Flow

Journal example

Debit CUSTOMER_LIAB  
Credit MERCHANT_LIAB

Effect

posted_balance updated.

---

# Settlement Flow

Merchant payout.

Debit MERCHANT_LIAB  
Credit BANK_CASH

---

# Reversal Flow

Incorrect journal.

Reversal swaps directions.

Debit MERCHANT_LIAB  
Credit CUSTOMER_LIAB

Ledger history remains intact.
