ALTER TABLE dbo.bookings
ADD payment_id UNIQUEIDENTIFIER NULL,
    hold_id UNIQUEIDENTIFIER NULL,
    payment_provider VARCHAR(40) NULL;
GO

CREATE INDEX ix_bookings_payment_id ON dbo.bookings(payment_id);
GO

CREATE INDEX ix_bookings_hold_id ON dbo.bookings(hold_id);
GO