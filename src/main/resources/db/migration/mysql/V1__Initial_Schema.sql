
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `jmalcloud` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `jmalcloud`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `access_token` (
  `id` varchar(24) NOT NULL,
  `access_token` varchar(255) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `last_active_time` datetime(6) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `articles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(255) NOT NULL,
  `alone_page` bit(1) DEFAULT NULL,
  `category_ids` json DEFAULT NULL,
  `cover` varchar(255) DEFAULT NULL,
  `has_draft` bit(1) DEFAULT NULL,
  `page_sort` int DEFAULT NULL,
  `is_release` bit(1) DEFAULT NULL,
  `slug` varchar(255) DEFAULT NULL,
  `tag_ids` json DEFAULT NULL,
  `file_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqsi7ot2lswfqp9a772eqclp83` (`public_id`),
  UNIQUE KEY `UKrmuoi47xf7daot59dh80jcst0` (`file_id`),
  KEY `articles_slug` (`slug`),
  CONSTRAINT `fk_article_to_file` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` varchar(24) NOT NULL,
  `category_background` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_default` bit(1) DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `parent_category_id` varchar(24) DEFAULT NULL,
  `slug` varchar(24) DEFAULT NULL,
  `user_id` varchar(24) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consumers` (
  `id` varchar(24) NOT NULL,
  `created_time` datetime(6) DEFAULT NULL,
  `updated_time` datetime(6) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `creator` bit(1) DEFAULT NULL,
  `introduction` varchar(255) DEFAULT NULL,
  `mfa_enabled` bit(1) DEFAULT NULL,
  `mfa_secret` text,
  `quota` int DEFAULT NULL,
  `roles` json DEFAULT NULL,
  `show_name` varchar(255) DEFAULT NULL,
  `slogan` text,
  `take_up_space` bigint DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `webp_disabled` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `direct_links` (
  `id` varchar(24) NOT NULL,
  `downloads` bigint DEFAULT NULL,
  `file_id` varchar(24) DEFAULT NULL,
  `mark` varchar(16) DEFAULT NULL,
  `update_date` datetime(6) DEFAULT NULL,
  `user_id` varchar(24) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `direct_links_file_id` (`file_id`),
  KEY `direct_links_mark` (`mark`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_history` (
  `id` varchar(24) NOT NULL,
  `charset` varchar(255) DEFAULT NULL,
  `compression` varchar(255) DEFAULT NULL,
  `file_id` varchar(255) DEFAULT NULL,
  `filename` varchar(255) DEFAULT NULL,
  `filepath` varchar(255) DEFAULT NULL,
  `operator` varchar(255) DEFAULT NULL,
  `size` bigint DEFAULT NULL,
  `time` varchar(255) DEFAULT NULL,
  `upload_date` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `file_history_links_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_props` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(255) NOT NULL,
  `props` json DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `share_base` bit(1) DEFAULT NULL,
  `share_id` varchar(24) DEFAULT NULL,
  `share_props` json DEFAULT NULL,
  `sub_share` bit(1) DEFAULT NULL,
  `tags` json DEFAULT NULL,
  `transcode_video` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmx5017y50962eteed1gh36tg6` (`public_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3597 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `files` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(255) NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `del_tag` int DEFAULT NULL,
  `etag` varchar(64) DEFAULT NULL,
  `etag_update_failed_attempts` int DEFAULT NULL,
  `has_content` bit(1) NOT NULL,
  `has_content_text` bit(1) NOT NULL,
  `has_html` bit(1) NOT NULL,
  `is_favorite` bit(1) DEFAULT NULL,
  `is_folder` bit(1) DEFAULT NULL,
  `last_etag_update_error` text,
  `last_etag_update_request_at` datetime(6) DEFAULT NULL,
  `lucene_index` int DEFAULT NULL,
  `md5` varchar(255) DEFAULT NULL,
  `mount_file_id` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `needs_etag_update` bit(1) DEFAULT NULL,
  `oss_folder` varchar(255) DEFAULT NULL,
  `path` varchar(255) NOT NULL,
  `size` bigint DEFAULT NULL,
  `suffix` varchar(32) DEFAULT NULL,
  `update_date` datetime(6) NOT NULL,
  `upload_date` datetime(6) NOT NULL,
  `user_id` varchar(24) NOT NULL,
  `props_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfrgl03fht0c5577i2lvdee1gt` (`public_id`),
  UNIQUE KEY `UK2ubuyws5t6qpiatqgmjlwa0wg` (`props_id`),
  KEY `files_name` (`name`),
  KEY `files_size` (`size`),
  KEY `files_is_folder` (`is_folder`),
  KEY `files_update_date` (`update_date`),
  KEY `files_upload_date` (`upload_date`),
  KEY `files_user_id` (`user_id`),
  KEY `files_path` (`path`),
  KEY `files_mount_file_id` (`mount_file_id`),
  KEY `files_del_tag` (`del_tag`),
  CONSTRAINT `FKrmivpiq5s6236tk73w1er54qp` FOREIGN KEY (`props_id`) REFERENCES `file_props` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3597 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `heartwings` (
  `id` varchar(24) NOT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `creator` varchar(255) DEFAULT NULL,
  `heartwings` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ldap_config` (
  `id` varchar(24) NOT NULL,
  `basedn` varchar(255) DEFAULT NULL,
  `roles` json DEFAULT NULL,
  `enable` bit(1) DEFAULT NULL,
  `ldap_server` varchar(255) DEFAULT NULL,
  `login_name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `userdn` varchar(255) DEFAULT NULL,
  `user_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(255) NOT NULL,
  `browser` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `device_model` varchar(64) DEFAULT NULL,
  `file_user_id` varchar(24) DEFAULT NULL,
  `filepath` varchar(255) DEFAULT NULL,
  `ip` varchar(40) DEFAULT NULL,
  `ip_info` json DEFAULT NULL,
  `method` varchar(10) DEFAULT NULL,
  `operating_system` varchar(64) DEFAULT NULL,
  `operation_fun` varchar(96) DEFAULT NULL,
  `operation_module` varchar(24) DEFAULT NULL,
  `remarks` varchar(255) DEFAULT NULL,
  `show_name` varchar(255) DEFAULT NULL,
  `status` int DEFAULT NULL,
  `time` bigint DEFAULT NULL,
  `type` varchar(16) DEFAULT NULL,
  `url` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK29tt9f4a30nrekmfwankpyrbq` (`public_id`),
  KEY `log_create_time` (`create_time`),
  KEY `log_username` (`username`),
  KEY `log_ip` (`ip`),
  KEY `log_type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=91608 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `menu` (
  `id` varchar(24) NOT NULL,
  `authority` varchar(64) DEFAULT NULL,
  `component` varchar(255) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `hide` bit(1) DEFAULT NULL,
  `icon` varchar(255) DEFAULT NULL,
  `menu_type` int DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `parent_id` varchar(24) DEFAULT NULL,
  `path` varchar(255) DEFAULT NULL,
  `sort_number` int DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ocr_config` (
  `id` varchar(24) NOT NULL,
  `created_time` datetime(6) DEFAULT NULL,
  `updated_time` datetime(6) DEFAULT NULL,
  `enable` bit(1) DEFAULT NULL,
  `max_tasks` int DEFAULT NULL,
  `ocr_engine` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `ocr_config_chk_1` CHECK (((`max_tasks` <= 8) and (`max_tasks` >= 1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `office_config` (
  `id` varchar(24) NOT NULL,
  `created_time` datetime(6) DEFAULT NULL,
  `updated_time` datetime(6) DEFAULT NULL,
  `callback_server` varchar(255) DEFAULT NULL,
  `document_server` varchar(255) DEFAULT NULL,
  `format` json DEFAULT NULL,
  `secret` varchar(255) DEFAULT NULL,
  `token_enabled` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `oss_config` (
  `id` varchar(24) NOT NULL,
  `created_time` datetime(6) DEFAULT NULL,
  `updated_time` datetime(6) DEFAULT NULL,
  `access_key` varchar(255) DEFAULT NULL,
  `bucket` varchar(255) DEFAULT NULL,
  `endpoint` varchar(255) DEFAULT NULL,
  `folder_name` varchar(255) DEFAULT NULL,
  `platform` tinyint DEFAULT NULL,
  `region` varchar(255) DEFAULT NULL,
  `secret_key` varchar(255) DEFAULT NULL,
  `user_id` varchar(24) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `oss_config_chk_1` CHECK ((`platform` between 0 and 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` varchar(24) NOT NULL,
  `code` varchar(32) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `menus` json DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `remarks` varchar(255) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `search_history` (
  `id` varchar(24) NOT NULL,
  `current_directory` varchar(255) DEFAULT NULL,
  `exact_search` bit(1) DEFAULT NULL,
  `folder` varchar(255) DEFAULT NULL,
  `include_file_content` bit(1) DEFAULT NULL,
  `include_file_name` bit(1) DEFAULT NULL,
  `include_tag_name` bit(1) DEFAULT NULL,
  `is_favorite` bit(1) DEFAULT NULL,
  `is_folder` bit(1) DEFAULT NULL,
  `keyword` varchar(255) DEFAULT NULL,
  `modify_end` bigint DEFAULT NULL,
  `modify_start` bigint DEFAULT NULL,
  `search_mount` bit(1) DEFAULT NULL,
  `search_overall` bit(1) DEFAULT NULL,
  `search_time` bigint DEFAULT NULL,
  `size_max` bigint DEFAULT NULL,
  `size_min` bigint DEFAULT NULL,
  `tag_id` varchar(255) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `user_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `search_history_user_id` (`user_id`),
  KEY `search_history_search_time` (`search_time`),
  KEY `search_history_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `share` (
  `id` varchar(24) NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `expire_date` datetime(6) DEFAULT NULL,
  `extraction_code` varchar(8) DEFAULT NULL,
  `father_share_id` varchar(24) DEFAULT NULL,
  `file_id` varchar(255) DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `is_folder` bit(1) DEFAULT NULL,
  `is_privacy` bit(1) DEFAULT NULL,
  `operation_permission_list` json DEFAULT NULL,
  `short_id` varchar(24) DEFAULT NULL,
  `user_id` varchar(24) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `share_short_id` (`short_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tag` (
  `id` varchar(24) NOT NULL,
  `color` varchar(16) DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `slug` varchar(32) DEFAULT NULL,
  `sort` int DEFAULT NULL,
  `tag_background` varchar(255) DEFAULT NULL,
  `user_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transcode_config` (
  `id` varchar(24) NOT NULL,
  `created_time` datetime(6) DEFAULT NULL,
  `updated_time` datetime(6) DEFAULT NULL,
  `bitrate` int DEFAULT NULL,
  `bitrate_cond` int DEFAULT NULL,
  `enable` bit(1) DEFAULT NULL,
  `frame_rate` double DEFAULT NULL,
  `frame_rate_cond` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `height_cond` int DEFAULT NULL,
  `is_re_transcode` bit(1) DEFAULT NULL,
  `max_threads` int DEFAULT NULL,
  `vtt_thumbnail_count` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `transcode_config_chk_1` CHECK (((`bitrate` >= 100) and (`bitrate` <= 1000000))),
  CONSTRAINT `transcode_config_chk_2` CHECK (((`bitrate_cond` <= 1000000) and (`bitrate_cond` >= 100))),
  CONSTRAINT `transcode_config_chk_3` CHECK (((`frame_rate` <= 120) and (`frame_rate` >= 1))),
  CONSTRAINT `transcode_config_chk_4` CHECK (((`frame_rate_cond` <= 120) and (`frame_rate_cond` >= 1))),
  CONSTRAINT `transcode_config_chk_5` CHECK (((`height` >= 100) and (`height` <= 10000))),
  CONSTRAINT `transcode_config_chk_6` CHECK (((`height_cond` >= 100) and (`height_cond` <= 10000))),
  CONSTRAINT `transcode_config_chk_7` CHECK (((`max_threads` <= 8) and (`max_threads` >= 1))),
  CONSTRAINT `transcode_config_chk_8` CHECK (((`vtt_thumbnail_count` >= 1) and (`vtt_thumbnail_count` <= 256)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trash` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(255) NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `has_content` bit(1) DEFAULT NULL,
  `hidden` bit(1) DEFAULT NULL,
  `is_folder` bit(1) DEFAULT NULL,
  `md5` varchar(255) DEFAULT NULL,
  `move` bit(1) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `path` varchar(255) DEFAULT NULL,
  `props` json DEFAULT NULL,
  `size` bigint DEFAULT NULL,
  `suffix` varchar(32) DEFAULT NULL,
  `update_date` datetime(6) DEFAULT NULL,
  `upload_date` datetime(6) DEFAULT NULL,
  `user_id` varchar(24) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm4juey927emhuijjcp7u39qh8` (`public_id`),
  KEY `trash_name` (`name`),
  KEY `trash_update_date` (`update_date`),
  KEY `trash_upload_date` (`upload_date`),
  KEY `trash_trash_user_id` (`user_id`),
  KEY `trash_path` (`path`)
) ENGINE=InnoDB AUTO_INCREMENT=49187 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `website_setting` (
  `id` varchar(24) NOT NULL,
  `alone_pages` json DEFAULT NULL,
  `archive_background` varchar(255) DEFAULT NULL,
  `background_desc_site` varchar(255) DEFAULT NULL,
  `background_site` varchar(255) DEFAULT NULL,
  `background_text_site` varchar(255) DEFAULT NULL,
  `category_background` varchar(255) DEFAULT NULL,
  `copyright` varchar(255) DEFAULT NULL,
  `footer_html` text,
  `iframe` text,
  `mfa_force_enable` bit(1) DEFAULT NULL,
  `netdisk_logo` varchar(255) DEFAULT NULL,
  `netdisk_name` varchar(255) DEFAULT NULL,
  `network_record_number` varchar(255) DEFAULT NULL,
  `network_record_number_str` varchar(255) DEFAULT NULL,
  `operating_buttons` text,
  `record_permission_num` varchar(255) DEFAULT NULL,
  `site_ico` varchar(255) DEFAULT NULL,
  `site_logo` varchar(255) DEFAULT NULL,
  `site_name` varchar(255) DEFAULT NULL,
  `site_url` varchar(255) DEFAULT NULL,
  `tag_background` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

