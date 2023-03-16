package com.streamsets.sessions.async;

import com.streamsets.sessions.SSOPrincipal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.Callable;

public interface SessionsCacheAsync {

    Mono<List<String>> allTokens();
    Mono<Boolean> cacheAll(Map<String, SSOPrincipal> sessions );
    Mono<Boolean> cache(String sessionHashId, SSOPrincipal principal);
    Mono<Optional<SSOPrincipal>> validateAndUpdateLastActivity(String sessionHashId, Callable<SSOPrincipal> validateFromDB, Callable<Boolean> updateMainDB );
    Flux<Boolean> invalidate(Collection<String> sessionHashIds);

}
