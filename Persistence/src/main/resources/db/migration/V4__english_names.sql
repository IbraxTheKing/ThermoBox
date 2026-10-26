-- 26.12: English names for the tables, the columns and the type values, like the code.
-- destructive: tables and columns are renamed, so a version before 26.12 cannot use the database
-- any more; going back means restoring the backup made before the update (see the release notes).
-- The names of the constraints and indexes (FK_salle_attr_salleA_id…) are left as they are: no
-- code uses them, and they differ between databases created by EclipseLink and by V1.

RENAME TABLE salle TO room,
             salle_attr TO room_attr,
             salle_temperature_attr TO room_temperature_attr,
             salle_image TO room_image,
             favori TO favorite,
             acces TO user_access,
             acces_restreint TO restricted_access,
             groupe TO user_group,
             groupe_membre TO user_group_member,
             groupe_acces TO user_group_access;

ALTER TABLE room_attr RENAME COLUMN salleA_id TO roomA_id, RENAME COLUMN salleB_id TO roomB_id;
ALTER TABLE room_temperature_attr RENAME COLUMN salle_id TO room_id;
ALTER TABLE room_image RENAME COLUMN salle_id TO room_id;
ALTER TABLE favorite RENAME COLUMN salle_id TO room_id;
ALTER TABLE user_access RENAME COLUMN salle_id TO room_id;
ALTER TABLE user_group RENAME COLUMN nom TO name;
ALTER TABLE user_group_member RENAME COLUMN groupe_id TO user_group_id;
ALTER TABLE user_group_access RENAME COLUMN groupe_id TO user_group_id, RENAME COLUMN salle_id TO room_id;

-- Kind of every element and of every temperature (single-table inheritance)
UPDATE room SET DTYPE = CASE DTYPE
    WHEN 'Salle' THEN 'Room'
    WHEN 'Batiment' THEN 'Building'
    WHEN 'Etage' THEN 'Floor'
    WHEN 'Boitier' THEN 'Device'
    ELSE DTYPE END;

UPDATE temperature SET DTYPE = CASE DTYPE
    WHEN 'Consigne' THEN 'Setpoint'
    WHEN 'Mesure' THEN 'Measurement'
    ELSE DTYPE END;
