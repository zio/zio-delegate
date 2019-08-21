package com.schuwalow.delegate

import foo._

object Main extends App {

  val a = new A[Int] { def foo[A](a: A) = a; val a = 1 }
  class Test(@delegate a1: A[Int], @delegate foo: B) extends A[Int] with B {
    override def b2: Int = 0
    override val a = 1
  }

  println(new Test(a, new B { override def b2: Int = 2 }).a)
  // Delegate.delegate[A[Int], B with C](a, new B with C { def bar() = {println("bar")}})
  // println(new Test(a).a)
}
