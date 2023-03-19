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


  def setUpSessionsManager(redisson: RedissonClient): SessionManager = {
    val sessionsCache = new RedisSessionsCacheV2(redisson)
    new StreamSetsSessionsManagerV2(sessionsCache)
  }

  def test(): Unit = {
    TestSessions.sync( sessionManager )
  }

  println(test())

  redisson.shutdown()


}
