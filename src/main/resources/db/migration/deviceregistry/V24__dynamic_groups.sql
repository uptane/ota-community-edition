ALTER TABLE DeviceGroup
ADD COLUMN `type` ENUM('static', 'dynamic') NOT NULL ,
ADD COLUMN expression VARCHAR(255) NULL;

