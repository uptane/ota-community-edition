update updates u join campaigns c on c.update_id = u.update_id set c.update_id = u.uuid
;

insert into updates (uuid, update_id, update_source_type, namespace, name)
  select c.update_id, c.update_id, 'multi_target', c.namespace, c.name from campaigns c
  where c.update_id not in (select uuid from updates)
  group by c.update_id ;

ALTER TABLE `campaigns` ADD CONSTRAINT `update_fk` FOREIGN KEY (`update_id`) REFERENCES `updates`(`uuid`)
;

ALTER TABLE `campaigns` MODIFY COLUMN `update_id` char(36) NOT NULL
;
