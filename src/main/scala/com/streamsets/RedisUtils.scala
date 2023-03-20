package com.streamsets

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.{Config, ReadMode}
import org.redisson.connection.balancer.RoundRobinLoadBalancer

import scala.jdk.CollectionConverters._

object RedisUtils {
  def setUpRedisson( conf: AppConf ): RedissonClient = {
    val config = new Config
    val redis = conf.redis
    redis.replicas match {
      case Nil =>
        config
          .setNettyThreads(conf.redis.threads)
          .useSingleServer
          .setAddress("redis://"+redis.primary)
          .setConnectionMinimumIdleSize(redis.pool / 2)
          .setConnectionPoolSize(redis.pool)
      case replicas =>
        val slavesConfig = config
          .setNettyThreads(conf.redis.threads)
          .useMasterSlaveServers()
          .setMasterAddress("redis://"+redis.primary)
        slavesConfig
          .setSlaveAddresses( replicas.toSet.map( s => "redis://"+s).asJava )
        val nodes = 1 + replicas.size
        slavesConfig
          .setReadMode( ReadMode.SLAVE )
          .setLoadBalancer( new RoundRobinLoadBalancer())
          .setMasterConnectionMinimumIdleSize(redis.pool/(nodes*2))
          .setSlaveConnectionMinimumIdleSize(redis.pool/(nodes*2))
          .setMasterConnectionPoolSize(redis.pool/nodes)
          .setSlaveConnectionPoolSize(redis.pool/nodes)


    }

    Redisson.create(config)
  }

}
