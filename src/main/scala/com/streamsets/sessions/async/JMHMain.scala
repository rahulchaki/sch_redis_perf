package com.streamsets.sessions.async


import com.streamsets.{RedisUtils, Settings}
import org.apache.commons.codec.digest.DigestUtils
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import org.redisson.api.RedissonReactiveClient
import reactor.core.publisher.Flux

import java.util
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}
import scala.jdk.CollectionConverters._

@State( Scope.Benchmark)
class TokensStateAsync {

  val conf = Settings.load()
  val redisson = setUpRedisson()
  val sessionManager = setUpSessionsManager( redisson )
  val tokens = new util.ArrayList[String]()
  val numBatches = 100
  val batchSize = 10000


  def setUpRedisson(): RedissonReactiveClient = {
    val redisson = RedisUtils.setUpRedisson(conf)
    redisson.getKeys.flushall();
    redisson.reactive()
  }


  def setUpSessionsManager( redisson: RedissonReactiveClient ): StreamSetsSessionsManagerAsync = {
    val sessionsCache = new RedisSessionsCacheAsync(redisson)
    new StreamSetsSessionsManagerAsync(sessionsCache)
  }



  @Setup(Level.Trial)
  def setup(): Unit = {
    println("Creating tokens")
    val time = System.currentTimeMillis()
    ( 0 until numBatches ).foreach{ _ =>
      sessionManager.createSessions( batchSize, 600000).toFuture.get.foreach { token =>
        tokens.add(token)
      }
    }
    println(s"Created ${ batchSize * numBatches } tokens in ${System.currentTimeMillis() - time} ms.")
    val token = tokens.get(0)
    val tokenStr = sessionManager.validate(tokens.get(0)).toFuture.get().map(_.getTokenStr).getOrElse("")
    println(s" Test validate $token  $tokenStr ${DigestUtils.sha256Hex(tokenStr).equals(token)}");
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    println( "Shutting down Redisson ")
    redisson.getKeys.flushall()
    redisson.shutdown()
  }


  def validate(): Unit = {
    val batch = ThreadLocalRandom.current().nextInt( numBatches )
    Flux.fromIterable(  tokens.asScala.slice(batch * batchSize, batch * batchSize + batchSize).toList.asJava )
      .flatMap( token =>  sessionManager.validate(token) )
      .collectList()
      .toFuture
      .get()
  }

}

class Benchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.SECONDS)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
  @Fork(1)
  @Threads(1)
  def validate( state: TokensStateAsync, blackhole: Blackhole ): Unit = {
    val token = state.validate()
    blackhole.consume( token )
  }

}

object JMHRun extends App {

  //System.setProperty("jmh.ignoreLock", "true")
  println(s"Starting execution ")
  val options = new OptionsBuilder()
    .mode(Mode.Throughput)
    //.mode(Mode.SampleTime)
    //.result(s"jmh_results_${i}.log")
    .include(classOf[Benchmarks].getSimpleName)
    .build()

  val results = new Runner(options).run()
  println(s"Finished execution ")

  //  val runs = 2
  //  val es = Executors.newFixedThreadPool(runs)
  //  val tasks = ( 0 until runs ).map { i =>
  //    new Callable[util.Collection[RunResult]] {
  //      override def call(): util.Collection[RunResult] = {
  //        println(s"Starting execution ${i}")
  //        val options = new OptionsBuilder()
  //          .mode(Mode.Throughput)
  //          //.mode(Mode.SampleTime)
  //          .result(s"jmh_results_${i}.log")
  //          .include(classOf[Benchmarks].getSimpleName)
  //          .build()
  //
  //        val results = new Runner(options).run()
  //        println(s"Finished execution ${i}")
  //        results
  //      }
  //    }
  //  }.toList
  //  es.invokeAll( util.Arrays.asList( tasks:_*))
  //es.shutdown()
  //es.awaitTermination( 15, TimeUnit.MINUTES);
}
