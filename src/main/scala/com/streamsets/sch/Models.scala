package com.streamsets.sch

import org.apache.commons.codec.digest.DigestUtils

import java.util.UUID
import scala.util.Random

case class SSOPrincipal(
                       token: String,
                       expiresIn: Long,
                       principalId: String,
                       principalName: String,
                       organisationId: String,
                       organisationName: String,
                       principalEmail: String,
                       roles: Set[ String ],
                       groups: Set[ String ],
                       attributes: Map[ String, String],
                       isApp: Boolean,
                       issuerURL: Option[ String ],
                       requesterIP: Option[ String ]
                       )

object SSOPrincipal{
  def makeToken(expiry: Long): String = {
    val instanceId = Random.alphanumeric.take(36).mkString
    val id = UUID.randomUUID().toString
    s"$instanceId|$id|$expiry"
  }

  def toSessionHashID(token: String): String = {
    DigestUtils.sha256Hex(token)
  }

  def newPrincipal( expiresIn: Long ): SSOPrincipal = {
    SSOPrincipal(
      token = makeToken( System.currentTimeMillis() + expiresIn ),
      expiresIn = expiresIn,
      principalId = Random.alphanumeric.take(36).mkString,
      principalName = Random.alphanumeric.take(20).mkString,
      organisationId = Random.alphanumeric.take(36).mkString,
      organisationName = Random.alphanumeric.take(20).mkString,
      principalEmail = Random.alphanumeric.take(20).mkString + "@" + "streamsets.com",
      roles = Set( "roles_1", "roles_2", "roles_3"),
      groups = Set( "group_1", "group_2", "group_3"),
      attributes = Map( "attr_1" -> "attr_1", "attr_2" -> "attr_2", "attr_3" -> "attr_3"),
      isApp = false,
      issuerURL = Some("www.streamsets.com"),
      requesterIP = Some("1.1.1.1")
    )
  }
  
}
