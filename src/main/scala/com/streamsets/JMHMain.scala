package com.streamsets

import org.apache.commons.codec.digest.DigestUtils
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

import java.util
import java.util.concurrent.TimeUnit
import scala.util.Random

@State( Scope.Benchmark)
class TokensState {

  val conf = Settings.load()
  val redisson = setUpRedisson()
  val sessionManager = setUpSessionsManager( redisson )
  val tokens = new util.ArrayList[String]()



  def setUpRedisson(): RedissonClient = {
    RedisUtils.setUpRedisson( conf)
  }


  def setUpSessionsManager( redisson: RedissonClient ): StreamSetsSessionsManager = {
    val sessionsCache = new RedisSessionsCache(redisson)
    new StreamSetsSessionsManager(sessionsCache)
  }



  @Setup(Level.Trial)
  def setup(): Unit = {
    println("Creating tokens")
    val numTokens = 10000
    val time = System.currentTimeMillis()
    (0 until numTokens).foreach { _ =>
      tokens.add( sessionManager.createSession(600000) )
    }
    println(s"Created $numTokens tokens in ${System.currentTimeMillis() - time} ms.")
    val token = tokens.get(0)
    val tokenStr = sessionManager.validate(tokens.get(0)).map(_.getTokenStr).getOrElse("")
    println(s" Test validate $token  $tokenStr ${DigestUtils.sha256Hex(tokenStr).equals(token)}");
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    println( "Shutting down Redisson ")
    redisson.getKeys.flushall()
    redisson.shutdown()
  }


  def validate(): Option[SSOPrincipal] = {
    val token = tokens.get(Random.nextInt(tokens.size()))
    sessionManager.validate(token)
  }

}

class Benchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
  @Fork(1)
  @Threads(108)
  def validate( state: TokensState, blackhole: Blackhole ): Unit = {
    val token = state.validate()
    blackhole.consume( token )
  }

}

object JMHRun extends App {



  val options = new OptionsBuilder()
    .mode(Mode.Throughput)
    .mode(Mode.SampleTime)
    .include(classOf[Benchmarks].getSimpleName)
    .build()



  new Runner(options).run();
}