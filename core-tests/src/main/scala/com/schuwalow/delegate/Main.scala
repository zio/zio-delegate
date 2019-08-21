package com.schuwalow.delegate

import foo._

object Main extends App {

  val a = new A[Int] { def foo[A](a: A) = a; val a = 1 }
  class Test(@Delegate0 a1: A[Int], @Delegate0 foo: B) extends A[Int] with B

  println(new Test(a, new B { val bar = () }).a)
  // Delegate.delegate[A[Int], B with C](a, new B with C { def bar() = {println("bar")}})
  // println(new Test(a).a)
}
