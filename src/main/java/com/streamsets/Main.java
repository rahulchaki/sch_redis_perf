package com.streamsets;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import scala.Option;

@SpringBootApplication
public class Main implements ApplicationRunner {
    public static void main(String[] args) {
        SpringApplication app =
        new SpringApplicationBuilder(Main.class)
                .web(WebApplicationType.REACTIVE)
                .build();
        app.run( args);
        while( true ){
            try {
                Thread.sleep( 10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public AppConf getAppConf() {
        return Settings$.MODULE$.load();
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public RedissonClient getRedisClient() {
        return RedisUtils$.MODULE$.setUpRedisson( getAppConf());
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public SessionManager getSessionManager(){
        SessionsCache sessionsCache = new RedisSessionsCache( getRedisClient() );
        return new StreamSetsSessionsManager( sessionsCache );
        //return new NoOpSessionManager();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println(" Testing sch redis............ ");
        SessionManager sessionManager = this.getSessionManager();
        String token = sessionManager.createSession( 60000);
        System.out.println(" Created session with token "+token);
        Option<SSOPrincipal> principal = sessionManager.validate( token );
        System.out.println(" Validated token with result " + principal.isDefined());
        boolean result = sessionManager.invalidate( token );
        System.out.println(" Invalidated token with result " + result);
    }
}
