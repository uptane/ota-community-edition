
CREATE TABLE `delegated_items` (
  `repo_id` char(36) COLLATE utf8_unicode_ci NOT NULL,
  `filename` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `rolename` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `checksum` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `length` bigint(20) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `custom` text COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`repo_id`,`rolename`, `filename`),
  CONSTRAINT delegated_item_delegations_fk FOREIGN KEY (repo_id, rolename) REFERENCES delegations (repo_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
;

create view aggregated_items AS
              SELECT 'targets.json' origin,
                  JSON_UNQUOTE(JSON_EXTRACT(custom, '$.name')) name,
                  JSON_UNQUOTE(JSON_EXTRACT(custom, '$.version')) version,
                  filename,
                  checksum,
                  length,
                  uri,
                  IFNULL(JSON_EXTRACT(custom, '$.hardwareIds'),'[]') hardwareids,
                  created_at,
                  repo_id,
                  custom
              from target_items
              UNION
              select JSON_UNQUOTE(rolename) origin,
                  JSON_UNQUOTE(JSON_EXTRACT(custom, '$.name')) name,
                  JSON_UNQUOTE(JSON_EXTRACT(custom, '$.version')) version,
                  filename,
                  checksum,
                  length,
                  NULL uri,
                  IFNULL(JSON_EXTRACT(custom, '$.hardwareIds'),'[]') hardwareids,
                  created_at,
                  repo_id,
                  custom
              FROM delegated_items
;