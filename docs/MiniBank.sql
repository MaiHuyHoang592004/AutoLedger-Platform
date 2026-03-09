/* =========================================================
   MiniBank - Full Compatible SQL Script
   Target: older SQL Server compatible build
   ========================================================= */

IF DB_ID('MiniBank') IS NULL
BEGIN
    CREATE DATABASE MiniBank;
END
GO

USE MiniBank;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* =========================================================
   0) CLEANUP
   ========================================================= */

IF OBJECT_ID('dbo.tr_hold_events_no_update_delete', 'TR') IS NOT NULL DROP TRIGGER dbo.tr_hold_events_no_update_delete;
IF OBJECT_ID('dbo.tr_ledger_journals_no_update_delete', 'TR') IS NOT NULL DROP TRIGGER dbo.tr_ledger_journals_no_update_delete;
IF OBJECT_ID('dbo.tr_ledger_postings_no_update_delete', 'TR') IS NOT NULL DROP TRIGGER dbo.tr_ledger_postings_no_update_delete;
IF OBJECT_ID('dbo.tr_audit_events_no_update_delete', 'TR') IS NOT NULL DROP TRIGGER dbo.tr_audit_events_no_update_delete;
GO

IF OBJECT_ID('dbo.sp_reverse_journal', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_reverse_journal;
IF OBJECT_ID('dbo.sp_init_refund_with_idem', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_init_refund_with_idem;
IF OBJECT_ID('dbo.sp_capture_hold_partial_with_idem', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_capture_hold_partial_with_idem;
IF OBJECT_ID('dbo.sp_void_hold_with_idem', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_void_hold_with_idem;
IF OBJECT_ID('dbo.sp_init_payment_with_idem', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_init_payment_with_idem;
IF OBJECT_ID('dbo.sp_idem_complete_success', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_idem_complete_success;
IF OBJECT_ID('dbo.sp_idem_begin', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_idem_begin;
IF OBJECT_ID('dbo.sp_outbox_claim_batch', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_outbox_claim_batch;
IF OBJECT_ID('dbo.sp_void_hold', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_void_hold;
IF OBJECT_ID('dbo.sp_capture_hold_partial', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_capture_hold_partial;
IF OBJECT_ID('dbo.sp_authorize_hold', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_authorize_hold;
IF OBJECT_ID('dbo.sp_apply_available_delta', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_apply_available_delta;
IF OBJECT_ID('dbo.sp_post_journal_posted_and_available', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_post_journal_posted_and_available;
IF OBJECT_ID('dbo.sp_post_journal_posted_only', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_post_journal_posted_only;
GO

IF TYPE_ID('dbo.PostingTvp') IS NOT NULL DROP TYPE dbo.PostingTvp;
GO

DECLARE @sql_drop_fk_payments NVARCHAR(MAX);
SET @sql_drop_fk_payments = N'';

SELECT @sql_drop_fk_payments = @sql_drop_fk_payments +
    N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id)) + N'.' + QUOTENAME(OBJECT_NAME(fk.parent_object_id)) +
    N' DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';' + CHAR(10)
FROM sys.foreign_keys fk
WHERE fk.referenced_object_id = OBJECT_ID('dbo.payments');

IF @sql_drop_fk_payments <> N''
    EXEC sp_executesql @sql_drop_fk_payments;
GO

IF OBJECT_ID('dbo.audit_events', 'U') IS NOT NULL DROP TABLE dbo.audit_events;
IF OBJECT_ID('dbo.outbox_messages', 'U') IS NOT NULL DROP TABLE dbo.outbox_messages;
IF OBJECT_ID('dbo.webhook_events_processed', 'U') IS NOT NULL DROP TABLE dbo.webhook_events_processed;
IF OBJECT_ID('dbo.refund_requests', 'U') IS NOT NULL DROP TABLE dbo.refund_requests;
IF OBJECT_ID('dbo.hold_events', 'U') IS NOT NULL DROP TABLE dbo.hold_events;
IF OBJECT_ID('dbo.holds', 'U') IS NOT NULL DROP TABLE dbo.holds;
IF OBJECT_ID('dbo.account_balance_history', 'U') IS NOT NULL DROP TABLE dbo.account_balance_history;
IF OBJECT_ID('dbo.ledger_postings', 'U') IS NOT NULL DROP TABLE dbo.ledger_postings;
IF OBJECT_ID('dbo.ledger_journals', 'U') IS NOT NULL DROP TABLE dbo.ledger_journals;
IF OBJECT_ID('dbo.account_balances_current', 'U') IS NOT NULL DROP TABLE dbo.account_balances_current;
IF OBJECT_ID('dbo.accounts', 'U') IS NOT NULL DROP TABLE dbo.accounts;
IF OBJECT_ID('dbo.idempotency_keys', 'U') IS NOT NULL DROP TABLE dbo.idempotency_keys;
IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL DROP TABLE dbo.payments;
IF OBJECT_ID('dbo.merchants', 'U') IS NOT NULL DROP TABLE dbo.merchants;
GO

/* =========================================================
   1) CORE TABLES
   ========================================================= */

CREATE TABLE dbo.merchants (
    merchant_id     UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_merchants PRIMARY KEY,
    merchant_code   NVARCHAR(50) NOT NULL,
    display_name    NVARCHAR(200) NOT NULL,
    status          TINYINT NOT NULL CONSTRAINT CK_merchants_status CHECK (status IN (1,2)),
    created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_merchants_created_at DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3) NOT NULL CONSTRAINT DF_merchants_updated_at DEFAULT SYSUTCDATETIME(),
    rv              ROWVERSION NOT NULL
);
GO
CREATE UNIQUE INDEX UX_merchants_code ON dbo.merchants(merchant_code);
GO

CREATE TABLE dbo.payments (
    payment_id              UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_payments PRIMARY KEY,
    merchant_id             UNIQUEIDENTIFIER NOT NULL,
    order_ref               NVARCHAR(100) NOT NULL,
    amount_minor            BIGINT NOT NULL,
    amount_refunded_minor   BIGINT NOT NULL CONSTRAINT DF_payments_refunded DEFAULT 0,
    currency                CHAR(3) NOT NULL,
    status                  TINYINT NOT NULL CONSTRAINT CK_payments_status CHECK (status IN (1,2,3,4,5)),
    status_reason           NVARCHAR(200) NULL,
    psp_payment_ref         NVARCHAR(120) NULL,
    created_at              DATETIME2(3) NOT NULL CONSTRAINT DF_payments_created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3) NOT NULL CONSTRAINT DF_payments_updated_at DEFAULT SYSUTCDATETIME(),
    rv                      ROWVERSION NOT NULL,

    CONSTRAINT FK_payments_merchant FOREIGN KEY (merchant_id) REFERENCES dbo.merchants(merchant_id),
    CONSTRAINT CK_payments_amount_pos CHECK (amount_minor > 0),
    CONSTRAINT CK_payments_refunded_nonneg CHECK (amount_refunded_minor >= 0),
    CONSTRAINT CK_payments_refunded_lte_amount CHECK (amount_refunded_minor <= amount_minor),
    CONSTRAINT CK_payments_currency CHECK (currency LIKE '[A-Z][A-Z][A-Z]')
);
GO
CREATE UNIQUE INDEX UX_payments_merchant_orderref ON dbo.payments(merchant_id, order_ref);
GO
CREATE INDEX IX_payments_status_updated ON dbo.payments(status, updated_at DESC)
INCLUDE (merchant_id, amount_minor, amount_refunded_minor, currency);
GO

/* =========================================================
   2) IDEMPOTENCY
   ========================================================= */

CREATE TABLE dbo.idempotency_keys (
    idem_id         BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_idempotency_keys PRIMARY KEY,
    merchant_id     UNIQUEIDENTIFIER NOT NULL,
    idem_key        NVARCHAR(80) NOT NULL,
    request_route   NVARCHAR(120) NOT NULL,
    request_hash    BINARY(32) NOT NULL,
    status          TINYINT NOT NULL CONSTRAINT CK_idem_status CHECK (status IN (1,2,3)),
    response_code   INT NULL,
    response_body   NVARCHAR(MAX) NULL,
    created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_idem_created_at DEFAULT SYSUTCDATETIME(),
    completed_at    DATETIME2(3) NULL,
    expires_at      DATETIME2(3) NOT NULL,
    CONSTRAINT FK_idem_merchant FOREIGN KEY (merchant_id) REFERENCES dbo.merchants(merchant_id)
);
GO
CREATE UNIQUE INDEX UX_idem_scope ON dbo.idempotency_keys(merchant_id, request_route, idem_key);
GO
CREATE INDEX IX_idem_expires ON dbo.idempotency_keys(expires_at);
GO

/* =========================================================
   3) WEBHOOK DEDUPE
   ========================================================= */

CREATE TABLE dbo.webhook_events_processed (
    psp_event_id    UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_webhook_events_processed PRIMARY KEY,
    event_type      NVARCHAR(60) NOT NULL,
    payment_id      UNIQUEIDENTIFIER NULL,
    received_at     DATETIME2(3) NOT NULL CONSTRAINT DF_webhook_received_at DEFAULT SYSUTCDATETIME(),
    payload_hash    BINARY(32) NOT NULL
);
GO
CREATE INDEX IX_webhook_payment_time ON dbo.webhook_events_processed(payment_id, received_at DESC);
GO

/* =========================================================
   4) ACCOUNTS + BALANCES + LEDGER
   ========================================================= */

CREATE TABLE dbo.accounts (
    account_id      INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_accounts PRIMARY KEY,
    account_code    NVARCHAR(80) NOT NULL,
    account_name    NVARCHAR(200) NOT NULL,
    account_type    TINYINT NOT NULL CONSTRAINT CK_accounts_type CHECK (account_type IN (1,2,3,4,5)),
    currency        CHAR(3) NOT NULL,
    normal_side     CHAR(1) NOT NULL CONSTRAINT CK_accounts_normal_side CHECK (normal_side IN ('D','C')),
    is_active       BIT NOT NULL CONSTRAINT DF_accounts_active DEFAULT 1,
    created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_accounts_created DEFAULT SYSUTCDATETIME()
);
GO
CREATE UNIQUE INDEX UX_accounts_code ON dbo.accounts(account_code);
GO
CREATE INDEX IX_accounts_type ON dbo.accounts(account_type);
GO

CREATE TABLE dbo.account_balances_current (
    account_id              INT NOT NULL CONSTRAINT PK_account_balances_current PRIMARY KEY,
    posted_balance_minor    BIGINT NOT NULL CONSTRAINT DF_bal_posted DEFAULT 0,
    available_balance_minor BIGINT NOT NULL CONSTRAINT DF_bal_avail DEFAULT 0,
    last_journal_id         UNIQUEIDENTIFIER NULL,
    updated_at              DATETIME2(3) NOT NULL CONSTRAINT DF_bal_updated DEFAULT SYSUTCDATETIME(),
    rv                      ROWVERSION NOT NULL,
    CONSTRAINT FK_bal_account FOREIGN KEY (account_id) REFERENCES dbo.accounts(account_id)
);
GO

CREATE TABLE dbo.ledger_journals (
    journal_id               UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_ledger_journals PRIMARY KEY,
    journal_type             NVARCHAR(40) NOT NULL,
    reference_id             UNIQUEIDENTIFIER NOT NULL,
    currency                 CHAR(3) NOT NULL,
    reversal_of_journal_id   UNIQUEIDENTIFIER NULL,
    created_by_actor         NVARCHAR(80) NULL,
    correlation_id           UNIQUEIDENTIFIER NULL,
    created_at               DATETIME2(3) NOT NULL CONSTRAINT DF_journals_created DEFAULT SYSUTCDATETIME(),
    prev_row_hash            VARBINARY(32) NULL,
    row_hash                 VARBINARY(32) NOT NULL,
    CONSTRAINT FK_journals_reversal FOREIGN KEY (reversal_of_journal_id) REFERENCES dbo.ledger_journals(journal_id)
);
GO
CREATE INDEX IX_journals_ref_time ON dbo.ledger_journals(reference_id, created_at DESC);
GO
CREATE INDEX IX_journals_reversal ON dbo.ledger_journals(reversal_of_journal_id);
GO

CREATE TABLE dbo.ledger_postings (
    posting_id      BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ledger_postings PRIMARY KEY,
    journal_id      UNIQUEIDENTIFIER NOT NULL,
    account_id      INT NOT NULL,
    direction       CHAR(1) NOT NULL CONSTRAINT CK_postings_dir CHECK (direction IN ('D','C')),
    amount_minor    BIGINT NOT NULL CONSTRAINT CK_postings_amount CHECK (amount_minor > 0),
    created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_postings_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_postings_journal FOREIGN KEY (journal_id) REFERENCES dbo.ledger_journals(journal_id),
    CONSTRAINT FK_postings_account FOREIGN KEY (account_id) REFERENCES dbo.accounts(account_id)
);
GO
CREATE INDEX IX_postings_journal ON dbo.ledger_postings(journal_id);
GO
CREATE INDEX IX_postings_account_time ON dbo.ledger_postings(account_id, created_at DESC) INCLUDE (direction, amount_minor);
GO

/* =========================================================
   5) BALANCE HISTORY
   ========================================================= */

CREATE TABLE dbo.account_balance_history (
    account_balance_history_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_account_balance_history PRIMARY KEY,
    account_id                 INT NOT NULL,
    journal_id                 UNIQUEIDENTIFIER NOT NULL,
    posted_balance_after_minor BIGINT NOT NULL,
    created_at                 DATETIME2(3) NOT NULL CONSTRAINT DF_abh_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_abh_account FOREIGN KEY (account_id) REFERENCES dbo.accounts(account_id),
    CONSTRAINT FK_abh_journal FOREIGN KEY (journal_id) REFERENCES dbo.ledger_journals(journal_id),
    CONSTRAINT UX_abh_account_journal UNIQUE(account_id, journal_id)
);
GO
CREATE INDEX IX_abh_account_time ON dbo.account_balance_history(account_id, created_at DESC) INCLUDE (posted_balance_after_minor, journal_id);
GO

/* =========================================================
   6) HOLDS + EVENTS
   ========================================================= */

CREATE TABLE dbo.holds (
    hold_id                 UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_holds PRIMARY KEY,
    payment_id              UNIQUEIDENTIFIER NOT NULL,
    merchant_id             UNIQUEIDENTIFIER NOT NULL,
    account_id              INT NOT NULL,
    original_amount_minor   BIGINT NOT NULL,
    remaining_amount_minor  BIGINT NOT NULL,
    currency                CHAR(3) NOT NULL,
    status                  TINYINT NOT NULL CONSTRAINT CK_holds_status CHECK (status IN (1,2,3,4)),
    expires_at              DATETIME2(3) NOT NULL,
    created_at              DATETIME2(3) NOT NULL CONSTRAINT DF_holds_created DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3) NOT NULL CONSTRAINT DF_holds_updated DEFAULT SYSUTCDATETIME(),
    rv                      ROWVERSION NOT NULL,

    CONSTRAINT FK_holds_payment FOREIGN KEY (payment_id) REFERENCES dbo.payments(payment_id),
    CONSTRAINT FK_holds_merchant FOREIGN KEY (merchant_id) REFERENCES dbo.merchants(merchant_id),
    CONSTRAINT FK_holds_account FOREIGN KEY (account_id) REFERENCES dbo.accounts(account_id),
    CONSTRAINT CK_holds_original_pos CHECK (original_amount_minor > 0),
    CONSTRAINT CK_holds_remaining_nonneg CHECK (remaining_amount_minor >= 0),
    CONSTRAINT CK_holds_remaining_lte_original CHECK (remaining_amount_minor <= original_amount_minor)
);
GO
CREATE UNIQUE INDEX UX_holds_payment_active ON dbo.holds(payment_id) WHERE status = 1;
GO
CREATE INDEX IX_holds_status_expires ON dbo.holds(status, expires_at) INCLUDE (payment_id, account_id, remaining_amount_minor);
GO

CREATE TABLE dbo.hold_events (
    hold_event_id                 BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_hold_events PRIMARY KEY,
    hold_id                       UNIQUEIDENTIFIER NOT NULL,
    event_type                    NVARCHAR(40) NOT NULL,
    amount_minor                  BIGINT NOT NULL,
    remaining_amount_minor_after  BIGINT NOT NULL,
    actor                         NVARCHAR(80) NULL,
    correlation_id                UNIQUEIDENTIFIER NULL,
    created_at                    DATETIME2(3) NOT NULL CONSTRAINT DF_hold_events_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_hold_events_hold FOREIGN KEY (hold_id) REFERENCES dbo.holds(hold_id),
    CONSTRAINT CK_hold_events_amount_nonneg CHECK (amount_minor >= 0),
    CONSTRAINT CK_hold_events_remaining_nonneg CHECK (remaining_amount_minor_after >= 0)
);
GO
CREATE INDEX IX_hold_events_hold_time ON dbo.hold_events(hold_id, created_at DESC);
GO

/* =========================================================
   7) REFUNDS
   ========================================================= */

CREATE TABLE dbo.refund_requests (
    refund_id       UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_refunds PRIMARY KEY,
    payment_id      UNIQUEIDENTIFIER NOT NULL,
    merchant_id     UNIQUEIDENTIFIER NOT NULL,
    amount_minor    BIGINT NOT NULL,
    currency        CHAR(3) NOT NULL,
    status          TINYINT NOT NULL CONSTRAINT CK_refunds_status CHECK (status IN (1,2,3,4)),
    maker_user      NVARCHAR(80) NOT NULL,
    checker_user    NVARCHAR(80) NULL,
    reason          NVARCHAR(200) NULL,
    created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_refunds_created DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3) NOT NULL CONSTRAINT DF_refunds_updated DEFAULT SYSUTCDATETIME(),
    rv              ROWVERSION NOT NULL,
    CONSTRAINT FK_refunds_payment FOREIGN KEY (payment_id) REFERENCES dbo.payments(payment_id),
    CONSTRAINT FK_refunds_merchant FOREIGN KEY (merchant_id) REFERENCES dbo.merchants(merchant_id),
    CONSTRAINT CK_refunds_amount CHECK (amount_minor > 0)
);
GO
CREATE INDEX IX_refunds_payment_time ON dbo.refund_requests(payment_id, created_at DESC);
GO
CREATE INDEX IX_refunds_status_time ON dbo.refund_requests(status, updated_at DESC);
GO

/* =========================================================
   8) OUTBOX + AUDIT
   ========================================================= */

CREATE TABLE dbo.outbox_messages (
    outbox_id       BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_outbox PRIMARY KEY,
    aggregate_type  NVARCHAR(40) NOT NULL,
    aggregate_id    UNIQUEIDENTIFIER NOT NULL,
    event_type      NVARCHAR(60) NOT NULL,
    event_id        UNIQUEIDENTIFIER NOT NULL,
    payload_json    NVARCHAR(MAX) NOT NULL,
    status          TINYINT NOT NULL CONSTRAINT CK_outbox_status CHECK (status IN (1,2,3,4)),
    attempt_count   INT NOT NULL CONSTRAINT DF_outbox_attempt DEFAULT 0,
    locked_until    DATETIME2(3) NULL,
    lock_owner      NVARCHAR(100) NULL,
    created_at      DATETIME2(3) NOT NULL CONSTRAINT DF_outbox_created DEFAULT SYSUTCDATETIME(),
    published_at    DATETIME2(3) NULL,
    CONSTRAINT UX_outbox_event UNIQUE(event_id)
);
GO
CREATE INDEX IX_outbox_pending ON dbo.outbox_messages(status, created_at) INCLUDE (aggregate_type, aggregate_id, event_type);
GO
CREATE INDEX IX_outbox_lock ON dbo.outbox_messages(locked_until) INCLUDE (status);
GO

CREATE TABLE dbo.audit_events (
    audit_id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_audit PRIMARY KEY,
    actor             NVARCHAR(80) NOT NULL,
    action            NVARCHAR(80) NOT NULL,
    resource_type     NVARCHAR(40) NOT NULL,
    resource_id       UNIQUEIDENTIFIER NOT NULL,
    correlation_id    UNIQUEIDENTIFIER NULL,
    request_id        UNIQUEIDENTIFIER NULL,
    session_id        UNIQUEIDENTIFIER NULL,
    trace_id          CHAR(32) NULL,
    ip_hash           BINARY(32) NULL,
    user_agent_hash   BINARY(32) NULL,
    before_state_json NVARCHAR(MAX) NULL,
    after_state_json  NVARCHAR(MAX) NULL,
    created_at        DATETIME2(3) NOT NULL CONSTRAINT DF_audit_created DEFAULT SYSUTCDATETIME(),
    prev_row_hash     VARBINARY(32) NULL,
    row_hash          VARBINARY(32) NOT NULL
);
GO
CREATE INDEX IX_audit_resource_time ON dbo.audit_events(resource_type, resource_id, created_at DESC);
GO
CREATE INDEX IX_audit_trace ON dbo.audit_events(trace_id, created_at DESC);
GO
CREATE INDEX IX_audit_time ON dbo.audit_events(created_at DESC);
GO

/* =========================================================
   9) TVP
   ========================================================= */

CREATE TYPE dbo.PostingTvp AS TABLE (
    account_id      INT NOT NULL,
    direction       CHAR(1) NOT NULL,
    amount_minor    BIGINT NOT NULL
);
GO

/* =========================================================
   10) IDEMPOTENCY HELPERS
   ========================================================= */

CREATE PROCEDURE dbo.sp_idem_begin
    @idem_key                    NVARCHAR(80),
    @merchant_id                 UNIQUEIDENTIFIER,
    @request_route               NVARCHAR(120),
    @request_hash                BINARY(32),
    @idempotency_ttl_hours       INT = 24,
    @in_progress_timeout_seconds INT = 60
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        INSERT INTO dbo.idempotency_keys (
            merchant_id, idem_key, request_route, request_hash, status, expires_at
        )
        VALUES (
            @merchant_id, @idem_key, @request_route, @request_hash, 1,
            DATEADD(HOUR, @idempotency_ttl_hours, SYSUTCDATETIME())
        );

        SELECT 'SUCCESS' AS Result, NULL AS ResponseCode, NULL AS ResponseBody;
        RETURN;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() NOT IN (2601, 2627)
        BEGIN
            DECLARE @ErrMsg1 NVARCHAR(4000), @ErrSeverity1 INT, @ErrState1 INT;
            SELECT
                @ErrMsg1 = ERROR_MESSAGE(),
                @ErrSeverity1 = CASE WHEN ERROR_SEVERITY() < 11 THEN 16 ELSE ERROR_SEVERITY() END,
                @ErrState1 = ERROR_STATE();

            RAISERROR(@ErrMsg1, @ErrSeverity1, @ErrState1);
            RETURN;
        END

        DECLARE
            @old_status TINYINT,
            @old_request_hash BINARY(32),
            @old_response_code INT,
            @old_response_body NVARCHAR(MAX),
            @old_created_at DATETIME2(3);

        SELECT
            @old_status = status,
            @old_request_hash = request_hash,
            @old_response_code = response_code,
            @old_response_body = response_body,
            @old_created_at = created_at
        FROM dbo.idempotency_keys
        WHERE merchant_id = @merchant_id
          AND request_route = @request_route
          AND idem_key = @idem_key;

        IF @old_request_hash IS NULL
        BEGIN
            RAISERROR('Unexpected idempotency state.', 16, 1);
            RETURN;
        END

        IF @old_request_hash <> @request_hash
        BEGIN
            RAISERROR('Idempotency key reused with different payload.', 16, 1);
            RETURN;
        END

        IF @old_status = 2
        BEGIN
            SELECT 'ALREADY_COMPLETED' AS Result,
                   @old_response_code AS ResponseCode,
                   @old_response_body AS ResponseBody;
            RETURN;
        END

        IF @old_status = 1
        BEGIN
            IF @old_created_at < DATEADD(SECOND, -@in_progress_timeout_seconds, SYSUTCDATETIME())
            BEGIN
                RAISERROR('Request is stale in-progress. Investigate or retry with care.', 16, 1);
                RETURN;
            END

            RAISERROR('Request is still in progress. Please wait.', 16, 1);
            RETURN;
        END

        RAISERROR('Unexpected idempotency state.', 16, 1);
        RETURN;
    END CATCH
END;
GO

CREATE PROCEDURE dbo.sp_idem_complete_success
    @idem_key        NVARCHAR(80),
    @merchant_id     UNIQUEIDENTIFIER,
    @request_route   NVARCHAR(120),
    @response_code   INT,
    @response_body   NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.idempotency_keys
    SET status = 2,
        response_code = @response_code,
        response_body = @response_body,
        completed_at = SYSUTCDATETIME()
    WHERE merchant_id = @merchant_id
      AND request_route = @request_route
      AND idem_key = @idem_key
      AND status = 1;

    IF @@ROWCOUNT <> 1
    BEGIN
        RAISERROR('Failed to complete idempotency record.', 16, 1);
        RETURN;
    END
END;
GO

/* =========================================================
   11) LEDGER PROCEDURES
   ========================================================= */

CREATE PROCEDURE dbo.sp_post_journal_posted_only
    @journal_id         UNIQUEIDENTIFIER,
    @journal_type       NVARCHAR(40),
    @reference_id       UNIQUEIDENTIFIER,
    @currency           CHAR(3),
    @postings           dbo.PostingTvp READONLY,
    @created_by_actor   NVARCHAR(80) = NULL,
    @correlation_id     UNIQUEIDENTIFIER = NULL,
    @reversal_of_journal_id UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF EXISTS (SELECT 1 FROM @postings WHERE amount_minor <= 0 OR direction NOT IN ('D','C'))
    BEGIN
        RAISERROR('Invalid postings', 16, 1);
        RETURN;
    END

    BEGIN TRAN;

    DECLARE @debit BIGINT, @credit BIGINT;
    SELECT @debit = COALESCE(SUM(amount_minor),0) FROM @postings WHERE direction='D';
    SELECT @credit = COALESCE(SUM(amount_minor),0) FROM @postings WHERE direction='C';

    IF (@debit <> @credit)
    BEGIN
        ROLLBACK;
        RAISERROR('Ledger invariant violated: debit != credit', 16, 1);
        RETURN;
    END

    DECLARE @prev_hash VARBINARY(32);
    SELECT TOP (1) @prev_hash = row_hash
    FROM dbo.ledger_journals WITH (UPDLOCK, HOLDLOCK)
    ORDER BY created_at DESC, journal_id DESC;

    DECLARE @journal_payload NVARCHAR(MAX);
    SET @journal_payload =
        ISNULL(CONVERT(NVARCHAR(36), @journal_id), N'') +
        N'|' + ISNULL(@journal_type, N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(36), @reference_id), N'') +
        N'|' + ISNULL(@currency, N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(36), @reversal_of_journal_id), N'') +
        N'|' + ISNULL(@created_by_actor, N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(36), @correlation_id), N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(130), @prev_hash, 1), N'');

    DECLARE @row_hash VARBINARY(32);
    SET @row_hash = HASHBYTES('SHA2_256', @journal_payload);

    INSERT INTO dbo.ledger_journals (
        journal_id, journal_type, reference_id, currency,
        reversal_of_journal_id, created_by_actor, correlation_id,
        prev_row_hash, row_hash
    )
    VALUES (
        @journal_id, @journal_type, @reference_id, @currency,
        @reversal_of_journal_id, @created_by_actor, @correlation_id,
        @prev_hash, @row_hash
    );

    INSERT INTO dbo.ledger_postings(journal_id, account_id, direction, amount_minor)
    SELECT @journal_id, p.account_id, p.direction, p.amount_minor
    FROM @postings p;

    ;WITH delta AS (
        SELECT
            p.account_id,
            SUM(CASE WHEN p.direction = a.normal_side THEN p.amount_minor ELSE -p.amount_minor END) AS delta_minor
        FROM @postings p
        JOIN dbo.accounts a ON a.account_id = p.account_id
        GROUP BY p.account_id
    )
    INSERT INTO dbo.account_balances_current(account_id, posted_balance_minor, available_balance_minor, last_journal_id, updated_at)
    SELECT d.account_id, 0, 0, NULL, SYSUTCDATETIME()
    FROM delta d
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.account_balances_current b WITH (UPDLOCK, HOLDLOCK)
        WHERE b.account_id = d.account_id
    );

    DECLARE @abh TABLE (
        account_id INT,
        journal_id UNIQUEIDENTIFIER,
        posted_balance_after_minor BIGINT,
        created_at DATETIME2(3)
    );

    ;WITH delta AS (
        SELECT
            p.account_id,
            SUM(CASE WHEN p.direction = a.normal_side THEN p.amount_minor ELSE -p.amount_minor END) AS delta_minor
        FROM @postings p
        JOIN dbo.accounts a ON a.account_id = p.account_id
        GROUP BY p.account_id
    )
    UPDATE b
    SET posted_balance_minor = b.posted_balance_minor + d.delta_minor,
        last_journal_id      = @journal_id,
        updated_at           = SYSUTCDATETIME()
    OUTPUT
        inserted.account_id,
        @journal_id,
        inserted.posted_balance_minor,
        SYSUTCDATETIME()
    INTO @abh(account_id, journal_id, posted_balance_after_minor, created_at)
    FROM dbo.account_balances_current b
    JOIN delta d ON d.account_id = b.account_id;

    INSERT INTO dbo.account_balance_history (
        account_id, journal_id, posted_balance_after_minor, created_at
    )
    SELECT account_id, journal_id, posted_balance_after_minor, created_at
    FROM @abh;

    COMMIT;
END;
GO

CREATE PROCEDURE dbo.sp_post_journal_posted_and_available
    @journal_id         UNIQUEIDENTIFIER,
    @journal_type       NVARCHAR(40),
    @reference_id       UNIQUEIDENTIFIER,
    @currency           CHAR(3),
    @postings           dbo.PostingTvp READONLY,
    @created_by_actor   NVARCHAR(80) = NULL,
    @correlation_id     UNIQUEIDENTIFIER = NULL,
    @reversal_of_journal_id UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF EXISTS (SELECT 1 FROM @postings WHERE amount_minor <= 0 OR direction NOT IN ('D','C'))
    BEGIN
        RAISERROR('Invalid postings', 16, 1);
        RETURN;
    END

    BEGIN TRAN;

    DECLARE @debit BIGINT, @credit BIGINT;
    SELECT @debit = COALESCE(SUM(amount_minor),0) FROM @postings WHERE direction='D';
    SELECT @credit = COALESCE(SUM(amount_minor),0) FROM @postings WHERE direction='C';

    IF (@debit <> @credit)
    BEGIN
        ROLLBACK;
        RAISERROR('Ledger invariant violated: debit != credit', 16, 1);
        RETURN;
    END

    DECLARE @prev_hash VARBINARY(32);
    SELECT TOP (1) @prev_hash = row_hash
    FROM dbo.ledger_journals WITH (UPDLOCK, HOLDLOCK)
    ORDER BY created_at DESC, journal_id DESC;

    DECLARE @journal_payload NVARCHAR(MAX);
    SET @journal_payload =
        ISNULL(CONVERT(NVARCHAR(36), @journal_id), N'') +
        N'|' + ISNULL(@journal_type, N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(36), @reference_id), N'') +
        N'|' + ISNULL(@currency, N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(36), @reversal_of_journal_id), N'') +
        N'|' + ISNULL(@created_by_actor, N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(36), @correlation_id), N'') +
        N'|' + ISNULL(CONVERT(NVARCHAR(130), @prev_hash, 1), N'');

    DECLARE @row_hash VARBINARY(32);
    SET @row_hash = HASHBYTES('SHA2_256', @journal_payload);

    INSERT INTO dbo.ledger_journals (
        journal_id, journal_type, reference_id, currency,
        reversal_of_journal_id, created_by_actor, correlation_id,
        prev_row_hash, row_hash
    )
    VALUES (
        @journal_id, @journal_type, @reference_id, @currency,
        @reversal_of_journal_id, @created_by_actor, @correlation_id,
        @prev_hash, @row_hash
    );

    INSERT INTO dbo.ledger_postings(journal_id, account_id, direction, amount_minor)
    SELECT @journal_id, p.account_id, p.direction, p.amount_minor
    FROM @postings p;

    ;WITH delta AS (
        SELECT
            p.account_id,
            SUM(CASE WHEN p.direction = a.normal_side THEN p.amount_minor ELSE -p.amount_minor END) AS delta_minor
        FROM @postings p
        JOIN dbo.accounts a ON a.account_id = p.account_id
        GROUP BY p.account_id
    )
    INSERT INTO dbo.account_balances_current(account_id, posted_balance_minor, available_balance_minor, last_journal_id, updated_at)
    SELECT d.account_id, 0, 0, NULL, SYSUTCDATETIME()
    FROM delta d
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.account_balances_current b WITH (UPDLOCK, HOLDLOCK)
        WHERE b.account_id = d.account_id
    );

    DECLARE @abh2 TABLE (
        account_id INT,
        journal_id UNIQUEIDENTIFIER,
        posted_balance_after_minor BIGINT,
        created_at DATETIME2(3)
    );

    ;WITH delta AS (
        SELECT
            p.account_id,
            SUM(CASE WHEN p.direction = a.normal_side THEN p.amount_minor ELSE -p.amount_minor END) AS delta_minor
        FROM @postings p
        JOIN dbo.accounts a ON a.account_id = p.account_id
        GROUP BY p.account_id
    )
    UPDATE b
    SET posted_balance_minor    = b.posted_balance_minor + d.delta_minor,
        available_balance_minor = b.available_balance_minor + d.delta_minor,
        last_journal_id         = @journal_id,
        updated_at              = SYSUTCDATETIME()
    OUTPUT
        inserted.account_id,
        @journal_id,
        inserted.posted_balance_minor,
        SYSUTCDATETIME()
    INTO @abh2(account_id, journal_id, posted_balance_after_minor, created_at)
    FROM dbo.account_balances_current b
    JOIN delta d ON d.account_id = b.account_id;

    INSERT INTO dbo.account_balance_history (
        account_id, journal_id, posted_balance_after_minor, created_at
    )
    SELECT account_id, journal_id, posted_balance_after_minor, created_at
    FROM @abh2;

    COMMIT;
