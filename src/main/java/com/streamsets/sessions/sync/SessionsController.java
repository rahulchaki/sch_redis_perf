package com.streamsets.sessions.sync;

import com.streamsets.sessions.SSOPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import scala.Option;

@RestController
@RequestMapping("/sessions")
public class SessionsController {

    @Autowired
    private SessionManager sessionManager;

    @PostMapping("/create")
    private Mono<String> createSession(@RequestParam("expiresIn") long expiresIn) {
        return Mono.just( this.sessionManager.createSession( expiresIn ) );
    }

    @PostMapping("/validate")
    private Mono<SSOPrincipal> validate(@RequestParam("token") String token) {
        Option<SSOPrincipal> principal = this.sessionManager.validate( token );
        return principal.isDefined() ? Mono.just( principal.get()) : Mono.empty();
    }

    @PostMapping("/invalidate")
    private Mono<Boolean> invalidate(@RequestParam("token") String token) {
        return Mono.just( this.sessionManager.invalidate( token ) );
    }


}
