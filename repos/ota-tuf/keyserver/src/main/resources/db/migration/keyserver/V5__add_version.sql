alter table `root_role_cache` rename to `signed_root_roles`
;

alter table signed_root_roles add `version` INT NOT NULL DEFAULT 1
;

alter table signed_root_roles modify `version` INT NOT NULL
;

