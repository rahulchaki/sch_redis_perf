package com.streamsets

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.{Config, ReadMode}
import org.redisson.connection.balancer.RandomLoadBalancer

object RedisUtils {
  def setUpRedisson( conf: AppConf ): RedissonClient = {
    val config = new Config
    val redis = conf.redis
    redis.replica match {
      case Some( replica ) =>
        config
          .setNettyThreads(conf.redis.threads)
          .useMasterSlaveServers()
          .setMasterAddress("redis://" + redis.primary.host + ":" + redis.primary.port)
          .addSlaveAddress( "redis://" + replica.host + ":" + replica.port)
          .setReadMode( ReadMode.SLAVE )
          .setLoadBalancer( new RandomLoadBalancer())
          .setMasterConnectionMinimumIdleSize(redis.pool/4)
          .setSlaveConnectionMinimumIdleSize(redis.pool/4)
          .setMasterConnectionPoolSize(redis.pool/2)
          .setSlaveConnectionPoolSize(redis.pool/2)

      case None =>
        config
          .setNettyThreads(conf.redis.threads)
          .useSingleServer
          .setAddress("redis://" + redis.primary.host + ":" + redis.primary.port)
          .setConnectionMinimumIdleSize( redis.pool/2)
          .setConnectionPoolSize(redis.pool)
    }

    Redisson.create(config)
  }

}
