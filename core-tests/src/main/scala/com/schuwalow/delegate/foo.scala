package com.schuwalow.delegate

object foo {
  trait A[B] {
    val a: Int
    def foo[A](a: A): A
    def foo1[A: Ordering](): Unit = ()
  }

  trait C {
    def bar1(): Int = 1
  }

  trait B {
    def b2: Int = 0
    def bar(): Unit
  }
}