END;
GO

CREATE PROCEDURE dbo.sp_apply_available_delta
    @account_id   INT,
    @delta_minor  BIGINT,
    @enforce_non_negative BIT = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRAN;

    IF NOT EXISTS (SELECT 1 FROM dbo.account_balances_current WITH (UPDLOCK, HOLDLOCK) WHERE account_id = @account_id)
    BEGIN
        INSERT INTO dbo.account_balances_current(account_id, posted_balance_minor, available_balance_minor, updated_at)
        VALUES (@account_id, 0, 0, SYSUTCDATETIME());
    END

    DECLARE @avail BIGINT;
    SELECT @avail = available_balance_minor
    FROM dbo.account_balances_current WITH (UPDLOCK, HOLDLOCK)
    WHERE account_id = @account_id;

    IF (@enforce_non_negative = 1 AND (@avail + @delta_minor) < 0)
    BEGIN
        ROLLBACK;
        RAISERROR('Insufficient funds (available balance would go negative)', 16, 1);
        RETURN;
    END

    UPDATE dbo.account_balances_current
    SET available_balance_minor = available_balance_minor + @delta_minor,
        updated_at = SYSUTCDATETIME()
    WHERE account_id = @account_id;

    COMMIT;
END;
GO

/* =========================================================
   12) HOLD PROCEDURES
   ========================================================= */

