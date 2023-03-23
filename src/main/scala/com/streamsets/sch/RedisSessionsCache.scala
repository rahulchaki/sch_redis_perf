package com.streamsets.sch

import com.streamsets.{RedisUtils, Settings}
import org.redisson.api.{BatchOptions, RMap, RedissonClient}
import org.redisson.client.codec.StringCodec
import reactor.core.publisher.{Flux, Mono}

import java.time.Duration
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.jdk.CollectionConverters._

class RedisSessionsCacheManager( redisson: RedissonClient ) {

  private def newHandler( sessionHashId: String ) = new RedisTokenHandler( redisson.getMap[String, AnyRef]( sessionHashId, StringCodec.INSTANCE ) )
  def allTokens(): Set[String] = redisson.reactive()
    .getKeys.getKeys(1000).collectList().toFuture.get().asScala.toSet

  def cacheAll(sessions: Map[String, SSOPrincipal]): Unit = {
    val batch = redisson.createBatch()
    sessions.foreach {
      case (sessionHashId, principal) =>
        val handler = batch.getMap[String, AnyRef](sessionHashId, StringCodec.INSTANCE)
        handler.putAllAsync(RedisEntry.toMap(SSOPrincipal.serialize(principal)).asJava)
    }
    batch.execute()
  }

  def cache(sessionHashId: String, principal: SSOPrincipal): Unit = {
    val handler = newHandler(sessionHashId)
    handler.create(SSOPrincipal.serialize(principal))
  }

  private def onPrincipalNotFound(
                                   sessionHashId: String,
                                   handler: RedisTokenHandler,
                                   validateFromDB: String => CompletableFuture[ Option[SSOPrincipal] ]
                                 ): CompletableFuture[ Option[SSOPrincipal] ]= {
    validateFromDB( sessionHashId )
      .thenApply{ result =>
        result match {
          case Some(principal) =>
            handler.create(SSOPrincipal.serialize(principal))
          case None =>
            handler.invalidate()
        }
        result
      }
    }


  private def onPrincipalFound(
                                sessionHashId: String,
                                handler: RedisTokenHandler,
                                session: Session,
                                updateMainDB: String => CompletableFuture[ Boolean ]
                              ): CompletableFuture[Option[SSOPrincipal]] = {

    val principal = Some(SSOPrincipal.deserialize(session.principal))
    val result = if ((System.currentTimeMillis() - session.lastDBUpdated) > Duration.ofMinutes(10).toMillis) {
      updateMainDB( sessionHashId )
        .thenCompose( toUpdateDB =>  handler.updateLastActivity(toUpdateDB) )
    } else {
      handler.updateLastActivity(false)
    }
    result
      .thenApply( _ => principal )
  }

  private def onCacheFound(
                    sessionHashId: String,
                    handler: RedisTokenHandler,
                    entry: RedisEntry,
                    validateFromDB: String => CompletableFuture[ Option[SSOPrincipal] ],
                    updateMainDB: String => CompletableFuture[ Boolean ]
                  ): CompletableFuture[Option[SSOPrincipal]] = {
    entry.isInvalid match {
      case Some(true) => CompletableFuture.completedFuture( None )
      case Some(false) | None =>
        entry.session match {
          case Some(session) =>
            onPrincipalFound( sessionHashId, handler, session, updateMainDB )
          case None =>
            onPrincipalNotFound( sessionHashId, handler, validateFromDB )
        }
    }
  }

  def validateAndUpdateLastActivity(
                                     sessionHashId: String,
                                     validateFromDB: String => CompletableFuture[ Option[SSOPrincipal] ],
                                     updateMainDB: String => CompletableFuture[ Boolean ]
                                   ): CompletableFuture[Option[SSOPrincipal]] = {
    val handler = newHandler(sessionHashId)
    handler.read()
      .thenCompose{
        case Some( entry ) =>
          onCacheFound( sessionHashId, handler, entry, validateFromDB, updateMainDB )
        case None =>
          onPrincipalNotFound( sessionHashId, handler, validateFromDB )
      }
  }

