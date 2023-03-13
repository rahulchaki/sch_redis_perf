package com.streamsets

import org.apache.commons.codec.digest.DigestUtils
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

object TestMain extends App {

  val conf = Settings.load()
  val redisson = setUpRedisson()
  val sessionManager = setUpSessionsManager(redisson)


  def setUpRedisson(): RedissonClient = {
    val config = new Config
    val redis = conf.redis
    config
      .setNettyThreads( conf.redis.threads)
      .useSingleServer
      .setAddress("redis://" + redis.host + ":" + redis.port)
    .setConnectionPoolSize( redis.pool)
    Redisson.create(config)
  }


  def setUpSessionsManager(redisson: RedissonClient): StreamSetsSessionsManager = {
    val sessionsCache = new RedisSessionsCache(redisson)
    new StreamSetsSessionsManager(sessionsCache)
  }

  def test(): Boolean = {
    val state = new TokensState
    val token = state.sessionManager.createSession(600000)
    val tokenStr = state.sessionManager.validate(token).map(_.getTokenStr).getOrElse("")
    DigestUtils.sha256Hex(tokenStr).equals(token)
  }

  println(test())

  redisson.shutdown()


}
