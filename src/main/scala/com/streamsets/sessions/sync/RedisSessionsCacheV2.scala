package com.streamsets.sessions.sync
import com.google.gson.Gson
import com.streamsets.sessions.{SSOPrincipal, SSOPrincipalJson}
import com.streamsets.{RedisUtils, Settings}
import org.redisson.api.{BatchOptions, RMap, RMapReactive, RedissonClient}
import org.redisson.client.codec.StringCodec
import reactor.core.publisher.{Flux, Mono}

import java.time.Duration
import scala.jdk.CollectionConverters._

class RedisSessionsCacheV2( redisson: RedissonClient ) {

  private val gson = new Gson

  private def newHandler( sessionHashId: String ) = new RedisHandler( redisson.getMap( sessionHashId, StringCodec.INSTANCE ) )

  def allTokens(): List[String] = redisson.getKeys.getKeysStream(1000).toList.asScala.toList
  def cacheAll(sessions: Map[String, SSOPrincipal]): Unit = {
    val batch = redisson.createBatch( BatchOptions.defaults().skipResult() )
    sessions.foreach{
      case ( sessionHashId, principal ) =>
        val handler = batch.getMap[String, AnyRef]( sessionHashId, StringCodec.INSTANCE )
        handler.putAllAsync( RedisEntry.toMap(gson.toJson( principal) ).asJava )
    }
    batch.execute()
  }

  def cache(sessionHashId: String, principal: SSOPrincipal): Unit = {
    val handler = newHandler( sessionHashId )
    handler.create( gson.toJson( principal) )
  }

  def validateAndUpdateLastActivity( sessionHashId: String, validateFromDB: () => Option[SSOPrincipal], updateMainDB: () => Boolean ): Option[SSOPrincipal] = {
    val handler = newHandler( sessionHashId )
    def principalNotFound(): Option[SSOPrincipal] = {
      validateFromDB() match {
        case Some( principal ) =>
          handler.create( gson.toJson( principal) )
          Some( principal )
        case None =>
          invalidate( List( sessionHashId ) )
          None
      }
    }
    handler.read() match {
      case Some( entry ) =>
        entry.isInvalid match {
          case Some( true ) => None
          case None | Some( false ) =>
            entry.session match {
              case Some( session ) =>
                val toUpdateDB = if( (System.currentTimeMillis() - session.lastDBUpdated) > Duration.ofMinutes(1).toMillis ){
                  updateMainDB()
                } else false
                handler.updateLastActivity( toUpdateDB )
                Some( gson.fromJson(session.principal, classOf[SSOPrincipalJson]) )
              case None => principalNotFound()
            }
        }
      case None => principalNotFound()
    }
  }

  def invalidate(sessionHashIds: List[String]): Boolean = {
    ! sessionHashIds.map{ sessionHashId =>
      val handler = newHandler( sessionHashId )
      handler.invalidate()
    }.contains( false )
  }

}

class RedisSessionsCacheV2Async( redissonClient : RedissonClient ) {

  private val redisson = redissonClient.reactive()

  private val gson = new Gson

  private def newHandler( sessionHashId: String ) = new RedisHandlerAsync( redisson.getMap( sessionHashId, StringCodec.INSTANCE ) )

  def allTokens(): Mono[ Set[String] ] = redisson
    .getKeys.getKeys(1000).collectList()
    .map( _.asScala.toSet )
  def cacheAll(sessions: Map[String, SSOPrincipal]): Mono[ Unit ] = {
    val batch = redisson.createBatch( BatchOptions.defaults().skipResult() )
    sessions.foreach{
      case ( sessionHashId, principal ) =>
        val handler = batch.getMap[String, AnyRef]( sessionHashId, StringCodec.INSTANCE )
        handler.putAll( RedisEntry.toMap(gson.toJson( principal) ).asJava )
    }
    batch.execute().map( _ => () )
  }

  def cache(sessionHashId: String, principal: SSOPrincipal): Mono[ Void ] = {
    val handler = newHandler( sessionHashId )
    handler.create( gson.toJson( principal) )
  }

  def validateAndUpdateLastActivity(
                                     sessionHashId: String,
                                     validateFromDB: () => Option[SSOPrincipal],
                                     updateMainDB: () => Boolean
                                   ): Mono[ Option[SSOPrincipal] ] = {
    val handler = newHandler( sessionHashId )
    def principalNotFound(): Mono[ Option[SSOPrincipal] ] = {
      validateFromDB() match {
        case Some( principal ) =>
          handler
            .create( gson.toJson( principal) )
            .map( _ => Some( principal ) )
        case None =>
          invalidate( List( sessionHashId ) )
            .map( _ => None )
      }
    }

    handler.read().flatMap {
      case Some(entry) =>
        entry.isInvalid match {
          case Some(true) => Mono.just(None)
          case None | Some(false) =>
            entry.session match {
              case Some(session) =>
                val toUpdateDB = if ((System.currentTimeMillis() - session.lastDBUpdated) > Duration.ofMinutes(1).toMillis) {
                  updateMainDB()
                } else false
                handler
                  .updateLastActivity(toUpdateDB)
                  .map(_ =>
                    Some(gson.fromJson(session.principal, classOf[SSOPrincipalJson]))
                  )

              case None => principalNotFound()
            }
        }
      case None => principalNotFound()
    }
  }

