package com.streamsets.spring

import com.streamsets.sch.{SSOPrincipal, SessionsManager}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.{PostMapping, RequestMapping, RequestParam, RestController}
import reactor.core.publisher.Mono

import java.util.Optional
import scala.jdk.OptionConverters.RichOption

@RestController
@RequestMapping(Array("/sessions"))
class SessionsController {
  @Autowired private val sessionManager: SessionsManager = null

  @PostMapping(Array("/create"))
  private def createSession(@RequestParam("expiresIn") expiresIn: Long): Mono[ String ] =
    Mono.just(this.sessionManager.createSessions(1, expiresIn).head)

  @PostMapping(Array("/validate"))
  private def validate(@RequestParam("token") token: String): Mono[ String ] = {
    Mono.fromFuture( this.sessionManager.validateAsync(token) )
      .map{
        case Some( principal ) => SSOPrincipal.serialize( principal )
        case None => "null"
      }
  }

  @PostMapping(Array("/invalidate"))
  private def invalidate(@RequestParam("token") token: String): Mono[Boolean] =
    Mono.just(this.sessionManager.invalidate(token))
}
