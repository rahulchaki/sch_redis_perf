package com.streamsets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.concurrent.Callable;

public interface SessionsCacheReactive {

    Mono<Boolean> cache(String sessionHashId, SSOPrincipal principal);
    Mono<SSOPrincipal> validateAndUpdateLastActivity(String sessionHashId, Callable<SSOPrincipal> validateFromDB, Callable<Boolean> updateMainDB );
    Flux<Boolean> invalidate(Collection<String> sessionHashIds);

}
