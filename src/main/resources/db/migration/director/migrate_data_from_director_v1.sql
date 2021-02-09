-- SET @@sql_mode = CONCAT(@@sql_mode, ',', 'ONLY_FULL_GROUP_BY');
-- SET @@sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''));

SET collation_connection = 'utf8_unicode_ci';

-- move this to old director code
create index if not exists ecu_serial_idx on director.ecus (`ecu_serial`);
create index if not exists ecu_update_assignments_target_v1_idx on director.ecu_update_assignments (`filepath`(500), `length`(254), checksum);
create index if not exists device_update_assignments_correlation_id_v1 on director.device_update_assignments (correlation_id);
create index if not exists file_cache_device_version_role on director.file_cache (`device`, `role`, `version`) ;
create index if not exists file_cache_device_created_at on director.file_cache(`device`,`created_at`);
create index if not exists file_cache_device_version on director.file_cache(`device`,`version`);

-- repo_namespaces
INSERT director_v2.repo_namespaces
SELECT * FROM director.repo_names v1
ON DUPLICATE KEY UPDATE repo_id = v1.repo_id, created_at = v1.created_at, updated_at = v1.updated_at;

-- select count(*) from director.repo_names ;

DROP TABLE IF EXISTS ecu_targets_v1;
create table ecu_targets_v1 AS
select namespace, max(id) id, filepath filename, length, max(checksum) checksum, hash sha256, uri, max(created_at) created_at, max(updated_at) updated_at from (
select namespace, uuid() id, filepath, length, checksum, json_unquote(json_extract(checksum, '$.hash')) hash, NULL uri, created_at, updated_at
from director.current_images c
UNION
select namespace, uuid(), target filepath, target_size length, JSON_OBJECT("method", "sha256", "hash", target_hash), target_hash, target_uri uri, created_at, updated_at
from director.multi_target_updates mtu
UNION
select namespace, uuid(), from_target filepath, from_target_size length, JSON_OBJECT("method", "sha256", "hash", from_target_hash), from_target_hash, from_target_uri uri, created_at, updated_at
from director.multi_target_updates mtu
where
 from_target is not null AND from_target_hash is not null
UNION
select namespace, uuid(), filepath, `length`, checksum, json_unquote(json_extract(checksum, '$.hash')) hash, uri, created_at, updated_at
from director.ecu_update_assignments eua
) i group by namespace, filename, hash, length
;

create unique index if not exists ecu_targets_v1_uniq_idx on ecu_targets_v1 (namespace, `filename`(500), sha256, length(70));
create unique index if not exists ecu_targets_v1_id_uniq_idx on ecu_targets_v1 (id);

update ecu_targets v2 join ecu_targets_v1 v1 using (namespace, filename, sha256, length) SET
   v1.id = v2.id,
   v2.uri = v1.uri,
   ;

-- ecu_targets
INSERT INTO ecu_targets (namespace, id, filename, length, checksum, sha256, uri, created_at, updated_at)
select namespace, id, filename, length, checksum, sha256, uri, created_at, updated_at from ecu_targets_v1
WHERE (namespace, filename, sha256, length) NOT IN (select namespace, filename, sha256, length from ecu_targets)
;

-- select count(*) FROM (SELECT * from (
-- select namespace, filepath, json_unquote(json_extract(checksum, '$.hash')) checksum, length from director.current_images
-- UNION
-- select namespace, target, target_hash, target_size from director.multi_target_updates
-- UNION
-- select namespace, from_target, from_target_hash, from_target_size from director.multi_target_updates  WHERE from_target is not null and from_target_hash is not null
-- UNION
-- select namespace, filepath, json_unquote(json_extract(checksum, '$.hash')) hash, `length` from director.ecu_update_assignments eua
-- ) _t1 GROUP by 1, 2, 3, 4) _t2
-- ;

-- ecus
insert into director_v2.ecus (namespace, ecu_serial, device_id, public_key, hardware_identifier, current_target, created_at, updated_at)
select e.namespace, e.ecu_serial, device, public_key, hardware_identifier, et.id, e.created_at, e.updated_at
FROM director.ecus e
LEFT JOIN (select c.ecu_serial, t.id, t.namespace, t.filename, t.checksum FROM director.current_images c JOIN ecu_targets_v1 t ON t.namespace = c.namespace AND t.filename = c.filepath AND t.checksum = c.checksum AND t.length = c.length) et
on et.ecu_serial = e.ecu_serial AND et.namespace = e.namespace
on duplicate key update
  device_id = e.device,
  public_key = e.public_key,
  hardware_identifier = e.hardware_identifier,
  current_target = et.id,
  created_at = e.created_at, updated_at = e.updated_at
;

-- select count(*) from director.ecus;
-- select count(*), ci.ecu_serial is null from director.ecus e left join director.current_images ci USING (namespace, ecu_serial) group by 2;
-- select count(*) from ecus where current_target is null;

-- devices
insert into director_v2.devices (namespace, id, primary_ecu_id, generated_metadata_outdated, created_at, updated_at)
select namespace, device, ecu_serial, 0, created_at, updated_at
from director.ecus e
where e.primary = 1
on duplicate
key update primary_ecu_id = e.ecu_serial, created_at = e.created_at, updated_at = e.updated_at;

-- select count(*) from director.ecus where `primary` = 1 ;

INSERT into director_v2.hardware_updates (namespace, id, hardware_identifier, to_target_id, from_target_id, target_format, created_at, updated_at)
select to_mtu.namespace, to_mtu.id, to_mtu.hardware_identifier, to_mtu.target_id, from_mtu.target_id, to_mtu.target_format, to_mtu.created_at, to_mtu.updated_at
FROM
(select mtu.namespace, mtu.id, mtu.hardware_identifier, et.id target_id, target_format, mtu.created_at, mtu.updated_at
  from director.multi_target_updates mtu JOIN director_v2.ecu_targets_v1 et ON mtu.namespace = et.namespace AND mtu.target = et.filename AND mtu.target_hash = et.sha256 AND mtu.target_size = et.length) to_mtu