CREATE PROCEDURE dbo.sp_authorize_hold
    @hold_id        UNIQUEIDENTIFIER,
    @payment_id     UNIQUEIDENTIFIER,
    @merchant_id    UNIQUEIDENTIFIER,
    @account_id     INT,
    @amount_minor   BIGINT,
    @currency       CHAR(3),
    @expires_at     DATETIME2(3),
    @actor          NVARCHAR(80) = NULL,
    @correlation_id UNIQUEIDENTIFIER = NULL,
    @enforce_non_negative BIT = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @amount_minor <= 0
    BEGIN
        RAISERROR('Invalid hold amount', 16, 1);
        RETURN;
    END

    BEGIN TRAN;

    SELECT 1
    FROM dbo.payments WITH (UPDLOCK, HOLDLOCK)
    WHERE payment_id = @payment_id;

    IF NOT EXISTS (SELECT 1 FROM dbo.account_balances_current WITH (UPDLOCK, HOLDLOCK) WHERE account_id = @account_id)
    BEGIN
        INSERT INTO dbo.account_balances_current(account_id, posted_balance_minor, available_balance_minor, updated_at)
        VALUES (@account_id, 0, 0, SYSUTCDATETIME());
    END

    DECLARE @avail BIGINT;
    SELECT @avail = available_balance_minor
    FROM dbo.account_balances_current WITH (UPDLOCK, HOLDLOCK)
    WHERE account_id = @account_id;

    IF (@enforce_non_negative = 1 AND @avail < @amount_minor)
    BEGIN
        ROLLBACK;
        RAISERROR('Insufficient funds for authorization', 16, 1);
        RETURN;
    END

    INSERT INTO dbo.holds(
        hold_id, payment_id, merchant_id, account_id,
        original_amount_minor, remaining_amount_minor,
        currency, status, expires_at
    )
    VALUES (
        @hold_id, @payment_id, @merchant_id, @account_id,
        @amount_minor, @amount_minor, @currency, 1, @expires_at
    );

    UPDATE dbo.account_balances_current
    SET available_balance_minor = available_balance_minor - @amount_minor,
        updated_at = SYSUTCDATETIME()
    WHERE account_id = @account_id;

    INSERT INTO dbo.hold_events(
        hold_id, event_type, amount_minor, remaining_amount_minor_after,
        actor, correlation_id
    )
    VALUES (
        @hold_id, N'AUTHORIZED', @amount_minor, @amount_minor,
        @actor, @correlation_id
    );

    COMMIT;
