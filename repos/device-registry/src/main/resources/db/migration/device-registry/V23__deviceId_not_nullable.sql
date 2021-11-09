UPDATE Device SET device_id = uuid WHERE device_id IS NULL;
ALTER TABLE Device MODIFY COLUMN device_id varchar(200) NOT NULL;