package com.streamsets

import com.streamsets.sch.RedisEntry
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.redisson.client.codec.StringCodec
import reactor.core.publisher.{Flux, Mono}

import java.util.concurrent.{CompletableFuture, ThreadLocalRandom, TimeUnit}
import java.{lang, util}
import scala.jdk.CollectionConverters._


@State( Scope.Benchmark)
class TokensState {

  val conf = Settings.load()
  val redisson = RedisUtils.setUpRedisson(conf)

  val tokens = new util.ArrayList[String]()

  val numTokens = 1_000_000
  val batchSize = 10_000

  @Setup(Level.Trial)
  def setup(): Unit = {
    val now = System.currentTimeMillis()
    println("Fetching tokens from Redis.")
    val tokensRedis = redisson.reactive().getKeys.getKeys(1000).collectList().toFuture.get().asScala.toSet
    println(s" Fetched ${tokensRedis.size} tokens from Redis in ${System.currentTimeMillis() - now }")
    tokens.addAll(tokensRedis.asJava)
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    println("Shutting down Redisson ")
    redisson.shutdown()
  }
  def validateInnerAsync( token: String  ): CompletableFuture[lang.Boolean] = {
    val newHandler = redisson.getMap[String, AnyRef]( token, StringCodec.INSTANCE )
    newHandler.readAllMapAsync()
      .toCompletableFuture
      .thenCompose{ _ =>
        newHandler
          .fastPutAsync( RedisEntry.LAST_ACTIVITY_AT, java.lang.Long.valueOf(System.currentTimeMillis()))
      }
  }

  def validateInnerReactive(token: String): Mono[lang.Boolean] = {
    val newHandler = redisson.reactive().getMap[String, AnyRef](token, StringCodec.INSTANCE)
    newHandler.readAllMap()
      .flatMap { _ =>
        newHandler.fastPut(RedisEntry.LAST_ACTIVITY_AT, java.lang.Long.valueOf(System.currentTimeMillis()))
      }
  }

  def validateInnerSync(token: String): lang.Boolean = {
    val newHandler = redisson.getMap[String, AnyRef](token, StringCodec.INSTANCE)
    val data = newHandler.readAllMap()
    val result = newHandler.fastPut(RedisEntry.LAST_ACTIVITY_AT, java.lang.Long.valueOf(System.currentTimeMillis()))
    java.lang.Boolean.valueOf(data.size() > 0 && result)
  }

  def wrapValidateInnerAsMono( token: String, which: Int ): Mono[lang.Boolean] = {
    which match {
      case 0 => Mono.fromFuture( ()=> validateInnerAsync(token ) )
      case 1 => validateInnerReactive( token )
      case 2 => Mono.just( validateInnerSync( token ) )
    }
  }
  def validateBatch( which: Int ): Int = {
    val batch = ThreadLocalRandom.current().nextInt(numTokens / batchSize)
    Flux.fromIterable(tokens.asScala.slice(batch * batchSize, batch * batchSize + batchSize).toList.asJava)
      .flatMap(
        token => wrapValidateInnerAsMono( token, which),
        conf.redis.threads
      )
      .collectList()
      .toFuture
      .get()
      .size()
  }

  def validateSync(): lang.Boolean = {
    val index = ThreadLocalRandom.current().nextInt(numTokens)
    validateInnerSync( tokens.get( index ) )
  }

}

class JMHBenchmarks {


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
  @Fork(1)
  @Threads(2)
  def validateAsync(state: TokensState, blackhole: Blackhole): Unit = {
    val numTokens = state.validateBatch(0)
    blackhole.consume(numTokens)
  }
//
//  @Benchmark
//  @BenchmarkMode(Array(Mode.Throughput))
//  @OutputTimeUnit(TimeUnit.SECONDS)
//  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
//  @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
//  @Fork(1)
//  @Threads(2)
//  def validateReactive(state: TokensState, blackhole: Blackhole): Unit = {
//    val numTokens = state.validateBatch(1)
//    blackhole.consume(numTokens)
//  }
//
//  @Benchmark
//  @BenchmarkMode(Array(Mode.Throughput))
//  @OutputTimeUnit(TimeUnit.MILLISECONDS)
//  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
//  @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
//  @Fork(1)
//  @Threads(128)
//  def validateSync(state: TokensState, blackhole: Blackhole): Unit = {
//    blackhole.consume( state.validateSync() )
//  }



}
