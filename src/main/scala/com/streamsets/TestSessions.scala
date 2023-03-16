package com.streamsets

import com.streamsets.sessions.async.SessionManagerAsync
import com.streamsets.sessions.sync.SessionManager
import org.apache.commons.codec.digest.DigestUtils

import java.util.concurrent.TimeUnit

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

}