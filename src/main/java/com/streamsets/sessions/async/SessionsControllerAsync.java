package com.streamsets.sessions.async;

import com.streamsets.sessions.SSOPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/sessions_async")
public class SessionsControllerAsync {

    @Autowired
    private SessionManagerAsync sessionManager;

    @PostMapping("/create")
    private Mono<String> createSession(@RequestParam("expiresIn") long expiresIn) {
        return this.sessionManager.createSession( expiresIn );
    }

    @PostMapping("/validate")
    private Mono<Optional<SSOPrincipal>> validate(@RequestParam("token") String token) {
        return this.sessionManager.validate( token ).map( prinSc -> prinSc.isDefined() ? Optional.of( prinSc.get()) : Optional.empty());
    }

    @PostMapping("/invalidate")
    private Mono<String> invalidate(@RequestParam("token") String token) {
        return this.sessionManager.invalidate( token );
    }

}
