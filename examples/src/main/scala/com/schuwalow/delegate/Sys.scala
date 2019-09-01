package com.schuwalow.delegate

import zio.blocking.Blocking
import zio.clock.Clock

trait Sys extends Serializable {
  def sys: Sys.Service[Any]
}
object Sys {
  trait Service[R] extends Serializable

  trait Live extends Sys { self: Clock with Blocking =>
    def sys = new Service[Any] {}
  }

  def withSys[R <: Clock with Blocking](a: R)(implicit ev: R Mix Sys): R with Sys = {
    class SysInstance(@delegate underlying: Clock with Blocking) extends Live
    ev.mix(a, new SysInstance(a))
  }

  val env: Clock.Live with Blocking.Live with Sys = withSys[Clock with Blocking](new Clock.Live with Blocking.Live)
}
