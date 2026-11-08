-- 26.12: the log of the changes made through the API (setpoints, rooms, users, alert rules…),
-- for the administrators. Who (kept as a name: the user may be deleted since), when, what, on
-- what (its name at the time), and the values sent (JSON, secrets removed). Emptied of what is
-- older than the retention an ADMIN chooses.

CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    at          DATETIME     NOT NULL,
    user_id     BIGINT       NULL,
    username    VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NULL,
    action      VARCHAR(60)  NOT NULL,
    target_type VARCHAR(30)  NULL,
    target_id   BIGINT       NULL,
    target_name VARCHAR(255) NULL,
    details     TEXT         NULL,
    PRIMARY KEY (id),
    INDEX IDX_audit_log_at (at),
    INDEX IDX_audit_log_user (user_id, id),
    INDEX IDX_audit_log_action (action, id),
    INDEX IDX_audit_log_target (target_type, target_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
