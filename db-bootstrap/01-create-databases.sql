set global max_connections=0;

create database if not exists campaigner;
create user if not exists 'campaigner'@'%' identified by 'campaigner';
grant all privileges on `campaigner%`.* to 'campaigner'@'%';

create database if not exists device_registry;
create user if not exists 'device_registry'@'%' identified by 'device_registry';
grant all privileges on `device_registry%`.* to 'device_registry'@'%';

-- create database if not exists director;
-- create user if not exists 'director'@'%' identified by 'director';
-- grant all privileges on `director%`.* to 'director'@'%';

create database if not exists director_v2;
create user if not exists 'director_v2'@'%' identified by 'director_v2';
grant all privileges on `director%`.* to 'director_v2'@'%';

create database if not exists sota_core;
create user if not exists 'sota_core'@'%' identified by 'sota_core';
grant all privileges on `sota_core%`.* to 'sota_core'@'%';

create database if not exists ota_treehub;
create user if not exists 'treehub'@'%' identified by 'treehub';
grant all privileges on `ota_treehub%`.* to 'treehub'@'%';

create database if not exists tuf_keyserver;
create user if not exists 'tuf_keyserver'@'%' identified by 'tuf_keyserver';
grant all privileges on `tuf_keyserver%`.* to 'tuf_keyserver'@'%';

create database if not exists tuf_repo;
create user if not exists 'tuf_repo'@'%' identified by 'tuf_repo';
grant all privileges on `tuf_repo%`.* to 'tuf_repo'@'%';

flush privileges;
