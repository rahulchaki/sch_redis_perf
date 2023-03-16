package com.streamsets;


import com.streamsets.sessions.async.RedisSessionsCacheAsync;
import com.streamsets.sessions.async.SessionManagerAsync;
import com.streamsets.sessions.async.SessionsCacheAsync;
import com.streamsets.sessions.async.StreamSetsSessionsManagerAsync;
import com.streamsets.sessions.sync.RedisSessionsCache;
import com.streamsets.sessions.sync.SessionManager;
import com.streamsets.sessions.sync.SessionsCache;
import com.streamsets.sessions.sync.StreamSetsSessionsManager;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

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

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public SessionManagerAsync getSessionManagerAsync() {
        SessionsCacheAsync sessionsCache = new RedisSessionsCacheAsync(getRedisClient().reactive());
        return new StreamSetsSessionsManagerAsync(sessionsCache);
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        SessionManager sync = getSessionManager();
        SessionManagerAsync async = getSessionManagerAsync();
        TestSessions$.MODULE$.sync( sync);
        TestSessions$.MODULE$.async( async );
    }
}
