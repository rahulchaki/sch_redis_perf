package com.streamsets

import com.streamsets.TestMain.conf
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.{Config, ReadMode}
import org.redisson.connection.balancer.{LoadBalancer, RandomLoadBalancer, WeightedRoundRobinBalancer}

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
          .setReadMode( ReadMode.MASTER_SLAVE )
          .setLoadBalancer( new RandomLoadBalancer())
          .setMasterConnectionPoolSize(redis.pool/4)
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
