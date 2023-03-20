package com.streamsets

import com.streamsets.sch.{RedisSessionsCache, RedisSessionsCacheAsync, SSOPrincipal, SessionsManager}
import org.apache.commons.codec.digest.DigestUtils
import org.openjdk.jmh.annotations._
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
  val tokens = new util.ArrayList[String]()

  val numTokens = 1_000_000
  val batchSize = 10_000

  def setUpRedisson(): RedissonClient = {
    val redisson = RedisUtils.setUpRedisson(conf)
    redisson.getKeys.flushall();
    redisson
  }


  def setUpSessionsManager(redisson: RedissonClient): SessionsManager = {
    val sessionsCache = new RedisSessionsCache(redisson)
    val sessionsCacheAsync = new RedisSessionsCacheAsync(redisson)
    new SessionsManager(sessionsCache,sessionsCacheAsync)
  }

  def createTokens(): List[String] = {
    redisson.getKeys.flushall()
    TestSessions.createTokens( numTokens, batchSize, sessionManager )
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
        val redisTokens = sessionManager.allTokens()
        val all = if( redisTokens.isEmpty )
          createTokens()
        else redisTokens.toList
        Files.writeString(Paths.get("tokens.list"), all.mkString(del))
        println(s"Fetched ${all.size} tokens from Redis in ${System.currentTimeMillis() - now} ms.")
        all
      }
    }

    tokens.addAll(keys.asJava)
    val token = tokens.get(0)
    val tokenStr = sessionManager.validate(token).map(_.token).getOrElse("")
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
      .flatMap(token => sessionManager.validateAsync(token))
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
