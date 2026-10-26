-- 26.12: what a large history makes slow.
--
-- latest_measurement: the latest measurement of each device, kept up to date by the data
-- services. Finding it among the whole history of a device means reading every one of its
-- measurements: with a month of history, the overview of a large site took tens of seconds.
-- A missing row is worked out again on the first read; here it is filled for the existing history.

CREATE TABLE IF NOT EXISTS latest_measurement (
    room_id        BIGINT   NOT NULL,
    temperature_id BIGINT   NOT NULL,
    date           DATETIME NULL,
    PRIMARY KEY (room_id),
    KEY IDX_latest_measurement_temperature_id (temperature_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Latest date of each device, then the last saved measurement at that date
INSERT INTO latest_measurement (room_id, temperature_id, date)
SELECT a.room_id, MAX(t.id), m.last_date
FROM room_temperature_attr a
JOIN temperature t ON t.id = a.temperature_id
JOIN (
    SELECT a2.room_id, MAX(t2.date) AS last_date
    FROM room_temperature_attr a2
    JOIN temperature t2 ON t2.id = a2.temperature_id
    WHERE t2.DTYPE = 'Measurement' AND a2.room_id IS NOT NULL
    GROUP BY a2.room_id
) m ON m.room_id = a.room_id AND t.date = m.last_date
WHERE t.DTYPE = 'Measurement'
GROUP BY a.room_id, m.last_date;

-- Lists of temperatures, the latest first (GET /api/v2/temperatures, a page at a time)
CREATE INDEX IDX_temperature_dtype_date ON temperature (DTYPE, date);
CREATE INDEX IDX_temperature_date ON temperature (date);
