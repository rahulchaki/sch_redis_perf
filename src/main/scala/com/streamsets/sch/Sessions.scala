package com.streamsets.sch

import org.apache.commons.codec.digest.DigestUtils

import java.util.concurrent.{CompletableFuture, Executor}


class SessionsManager(
                       cache: RedisSessionsCacheManager,
                       dbExecutor: Option[ Executor ] = None
                     ) {
  def newSessions(num: Int, expiresIn: Long): Map[String, SSOPrincipal] = {
    (0 until num).map { _ =>
      val principal = SSOPrincipal.newPrincipal(expiresIn)
      val sessionHashId = SSOPrincipal.toSessionHashID(principal.token)
      sessionHashId -> principal
    }.toMap
  }

  def createSessions(num: Int, expiresIn: Long): List[String] = {
    val sessions = newSessions(num, expiresIn)
    if (sessions.size == 1)
      cache.cache(sessions.head._1, sessions.head._2)
    else
      cache.cacheAll(sessions)
    sessions.keys.toList
  }

  def allTokens(): Set[String] = cache.allTokens()

  def validateAsync(token: String ): CompletableFuture[Option[SSOPrincipal]] = {

    def validateFromDB( sessionHashId: String ): Option[ SSOPrincipal ] = {
      println(s"XXXXXXXXXXXXXXXXXXXXX  validateFromDB called for token $sessionHashId. This will block for 200 ms. ")
      Thread.sleep(200)
      Some(SSOPrincipal.newPrincipal(0))
    }
    def updateMainDB(sessionHashId: String): Boolean = {
      println(s"XXXXXXXXXXXXXXXXXXXXX  updateMainDB called for token $sessionHashId. This will block for 200 ms. ")
      Thread.sleep(200)
      true
    }

    def wrapWithExecutor[T]( fn: String => T): String => CompletableFuture[ T ] = {
      dbExecutor match {
        case Some(exec) => sessionHashId => CompletableFuture.supplyAsync( () => fn( sessionHashId ), exec)
        case None => sessionHashId => CompletableFuture.supplyAsync( () => fn( sessionHashId ) )
      }
    }

    cache.validateAndUpdateLastActivity(token, wrapWithExecutor( validateFromDB ), wrapWithExecutor( updateMainDB) )
  }

  def invalidate(token: String): Boolean = cache.invalidate( token )

}


object SessionsManager {
  def test(sessionManager: SessionsManager): Unit = {
    println(" Testing sch redis............ ")
    val tokens = sessionManager.createSessions(100, 60000)
    println(s" Created  ${tokens.size} tokens " )
    tokens.foreach{ token =>
      val principal = sessionManager.validateAsync(token).get()
      println(" Validated token with result " + principal.isDefined)
      val tokenStr = principal.map(_.token).getOrElse("")
      println(s" Token : $token  tokenStr $tokenStr result ${DigestUtils.sha256Hex(tokenStr).equals(token)}")
      val result = sessionManager.invalidate(token)
      println(" Invalidated token with result " + result)
    }
  }

  def createTokens(numTokens: Int, batchSize: Int, sessionManager: SessionsManager): List[String] = {
    println("Creating tokens")
    val time = System.currentTimeMillis()
    val numBatches = numTokens / batchSize
    val tokens = ( 0 until numBatches ).flatMap { _ =>
      sessionManager.createSessions(batchSize, 600000)
    }.toList
    println(s"Created ${tokens.size} tokens in ${System.currentTimeMillis() - time} ms.")
    tokens
  }
}