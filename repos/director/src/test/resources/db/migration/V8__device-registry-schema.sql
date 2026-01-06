
drop schema if exists ${old-schema}
;
create schema ${old-schema}
;
use ${old-schema}
;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DeletedDevice` (
  `namespace` char(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `device_uuid` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `device_id` varchar(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  PRIMARY KEY (`namespace`,`device_uuid`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Device` (
  `namespace` char(200) COLLATE utf8mb3_bin NOT NULL,
  `uuid` char(36) COLLATE utf8mb3_bin NOT NULL,
  `device_id` varchar(200) COLLATE utf8mb3_bin NOT NULL,
  `device_type` smallint(6) NOT NULL,
  `last_seen` datetime(3) DEFAULT NULL,
  `device_name` varchar(200) COLLATE utf8mb3_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `activated_at` datetime(3) DEFAULT NULL,
  `device_status` enum('NotSeen','Error','UpToDate','UpdatePending','Outdated') COLLATE utf8mb3_bin DEFAULT 'NotSeen',
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hibernated` tinyint(1) NOT NULL,
  PRIMARY KEY (`uuid`),
  UNIQUE KEY `namespace` (`namespace`,`device_name`),
  UNIQUE KEY `namespace_2` (`namespace`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'IGNORE_SPACE,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION' */ ;
 DELIMITER ;;
/*!50003 CREATE TRIGGER device_hibernate_status_update AFTER UPDATE ON Device
       FOR EACH ROW
       INSERT INTO DeviceHibernationStatus (device_uuid, previous_status, new_status) VALUES (NEW.uuid, OLD.hibernated, NEW.hibernated) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DeviceGroup` (
  `id` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `group_name` varchar(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `namespace` char(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `type` enum('static','dynamic') COLLATE utf8mb3_unicode_ci NOT NULL,
  `expression` varchar(255) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `namespace` (`namespace`,`group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DeviceHibernationStatus` (
  `device_uuid` char(36) COLLATE utf8mb3_bin NOT NULL,
  `previous_status` tinyint(1) NOT NULL,
  `new_status` tinyint(1) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  KEY `device_uuid` (`device_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DeviceInstallationResult` (
  `correlation_id` varchar(256) COLLATE utf8mb3_unicode_ci NOT NULL,
  `result_code` varchar(256) COLLATE utf8mb3_unicode_ci NOT NULL,
  `device_uuid` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `received_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `installation_report` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`correlation_id`,`device_uuid`),
  KEY `idx_device_device_uuid` (`device_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DevicePublicCredentials` (
  `device_uuid` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `public_credentials` longblob NOT NULL,
  `type_credentials` enum('PEM','OAuthClientCredentials') COLLATE utf8mb3_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`device_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DeviceSystem` (
  `uuid` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `system_info` longtext COLLATE utf8mb3_unicode_ci DEFAULT '{}',
  `local_ipv4` char(15) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `mac_address` char(17) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `hostname` varchar(255) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DeviceType` (
  `id` smallint(6) NOT NULL,
  `name` varchar(200) COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcuInstallationResult` (
  `correlation_id` varchar(256) COLLATE utf8mb3_unicode_ci NOT NULL,
  `result_code` varchar(256) COLLATE utf8mb3_unicode_ci NOT NULL,
  `device_uuid` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ecu_id` varchar(64) COLLATE utf8mb3_unicode_ci NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`correlation_id`,`device_uuid`,`ecu_id`),
  CONSTRAINT `fk_ecu_report_device_report` FOREIGN KEY (`correlation_id`, `device_uuid`) REFERENCES `DeviceInstallationResult` (`correlation_id`, `device_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcuReplacement` (
  `device_uuid` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `former_ecu_id` char(64) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `former_hardware_id` varchar(200) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `current_ecu_id` char(64) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `current_hardware_id` varchar(200) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `replaced_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `success` tinyint(1) NOT NULL,
  KEY `fk_ecu_replacement_device` (`device_uuid`),
  CONSTRAINT `fk_ecu_replacement_device` FOREIGN KEY (`device_uuid`) REFERENCES `Device` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EventJournal` (
  `device_uuid` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `event_id` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `device_time` datetime(3) NOT NULL,
  `event_type_id` varchar(100) COLLATE utf8mb3_unicode_ci NOT NULL,
  `event_type_version` tinyint(3) unsigned NOT NULL,
  `event` longblob NOT NULL,
  `received_at` datetime NOT NULL,
  PRIMARY KEY (`device_uuid`,`event_id`),
  CONSTRAINT `fk_event_device` FOREIGN KEY (`device_uuid`) REFERENCES `Device` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GroupMembers` (
  `device_uuid` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `group_id` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  PRIMARY KEY (`group_id`,`device_uuid`),
  CONSTRAINT `GroupMembers_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `DeviceGroup` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `IndexedEvents` (
  `device_uuid` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `event_id` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `correlation_id` varchar(256) COLLATE utf8mb3_unicode_ci NOT NULL,
  `event_type` varchar(256) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`device_uuid`,`event_id`),
  KEY `correlation_id` (`correlation_id`),
  CONSTRAINT `fk_indexed_event` FOREIGN KEY (`device_uuid`, `event_id`) REFERENCES `EventJournal` (`device_uuid`, `event_id`),
  CONSTRAINT `fk_indexed_event_device` FOREIGN KEY (`device_uuid`) REFERENCES `Device` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `IndexedEventsArchive` (
  `device_uuid` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `event_id` char(36) COLLATE utf8mb3_unicode_ci NOT NULL,
  `correlation_id` varchar(256) COLLATE utf8mb3_unicode_ci NOT NULL,
  `event_type` varchar(256) COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`device_uuid`,`event_id`),
  KEY `correlation_id` (`correlation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `InstalledPackage` (
  `device_uuid` char(64) COLLATE utf8mb3_unicode_ci NOT NULL,
  `name` varchar(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `version` varchar(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `last_modified` datetime NOT NULL,
  PRIMARY KEY (`device_uuid`,`name`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `PackageListItem` (
  `namespace` varchar(255) COLLATE utf8mb3_unicode_ci NOT NULL,
  `package_name` varchar(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `package_version` varchar(200) COLLATE utf8mb3_unicode_ci NOT NULL,
  `comment` text COLLATE utf8mb3_unicode_ci NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`namespace`,`package_name`,`package_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TaggedDevice` (
  `namespace` varchar(255) COLLATE utf8mb3_bin NOT NULL,
  `device_uuid` char(36) COLLATE utf8mb3_bin NOT NULL,
  `tag_id` varchar(50) COLLATE utf8mb3_bin NOT NULL,
  `tag_value` varchar(50) COLLATE utf8mb3_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  PRIMARY KEY (`device_uuid`,`tag_id`),
  KEY `tag_id` (`tag_id`),
  CONSTRAINT `fk_device_uuid` FOREIGN KEY (`device_uuid`) REFERENCES `Device` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `schema_version` (
  `installed_rank` int(11) NOT NULL,
  `version` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb3_bin NOT NULL,
  `type` varchar(20) COLLATE utf8mb3_bin NOT NULL,
  `script` varchar(1000) COLLATE utf8mb3_bin NOT NULL,
  `checksum` int(11) DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb3_bin NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT current_timestamp(),
  `execution_time` int(11) NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `schema_version_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;
