-- 26.12: who the person behind an account is (import of a list of staff), and provisional
-- passwords: an imported account (or the first admin) gets a generated password its owner must
-- replace at the first login, valid until provisional_expires_at (NULL: no limit).

ALTER TABLE `user`
    ADD COLUMN first_name VARCHAR(100) NULL,
    ADD COLUMN last_name VARCHAR(100) NULL,
    ADD COLUMN email VARCHAR(255) NULL,
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN provisional_expires_at DATETIME NULL;

-- Duplicates are looked up by address at every import
CREATE INDEX IDX_user_email ON `user` (email);
