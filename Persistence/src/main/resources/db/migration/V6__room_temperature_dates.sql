-- 26.12: the kind and the date of each temperature, next to the element it belongs to.
-- A period of one device (its curve over a day) had to read its whole history: the date was in
-- "temperature", the device in "room_temperature_attr", and no index could cover both. With a copy
-- of the kind and the date here, the index (room_id, kind, date) reads that period only.
-- The data services keep the copies up to date. A few minutes for millions of measurements.

ALTER TABLE room_temperature_attr
    ADD COLUMN kind VARCHAR(31) NULL,
    ADD COLUMN date DATETIME    NULL;

UPDATE room_temperature_attr e
JOIN temperature t ON t.id = e.temperature_id
SET e.kind = t.DTYPE, e.date = t.date;

CREATE INDEX IDX_room_temperature_attr_room_kind_date ON room_temperature_attr (room_id, kind, date);
