package com.streamsets.sessions.sync;


import com.google.gson.Gson;
import com.streamsets.sessions.SSOPrincipal;
import com.streamsets.sessions.SSOPrincipalJson;
import com.streamsets.sessions.async.RedisSessionsCacheAsync;
import org.redisson.api.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class RedisSessionsCache implements SessionsCache {

    private RedissonClient redisson;
    private PrincipalCodec principalCodec;
    public RedisSessionsCache( RedissonClient redisson ) {
        this.redisson = redisson;
        this.principalCodec = new PrincipalCodecImpl();
    }

    @Override
    public void cacheAll(Map<String, SSOPrincipal> sessions) {
        new RBatchExecutor()
                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                        {
                            for( Map.Entry<String, SSOPrincipal> entry: sessions.entrySet() ){
                                String sessionHashId = entry.getKey();
                                SSOPrincipal principal = entry.getValue();
                                invalidTokens.removeAsync(sessionHashId);
                                principals.fastPutAsync(sessionHashId, principalCodec.toString( (SSOPrincipalJson) principal));
                                lastActivity.fastPutAsync( sessionHashId, System.currentTimeMillis());
                                dbUpdated.addAsync(sessionHashId,  10, TimeUnit.MINUTES);
                            }


                        }
                );
    }

    @Override
    public void cache(String sessionHashId, SSOPrincipal principal) {
        new RBatchExecutor()
                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                        {
                            invalidTokens.removeAsync(sessionHashId);
                            principals.fastPutAsync(sessionHashId, principalCodec.toString( (SSOPrincipalJson) principal));
                            lastActivity.fastPutAsync( sessionHashId, System.currentTimeMillis());
                            dbUpdated.addAsync(sessionHashId,  10, TimeUnit.MINUTES);
                        }
                );

    }

    @Override
    public SSOPrincipal validateAndUpdateLastActivity( String sessionHashId, Callable<SSOPrincipal> validateFromDB, Callable<Boolean> updateMainDB ){
        SSOPrincipal principal;
        List<?> responses = new RBatchExecutor()
                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                        {
                            invalidTokens.containsAsync(sessionHashId);
                            principals.getAsync(sessionHashId);
                            lastActivity.fastPutAsync( sessionHashId, System.currentTimeMillis());
                            dbUpdated.containsAsync(sessionHashId);
                        }
                );
        boolean isInvalid = (Boolean) responses.get(0);
        String principalJson = (String) responses.get(1);
        boolean lastActivityUpdated = (Boolean) responses.get(2);
        boolean isDBUpdated = (Boolean) responses.get(3);
        if( isInvalid ){
            return null;
        }
        else if( principalJson != null ){
            principal = principalCodec.fromString( principalJson );
            if( !isDBUpdated ){
                boolean dbUpdated;
                try{
                    dbUpdated = updateMainDB.call();
                } catch( Exception e ){
                    dbUpdated = false;
                }
                if( dbUpdated ){
                    new RBatchExecutor()
                            .execute( (invalidTokens, principals, lastActivity, dbUpdatedCache) ->
                                    dbUpdatedCache.addAsync(sessionHashId, 1, TimeUnit.MINUTES)
                            );
                }
            }
        }
        else{
            try{
                principal = validateFromDB.call();
                if( principal != null ){
                    this.cache( sessionHashId, principal );
                }
                else{
                    this.invalidate( Collections.singletonList( sessionHashId ));
                }
            } catch ( Exception e){
                principal = null;
            }

        }
        return principal;
    }
    @Override
    public void invalidate(Collection<String> sessionHashIds) {
        for( String sessionHashId: sessionHashIds ){
            new RBatchExecutor()
                    .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                            {
                                invalidTokens.addAsync(sessionHashId, 10, TimeUnit.MINUTES);
                                principals.fastRemoveAsync( sessionHashId );
                                lastActivity.fastRemoveAsync( sessionHashId );
                                dbUpdated.removeAsync( sessionHashId );
                            }
                    );
        }
    }

    class RBatchExecutor{
        List<?> execute( BatchOperations operations){
            RBatch batch = redisson.createBatch(BatchOptions.defaults());
            operations.execute(
                    batch.getSetCache("user_tokens#invalid", StringCodec.INSTANCE),
                    batch.getMap("user_tokens#principals", StringCodec.INSTANCE),
                    batch.getMap("user_tokens#last_activity", LongCodec.INSTANCE),
                    batch.getSetCache("user_tokens#is_db_updated", StringCodec.INSTANCE)
            );
            return batch.execute().getResponses();
        }
    }
    interface BatchOperations{
        void execute(
                RSetCacheAsync<String> invalidTokens,
                RMapAsync<String, String> principals,
                RMapAsync<String, Long> lastActivity,
                RSetCacheAsync< String > dbUpdated
        );
    }

    interface PrincipalCodec {
        String toString( SSOPrincipalJson principal);
        SSOPrincipalJson fromString( String serialized );
    }

    class PrincipalCodecImpl implements PrincipalCodec {
        private Gson gson = new Gson();
        @Override
        public String toString(SSOPrincipalJson principal) {
            return gson.toJson( principal);
        }

        @Override
        public SSOPrincipalJson fromString(String serialized) {
            return gson.fromJson( serialized, SSOPrincipalJson.class);
        }
    }
}