END;
GO

CREATE PROCEDURE dbo.sp_capture_hold_partial
    @hold_id                UNIQUEIDENTIFIER,
    @capture_amount_minor   BIGINT,
    @journal_id             UNIQUEIDENTIFIER,
    @journal_type           NVARCHAR(40),
    @reference_id           UNIQUEIDENTIFIER,
    @currency               CHAR(3),
    @postings               dbo.PostingTvp READONLY,
    @actor                  NVARCHAR(80) = NULL,
    @correlation_id         UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @capture_amount_minor <= 0
    BEGIN
        RAISERROR('Invalid capture amount', 16, 1);
        RETURN;
    END

    BEGIN TRAN;

    DECLARE @status TINYINT, @remaining BIGINT;

    SELECT @status = status, @remaining = remaining_amount_minor
    FROM dbo.holds WITH (UPDLOCK, HOLDLOCK)
    WHERE hold_id = @hold_id;

    IF @status IS NULL
    BEGIN
        ROLLBACK;
        RAISERROR('Hold not found', 16, 1);
        RETURN;
    END

    IF @status <> 1
    BEGIN
        COMMIT;
        RETURN;
    END

    IF @capture_amount_minor > @remaining
    BEGIN
        ROLLBACK;
        RAISERROR('Capture amount exceeds remaining hold', 16, 1);
        RETURN;
    END

    DECLARE @tvp_debit BIGINT, @tvp_credit BIGINT;
    SELECT @tvp_debit = COALESCE(SUM(amount_minor), 0) FROM @postings WHERE direction = 'D';
    SELECT @tvp_credit = COALESCE(SUM(amount_minor), 0) FROM @postings WHERE direction = 'C';

    IF (@tvp_debit <> @tvp_credit)
    BEGIN
        ROLLBACK;
        RAISERROR('Capture postings are not balanced', 16, 1);
        RETURN;
    END

    IF (@tvp_debit <> @capture_amount_minor OR @tvp_credit <> @capture_amount_minor)
    BEGIN
        ROLLBACK;
        RAISERROR('Capture postings total does not match capture amount', 16, 1);
        RETURN;
    END

    EXEC dbo.sp_post_journal_posted_only
        @journal_id = @journal_id,
        @journal_type = @journal_type,
        @reference_id = @reference_id,
        @currency = @currency,
        @postings = @postings,
        @created_by_actor = @actor,
        @correlation_id = @correlation_id,
        @reversal_of_journal_id = NULL;

    UPDATE dbo.holds
    SET remaining_amount_minor = remaining_amount_minor - @capture_amount_minor,
        status = CASE WHEN (remaining_amount_minor - @capture_amount_minor) = 0 THEN 2 ELSE 1 END,
        updated_at = SYSUTCDATETIME()
    WHERE hold_id = @hold_id;

    INSERT INTO dbo.hold_events(
        hold_id, event_type, amount_minor, remaining_amount_minor_after,
        actor, correlation_id
    )
    SELECT
        @hold_id,
        N'CAPTURED',
        @capture_amount_minor,
        remaining_amount_minor,
        @actor,
        @correlation_id
    FROM dbo.holds
    WHERE hold_id = @hold_id;

    COMMIT;
