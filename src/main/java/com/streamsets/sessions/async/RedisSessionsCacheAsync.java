package com.streamsets.sessions.async;

import com.google.gson.Gson;
import com.streamsets.sessions.SSOPrincipal;
import com.streamsets.sessions.SSOPrincipalJson;
import org.redisson.api.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class RedisSessionsCacheAsync implements SessionsCacheAsync {

    private RedissonReactiveClient redisson;
    private PrincipalCodec principalCodec;
    public RedisSessionsCacheAsync( RedissonReactiveClient redisson ) {
        this.redisson = redisson;
        this.principalCodec = new PrincipalCodecImpl();
    }

    @Override
    public Mono<List<String>> allTokens() {
        RMapReactive<String, String> principals = this.redisson.getMap("user_tokens#principals", StringCodec.INSTANCE);
        return principals.keyIterator(1000).collectList();
    }

    @Override
    public Mono<Boolean> cacheAll(Map<String, SSOPrincipal> sessions) {
        return new RBatchExecutor()
                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                        {
                            for( Map.Entry<String, SSOPrincipal> entry: sessions.entrySet() ){
                                String sessionHashId = entry.getKey();
                                SSOPrincipal principal = entry.getValue();
                                invalidTokens.remove(sessionHashId);
                                principals.fastPut(sessionHashId, principalCodec.toString( (SSOPrincipalJson) principal));
                                lastActivity.fastPut( sessionHashId, System.currentTimeMillis());
                                dbUpdated.add(sessionHashId,  10, TimeUnit.MINUTES);
                            }


                        }
                )
                .map( responses -> true);
    }

    @Override
    public Mono<Boolean> cache(String sessionHashId, SSOPrincipal principal) {
        return new RBatchExecutor()
                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                        {
                            invalidTokens.remove(sessionHashId);
                            principals.fastPut(sessionHashId, principalCodec.toString( (SSOPrincipalJson) principal));
                            lastActivity.fastPut( sessionHashId, System.currentTimeMillis());
                            dbUpdated.add(sessionHashId,  10, TimeUnit.MINUTES);
                        }
                )
                .map( responses ->
                        {
                            boolean invalidTokenRemoved = (Boolean) responses.get(0);
                            boolean principalAdded = (Boolean) responses.get(1);
                            boolean lastActivityAdded = (Boolean) responses.get(2);
                            boolean dbUpdated = (Boolean) responses.get(3);
                            return invalidTokenRemoved && principalAdded ;
                        }
                );

    }

    @Override
    public Mono<Optional<SSOPrincipal>> validateAndUpdateLastActivity(String sessionHashId, Callable<SSOPrincipal> validateFromDB, Callable<Boolean> updateMainDB ){
        final Mono<Optional<SSOPrincipal>> emptyResult = Mono.just( Optional.empty() );
        return new RBatchExecutor()
                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                        {
                            invalidTokens.contains(sessionHashId);
                            principals.get(sessionHashId);
                            lastActivity.fastPut( sessionHashId, System.currentTimeMillis());
                            dbUpdated.contains(sessionHashId);
                        }
                )
                .flatMap( responses -> {
                    boolean isInvalid = (Boolean) responses.get(0);
                    String principalJson = (String) responses.get(1);
                    boolean lastActivityUpdated = (Boolean) responses.get(2);
                    boolean isDBUpdated = (Boolean) responses.get(3);
                    if( isInvalid ){
                        return emptyResult;
                    }
                    else if( principalJson != null ){
                        SSOPrincipal principal = principalCodec.fromString( principalJson );
                        if( !isDBUpdated ){
                            boolean dbUpdated;
                            try{
                                dbUpdated = updateMainDB.call();
                            } catch( Exception e ){
                                dbUpdated = false;
                            }
                            if( dbUpdated ){
                                return new RBatchExecutor()
                                        .execute( (invalidTokens, principals, lastActivity, dbUpdatedCache) ->
                                                dbUpdatedCache.add(sessionHashId, 10, TimeUnit.MINUTES)
                                        )
                                        .map( tmp -> Optional.of(principal));
                            }
                            else return Mono.just( Optional.of(principal) );
                        }
                        else return Mono.just( Optional.of(principal) );

                    }
                    else{
                        try{
                            SSOPrincipal principal = validateFromDB.call();
                            if( principal != null ){
                                return this.cache( sessionHashId, principal )
                                        .map( tmp -> Optional.of( principal));
                            }
                            else{
                                return this.invalidate( Collections.singletonList( sessionHashId ))
                                        .collectList()
                                        .map( tmp -> Optional.of( principal));
                            }
                        } catch ( Exception e){
                            return emptyResult;
                        }

                    }

                });
    }
    @Override
    public Flux<Boolean> invalidate(Collection<String> sessionHashIds) {
        return Flux
                .fromIterable( sessionHashIds )
                .flatMap( sessionHashId ->
                                new RBatchExecutor()
                                        .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
                                                {
                                                    invalidTokens.add(sessionHashId, 10, TimeUnit.MINUTES);
                                                    principals.fastRemove( sessionHashId );
                                                    lastActivity.fastRemove( sessionHashId );
                                                    dbUpdated.remove( sessionHashId );
                                                }
                                        )
                                        .map( responses ->
                                                {
                                                    boolean addedToInvalidTokens = (Boolean) responses.get(0);
                                                    boolean principalRemoved = (  (Long) responses.get(1) ) > 0;
                                                    boolean lastActivityRemoved = (  (Long) responses.get(2) ) > 0;
                                                    boolean dbUpdatedRemoved = (Boolean) responses.get(3);
                                                    return addedToInvalidTokens && principalRemoved;
                                                }
                                        )
                );
    }

    class RBatchExecutor{
        Mono<List<?>> execute(BatchOperations operations){
            RBatchReactive batch = redisson.createBatch(BatchOptions.defaults());
            operations.execute(
                    batch.getSetCache("user_tokens#invalid", StringCodec.INSTANCE),
                    batch.getMap("user_tokens#principals", StringCodec.INSTANCE),
                    batch.getMap("user_tokens#last_activity", LongCodec.INSTANCE),
                    batch.getSetCache("user_tokens#is_db_updated", StringCodec.INSTANCE)
            );
            return batch.execute().map(BatchResult::getResponses);
        }
    }
    interface BatchOperations{
        void execute(
                RSetCacheReactive<String> invalidTokens,
                RMapReactive<String, String> principals,
                RMapReactive<String, Long> lastActivity,
                RSetCacheReactive< String > dbUpdated
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
