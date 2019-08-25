package com.schuwalow.delegate

import zio.blocking.Blocking

import zio._
import zio.clock.Clock
import zio.blocking.Blocking

trait Env extends clock.Clock.Live with console.Console.Live with scheduler.Scheduler
trait Foo {
  def a: Int = 0
}

trait Foo4 extends Foo {
  override def a: Int = 4
}
trait Foo2 extends Foo {
  abstract override def a: Int = 3
}
abstract class Foo3 extends Foo {
  override def a: Int = 1
  def c: Int = 3
}
trait Bar {
  protected[delegate] def bar = 4
}
abstract class C1 extends Clock.Live

object Main extends _root_.scala.App {

  println(new Foo3 with Foo4 {}.a)

  class WithRandom(@delegate(verbose = true) old: Foo with Foo2 with Foo3 with Blocking with Bar) extends C1 {
    override def a = 2
  }

  println(new WithRandom(new Foo3 with Foo with Foo2 with Blocking.Live with Bar {
    override def a = 4
    override def bar = 2
  }).bar)
}
