alter table `keys`
add COLUMN `repo_id` char(36) NULL after key_id,
add COLUMN `role_type` ENUM('ROOT', 'SNAPSHOT', 'TARGETS', 'TIMESTAMP') NULL after repo_id
;

update `keys` k  Join `roles` r on k.role_id = r.role_id
SET k.repo_id = r.repo_id, k.role_type = r.role_type
;

ALTER TABLE `keys` DROP FOREIGN KEY keys_ibfk_1;

alter table `keys`
MODIFY COLUMN `repo_id` char(36) NOT NULL,
MODIFY COLUMN `role_type` ENUM('ROOT', 'SNAPSHOT', 'TARGETS', 'TIMESTAMP') NOT NULL
;

create index idx_keys_repo_id_role_type on `keys`(repo_id, role_type)
;

create table roles_keys_deleted as
select key_id, role_id from `keys`
;

alter table `keys` DROP COLUMN `role_id`
;

alter table `roles` rename to `roles_deleted`
;
