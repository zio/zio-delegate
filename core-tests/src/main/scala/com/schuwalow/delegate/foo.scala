package com.schuwalow.delegate

object foo {

  trait C { self: B =>
    def bar1(): Int = 1
  }

  trait B {
    protected def b2: Int
    // def bar(int: Int = 1): Unit
  }

  trait A[B] {
    val a: Int
    def foo[A](a: A): A
    def foo2(i: Int): Int         = 0
    def foo2(str: String): String = ""
    def foo1[A: Ordering](): Unit = ()
  }
}