END;
GO

CREATE PROCEDURE dbo.sp_void_hold
    @hold_id      UNIQUEIDENTIFIER,
    @void_status  TINYINT,
    @actor        NVARCHAR(80) = NULL,
    @correlation_id UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @void_status NOT IN (3,4)
    BEGIN
        RAISERROR('Invalid void status', 16, 1);
        RETURN;
    END

    BEGIN TRAN;

    DECLARE @account_id INT, @status TINYINT, @remain BIGINT;

    SELECT @account_id = account_id, @status = status, @remain = remaining_amount_minor
    FROM dbo.holds WITH (UPDLOCK, HOLDLOCK)
    WHERE hold_id = @hold_id;

    IF @status IS NULL
    BEGIN
        ROLLBACK;
        RAISERROR('Hold not found', 16, 1);
        RETURN;
    END

    IF @status <> 1
    BEGIN
        COMMIT;
        RETURN;
    END

    UPDATE dbo.account_balances_current WITH (UPDLOCK, HOLDLOCK)
    SET available_balance_minor = available_balance_minor + @remain,
        updated_at = SYSUTCDATETIME()
    WHERE account_id = @account_id;

    UPDATE dbo.holds
    SET status = @void_status,
        updated_at = SYSUTCDATETIME()
    WHERE hold_id = @hold_id;

    INSERT INTO dbo.hold_events(
        hold_id, event_type, amount_minor, remaining_amount_minor_after,
        actor, correlation_id
    )
    VALUES (
        @hold_id,
        CASE WHEN @void_status = 3 THEN N'VOIDED' ELSE N'EXPIRED' END,
        @remain,
        0,
        @actor,
        @correlation_id
    );

    COMMIT;
