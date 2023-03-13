//package com.streamsets;
//
//import com.google.gson.Gson;
//import org.redisson.api.*;
//import org.redisson.client.codec.LongCodec;
//import org.redisson.client.codec.StringCodec;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.util.Collection;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.Callable;
//import java.util.concurrent.TimeUnit;
//
//public class RedisSessionsCacheAsync implements SessionsCacheReactive {
//
//    private RedissonReactiveClient redisson;
//    private RedisSessionsCache.PrincipalCodec principalCodec;
//    public RedisSessionsCacheAsync( RedissonReactiveClient redisson ) {
//        this.redisson = redisson;
//        this.principalCodec = new PrincipalCodecImpl();
//    }
//
//    @Override
//    public Mono<Boolean> cache(String sessionHashId, SSOPrincipal principal) {
//        return new RBatchExecutor()
//                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
//                        {
//                            invalidTokens.remove(sessionHashId);
//                            principals.fastPut(sessionHashId, principalCodec.toString( (SSOPrincipalJson) principal));
//                            lastActivity.fastPut( sessionHashId, System.currentTimeMillis());
//                            dbUpdated.add(sessionHashId,  10, TimeUnit.MINUTES);
//                        }
//                )
//                .map( responses ->
//                        {
//                            boolean invalidTokenRemoved = (Boolean) responses.get(0);
//                            boolean principalAdded = (Boolean) responses.get(1);
//                            boolean lastActivityAdded = (Boolean) responses.get(2);
//                            boolean dbUpdated = (Boolean) responses.get(3);
//                            return invalidTokenRemoved && principalAdded ;
//                        }
//                );
//
//    }
//
//    @Override
//    public Mono<SSOPrincipal> validateAndUpdateLastActivity(String sessionHashId, Callable<SSOPrincipal> validateFromDB, Callable<Boolean> updateMainDB ){
//        SSOPrincipal principal;
//        List<?> responses = new RBatchExecutor()
//                .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
//                        {
//                            invalidTokens.containsAsync(sessionHashId);
//                            principals.getAsync(sessionHashId);
//                            lastActivity.fastPutAsync( sessionHashId, System.currentTimeMillis());
//                            dbUpdated.containsAsync(sessionHashId);
//                        }
//                );
//        boolean isInvalid = (Boolean) responses.get(0);
//        String principalJson = (String) responses.get(1);
//        boolean lastActivityUpdated = (Boolean) responses.get(2);
//        boolean isDBUpdated = (Boolean) responses.get(3);
//        if( isInvalid ){
//            return null;
//        }
//        else if( principalJson != null ){
//            principal = principalCodec.fromString( principalJson );
//            if( !isDBUpdated ){
//                boolean dbUpdated;
//                try{
//                    dbUpdated = updateMainDB.call();
//                } catch( Exception e ){
//                    dbUpdated = false;
//                }
//                if( dbUpdated ){
//                    new RedisSessionsCache.RBatchExecutor()
//                            .execute( (invalidTokens, principals, lastActivity, dbUpdatedCache) ->
//                                    dbUpdatedCache.addAsync(sessionHashId, 1, TimeUnit.MINUTES)
//                            );
//                }
//            }
//        }
//        else{
//            try{
//                principal = validateFromDB.call();
//                if( principal != null ){
//                    this.cache( sessionHashId, principal );
//                }
//                else{
//                    this.invalidate( Collections.singletonList( sessionHashId ));
//                }
//            } catch ( Exception e){
//                principal = null;
//            }
//
//        }
//        return principal;
//    }
//    @Override
//    public Flux<Boolean> invalidate(Collection<String> sessionHashIds) {
//        return Flux
//                .fromIterable( sessionHashIds )
//                .flatMap( sessionHashId ->
//                                new RBatchExecutor()
//                                        .execute( (invalidTokens, principals, lastActivity, dbUpdated) ->
//                                                {
//                                                    invalidTokens.add(sessionHashId, 10, TimeUnit.MINUTES);
//                                                    principals.fastRemove( sessionHashId );
//                                                    lastActivity.fastRemove( sessionHashId );
//                                                    dbUpdated.remove( sessionHashId );
//                                                }
//                                        )
//                                        .map( responses ->
//                                                {
//                                                    boolean addedToInvalidTokens = (Boolean) responses.get(0);
//                                                    boolean principalRemoved = (  (Long) responses.get(1) ) > 0;
//                                                    boolean lastActivityRemoved = (  (Long) responses.get(2) ) > 0;
//                                                    boolean dbUpdatedRemoved = (Boolean) responses.get(3);
//                                                    return addedToInvalidTokens && principalRemoved;
//                                                }
//                                        )
//                );
//    }
//
//    class RBatchExecutor{
//        Mono<List<?>> execute(BatchOperations operations){
//            RBatchReactive batch = redisson.createBatch(BatchOptions.defaults());
//            operations.execute(
//                    batch.getSetCache("user_tokens#invalid", StringCodec.INSTANCE),
//                    batch.getMap("user_tokens#principals", StringCodec.INSTANCE),
//                    batch.getMap("user_tokens#last_activity", LongCodec.INSTANCE),
//                    batch.getSetCache("user_tokens#is_db_updated", StringCodec.INSTANCE)
//            );
//            return batch.execute().map(BatchResult::getResponses);
//        }
//    }
//    interface BatchOperations{
//        void execute(
//                RSetCacheReactive<String> invalidTokens,
//                RMapReactive<String, String> principals,
//                RMapReactive<String, Long> lastActivity,
//                RSetCacheReactive< String > dbUpdated
//        );
//    }
//
//    interface PrincipalCodec {
//        String toString( SSOPrincipalJson principal);
//        SSOPrincipalJson fromString( String serialized );
//    }
//
//    class PrincipalCodecImpl implements RedisSessionsCache.PrincipalCodec {
//        private Gson gson = new Gson();
//        @Override
//        public String toString(SSOPrincipalJson principal) {
//            return gson.toJson( principal);
//        }
//
//        @Override
//        public SSOPrincipalJson fromString(String serialized) {
//            return gson.fromJson( serialized, SSOPrincipalJson.class);
//        }
//    }
//}
