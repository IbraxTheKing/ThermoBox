-- 26.12: temperature alerts. A room whose measures stay above its setpoint is reported by email
-- or webhook (channels), under rules that say when and for which buildings, floors or rooms.
-- The history keeps every excess (alert_event) and every message sent about it (alert_delivery).

CREATE TABLE IF NOT EXISTS alert_channel (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    -- What may be shown (JSON); the secrets (SMTP password, webhook URL) are encrypted apart
    settings   TEXT         NOT NULL,
    secret     TEXT         NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS alert_rule (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(100) NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    tolerance            FLOAT        NOT NULL,
    min_duration_seconds INT          NOT NULL,
    clear_after_seconds  INT          NOT NULL,
    reminder_seconds     INT          NOT NULL,
    notify_resolution    BOOLEAN      NOT NULL,
    -- TRUE: the whole installation; FALSE: the elements of alert_rule_scope only (none: nothing)
    global_scope         BOOLEAN      NOT NULL,
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS alert_rule_scope (
    rule_id    BIGINT NOT NULL,
    element_id BIGINT NOT NULL,
    PRIMARY KEY (rule_id, element_id),
    CONSTRAINT FK_alert_rule_scope_rule FOREIGN KEY (rule_id) REFERENCES alert_rule (id) ON DELETE CASCADE,
    CONSTRAINT FK_alert_rule_scope_element FOREIGN KEY (element_id) REFERENCES room (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS alert_rule_channel (
    rule_id    BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    PRIMARY KEY (rule_id, channel_id),
    CONSTRAINT FK_alert_rule_channel_rule FOREIGN KEY (rule_id) REFERENCES alert_rule (id) ON DELETE CASCADE,
    CONSTRAINT FK_alert_rule_channel_channel FOREIGN KEY (channel_id) REFERENCES alert_channel (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- No foreign key to room or alert_rule: the history outlives them (their names are copied)
CREATE TABLE IF NOT EXISTS alert_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    rule_id           BIGINT       NULL,
    rule_name         VARCHAR(100) NOT NULL,
    room_id           BIGINT       NOT NULL,
    room_name         VARCHAR(255) NULL,
    place             VARCHAR(500) NOT NULL,
    state             VARCHAR(10)  NOT NULL,
    exceeded_since    DATETIME     NOT NULL,
    triggered_at      DATETIME     NOT NULL,
    resolved_at       DATETIME     NULL,
    setpoint          FLOAT        NOT NULL,
    threshold         FLOAT        NOT NULL,
    current_setpoint  FLOAT        NULL,
    temperature       FLOAT        NOT NULL,
    peak              FLOAT        NOT NULL,
    last_temperature  FLOAT        NULL,
    devices           TEXT         NULL,
    last_notified_at  DATETIME     NULL,
    reminders         INT          NOT NULL DEFAULT 0,
    reason            VARCHAR(255) NULL,
    PRIMARY KEY (id),
    INDEX IDX_alert_event_state (state, rule_id, room_id),
    INDEX IDX_alert_event_triggered (triggered_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS alert_delivery (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        BIGINT       NULL,
    channel_id      BIGINT       NULL,
    channel_name    VARCHAR(100) NOT NULL,
    kind            VARCHAR(10)  NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at DATETIME     NULL,
    last_error      VARCHAR(500) NULL,
    created_at      DATETIME     NOT NULL,
    sent_at         DATETIME     NULL,
    PRIMARY KEY (id),
    INDEX IDX_alert_delivery_due (status, next_attempt_at),
    INDEX IDX_alert_delivery_event (event_id),
    CONSTRAINT FK_alert_delivery_event FOREIGN KEY (event_id) REFERENCES alert_event (id) ON DELETE CASCADE,
    CONSTRAINT FK_alert_delivery_channel FOREIGN KEY (channel_id) REFERENCES alert_channel (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