END;
GO

/* =========================================================
   13) IDEMPOTENT FLOWS
   ========================================================= */

CREATE PROCEDURE dbo.sp_init_payment_with_idem
    @idem_key                    NVARCHAR(80),
    @merchant_id                 UNIQUEIDENTIFIER,
    @request_route               NVARCHAR(120),
    @request_hash                BINARY(32),
    @payment_id                  UNIQUEIDENTIFIER,
    @amount_minor                BIGINT,
    @currency                    CHAR(3),
    @order_ref                   NVARCHAR(100),
    @actor                       NVARCHAR(80),
    @correlation_id              UNIQUEIDENTIFIER = NULL,
    @request_id                  UNIQUEIDENTIFIER = NULL,
    @session_id                  UNIQUEIDENTIFIER = NULL,
    @trace_id                    CHAR(32) = NULL,
    @idempotency_ttl_hours       INT = 24,
    @in_progress_timeout_seconds INT = 60
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @amount_minor <= 0
    BEGIN
        RAISERROR('Invalid payment amount', 16, 1);
        RETURN;
    END

    BEGIN TRAN;

    DECLARE @idem_result TABLE (
        Result NVARCHAR(40),
        ResponseCode INT NULL,
        ResponseBody NVARCHAR(MAX) NULL
    );

    INSERT INTO @idem_result
    EXEC dbo.sp_idem_begin
        @idem_key = @idem_key,
        @merchant_id = @merchant_id,
        @request_route = @request_route,
        @request_hash = @request_hash,
        @idempotency_ttl_hours = @idempotency_ttl_hours,
        @in_progress_timeout_seconds = @in_progress_timeout_seconds;

    IF EXISTS (SELECT 1 FROM @idem_result WHERE Result = 'ALREADY_COMPLETED')
    BEGIN
        COMMIT;
        SELECT Result, ResponseCode, ResponseBody FROM @idem_result;
        RETURN;
    END

    INSERT INTO dbo.payments (
        payment_id, merchant_id, order_ref, amount_minor, currency, status
    )
    VALUES (
        @payment_id, @merchant_id, @order_ref, @amount_minor, @currency, 1
    );

    DECLARE @payment_created_event_id UNIQUEIDENTIFIER;
    SET @payment_created_event_id = NEWID();

    DECLARE @outbox_payload NVARCHAR(MAX);
    SET @outbox_payload =
        N'{"event_id":"' + CONVERT(NVARCHAR(36), @payment_created_event_id) +
        N'","event_type":"PaymentCreated"' +
        N',"payment_id":"' + CONVERT(NVARCHAR(36), @payment_id) +
        N'","merchant_id":"' + CONVERT(NVARCHAR(36), @merchant_id) +
        N'","order_ref":"' + REPLACE(@order_ref, '"', '\"') +
        N'","amount_minor":' + CONVERT(NVARCHAR(20), @amount_minor) +
        N',"currency":"' + @currency +
        N'","status":"created"}';

    INSERT INTO dbo.outbox_messages (
        aggregate_type, aggregate_id, event_type, event_id, payload_json, status
    )
    VALUES (
        N'Payment', @payment_id, N'PaymentCreated', @payment_created_event_id, @outbox_payload, 1
    );

    DECLARE @prev_audit_hash VARBINARY(32);
    SELECT TOP (1) @prev_audit_hash = row_hash
    FROM dbo.audit_events WITH (UPDLOCK, HOLDLOCK)
    ORDER BY created_at DESC, audit_id DESC;

    DECLARE @after_json NVARCHAR(MAX);
    SET @after_json =
        N'{"payment_id":"' + CONVERT(NVARCHAR(36), @payment_id) +
        N'","order_ref":"' + REPLACE(@order_ref, '"', '\"') +
        N'","amount_minor":' + CONVERT(NVARCHAR(20), @amount_minor) +
        N',"currency":"' + @currency +
        N'","status":"created"}';

    DECLARE @audit_payload NVARCHAR(MAX);
    SET @audit_payload =
        ISNULL(@actor,N'') + N'|PAYMENT_CREATE|Payment|' +
        CONVERT(NVARCHAR(36), @payment_id) + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @correlation_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @request_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @session_id),N'') + N'|' +
        ISNULL(@trace_id,N'') + N'|' +
        ISNULL(@after_json,N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(130), @prev_audit_hash, 1), N'');

    INSERT INTO dbo.audit_events (
        actor, action, resource_type, resource_id,
        correlation_id, request_id, session_id, trace_id,
        before_state_json, after_state_json, prev_row_hash, row_hash
    )
    VALUES (
        @actor, N'PAYMENT_CREATE', N'Payment', @payment_id,
        @correlation_id, @request_id, @session_id, @trace_id,
        NULL, @after_json, @prev_audit_hash, HASHBYTES('SHA2_256', @audit_payload)
    );

    DECLARE @response_body NVARCHAR(MAX);
    SET @response_body =
        N'{"payment_id":"' + CONVERT(NVARCHAR(36), @payment_id) +
        N'","status":"created"}';

    EXEC dbo.sp_idem_complete_success
        @idem_key = @idem_key,
        @merchant_id = @merchant_id,
        @request_route = @request_route,
        @response_code = 201,
        @response_body = @response_body;

    COMMIT;

    SELECT 'SUCCESS' AS Result, 201 AS ResponseCode, @response_body AS ResponseBody;
END;
GO

