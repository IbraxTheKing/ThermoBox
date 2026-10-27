-- 26.11: access to bâtiments, étages and salles.
-- IF NOT EXISTS: the 26.11 nightlies created these tables before migrations existed.

-- Elements a restricted user may see (a bâtiment or an étage also grants what it contains)
CREATE TABLE IF NOT EXISTS acces (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    user_id  BIGINT NOT NULL,
    salle_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UNQ_acces_0 UNIQUE (user_id, salle_id),
    CONSTRAINT FK_acces_user_id FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT FK_acces_salle_id FOREIGN KEY (salle_id) REFERENCES salle (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One row per restricted user; without a row the user sees everything
CREATE TABLE IF NOT EXISTS acces_restreint (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UNQ_acces_restreint_user_id UNIQUE (user_id),
    CONSTRAINT FK_acces_restreint_user_id FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
