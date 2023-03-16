package com.streamsets.sessions.sync

import com.streamsets.{RedisUtils, Settings, TestSessions}
import org.redisson.api.RedissonClient

object TestMainSync extends App {

  val conf = Settings.load()
  val redisson = setUpRedisson()
  val sessionManager = setUpSessionsManager(redisson)


  def setUpRedisson(): RedissonClient = {
    RedisUtils.setUpRedisson( conf )
  }


  def setUpSessionsManager(redisson: RedissonClient): StreamSetsSessionsManager = {
    val sessionsCache = new RedisSessionsCache(redisson)
    new StreamSetsSessionsManager(sessionsCache)
  }

  def test(): Unit = {
    TestSessions.sync( sessionManager )
  }

  println(test())

  redisson.shutdown()


}
