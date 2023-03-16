package com.streamsets

import com.streamsets.sessions.async.SessionManagerAsync
import com.streamsets.sessions.sync.SessionManager
import org.apache.commons.codec.digest.DigestUtils
import reactor.core.publisher.Flux

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._


object TestSessions {

  def async(sessionManager: SessionManagerAsync): Unit = {
    println(" Async Testing sch redis............ ")
    val task = sessionManager
      .createSession(600000)
      .flatMap { token =>
        println(" Async Created session with token " + token)
        sessionManager
          .validate(token)
          .map { principalOpt =>
            println(" Async Validated token with result " + principalOpt.isDefined)
            val tokenStr = principalOpt
              .map(_.getTokenStr).getOrElse("")
            val result = DigestUtils.sha256Hex(tokenStr).equals(token)
            println(s" Token : $token  tokenStr $tokenStr result $result")
            result
          }
          .flatMap( _ =>
            sessionManager.invalidate(token)
              .doOnEach( result  =>  println(" Async Invalidated token with result " + result))
          )
      }
    task.toFuture.get(1, TimeUnit.MINUTES)
  }
  def sync( sessionManager: SessionManager ): Unit = {
    println(" Testing sch redis............ ")
    val token = sessionManager.createSession(60000)
    println(" Created session with token " + token)
    val principal = sessionManager.validate(token)
    println(" Validated token with result " + principal.isDefined)
    val tokenStr = principal.map(_.getTokenStr).getOrElse("")
    println(s" Token : $token  tokenStr $tokenStr result ${DigestUtils.sha256Hex(tokenStr).equals(token)}")
    val result = sessionManager.invalidate(token)
    println(" Invalidated token with result " + result)
  }

  def createTokens( numTokens: Int, batchSize: Int, sessionManager: SessionManagerAsync ): Unit = {
    println("Creating tokens")
    val time = System.currentTimeMillis()
    val tokens =  Flux.fromIterable( (0 until (numTokens/batchSize)).toList.asJava  )
      .flatMap( batch => sessionManager.createSessions( batchSize, 600000) )
      .collectList()
      .map( _.asScala.toList.flatten )
      .toFuture.get
    println(s"Created ${tokens.size} tokens in ${System.currentTimeMillis() - time} ms.")
    val token = tokens.head
    val tokenStr = sessionManager.validate(tokens.head).toFuture.get().map(_.getTokenStr).getOrElse("")
    println(s" Test validate $token  $tokenStr ${DigestUtils.sha256Hex(tokenStr).equals(token)}");
  }

}