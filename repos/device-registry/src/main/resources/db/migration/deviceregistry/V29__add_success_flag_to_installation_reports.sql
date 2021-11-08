ALTER TABLE DeviceInstallationResult
ADD success BOOL NOT NULL;

UPDATE DeviceInstallationResult
SET success = CASE WHEN result_code = "0" THEN true ELSE false END;

ALTER TABLE EcuInstallationResult
ADD success BOOL NOT NULL;

UPDATE EcuInstallationResult
SET success = CASE WHEN result_code = "0" THEN true ELSE false END;