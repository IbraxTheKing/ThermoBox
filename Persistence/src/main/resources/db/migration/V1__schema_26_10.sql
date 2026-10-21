-- Schema of ThermoBox 26.10, as EclipseLink created it.
-- IF NOT EXISTS: on a database created before migrations, existing tables are kept as they are.

CREATE TABLE IF NOT EXISTS `user` (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NULL,
    password VARCHAR(255) NULL,
    type     INTEGER      NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Salle, Batiment, Etage and Boitier share this table (DTYPE)
CREATE TABLE IF NOT EXISTS salle (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    DTYPE VARCHAR(31)  NULL,
    name  VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Consigne and Mesure share this table (DTYPE)
CREATE TABLE IF NOT EXISTS temperature (
    id    BIGINT      NOT NULL AUTO_INCREMENT,
    DTYPE VARCHAR(31) NULL,
    value FLOAT       NULL,
    date  DATETIME    NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS salle_attr (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    salleA_id BIGINT NOT NULL,
    salleB_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_salle_attr_salleA_id FOREIGN KEY (salleA_id) REFERENCES salle (id),
    CONSTRAINT FK_salle_attr_salleB_id FOREIGN KEY (salleB_id) REFERENCES salle (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS salle_temperature_attr (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    salle_id       BIGINT NULL,
    temperature_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_salle_temperature_attr_salle_id FOREIGN KEY (salle_id) REFERENCES salle (id),
    CONSTRAINT FK_salle_temperature_attr_temperature_id FOREIGN KEY (temperature_id) REFERENCES temperature (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS favori (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    user_id  BIGINT NOT NULL,
    salle_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UNQ_favori_0 UNIQUE (user_id, salle_id),
    CONSTRAINT FK_favori_user_id FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT FK_favori_salle_id FOREIGN KEY (salle_id) REFERENCES salle (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS salle_image (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    salle_id     BIGINT       NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    data         LONGBLOB     NOT NULL,
    updated      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UNQ_salle_image_salle_id UNIQUE (salle_id),
    CONSTRAINT FK_salle_image_salle_id FOREIGN KEY (salle_id) REFERENCES salle (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
