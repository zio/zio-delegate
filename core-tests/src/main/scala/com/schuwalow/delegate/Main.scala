package com.schuwalow.delegate

import zio.blocking.Blocking

import zio._
import zio.clock.Clock
import zio.blocking.Blocking

trait Env extends clock.Clock.Live with console.Console.Live with scheduler.Scheduler
trait Foo {
  def a: Int = 0
}
trait Foo2 extends Foo {
  abstract override def a: Int = 3
}
abstract class Foo3 extends Foo {
  override def a: Int = 1
  def c: Int = 3
}
abstract class C1 extends Clock.Live

object Main extends _root_.scala.App {
  // val a = new A[Int] { def foo[A](a: A) = a; val a = 1 }
  // class Test(@delegate a1: A[Int], @delegate foo: B) extends A[Int] with B with C {
  //   override def b2: Int = 2
  //   override val a = 1
  // }

  // println(new Test(a, new B { override def b2: Int = 2 }).a)
  // // Delegate.delegate[A[Int], B with C](a, new B with C { def bar() = {println("bar")}})
  // // println(new Test(a).a)

  class WithRandom(@delegate(verbose = true, forwardObjectMethods = true) old: Foo with Foo2 with Foo3 with Blocking) extends C1 {
    override def a = 2
  }
  println(new WithRandom(new Foo3 with Foo with Foo2 with Blocking.Live {
    override def a = 4
  }).a)
}
