DROP TRIGGER ${old-schema}.device_hibernate_status_update
;

DROP VIEW IF EXISTS Device
;
ALTER TABLE ${old-schema}.Device RENAME TO Device
;

DROP VIEW IF EXISTS DeviceHibernationStatus
;
ALTER TABLE ${old-schema}.DeviceHibernationStatus RENAME TO DeviceHibernationStatus
;

DROP VIEW IF EXISTS DeviceInstallationResult
;
ALTER TABLE ${old-schema}.DeviceInstallationResult RENAME TO DeviceInstallationResult
;

DROP VIEW IF EXISTS EcuInstallationResult
;
ALTER TABLE ${old-schema}.EcuInstallationResult RENAME TO EcuInstallationResult
;

DROP VIEW IF EXISTS DeviceGroup
;
ALTER TABLE ${old-schema}.DeviceGroup RENAME TO DeviceGroup
;

DROP VIEW IF EXISTS DevicePublicCredentials
;
ALTER TABLE ${old-schema}.DevicePublicCredentials RENAME TO DevicePublicCredentials
;

DROP VIEW IF EXISTS DeviceSystem
;
ALTER TABLE ${old-schema}.DeviceSystem RENAME TO DeviceSystem
;

DROP VIEW IF EXISTS DeviceType
;
ALTER TABLE ${old-schema}.DeviceType RENAME TO DeviceType
;

DROP VIEW IF EXISTS EcuReplacement
;
ALTER TABLE ${old-schema}.EcuReplacement RENAME TO EcuReplacement
;

DROP VIEW IF EXISTS EventJournal
;
ALTER TABLE ${old-schema}.EventJournal RENAME TO EventJournal
;

DROP VIEW IF EXISTS GroupMembers
;
ALTER TABLE ${old-schema}.GroupMembers RENAME TO GroupMembers
;

DROP VIEW IF EXISTS IndexedEvents
;
ALTER TABLE ${old-schema}.IndexedEvents RENAME TO IndexedEvents
;

DROP VIEW IF EXISTS IndexedEventsArchive
;
ALTER TABLE ${old-schema}.IndexedEventsArchive RENAME TO IndexedEventsArchive
;

DROP VIEW IF EXISTS InstalledPackage
;
ALTER TABLE ${old-schema}.InstalledPackage RENAME TO InstalledPackage
;

DROP VIEW IF EXISTS PackageListItem
;
ALTER TABLE ${old-schema}.PackageListItem RENAME TO PackageListItem
;

DROP VIEW IF EXISTS TaggedDevice
;
ALTER TABLE ${old-schema}.TaggedDevice RENAME TO TaggedDevice
;

DROP VIEW IF EXISTS DeletedDevice
;
ALTER TABLE ${old-schema}.DeletedDevice RENAME TO DeletedDevice
;

-- utils service still needs these views
CREATE VIEW IF NOT EXISTS ${old-schema}.DeviceGroup AS
SELECT * FROM DeviceGroup
;

CREATE VIEW IF NOT EXISTS ${old-schema}.GroupMembers AS
SELECT * FROM GroupMembers
;

CREATE TRIGGER device_hibernate_status_update AFTER UPDATE ON Device
       FOR EACH ROW
       INSERT INTO DeviceHibernationStatus (device_uuid, previous_status, new_status) VALUES (NEW.uuid, OLD.hibernated, NEW.hibernated)
;
