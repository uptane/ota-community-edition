create table `repo_namespaces` (
  `namespace` VARCHAR(254) NOT NULL,
  `repo_id` char(36) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  primary key(`namespace`)
)
;
