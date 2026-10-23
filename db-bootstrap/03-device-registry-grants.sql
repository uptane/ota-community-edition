-- director's V9/V10 migrations read from and move tables out of
-- device_registry (see 02-device-registry-schema.sql)
grant all privileges on `device_registry`.* to 'director_v2'@'%';
flush privileges;
