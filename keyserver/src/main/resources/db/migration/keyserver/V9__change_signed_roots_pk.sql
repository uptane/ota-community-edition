alter table signed_root_roles
 drop primary key, add primary key(repo_id, version);
