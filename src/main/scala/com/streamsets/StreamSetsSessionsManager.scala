package com.streamsets

import java.util.Collections

class StreamSetsSessionsManager( sessionsCache: SessionsCache ) extends SessionManager {
  override def createSession( expiresIn: Long ): String = {
    val principal = newPrincipal( expiresIn )
    val sessionHashId = toSessionHashID( principal.getTokenStr )
    sessionsCache.cache( sessionHashId, principal )
    sessionHashId
  }

  override def validate(token: String): Option[SSOPrincipal] = {
    Option(
      sessionsCache.validateAndUpdateLastActivity( token, () => newPrincipal(0), () => true)
    )
  }

  override def invalidate(token: String): Boolean = {
    sessionsCache.invalidate( Collections.singletonList( token ) )
    true
  }
}
