package com.streamsets.sessions.async

import com.streamsets.{RedisUtils, Settings, TestSessions}
import org.redisson.api.RedissonReactiveClient

object TestMainAsync extends App {

  val conf = Settings.load()
  val redisson = setUpRedisson()
  val sessionManager = setUpSessionsManager(redisson)


  def setUpRedisson(): RedissonReactiveClient = {
    RedisUtils.setUpRedisson(conf).reactive()
  }


  def setUpSessionsManager(redisson: RedissonReactiveClient): StreamSetsSessionsManagerAsync = {
    val sessionsCache = new RedisSessionsCacheAsync(redisson)
    new StreamSetsSessionsManagerAsync(sessionsCache)
  }

  def test(): Unit  = {
    TestSessions.async( sessionManager )
  }

  def createTokens(): Unit = {
    redisson.getKeys.flushall().toFuture.get()
    TestSessions.createTokens( 1000000, 10000, sessionManager)
    val numKeys = redisson.getKeys.count().toFuture.get()
    println(s" $numKeys tokens created ")
  }

  createTokens()

  redisson.shutdown()

}