CREATE PROCEDURE dbo.sp_capture_hold_partial_with_idem
    @idem_key                    NVARCHAR(80),
    @merchant_id                 UNIQUEIDENTIFIER,
    @request_route               NVARCHAR(120),
    @request_hash                BINARY(32),
    @hold_id                     UNIQUEIDENTIFIER,
    @capture_amount_minor        BIGINT,
    @journal_id                  UNIQUEIDENTIFIER,
    @journal_type                NVARCHAR(40),
    @reference_id                UNIQUEIDENTIFIER,
    @currency                    CHAR(3),
    @postings                    dbo.PostingTvp READONLY,
    @actor                       NVARCHAR(80),
    @correlation_id              UNIQUEIDENTIFIER = NULL,
    @request_id                  UNIQUEIDENTIFIER = NULL,
    @session_id                  UNIQUEIDENTIFIER = NULL,
    @trace_id                    CHAR(32) = NULL,
    @idempotency_ttl_hours       INT = 24,
    @in_progress_timeout_seconds INT = 60
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRAN;

    DECLARE @idem_result TABLE (Result NVARCHAR(40), ResponseCode INT NULL, ResponseBody NVARCHAR(MAX) NULL);

    INSERT INTO @idem_result
    EXEC dbo.sp_idem_begin
        @idem_key, @merchant_id, @request_route, @request_hash,
        @idempotency_ttl_hours, @in_progress_timeout_seconds;

    IF EXISTS (SELECT 1 FROM @idem_result WHERE Result = 'ALREADY_COMPLETED')
    BEGIN
        COMMIT;
        SELECT Result, ResponseCode, ResponseBody FROM @idem_result;
        RETURN;
    END

    DECLARE @before_json NVARCHAR(MAX);
    SELECT @before_json =
        N'{"hold_id":"' + CONVERT(NVARCHAR(36), hold_id) +
        N'","remaining_amount_minor":' + CONVERT(NVARCHAR(20), remaining_amount_minor) +
        N',"status":' + CONVERT(NVARCHAR(10), status) + N'}'
    FROM dbo.holds
    WHERE hold_id = @hold_id;

    EXEC dbo.sp_capture_hold_partial
        @hold_id = @hold_id,
        @capture_amount_minor = @capture_amount_minor,
        @journal_id = @journal_id,
        @journal_type = @journal_type,
        @reference_id = @reference_id,
        @currency = @currency,
        @postings = @postings,
        @actor = @actor,
        @correlation_id = @correlation_id;

    DECLARE @after_json NVARCHAR(MAX);
    SELECT @after_json =
        N'{"hold_id":"' + CONVERT(NVARCHAR(36), hold_id) +
        N'","remaining_amount_minor":' + CONVERT(NVARCHAR(20), remaining_amount_minor) +
        N',"status":' + CONVERT(NVARCHAR(10), status) + N'}'
    FROM dbo.holds
    WHERE hold_id = @hold_id;

    DECLARE @prev_audit_hash VARBINARY(32);
    SELECT TOP (1) @prev_audit_hash = row_hash
    FROM dbo.audit_events WITH (UPDLOCK, HOLDLOCK)
    ORDER BY created_at DESC, audit_id DESC;

    DECLARE @audit_payload NVARCHAR(MAX);
    SET @audit_payload =
        ISNULL(@actor,N'') + N'|HOLD_CAPTURE|Hold|' +
        CONVERT(NVARCHAR(36), @hold_id) + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @correlation_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @request_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @session_id),N'') + N'|' +
        ISNULL(@trace_id,N'') + N'|' +
        ISNULL(@before_json,N'') + N'|' +
        ISNULL(@after_json,N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(130), @prev_audit_hash, 1), N'');

    INSERT INTO dbo.audit_events (
        actor, action, resource_type, resource_id,
        correlation_id, request_id, session_id, trace_id,
        before_state_json, after_state_json, prev_row_hash, row_hash
    )
    VALUES (
        @actor, N'HOLD_CAPTURE', N'Hold', @hold_id,
        @correlation_id, @request_id, @session_id, @trace_id,
        @before_json, @after_json, @prev_audit_hash, HASHBYTES('SHA2_256', @audit_payload)
    );

    DECLARE @response_body NVARCHAR(MAX);
    SET @response_body =
        N'{"hold_id":"' + CONVERT(NVARCHAR(36), @hold_id) +
        N'","capture_amount_minor":' + CONVERT(NVARCHAR(20), @capture_amount_minor) + N'}';

    EXEC dbo.sp_idem_complete_success
        @idem_key, @merchant_id, @request_route, 200, @response_body;

    COMMIT;

    SELECT 'SUCCESS' AS Result, 200 AS ResponseCode, @response_body AS ResponseBody;
END;
GO

CREATE PROCEDURE dbo.sp_void_hold_with_idem
    @idem_key                    NVARCHAR(80),
    @merchant_id                 UNIQUEIDENTIFIER,
    @request_route               NVARCHAR(120),
    @request_hash                BINARY(32),
    @hold_id                     UNIQUEIDENTIFIER,
    @void_status                 TINYINT,
    @actor                       NVARCHAR(80),
    @correlation_id              UNIQUEIDENTIFIER = NULL,
    @request_id                  UNIQUEIDENTIFIER = NULL,
    @session_id                  UNIQUEIDENTIFIER = NULL,
    @trace_id                    CHAR(32) = NULL,
    @idempotency_ttl_hours       INT = 24,
    @in_progress_timeout_seconds INT = 60
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRAN;

    DECLARE @idem_result TABLE (Result NVARCHAR(40), ResponseCode INT NULL, ResponseBody NVARCHAR(MAX) NULL);

    INSERT INTO @idem_result
    EXEC dbo.sp_idem_begin
        @idem_key, @merchant_id, @request_route, @request_hash,
        @idempotency_ttl_hours, @in_progress_timeout_seconds;

    IF EXISTS (SELECT 1 FROM @idem_result WHERE Result = 'ALREADY_COMPLETED')
    BEGIN
        COMMIT;
        SELECT Result, ResponseCode, ResponseBody FROM @idem_result;
        RETURN;
    END

    DECLARE @before_json NVARCHAR(MAX);
    SELECT @before_json =
        N'{"hold_id":"' + CONVERT(NVARCHAR(36), hold_id) +
        N'","remaining_amount_minor":' + CONVERT(NVARCHAR(20), remaining_amount_minor) +
        N',"status":' + CONVERT(NVARCHAR(10), status) + N'}'
    FROM dbo.holds
    WHERE hold_id = @hold_id;

    EXEC dbo.sp_void_hold
        @hold_id = @hold_id,
        @void_status = @void_status,
        @actor = @actor,
        @correlation_id = @correlation_id;

    DECLARE @after_json NVARCHAR(MAX);
    SELECT @after_json =
        N'{"hold_id":"' + CONVERT(NVARCHAR(36), hold_id) +
        N'","remaining_amount_minor":' + CONVERT(NVARCHAR(20), remaining_amount_minor) +
        N',"status":' + CONVERT(NVARCHAR(10), status) + N'}'
    FROM dbo.holds
    WHERE hold_id = @hold_id;

    DECLARE @prev_audit_hash VARBINARY(32);
    SELECT TOP (1) @prev_audit_hash = row_hash
    FROM dbo.audit_events WITH (UPDLOCK, HOLDLOCK)
    ORDER BY created_at DESC, audit_id DESC;

    DECLARE @audit_payload NVARCHAR(MAX);
    SET @audit_payload =
        ISNULL(@actor,N'') + N'|HOLD_VOID|Hold|' +
        CONVERT(NVARCHAR(36), @hold_id) + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @correlation_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @request_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @session_id),N'') + N'|' +
        ISNULL(@trace_id,N'') + N'|' +
        ISNULL(@before_json,N'') + N'|' +
        ISNULL(@after_json,N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(130), @prev_audit_hash, 1), N'');

    INSERT INTO dbo.audit_events (
        actor, action, resource_type, resource_id,
        correlation_id, request_id, session_id, trace_id,
        before_state_json, after_state_json, prev_row_hash, row_hash
    )
    VALUES (
        @actor, N'HOLD_VOID', N'Hold', @hold_id,
        @correlation_id, @request_id, @session_id, @trace_id,
        @before_json, @after_json, @prev_audit_hash, HASHBYTES('SHA2_256', @audit_payload)
    );

    DECLARE @response_body NVARCHAR(MAX);
    SET @response_body =
        N'{"hold_id":"' + CONVERT(NVARCHAR(36), @hold_id) +
        N'","void_status":' + CONVERT(NVARCHAR(10), @void_status) + N'}';

    EXEC dbo.sp_idem_complete_success
        @idem_key, @merchant_id, @request_route, 200, @response_body;

    COMMIT;

    SELECT 'SUCCESS' AS Result, 200 AS ResponseCode, @response_body AS ResponseBody;
END;
GO

