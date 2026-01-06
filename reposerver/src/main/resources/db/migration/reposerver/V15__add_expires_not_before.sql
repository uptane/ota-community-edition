ALTER TABLE repo_namespaces ADD COLUMN `expires_not_before` DATETIME(3) NULL AFTER repo_id
;

CREATE UNIQUE INDEX repo_namespaces_repo_id
ON repo_namespaces(repo_id)
;
