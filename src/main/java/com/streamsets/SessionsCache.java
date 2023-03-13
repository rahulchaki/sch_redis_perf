package com.streamsets;

import java.util.Collection;
import java.util.concurrent.Callable;

public interface SessionsCache {
    void cache( String sessionHashId, SSOPrincipal principal);
    SSOPrincipal validateAndUpdateLastActivity(String sessionHashId, Callable<SSOPrincipal> validateFromDB, Callable<Boolean> updateMainDB );
    void invalidate( Collection<String> sessionHashIds);

    public class PrincipalEntry {

        public SSOPrincipal principal;
        public long lastActivityTS;
        public long lastActivityCutoff;

    }

}
