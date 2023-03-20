package com.streamsets.sch

import reactor.core.publisher.Mono


class SessionsManager (
                        cacheSync: RedisSessionsCache,
                        cacheAsync: RedisSessionsCacheAsync
                      ){

  def newSessions( num: Int, expiresIn: Long ): Map[String, SSOPrincipal] = {
    (0 until num).map { _ =>
      val principal = SSOPrincipal.newPrincipal(expiresIn)
      val sessionHashId = SSOPrincipal.toSessionHashID(principal.token)
      sessionHashId -> principal
    }.toMap
  }


  def createSessions(num: Int, expiresIn: Long): List[ String ] = {
    val sessions = newSessions( num, expiresIn )
    cacheSync.cacheAll( newSessions( num, expiresIn ) )
    sessions.keys.toList
  }
  def createSessionsAsync(num: Int, expiresIn: Long): Mono[ List[ String ] ] = {
    val sessions = newSessions(num, expiresIn)
    cacheAsync
      .cacheAll(newSessions(num, expiresIn))
      .map( _ => sessions.keys.toList )
  }

  def allTokens(): Set[String] = cacheSync.allTokens()
  def allTokensAsync(): Mono[ Set[String] ] = cacheAsync.allTokens()

  def validate(token: String): Option[SSOPrincipal] = {
    cacheSync.validateAndUpdateLastActivity(token, () => Some(SSOPrincipal.newPrincipal(0)), () => true)
  }
  def validateAsync(token: String): Mono[Option[SSOPrincipal]] = {
    cacheAsync.validateAndUpdateLastActivity(token, () => Some(SSOPrincipal.newPrincipal(0)), () => true)
  }


  def invalidate(token: String): Boolean = cacheSync.invalidate( List( token ))
  def invalidateAsync(token: String): Mono[Boolean] = cacheAsync.invalidate( List( token ))


}

