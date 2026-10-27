-- 26.11: groups of users, each giving access to bâtiments, étages and salles.

CREATE TABLE IF NOT EXISTS groupe (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    nom         VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT UNQ_groupe_nom UNIQUE (nom)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS groupe_membre (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    groupe_id BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UNQ_groupe_membre_0 UNIQUE (groupe_id, user_id),
    CONSTRAINT FK_groupe_membre_groupe_id FOREIGN KEY (groupe_id) REFERENCES groupe (id),
    CONSTRAINT FK_groupe_membre_user_id FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS groupe_acces (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    groupe_id BIGINT NOT NULL,
    salle_id  BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UNQ_groupe_acces_0 UNIQUE (groupe_id, salle_id),
    CONSTRAINT FK_groupe_acces_groupe_id FOREIGN KEY (groupe_id) REFERENCES groupe (id),
    CONSTRAINT FK_groupe_acces_salle_id FOREIGN KEY (salle_id) REFERENCES salle (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
