package com.advancedtelematic.campaigner.db

import java.sql.SQLIntegrityConstraintViolationException

object Errors {

  object UpdateFKViolation {
    def unapply(e: SQLIntegrityConstraintViolationException): Boolean =
      e.getErrorCode == 1452 && e.getMessage.contains("update_fk")
  }

  object CampaignFKViolation {
    def unapply(e: SQLIntegrityConstraintViolationException): Boolean =
      e.getErrorCode == 1452 && e.getMessage.contains("fk_parent_campaign_uuid")
  }

}
