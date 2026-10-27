-- 26.12: hourly averages of the measurements of each device.
-- A curve over a week or a month needs one point per hour at most: reading every measurement
-- (six an hour, or sixty with a device that measures every minute) only to average them away
-- weighed on the database. The data services keep this table up to date.

CREATE TABLE IF NOT EXISTS measurement_hourly (
    room_id   BIGINT   NOT NULL,
    hour      DATETIME NOT NULL,
    count     INT      NOT NULL,
    total     DOUBLE   NOT NULL,
    min_value FLOAT    NOT NULL,
    max_value FLOAT    NOT NULL,
    PRIMARY KEY (room_id, hour)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO measurement_hourly (room_id, hour, count, total, min_value, max_value)
SELECT e.room_id, DATE_FORMAT(e.date, '%Y-%m-%d %H:00:00'), COUNT(*), SUM(t.value), MIN(t.value), MAX(t.value)
FROM room_temperature_attr e
JOIN temperature t ON t.id = e.temperature_id
WHERE e.kind = 'Measurement' AND e.date IS NOT NULL AND t.value IS NOT NULL AND e.room_id IS NOT NULL
GROUP BY e.room_id, DATE_FORMAT(e.date, '%Y-%m-%d %H:00:00');
