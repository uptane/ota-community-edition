ALTER TABLE devices RENAME to provisioned_devices
;

CREATE INDEX IF NOT EXISTS namespace_uuid ON Device(namespace, uuid)
;

CREATE INDEX IF NOT EXISTS ecus_hardware_identifier ON ecus(hardware_identifier)
;

