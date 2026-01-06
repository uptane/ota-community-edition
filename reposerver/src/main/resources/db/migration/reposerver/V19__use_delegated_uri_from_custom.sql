
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
          created_at,
          repo_id,
          custom
      FROM delegated_items
;