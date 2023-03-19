package com.streamsets.sessions.sync

import com.streamsets.sessions.{SSOPrincipal, SSOPrincipalJson}
import org.apache.commons.codec.digest.DigestUtils

import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.{Collections, UUID}
import scala.util.Random


object SessionManager {
  def makeToken(expiry: Long): String = {
    val instanceId = Random.alphanumeric.take(36).mkString
    val id = UUID.randomUUID().toString
    s"$instanceId|$id|$expiry"
  }

  def toSessionHashID(token: String): String = {
    DigestUtils.sha256Hex(token)
  }

  def newPrincipal(expiresIn: Long): SSOPrincipal = {
    val principal = new SSOPrincipalJson
    principal.setTokenStr(makeToken(System.currentTimeMillis() + expiresIn))
    principal.setPrincipalId(Random.alphanumeric.take(36).mkString)
    principal.setPrincipalName(Random.alphanumeric.take(20).mkString)
    principal.setApp(false)
    principal.setEmail(Random.alphanumeric.take(20).mkString + "@" + "streamsets.com")
    principal.setExpires(expiresIn)
    principal.setOrganizationId(Random.alphanumeric.take(36).mkString)
    principal.setOrganizationName(Random.alphanumeric.take(20).mkString)
    principal.setRoles(util.Arrays.asList("role1", "role2", "role3"))
    principal.setGroups(util.Arrays.asList("graoup1@streamsets.com", "graoup2@streamsets.com"))
    principal
  }
}

trait SessionManager {
  def createSession( expiresIn: Long ): String

  def validate( token: String ): Option[SSOPrincipal]

  def invalidate( token: String ): Boolean

}

class StreamSetsSessionsManagerV2( sessionsCache: RedisSessionsCacheV2 ) extends SessionManager {

  def createSessions(num: Int, expiresIn: Long): List[ String ] = {
    val sessions =
    (0 until num).map { _ =>
      val principal = SessionManager.newPrincipal(expiresIn)
      val sessionHashId = SessionManager.toSessionHashID(principal.getTokenStr)
      sessionHashId -> principal
    }.toMap

    sessionsCache
      .cacheAll(sessions)
    sessions.keys.toList
  }

  def allTokens(): List[String] = sessionsCache.allTokens()

  override def createSession(expiresIn: Long): String = {
    val principal = SessionManager.newPrincipal(expiresIn)
    val sessionHashId = SessionManager.toSessionHashID(principal.getTokenStr)
    sessionsCache.cache(sessionHashId, principal)
    sessionHashId
  }

  override def validate(token: String): Option[SSOPrincipal] = {
    sessionsCache.validateAndUpdateLastActivity( token, () => Some( SessionManager.newPrincipal(0) ), () => true )
  }

  override def invalidate(token: String): Boolean = sessionsCache.invalidate( List( token ))
}

class StreamSetsSessionsManager( sessionsCache: SessionsCache ) extends SessionManager {
  override def createSession( expiresIn: Long ): String = {
    val principal = SessionManager.newPrincipal( expiresIn )
    val sessionHashId = SessionManager.toSessionHashID( principal.getTokenStr )
    sessionsCache.cache( sessionHashId, principal )
    sessionHashId
  }

  override def validate(token: String): Option[SSOPrincipal] = {
    Option(
      sessionsCache.validateAndUpdateLastActivity( token, () => SessionManager.newPrincipal(0), () => true)
    )
  }

  override def invalidate(token: String): Boolean = {
    sessionsCache.invalidate( Collections.singletonList( token ) )
    true
  }
}

class NoOpSessionManager extends SessionManager {

  private val validTokens = new ConcurrentHashMap[String, SSOPrincipal]()

  override def createSession( expiresIn: Long ): String = {
    val principal = SessionManager.newPrincipal(expiresIn)
    val sessionHashId = SessionManager.toSessionHashID(principal.getTokenStr)
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

