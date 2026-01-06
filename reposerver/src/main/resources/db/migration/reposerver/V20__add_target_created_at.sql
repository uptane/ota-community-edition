ALTER TABLE delegated_items ADD COLUMN target_created_at datetime(3) NULL;

ALTER view aggregated_items AS
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
          JSON_UNQUOTE(JSON_EXTRACT(custom, '$.uri')) uri,
          IFNULL(JSON_EXTRACT(custom, '$.hardwareIds'),'[]') hardwareids,
          COALESCE(target_created_at, JSON_EXTRACT(custom, '$.created_at'), created_at) created_at,
          repo_id,
          custom
      FROM delegated_items
;
