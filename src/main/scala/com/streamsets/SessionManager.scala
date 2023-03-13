package com.streamsets

import org.apache.commons.codec.digest.DigestUtils

import java.util
import java.util.{Collections, UUID}
import java.util.concurrent.ConcurrentHashMap
import scala.util.Random

trait SessionManager {

  def makeToken( expiry: Long ): String = {
    val instanceId = Random.alphanumeric.take(36).mkString
    val id = UUID.randomUUID().toString
    s"$instanceId|$id|$expiry"
  }

  def toSessionHashID( token: String ): String = {
    DigestUtils.sha256Hex(token)
  }
  def newPrincipal( expiresIn: Long ): SSOPrincipal = {
    val principal = new SSOPrincipalJson
    principal.setTokenStr(  makeToken( System.currentTimeMillis() + expiresIn ) )
    principal.setPrincipalId(Random.alphanumeric.take(36).mkString)
    principal.setPrincipalName(Random.alphanumeric.take(20).mkString)
    principal.setApp( false)
    principal.setEmail(Random.alphanumeric.take(20).mkString+"@"+"streamsets.com")
    principal.setExpires( expiresIn )
    principal.setOrganizationId(Random.alphanumeric.take(36).mkString)
    principal.setOrganizationName( Random.alphanumeric.take(20).mkString)
    principal.setRequestIpAddress("*")
    principal.setRoles( util.Arrays.asList( "role1", "role2", "role3"))
    principal.setGroups( util.Arrays.asList("graoup1@streamsets.com", "graoup2@streamsets.com"))
    principal
  }

  def createSession( expiresIn: Long ): String

  def validate( token: String ): Option[SSOPrincipal]

  def invalidate( token: String ): Boolean

}

class NoOpSessionManager extends SessionManager {

  private val validTokens = new ConcurrentHashMap[String, SSOPrincipal]()

  override def createSession( expiresIn: Long ): String = {
    val principal = newPrincipal(expiresIn)
    val sessionHashId = toSessionHashID(principal.getTokenStr)
    validTokens.put( sessionHashId, principal )
    sessionHashId
  }

  override def validate(token: String): Option[SSOPrincipal] = {
    Option( validTokens.get(token) )
  }

  override def invalidate(token: String): Boolean = {
    Option(validTokens.remove(token)).isDefined
  }
}

