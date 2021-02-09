drop database if exists director1_test;
create database director1_test default character set utf8 default collate utf8_bin;

CREATE TABLE director1_test.file_cache (
          `role` enum('ROOT','SNAPSHOT','TARGETS','TIMESTAMP') COLLATE utf8_unicode_ci NOT NULL,
          `version` int(11) NOT NULL,
          `device` char(36) COLLATE utf8_unicode_ci NOT NULL,
          `file_entity` longtext COLLATE utf8_unicode_ci NOT NULL,
          `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
          `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
          `expires` datetime(3) NOT NULL,
          PRIMARY KEY (`role`,`version`,`device`));

insert into director1_test.file_cache values ('SNAPSHOT', 0, '00000095-1454-40a5-b54e-caeb117f7aab',
    '{"signatures":[{"keyid":"be7b4b5b36f1e8ab0e5e211d3f781bdfd7eeafd5351942b239090aeccf0e65cc","method":"rsassa-pss-sha256","sig":"Y1+Oqx/ORmO/Ru384w4DoP2GDkdPv92h12yyDAaQjknIFtVV+1MRacVdG6axJYsG4nffcWqf7yWN6HJWfflIwxfhaK6mdLXU621jed+vQb7pdsdrzdVaKaN0mcxj1ICRhzsErfBI3HIOUX1E4FhYtB7fKURqXjWtqKgYSXA7X+Hr+RM+bPaIuzvWg5jh8vrqB8EnCQNXoXiy9Rsuk5jtU21JgyXiQOTfsEAxy6D8XJ8ebgXW1q0izpLNJqOuMQk/1mkvjfzY9+qBgYuKdUTLjcglMpxzRjfJIx8C2OUxG6C8WrqbS9lNkBKbEeVpcmw5AOPT24PELww7ndM3+6JnkA=="}],"signed":{"_type":"Snapshot","meta":{"targets.json":{"hashes":{"sha256":"11f31e74802087786cc44ff2b320a6c6d2172e952afae7b45d00fcc0a62919ae"},"length":562,"version":0}},"expires":"2019-12-24T03:15:58Z","version":0}}',
    '2019-11-23 03:15:58.687', '2019-11-23 03:15:58.687', '2019-12-24 03:15:58.542');
