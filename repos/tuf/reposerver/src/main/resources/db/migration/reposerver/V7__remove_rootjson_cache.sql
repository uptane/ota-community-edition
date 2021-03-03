

create table signed_root_json_delete as
select * from signed_roles where role_type = 'ROOT'
;

delete from signed_roles where role_type = 'ROOT'
;

alter table signed_roles modify role_type ENUM('SNAPSHOT','TARGETS','TIMESTAMP')
;