LEFT JOIN
(select mtu.namespace, mtu.id, mtu.hardware_identifier, et.id target_id from director.multi_target_updates mtu JOIN director_v2.ecu_targets_v1 et ON mtu.namespace = et.namespace AND mtu.from_target = et.filename AND mtu.from_target_hash = et.sha256 AND mtu.from_target_size = et.length) from_mtu
ON to_mtu.namespace = from_mtu.namespace AND to_mtu.id = from_mtu.id AND to_mtu.hardware_identifier = from_mtu.hardware_identifier
ON duplicate key update target_format = VALUES(target_format), updated_at = VALUES(updated_at)
;

-- select count(*), from_target is null  from director.multi_target_updates group by 2;
-- select count(*), from_target_id is null from hardware_updates group by 2;

DROP TABLE IF EXISTS assignments_v1;
create table assignments_v1 AS
select
  eua.namespace, eua.device_id, eua.ecu_id ecu_serial, et.id ecu_target_id, dua.correlation_id, dua.served in_flight, eua.created_at, eua.updated_at,
   (ranked_eua.rank = 1 AND (dct.device_current_target is NULL OR dct.device_current_target < eua.version)) running, ranked_eua.rank version_rank, dct.device_current_target
FROM director.ecu_update_assignments eua
JOIN
  (select namespace, ecu_id, version, device_id, ROW_NUMBER() OVER (PARTITION BY device_id ORDER BY version DESC) rank from director.ecu_update_assignments) ranked_eua
  USING (namespace, ecu_id, device_id, version)
JOIN ecu_targets_v1 et ON et.namespace = eua.namespace and et.filename = eua.filepath and et.length = eua.length and et.sha256 = json_unquote(json_extract(eua.checksum, '$.hash'))
LEFT JOIN director.device_current_target dct ON dct.device = eua.device_id
JOIN director.device_update_assignments dua ON dua.namespace = eua.namespace AND dua.version = eua.version AND dua.device_id = eua.device_id
;

create index if not exists assignments_v1_device_id_ecu_serial_idx on assignments_v1 (device_id, ecu_serial);

-- WARNING: This makes the migration idempotent, however it might delete important data from director v2
-- Before running this make sure this is what you want to do
SELECT count(*) FROM director_v2.assignments a JOIN assignments_v1 USING (device_id, ecu_serial)
;
DELETE FROM director_v2.assignments where (device_id, ecu_serial) in (select device_id, ecu_serial from assignments_v1)
;

-- RUNNING assignments
insert into director_v2.assignments (namespace, device_id, ecu_serial, ecu_target_id, correlation_id, in_flight, created_at, updated_at)
select namespace, device_id, ecu_serial, ecu_target_id, correlation_id, in_flight, created_at, updated_at
FROM assignments_v1
WHERE running = 1
;

-- PROCESSED Assignments
insert into director_v2.processed_assignments (namespace, device_id, ecu_serial, ecu_target_id, correlation_id, successful, canceled, created_at, updated_at)
select namespace, device_id, ecu_serial, ecu_target_id, coalesce(correlation_id, 'urn:here-ota:mtu:00000000-0000-0000-0000-000000000000'), 1, 0, created_at, updated_at
FROM assignments_v1
WHERE running = 0
;

-- control counts
select count(*) from assignments;
select count(*) from processed_assignments;

-- select count(*), ranked_eua.rank = 1 AND (dct.device_current_target is NULL OR dct.device_current_target < eua.version) running FROM
--   director.ecu_update_assignments eua
-- JOIN
--   (select namespace, ecu_id, version, device_id, ROW_NUMBER() OVER (PARTITION BY device_id ORDER BY version DESC) rank from director.ecu_update_assignments) ranked_eua
-- USING (namespace, ecu_id, device_id, version)
-- LEFT JOIN director.device_current_target dct ON dct.device = eua.device_id
-- GROUP BY running
-- ;
-- select count(distinct correlation_id) from director.ecu_update_assignments eua join director.device_update_assignments dua USING (namespace, device_id, version) ;
-- select count(distinct correlation_id) from (select correlation_id from assignments a UNION select correlation_id from processed_assignments p) _t;a

insert into director_v2.auto_update_definitions (id, namespace, device_id, ecu_serial, target_name, deleted, created_at)
select uuid() uuid, namespace, device, ecu_serial, target_name, 0, '1970-01-01 00:00:00'
FROM director.auto_updates
on duplicate key update device_id = device
;

-- check how many key violations are there
select count(*) signed_roles_duplicates from director.file_cache fc join signed_roles sr on fc.device = sr.device_id
and fc.role = sr.role and fc.version = sr.version where fc.expires <> sr.expires_at ;

-- select count(*) from director.auto_updates ;

-- depending on above query, use `on duplicate key` or `where` to make this idempotent
-- on duplicate key update checksum=null,`length`= null, content = file_entity, created_at=fc.created_at, updated_at=fc.updated_at,expires_at=expires
-- where (device, role, version) not in (select device_id, role, version from signed_roles)
insert into director_v2.signed_roles (role, version, device_id, content, created_at, updated_at, expires_at, checksum, `length`)
select role, version, device, file_entity, created_at, updated_at, expires, NULL, NULL from director.file_cache fc
on duplicate key update checksum=null,`length`= null, content = file_entity, created_at=fc.created_at, updated_at=fc.updated_at,expires_at=expires
;

-- select count(*) from director.file_cache ;
