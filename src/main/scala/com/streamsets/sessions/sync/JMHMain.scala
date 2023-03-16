//package com.streamsets.sessions.sync
//
//import com.streamsets.sessions.SSOPrincipal
//import com.streamsets.{RedisUtils, Settings}
//import org.apache.commons.codec.digest.DigestUtils
//import org.openjdk.jmh.annotations._
//import org.openjdk.jmh.infra.Blackhole
//import org.openjdk.jmh.runner.Runner
//import org.openjdk.jmh.runner.options.OptionsBuilder
//import org.redisson.api.RedissonClient
//
//import java.util
//import java.util.concurrent.{ThreadLocalRandom, TimeUnit}
//
//@State( Scope.Benchmark)
//class TokensState {
//
//  val conf = Settings.load()
//  val redisson = setUpRedisson()
//  val sessionManager = setUpSessionsManager( redisson )
//  val tokens = new util.ArrayList[String]()
//
//
//
//  def setUpRedisson(): RedissonClient = {
//    val redisson = RedisUtils.setUpRedisson( conf)
//    redisson.getKeys.flushall();
//    redisson
//  }
//
//
//  def setUpSessionsManager( redisson: RedissonClient ): StreamSetsSessionsManager = {
//    val sessionsCache = new RedisSessionsCache(redisson)
//    new StreamSetsSessionsManager(sessionsCache)
//  }
//
//
//
//  @Setup(Level.Trial)
//  def setup(): Unit = {
//    println("Creating tokens")
//    val numTokens = 10000
//    val time = System.currentTimeMillis()
//    (0 until numTokens).foreach { _ =>
//      tokens.add( sessionManager.createSession(600000) )
//    }
//    println(s"Created $numTokens tokens in ${System.currentTimeMillis() - time} ms.")
//    val token = tokens.get(0)
//    val tokenStr = sessionManager.validate(tokens.get(0)).map(_.getTokenStr).getOrElse("")
//    println(s" Test validate $token  $tokenStr ${DigestUtils.sha256Hex(tokenStr).equals(token)}");
//  }
//
//  @TearDown(Level.Trial)
//  def teardown(): Unit = {
//    println( "Shutting down Redisson ")
//    redisson.getKeys.flushall()
//    redisson.shutdown()
//  }
//
//
//  def validate(): Option[SSOPrincipal] = {
//    val token = tokens.get(ThreadLocalRandom.current().nextInt(tokens.size()))
//    sessionManager.validate(token)
//    None
//  }
//
//}
//
//class Benchmarks {
//
//  @Benchmark
//  @BenchmarkMode(Array(Mode.Throughput))
//  @OutputTimeUnit(TimeUnit.MILLISECONDS)
//  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
//  @Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
//  @Fork(1)
//  @Threads(64)
//  def validate( state: TokensState, blackhole: Blackhole ): Unit = {
//    val token = state.validate()
//    blackhole.consume( token )
//  }
//
//}
//
//object JMHRun extends App {
//
//  //System.setProperty("jmh.ignoreLock", "true")
//  println(s"Starting execution ")
//  val options = new OptionsBuilder()
//    .mode(Mode.Throughput)
//    //.mode(Mode.SampleTime)
//    //.result(s"jmh_results_${i}.log")
//    .include(classOf[Benchmarks].getSimpleName)
//    .build()
//
//  val results = new Runner(options).run()
//  println(s"Finished execution ")
//
////  val runs = 2
////  val es = Executors.newFixedThreadPool(runs)
////  val tasks = ( 0 until runs ).map { i =>
////    new Callable[util.Collection[RunResult]] {
////      override def call(): util.Collection[RunResult] = {
////        println(s"Starting execution ${i}")
////        val options = new OptionsBuilder()
////          .mode(Mode.Throughput)
////          //.mode(Mode.SampleTime)
////          .result(s"jmh_results_${i}.log")
////          .include(classOf[Benchmarks].getSimpleName)
////          .build()
////
////        val results = new Runner(options).run()
////        println(s"Finished execution ${i}")
////        results
////      }
////    }
////  }.toList
////  es.invokeAll( util.Arrays.asList( tasks:_*))
//  //es.shutdown()
//  //es.awaitTermination( 15, TimeUnit.MINUTES);
//}