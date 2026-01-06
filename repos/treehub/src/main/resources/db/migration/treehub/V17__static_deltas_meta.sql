CREATE TABLE `static_deltas` (
  `namespace` varchar(254) NOT NULL,
  `id` char(87) NOT NULL,
  `to` char(64) NOT NULL,
  `size` bigint(20) NOT NULL,
  `superblock_hash` char(64) NOT NULL,
  `status` ENUM('uploading', 'available') NOT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  PRIMARY KEY (`namespace`,`id`)
)
;

CREATE INDEX `idx_static_deltas_namespace_to` on `static_deltas` (`namespace`, `to`)
;
