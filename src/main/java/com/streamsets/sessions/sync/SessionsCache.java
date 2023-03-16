package com.streamsets.sessions.sync;

import com.streamsets.sessions.SSOPrincipal;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

public interface SessionsCache {

    void cacheAll(Map<String, SSOPrincipal> sessions );
    void cache( String sessionHashId, SSOPrincipal principal);
    SSOPrincipal validateAndUpdateLastActivity(String sessionHashId, Callable<SSOPrincipal> validateFromDB, Callable<Boolean> updateMainDB );
    void invalidate( Collection<String> sessionHashIds);

}
