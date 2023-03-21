package com.streamsets

import com.streamsets.sch.{RedisSessionsCacheManager, SSOPrincipal, SessionsManager}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.redisson.api.RedissonClient
import reactor.core.publisher.{Flux, Mono}

import java.nio.file.{Files, Paths}
import java.util
import java.util.concurrent.{Executors, ThreadLocalRandom, TimeUnit}
import scala.io.Source
import scala.jdk.CollectionConverters._


@State( Scope.Benchmark)
class TokensState {

  val conf = Settings.load()
  val dbExecutor = Executors.newFixedThreadPool( conf.sch.dbIOThreads )
  val redisson = setUpRedisson()
  val sessionManager = setUpSessionsManager()
  val tokens = new util.ArrayList[String]()

  val numTokens = 1_000_000
  val batchSize = 10_000

  def setUpRedisson(): RedissonClient = {
    val redisson = RedisUtils.setUpRedisson(conf)
    redisson.getKeys.flushall();
    redisson
  }


  def setUpSessionsManager(): SessionsManager = {
    val sessionsCache = new RedisSessionsCacheManager( redisson )
    new SessionsManager(sessionsCache, Some(dbExecutor))
  }

  def createTokens(): List[String] = {
    redisson.getKeys.flushall()
    SessionsManager.createTokens( numTokens, batchSize, sessionManager )
  }

  @Setup(Level.Trial)
  def setup(): Unit = {
    SessionsManager.test( sessionManager )
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

  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    println("Shutting down Redisson ")
    redisson.shutdown()
  }

  def validate(): Option[SSOPrincipal] = {
    val index = ThreadLocalRandom.current().nextInt(numTokens)
    sessionManager.validateAsync(tokens.get(index)).get()
  }

  def validateAsync(): Int = {
    val batch = ThreadLocalRandom.current().nextInt( numTokens / batchSize)
    Flux.fromIterable(tokens.asScala.slice(batch * batchSize, batch * batchSize + batchSize).toList.asJava)
      .flatMap(token => Mono.fromFuture( () => sessionManager.validateAsync(token) ) )
      .collectList()
      .toFuture
      .get()
      .size()
  }

}

class JMHBenchmarks {


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
  @Fork(1)
  @Threads(1)
  def validateAsync(state: TokensState, blackhole: Blackhole): Unit = {
    val numTokens = state.validateAsync()
    blackhole.consume(numTokens)
  }
//
//  @Benchmark
//  @BenchmarkMode(Array(Mode.Throughput))
//  @OutputTimeUnit(TimeUnit.MILLISECONDS)
//  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
//  @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
//  @Fork(1)
//  @Threads(64)
//  def validate(state: TokensState, blackhole: Blackhole): Unit = {
//    blackhole.consume(
//      state.validate()
//    )
//  }


}
