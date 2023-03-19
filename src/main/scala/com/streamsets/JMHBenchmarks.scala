package com.streamsets

import com.streamsets.sessions.SSOPrincipal
import com.streamsets.sessions.async.StreamSetsSessionsManagerV2Async
import com.streamsets.sessions.sync.{RedisSessionsCacheV2, RedisSessionsCacheV2Async, StreamSetsSessionsManagerV2}
import org.apache.commons.codec.digest.DigestUtils
import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Fork, Level, Measurement, Mode, OutputTimeUnit, Scope, Setup, State, TearDown, Threads, Warmup}
import org.openjdk.jmh.infra.Blackhole
import org.redisson.api.RedissonClient
import reactor.core.publisher.Flux

import java.nio.file.{Files, Paths}
import java.util
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}
import scala.io.Source
import scala.jdk.CollectionConverters._


@State( Scope.Benchmark)
class TokensState {

  val conf = Settings.load()
  val redisson = setUpRedisson()
  val sessionManager = setUpSessionsManager(redisson)
  val sessionManagerAsync = setUpSessionsManagerAsync(redisson)
  val tokens = new util.ArrayList[String]()

  val numTokens = 1_000_000
  val batchSize = 10_000

  def setUpRedisson(): RedissonClient = {
    val redisson = RedisUtils.setUpRedisson(conf)
    redisson.getKeys.flushall();
    redisson
  }


  def setUpSessionsManager(redisson: RedissonClient): StreamSetsSessionsManagerV2 = {
    val sessionsCache = new RedisSessionsCacheV2(redisson)
    new StreamSetsSessionsManagerV2(sessionsCache)
  }

  def setUpSessionsManagerAsync(redisson: RedissonClient): StreamSetsSessionsManagerV2Async = {
    val sessionsCache = new RedisSessionsCacheV2Async(redisson)
    new StreamSetsSessionsManagerV2Async(sessionsCache)
  }

  def createTokens(): List[String] = {
    redisson.getKeys.flushall()
    println("Creating tokens")
    val now = System.currentTimeMillis()
    val tokens = (0 until (numTokens / batchSize)).flatMap { batch =>
      sessionManager.createSessions(batchSize, 600000)
    }.toList
    println(s" Created ${tokens.size} tokens in ${System.currentTimeMillis() - now} ms. ")
    tokens
  }

  @Setup(Level.Trial)
  def setup(): Unit = {
    val forceCreate = true
    val now = System.currentTimeMillis()
    val del = "###"
    val keys = if (forceCreate) {
      createTokens()
    }
    else {
      if (Files.exists(Paths.get("tokens.list"))) {
        println("Loading tokens from file.")
        val all = Source.fromFile("tokens.list").mkString.split(del).toList
        println(s"Loaded ${all.size} tokens from file in ${System.currentTimeMillis() - now} ms.")
        all
      } else {
        println("Fetching tokens from Redis.")
        val all = sessionManager.allTokens() match {
          case Nil => createTokens()
          case list => list
        }
        Files.writeString(Paths.get("tokens.list"), all.mkString(del))
        println(s"Fetched ${all.size} tokens from Redis in ${System.currentTimeMillis() - now} ms.")
        all
      }
    }

    tokens.addAll(keys.asJava)
    val token = tokens.get(0)
    val tokenStr = sessionManager.validate(token).map(_.getTokenStr).getOrElse("")
    println(s" Test validate $token  $tokenStr ${DigestUtils.sha256Hex(tokenStr).equals(token)}")
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    println("Shutting down Redisson ")
    redisson.shutdown()
  }

  def validate(): Option[SSOPrincipal] = {
    val token = tokens.get(ThreadLocalRandom.current().nextInt(tokens.size()))
    sessionManager.validate(token)
    None
  }

  def validateAsync(): Int = {
    val batch = ThreadLocalRandom.current().nextInt( numTokens / batchSize)
    Flux.fromIterable(tokens.asScala.slice(batch * batchSize, batch * batchSize + batchSize).toList.asJava)
      .flatMap(token => sessionManagerAsync.validate(token))
      .collectList()
      .toFuture
      .get()
      .size()
  }

}

class JMHBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
  @Fork(1)
  @Threads(64)
  def validate(state: TokensState, blackhole: Blackhole): Unit = {
    val token = state.validate()
    blackhole.consume(token)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
  @Fork(1)
  @Threads(1)
  def validateAsync(state: TokensState, blackhole: Blackhole): Unit = {
    val numTokens = state.validateAsync()
    blackhole.consume( numTokens )
  }

}