CREATE PROCEDURE dbo.sp_init_refund_with_idem
    @idem_key                    NVARCHAR(80),
    @merchant_id                 UNIQUEIDENTIFIER,
    @request_route               NVARCHAR(120),
    @request_hash                BINARY(32),
    @refund_id                   UNIQUEIDENTIFIER,
    @payment_id                  UNIQUEIDENTIFIER,
    @amount_minor                BIGINT,
    @currency                    CHAR(3),
    @maker_user                  NVARCHAR(80),
    @reason                      NVARCHAR(200) = NULL,
    @correlation_id              UNIQUEIDENTIFIER = NULL,
    @request_id                  UNIQUEIDENTIFIER = NULL,
    @session_id                  UNIQUEIDENTIFIER = NULL,
    @trace_id                    CHAR(32) = NULL,
    @idempotency_ttl_hours       INT = 24,
    @in_progress_timeout_seconds INT = 60
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRAN;

    DECLARE @idem_result TABLE (Result NVARCHAR(40), ResponseCode INT NULL, ResponseBody NVARCHAR(MAX) NULL);

    INSERT INTO @idem_result
    EXEC dbo.sp_idem_begin
        @idem_key, @merchant_id, @request_route, @request_hash,
        @idempotency_ttl_hours, @in_progress_timeout_seconds;

    IF EXISTS (SELECT 1 FROM @idem_result WHERE Result = 'ALREADY_COMPLETED')
    BEGIN
        COMMIT;
        SELECT Result, ResponseCode, ResponseBody FROM @idem_result;
        RETURN;
    END

    INSERT INTO dbo.refund_requests (
        refund_id, payment_id, merchant_id, amount_minor, currency,
        status, maker_user, reason
    )
    VALUES (
        @refund_id, @payment_id, @merchant_id, @amount_minor, @currency,
        1, @maker_user, @reason
    );

    DECLARE @prev_audit_hash VARBINARY(32);
    SELECT TOP (1) @prev_audit_hash = row_hash
    FROM dbo.audit_events WITH (UPDLOCK, HOLDLOCK)
    ORDER BY created_at DESC, audit_id DESC;

    DECLARE @after_json NVARCHAR(MAX);
    SET @after_json =
        N'{"refund_id":"' + CONVERT(NVARCHAR(36), @refund_id) +
        N'","payment_id":"' + CONVERT(NVARCHAR(36), @payment_id) +
        N'","amount_minor":' + CONVERT(NVARCHAR(20), @amount_minor) +
        N',"currency":"' + @currency +
        N'","status":"requested"}';

    DECLARE @audit_payload NVARCHAR(MAX);
    SET @audit_payload =
        ISNULL(@maker_user,N'') + N'|REFUND_REQUEST|Refund|' +
        CONVERT(NVARCHAR(36), @refund_id) + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @correlation_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @request_id),N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(36), @session_id),N'') + N'|' +
        ISNULL(@trace_id,N'') + N'|' +
        ISNULL(@after_json,N'') + N'|' +
        ISNULL(CONVERT(NVARCHAR(130), @prev_audit_hash, 1), N'');

    INSERT INTO dbo.audit_events (
        actor, action, resource_type, resource_id,
        correlation_id, request_id, session_id, trace_id,
        before_state_json, after_state_json, prev_row_hash, row_hash
    )
    VALUES (
        @maker_user, N'REFUND_REQUEST', N'Refund', @refund_id,
        @correlation_id, @request_id, @session_id, @trace_id,
        NULL, @after_json, @prev_audit_hash, HASHBYTES('SHA2_256', @audit_payload)
    );

    DECLARE @response_body NVARCHAR(MAX);
    SET @response_body =
        N'{"refund_id":"' + CONVERT(NVARCHAR(36), @refund_id) +
        N'","status":"requested"}';

    EXEC dbo.sp_idem_complete_success
        @idem_key, @merchant_id, @request_route, 201, @response_body;

    COMMIT;

    SELECT 'SUCCESS' AS Result, 201 AS ResponseCode, @response_body AS ResponseBody;
END;
GO

/* =========================================================
   14) REVERSAL + OUTBOX
   ========================================================= */

CREATE PROCEDURE dbo.sp_reverse_journal
    @target_journal_id   UNIQUEIDENTIFIER,
    @reversal_journal_id UNIQUEIDENTIFIER,
    @reference_id        UNIQUEIDENTIFIER,
    @actor               NVARCHAR(80),
    @correlation_id      UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @currency CHAR(3), @journal_type NVARCHAR(40), @reversal_journal_type NVARCHAR(40);

    SELECT @currency = currency, @journal_type = journal_type
    FROM dbo.ledger_journals
    WHERE journal_id = @target_journal_id;

    IF @currency IS NULL
    BEGIN
        RAISERROR('Target journal not found.', 16, 1);
        RETURN;
    END

    IF EXISTS (SELECT 1 FROM dbo.ledger_journals WHERE reversal_of_journal_id = @target_journal_id)
    BEGIN
        RAISERROR('Target journal already reversed.', 16, 1);
        RETURN;
    END

    SET @reversal_journal_type = @journal_type + N'_REVERSAL';

    DECLARE @reversal_postings dbo.PostingTvp;

    INSERT INTO @reversal_postings(account_id, direction, amount_minor)
    SELECT
        account_id,
        CASE WHEN direction = 'D' THEN 'C' ELSE 'D' END,
        amount_minor
    FROM dbo.ledger_postings
    WHERE journal_id = @target_journal_id;

    EXEC dbo.sp_post_journal_posted_only
        @journal_id = @reversal_journal_id,
        @journal_type = @reversal_journal_type,
        @reference_id = @reference_id,
        @currency = @currency,
        @postings = @reversal_postings,
        @created_by_actor = @actor,
        @correlation_id = @correlation_id,
        @reversal_of_journal_id = @target_journal_id;
END;
GO

CREATE PROCEDURE dbo.sp_outbox_claim_batch
    @batch_size   INT,
    @lock_seconds INT,
    @lock_owner   NVARCHAR(100)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    ;WITH cte AS (
        SELECT TOP (@batch_size) *
        FROM dbo.outbox_messages WITH (READPAST, UPDLOCK, ROWLOCK)
        WHERE status = 1
          AND (locked_until IS NULL OR locked_until < SYSUTCDATETIME())
        ORDER BY created_at
    )
    UPDATE cte
    SET status = 2,
        locked_until = DATEADD(SECOND, @lock_seconds, SYSUTCDATETIME()),
        lock_owner = @lock_owner,
        attempt_count = attempt_count + 1
    OUTPUT
        inserted.outbox_id,
        inserted.event_id,
        inserted.event_type,
        inserted.aggregate_type,
        inserted.aggregate_id,
        inserted.payload_json;
END;
GO

/* =========================================================
   15) APPEND-ONLY TRIGGERS
   ========================================================= */

CREATE TRIGGER dbo.tr_ledger_journals_no_update_delete
ON dbo.ledger_journals
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    RAISERROR('ledger_journals is append-only. Use reversal journal instead of UPDATE/DELETE.', 16, 1);
END;
GO

CREATE TRIGGER dbo.tr_ledger_postings_no_update_delete
ON dbo.ledger_postings
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    RAISERROR('ledger_postings is append-only. Use reversal journal instead of UPDATE/DELETE.', 16, 1);
END;
GO

CREATE TRIGGER dbo.tr_audit_events_no_update_delete
ON dbo.audit_events
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    RAISERROR('audit_events is append-only. UPDATE/DELETE is not allowed.', 16, 1);
END;
GO

CREATE TRIGGER dbo.tr_hold_events_no_update_delete
ON dbo.hold_events
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    RAISERROR('hold_events is append-only. UPDATE/DELETE is not allowed.', 16, 1);
END;
GO

/* =========================================================
   16) SEED DATA
   ========================================================= */

INSERT INTO dbo.accounts(account_code, account_name, account_type, currency, normal_side)
VALUES
('BANK_CASH',        'Bank cash account',        1, 'VND', 'D'),
('PSP_CLEARING',     'PSP clearing account',     1, 'VND', 'D'),
('MERCHANT_LIAB',    'Merchant payable balance', 2, 'VND', 'C'),
('CUSTOMER_LIAB',    'Customer wallet balance',  2, 'VND', 'C'),
('PAYMENT_REVENUE',  'Payment revenue',          4, 'VND', 'C'),
('FEE_EXPENSE',      'Payment fee expense',      5, 'VND', 'D');
GO

INSERT INTO dbo.account_balances_current(account_id, posted_balance_minor, available_balance_minor, updated_at)
SELECT account_id, 0, 0, SYSUTCDATETIME()
FROM dbo.accounts;
GO