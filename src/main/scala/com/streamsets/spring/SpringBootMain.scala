package com.streamsets.spring

import com.streamsets.sch.{RedisSessionsCache, RedisSessionsCacheAsync, SessionsManager}
import com.streamsets.{AppConf, RedisUtils, Settings, TestSessions}
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.{ApplicationArguments, ApplicationRunner, WebApplicationType}
import org.springframework.context.annotation.{Bean, Scope}

@SpringBootApplication
class SpringBootMain extends ApplicationRunner {
  final def main(args: Array[String]): Unit = {
    val app = new SpringApplicationBuilder(classOf[SpringBootMain]).web(WebApplicationType.REACTIVE).build
    app.run(args.toList:_*)
    while (true) try Thread.sleep(10000)
    catch {
      case e: InterruptedException =>
        throw new RuntimeException(e)
    }
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  def getAppConf: AppConf = Settings.load()

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  def getRedisClient: RedissonClient = RedisUtils.setUpRedisson(getAppConf)

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
  def getSessionManager: SessionsManager = {
    val redis = getRedisClient
    val sessionsCacheSync = new RedisSessionsCache( redis )
    val sessionsCacheAsync = new RedisSessionsCacheAsync( redis )
    new SessionsManager(
      sessionsCacheSync, sessionsCacheAsync
    )
  }

  override def run(args: ApplicationArguments): Unit = {
    val sessionsManager = getSessionManager
    TestSessions.sync( sessionsManager )
    TestSessions.async( sessionsManager )
  }
}
