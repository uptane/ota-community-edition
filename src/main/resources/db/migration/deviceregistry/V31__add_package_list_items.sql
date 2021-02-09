CREATE TABLE PackageListItem (
  namespace VARCHAR(255) NOT NULL,
  package_name VARCHAR(200) NOT NULL,
  package_version VARCHAR(200) NOT NULL,
  `comment` TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (namespace, package_name, package_version)
);
