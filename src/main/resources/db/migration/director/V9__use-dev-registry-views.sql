CREATE VIEW Device AS SELECT * FROM ${old-schema}.Device
;

CREATE VIEW DeviceHibernationStatus AS SELECT * FROM ${old-schema}.DeviceHibernationStatus
;

CREATE VIEW DeviceInstallationResult AS SELECT * FROM ${old-schema}.DeviceInstallationResult
;

CREATE VIEW EcuInstallationResult AS SELECT * FROM ${old-schema}.EcuInstallationResult
;

CREATE VIEW DeviceGroup AS SELECT * FROM ${old-schema}.DeviceGroup
;

CREATE VIEW DevicePublicCredentials AS SELECT * FROM ${old-schema}.DevicePublicCredentials
;

CREATE VIEW DeviceSystem AS SELECT * FROM ${old-schema}.DeviceSystem
;

CREATE VIEW DeviceType AS SELECT * FROM ${old-schema}.DeviceType
;

CREATE VIEW EcuReplacement AS SELECT * FROM ${old-schema}.EcuReplacement
;

CREATE VIEW EventJournal AS SELECT * FROM ${old-schema}.EventJournal
;

CREATE VIEW GroupMembers AS SELECT * FROM ${old-schema}.GroupMembers
;

CREATE VIEW IndexedEvents AS SELECT * FROM ${old-schema}.IndexedEvents
;

CREATE VIEW IndexedEventsArchive AS SELECT * FROM ${old-schema}.IndexedEventsArchive
;

CREATE VIEW InstalledPackage AS SELECT * FROM ${old-schema}.InstalledPackage
;

CREATE VIEW PackageListItem AS SELECT * FROM ${old-schema}.PackageListItem
;

CREATE VIEW TaggedDevice AS SELECT * FROM ${old-schema}.TaggedDevice
;

CREATE VIEW DeletedDevice AS SELECT * FROM ${old-schema}.DeletedDevice
;
