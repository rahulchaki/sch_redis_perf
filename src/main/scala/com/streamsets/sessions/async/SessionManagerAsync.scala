package com.streamsets.sessions.async

import com.streamsets.sessions.sync.{RedisSessionsCacheV2Async, SessionManager}
import com.streamsets.sessions.SSOPrincipal
import reactor.core.publisher.Mono

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._

trait SessionManagerAsync {

  def allTokens(): Mono[ Set[ String ] ]
  def createSessions(num: Int, expiresIn: Long): Mono[List[String]]
  def createSession( expiresIn: Long ): Mono[String]

  def validate( token: String ): Mono[Option[SSOPrincipal]]

  def invalidate( token: String ): Mono[Boolean]

}


class StreamSetsSessionsManagerAsync(sessionsCache: SessionsCacheAsync ) extends SessionManagerAsync {



  override def createSessions(num: Int, expiresIn: Long): Mono[List[String]] = {
    val sessions = new util.HashMap[String, SSOPrincipal]()
    ( 0 until num ).foreach{ _ =>
      val principal = SessionManager.newPrincipal(expiresIn)
      val sessionHashId = SessionManager.toSessionHashID(principal.getTokenStr)
      sessions.put( sessionHashId, principal )
    }

    sessionsCache
      .cacheAll(sessions)
      .map{ _ =>
        sessions.asScala.keys.toList
      }

  }
  override def createSession( expiresIn: Long ): Mono[String] = {
    val principal = SessionManager.newPrincipal( expiresIn )
    val sessionHashId = SessionManager.toSessionHashID( principal.getTokenStr )
    sessionsCache
      .cache( sessionHashId, principal )
      .map( _ => sessionHashId )

  }

  override def validate(token: String): Mono[Option[SSOPrincipal]] = {
    sessionsCache
      .validateAndUpdateLastActivity( token, () => SessionManager.newPrincipal(0), () => true)
      .map( jOpt => if( jOpt.isEmpty) None else Some( jOpt.get() ) )
  }

  override def invalidate(token: String): Mono[Boolean] = {
    sessionsCache
      .invalidate( Collections.singletonList( token ) )
      .collectList()
      .map( _ => true)
  }

  override def allTokens(): Mono[Set[String]] = {
    sessionsCache.allTokens().map( _.asScala.toSet )
  }
}


class StreamSetsSessionsManagerV2Async( sessionsCache: RedisSessionsCacheV2Async ) extends SessionManagerAsync {

  def createSessions(num: Int, expiresIn: Long): Mono[ List[ String ] ] = {
    val sessions =
      (0 until num).map { _ =>
        val principal = SessionManager.newPrincipal(expiresIn)
        val sessionHashId = SessionManager.toSessionHashID(principal.getTokenStr)
        sessionHashId -> principal
      }.toMap

    sessionsCache
      .cacheAll(sessions)
      .map( _ => sessions.keys.toList )

  }

  def allTokens(): Mono[ Set[String] ] = sessionsCache.allTokens()

  override def createSession(expiresIn: Long): Mono[String] = {
    val principal = SessionManager.newPrincipal(expiresIn)
    val sessionHashId = SessionManager.toSessionHashID(principal.getTokenStr)
    sessionsCache
      .cache(sessionHashId, principal)
      .map( _ => sessionHashId )
  }

  override def validate(token: String): Mono[ Option[SSOPrincipal] ] = {
    sessionsCache.validateAndUpdateLastActivity( token, () => Some( SessionManager.newPrincipal(0) ), () => true )
  }

  override def invalidate(token: String): Mono[ Boolean ] = sessionsCache.invalidate( List( token ))
}