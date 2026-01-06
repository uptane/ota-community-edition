alter table Device
ADD `mqtt_status` ENUM ('NotSeen', 'Online', 'Offline') DEFAULT 'NotSeen',
ADD `mqtt_last_seen` datetime(3) DEFAULT NULL
;

-- remove default
alter table Device
MODIFY `mqtt_status` ENUM ('NotSeen', 'Online', 'Offline') NOT NULL
;
