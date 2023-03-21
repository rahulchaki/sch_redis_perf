package com.streamsets.spring

import com.streamsets.sch.{RedisSessionsCacheManager, SessionsManager}
import com.streamsets.{AppConf, RedisUtils, Settings}
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.{ApplicationArguments, ApplicationRunner, WebApplicationType}
import org.springframework.context.annotation.{Bean, Scope}

import java.util.concurrent.{Executor, Executors}

@SpringBootApplication
class SpringBoot extends ApplicationRunner {

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  def getAppConf: AppConf = Settings.load()

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  def getRedisClient: RedissonClient = RedisUtils.setUpRedisson(getAppConf)

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  def getDBExecutor: Executor = {
    Executors.newFixedThreadPool( getAppConf.sch.dbIOThreads )
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  def getSessionManager: SessionsManager = {
    val redis = getRedisClient
    val sessionsCache = new RedisSessionsCacheManager( redis )
    new SessionsManager( sessionsCache, Some(getDBExecutor) )
  }

  override def run(args: ApplicationArguments): Unit = {
    val sessionsManager = getSessionManager
    SessionsManager.test( sessionsManager )
  }
}

object SpringBootMain extends App {
  val app = new SpringApplicationBuilder(classOf[SpringBoot]).web(WebApplicationType.REACTIVE).build
  app.run(args.toList: _*)

  while (true) try Thread.sleep(10000)
  catch {
    case e: InterruptedException =>
      throw new RuntimeException(e)
  }
}