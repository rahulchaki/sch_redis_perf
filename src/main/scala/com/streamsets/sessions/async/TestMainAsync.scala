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

  println(test())

  redisson.shutdown()

}