  def invalidate(sessionHashId: String): Boolean = {
    val handler = newHandler(sessionHashId)
    handler.invalidate()
  }
}

class RedisTokenHandler( redis: RMap[ String, AnyRef ] ) {
  def create(principal: String): Unit = {
    val updates = RedisEntry.toMap(principal).asJava
    redis.putAll(updates)
  }

  def read(): CompletableFuture[Option[RedisEntry]] = {
    redis.readAllMapAsync()
      .thenApply { raw =>
        val data = raw.asScala.toMap.asInstanceOf[Map[String, String]]
        if (data.isEmpty) None
        else {
          Some(RedisEntry.fromMap(data))
        }
      }.toCompletableFuture
  }

  def updateLastActivity(hasDBUpdated: Boolean): CompletableFuture[Boolean] = {
    val now = java.lang.Long.valueOf(System.currentTimeMillis())
    if (hasDBUpdated) {
      redis.putAllAsync(
        Map(
          RedisEntry.LAST_ACTIVITY_AT -> now,
          RedisEntry.LAST_DB_UPDATED -> now
        ).asJava
      )
        .thenApply(_ => true)
        .toCompletableFuture

    }
    else {
      redis
        .fastPutAsync(RedisEntry.LAST_ACTIVITY_AT, now)
        .thenApply(_.booleanValue())
        .toCompletableFuture

    }
  }

  def invalidate(): Boolean = {
    val isExpired = redis.expire( Duration.ofSeconds(5) )
    redis.fastPut(RedisEntry.IS_INVALID, java.lang.Boolean.TRUE)
    isExpired
  }
}


case class Session( lastActivity: Long, lastDBUpdated: Long, principal: String )
case class RedisEntry( isInvalid: Option[Boolean], session: Option[Session] )
object RedisEntry{

  val IS_INVALID = "IS_INVALID"
  val SSO_PRINCIPAL = "SSO_PRINCIPAL"
  val LAST_ACTIVITY_AT = "LAST_ACTIVITY_AT"
  val LAST_DB_UPDATED = "LAST_DB_UPDATED"

  def toMap( principal: String ): Map[ String, AnyRef ] = {
    val now = java.lang.Long.valueOf(System.currentTimeMillis())
    Map(
      RedisEntry.IS_INVALID -> java.lang.Boolean.FALSE,
      RedisEntry.SSO_PRINCIPAL -> principal,
      RedisEntry.LAST_ACTIVITY_AT -> now,
      RedisEntry.LAST_DB_UPDATED -> now
    )
  }
  def fromMap( data: Map[String, String ] ): RedisEntry = {
    val isInvalid = data.get( IS_INVALID).map( _.toBoolean )
    val principal = data.get(SSO_PRINCIPAL)
    principal match {
      case Some(value) =>
        val lastActivity = data(LAST_ACTIVITY_AT).toLong
        val lastDBUpdated = data(LAST_DB_UPDATED).toLong
        RedisEntry( isInvalid , Some( Session( lastActivity, lastDBUpdated, value ) ) )
      case None =>
        RedisEntry( isInvalid, None )
    }
  }
}

object RedisTokenHandlerTest extends App {
  val conf = Settings.load()
  val redisson = RedisUtils.setUpRedisson(conf)

  val created = Flux.fromIterable( ( 0 until 100 ).toList.asJava )
    .map{ i =>
      val handler = new RedisTokenHandler(redisson.getMap(s"token_$i", StringCodec.INSTANCE))
      handler.create(s"{name:abs_${i}}")
    }
    .collectList()
    .toFuture.get()

  println(s" Created tokens ${created.size()}")

  val principals = Flux.fromIterable((0 until 100).toList.asJava)
    .flatMap { i =>
      val handler = new RedisTokenHandler(redisson.getMap(s"token_$i", StringCodec.INSTANCE))
      Mono.fromFuture( handler.read() )
    }
    .collectList()
    .toFuture.get()

  println(s" Read tokens ${principals.size()} first ${principals.get(0)}")

}