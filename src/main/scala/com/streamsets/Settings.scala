package com.streamsets

import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration._


case class Pauses( validate: FiniteDuration, invalidate: FiniteDuration)
case class Durations( total: FiniteDuration, validate: FiniteDuration, invalidate: FiniteDuration, pauses: Pauses )
case class SimulationConf( users: Int, createMaxTries: Int, durations: Durations )

case class SCHConf( host: String, port: Int, expiresIn: FiniteDuration )
case class RedisConf( host: String, port: Int, pool: Int, threads: Int )

case class AppConf(
                    redis: RedisConf = RedisConf( host = "127.0.0.1", port = 6379, pool = 64, threads = 64),
                    sch: SCHConf = SCHConf( host = "localhost", port = 8080, expiresIn = 60.seconds ),
                    simulation: SimulationConf = SimulationConf(
                      users = 1000,
                      createMaxTries= 4,
                      durations = Durations(
                        total= 60.seconds, validate = 10.seconds, invalidate = 5.seconds,
                        pauses = Pauses( validate = 1.second, invalidate = 1.second)
                      )
                    )
                  )


object Settings {
  def load(): AppConf = {
    ConfigSource.default.loadOrThrow[AppConf]
  }
}