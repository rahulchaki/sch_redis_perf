package com.streamsets

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._


class SCHRedisSimulation extends Simulation {

  val conf = Settings.load()
  val schConf = conf.sch
  val simulationConf = conf.simulation

  println("Starting load test with configuration ")
  println( conf )
  println("======================================")

  val httpProtocol = http.baseUrl(s"http://${schConf.api}/sessions")

  val feeder = Iterator.continually {
    Map("expiresIn" -> schConf.expiresIn.toMillis )
  }

  val createSession = http("create")
    .post("/create")
    .queryParam("expiresIn", "#{expiresIn}")
    .check(bodyString.saveAs("token"))


  val validate = http("validate")
    .post("/validate")
    .queryParam("token", "#{token}")

  val invalidate = http("invalidate")
    .post("/invalidate")
    .queryParam("token", "#{token}")

  val users = scenario("users")
    .exec(
      feed(feeder)
        .exec( createSession )
        .doIf( session => session.contains("token")){
          during(simulationConf.durations.validate) {
            exec(validate)
              .pause(simulationConf.durations.pauses.validate)
          }
            .exec(invalidate)
            .during(simulationConf.durations.invalidate) {
              exec(validate)
                .pause(simulationConf.durations.pauses.invalidate)
            }
        }
    )


  setUp(
    users.inject(
      constantConcurrentUsers(simulationConf.users).during(simulationConf.durations.total),
    )
  ).protocols(httpProtocol)


}
