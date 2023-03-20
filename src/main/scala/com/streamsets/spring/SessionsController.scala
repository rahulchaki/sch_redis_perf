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
  private def validate(@RequestParam("token") token: String): Mono[ Optional[SSOPrincipal] ] = {
    Mono.just( this.sessionManager.validate(token).toJava )
  }

  @PostMapping(Array("/invalidate"))
  private def invalidate(@RequestParam("token") token: String): Mono[Boolean] =
    Mono.just(this.sessionManager.invalidate(token))
}

@RestController
@RequestMapping(Array("/sessions_async"))
class SessionsControllerAsync {
  @Autowired private val sessionManager: SessionsManager = null

  @PostMapping(Array("/create"))
  private def createSession(@RequestParam("expiresIn") expiresIn: Long): Mono[ String ] =
    this.sessionManager
      .createSessionsAsync( 1, expiresIn)
      .map( _.head )

  @PostMapping(Array("/validate"))
  private def validate(@RequestParam("token") token: String): Mono[ Optional[ SSOPrincipal] ] = {
    this.sessionManager
      .validateAsync(token)
      .map( _.toJava)
  }

  @PostMapping(Array("/invalidate"))
  private def invalidate(@RequestParam("token") token: String): Mono[Boolean] =
    this.sessionManager.invalidateAsync(token)
}