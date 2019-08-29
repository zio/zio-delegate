package com.schuwalow.delegate

import zio._
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

  def withSys[A <: Clock with Blocking](a: A)(implicit ev: A Mix Sys): A with Sys = {
    class SysInstance(@delegate a1: Clock with Blocking) extends Live
    ev.mix(a, new SysInstance(a))
  }
}


object Main extends _root_.scala.App {

  class Foo {
    def foo: Int = 2
  }
  trait Bar {
    def bar: Int
  }
  def withBar[A](a: A)(implicit ev: Mix[A, Bar]): A with Bar = {
    ev.mix(a, new Bar { def bar = 2 })
  }
  println(withBar[Foo](new Foo()).bar) // 2

}
