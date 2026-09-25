CREATE TABLE platform_admins (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    is_bootstrap_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_platform_admin_bootstrap_value CHECK (is_bootstrap_admin IN (TRUE, FALSE))
);

CREATE UNIQUE INDEX uq_platform_admin_single_bootstrap
    ON platform_admins (is_bootstrap_admin)
    WHERE is_bootstrap_admin = TRUE;