  def invalidate( sessionHashId: String ): Mono[ Boolean ] = {
    val handler = newHandler(sessionHashId)
    handler.invalidate()
  }
  def invalidate(sessionHashIds: List[String]): Mono[ Boolean ] = {
    Flux.fromIterable( sessionHashIds.asJava )
      .flatMap{ sessionHashId =>
        val handler = newHandler(sessionHashId)
        handler.invalidate()
      }
      .collectList()
      .map( results => results.asScala.forall( _ == true ) )

  }

}

//object RedisHandler {
//  def readAndUpdateLastActivity( sessionHashId: String, batch: RBatch ): Option[RedisEntry] = {
//    val handler = batch.getMap[String, AnyRef](sessionHashId, StringCodec.INSTANCE)
//    handler.getAllAsync(  Set( RedisEntry.IS_INVALID, RedisEntry.LAST_DB_UPDATED, RedisEntry.SSO_PRINCIPAL ).asJava )
//    handler.putAsync( RedisEntry.LAST_ACTIVITY_AT, System.currentTimeMillis() )
//    val responses = batch.execute().getResponses
//    val rest = responses.get( 0 ).asInstanceOf[ java.util.Map[ String, String ]]
//    rest.put( RedisEntry.LAST_ACTIVITY_AT,  responses.get( 1 ).asInstanceOf[String] )
//    if (rest.isEmpty) None
//    else {
//      Some(RedisEntry.fromMap(data))
//    }
//
//  }
//}

class RedisHandlerAsync( redis: RMapReactive[ String, AnyRef ] ){
  def create( principal: String ): Mono[ Void ] = {
    val updates = RedisEntry.toMap(principal).asJava
    redis.putAll(updates)
  }
  def read(): Mono[ Option[RedisEntry] ] = {
    redis.readAllMap().map{ raw =>
      val data = raw.asScala.toMap.asInstanceOf[Map[String, String]]
      if (data.isEmpty) None
      else {
        Some(RedisEntry.fromMap(data))
      }
    }
  }

  def updateLastActivity(hasDBUpdated: Boolean): Mono[ Void ] = {
    val now = java.lang.Long.valueOf(System.currentTimeMillis())
    val updates = if (hasDBUpdated) {
      Map(RedisEntry.LAST_ACTIVITY_AT -> now, RedisEntry.LAST_DB_UPDATED -> now)
    }
    else {
      Map(RedisEntry.LAST_ACTIVITY_AT -> now)
    }
    redis.putAll(updates.asJava)
  }

  def invalidate(): Mono[ Boolean ] = {
    redis.expire( Duration.ofMinutes(1) )
      .flatMap { isExpired =>
        redis
          .fastPut(RedisEntry.IS_INVALID, java.lang.Boolean.TRUE)
          .map( _ => isExpired )
      }
  }


}
class RedisHandler( redis: RMap[ String, AnyRef ] ){

  def create( principal: String ): Unit = {
    val updates = RedisEntry.toMap( principal ).asJava
    redis.putAll( updates )
  }
  def read(): Option[RedisEntry] = {
    val data = redis.readAllMap().asScala.toMap.asInstanceOf[ Map[String, String ]]
    if( data.isEmpty) None
    else {
      Some( RedisEntry.fromMap( data ) )
    }

  }

  def updateLastActivity( hasDBUpdated: Boolean ): Unit = {
    val now = java.lang.Long.valueOf( System.currentTimeMillis() )
    val updates = if( hasDBUpdated ){
      Map( RedisEntry.LAST_ACTIVITY_AT -> now, RedisEntry.LAST_DB_UPDATED -> now )
    }
    else{
      Map( RedisEntry.LAST_ACTIVITY_AT -> now )
    }
    redis.putAll( updates.asJava )
  }

  def invalidate(): Boolean = {
    val expirySet = redis.expire( Duration.ofMinutes(1))
    redis.fastPut(RedisEntry.IS_INVALID, java.lang.Boolean.TRUE )
    expirySet
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


object RedisHandlerTest extends App {
  val conf = Settings.load()
  val redisson = RedisUtils.setUpRedisson(conf)
  ( 0 until 100 ).foreach{ i =>
    val handler = new RedisHandler(redisson.getMap(s"token_$i", StringCodec.INSTANCE))
    val now = System.currentTimeMillis()
    handler.create("{name:abs}")
    println(handler.read())
  }

}