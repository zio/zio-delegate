package com.schuwalow.delegate

class DelegateSpec extends UnitSpec {

  describe("delegate annotation") {
    it("should automatically extend traits") {
      trait Foo {
        def a: Int
      }
      {
        class Bar(@delegate foo: Foo) extends Foo
        assert((new Bar(new Foo { def a = 3 })).a == 3)
      }
    }
    it("should allow overrides") {
      trait Foo {
        def a: Int = 3
      }
      {
        class Bar(@delegate foo: Foo) extends Foo {
          override def a = 4
        }
        assert((new Bar(new Foo {})).a == 4)
      }
    }
    it("should handle final methods") {
      trait Foo {
        final def a: Int = 3
      }
      {
        class Bar(@delegate foo: Foo) extends Foo
        assert((new Bar(new Foo {})).a == 3)
      }
    }
    it("should handle locally visible symbols") {
      object Test {
        trait Foo {
          final def a: Int = 3
        }
      }
      {
        class Bar(@delegate foo: Test.Foo) extends Test.Foo
        assert((new Bar(new Test.Foo {})).a == 3)
      }
    }
    it("should work with abstract classes when explicitly extending") {
      abstract class Foo {
        def a: Int
      }
      {
        class Bar(@delegate foo: Foo) extends Foo
        assert((new Bar(new Foo { def a = 3 })).a == 3)
      }
    }
    it("should handle methods with same name but different signatures") {
      abstract class Foo {
        def a(i: Int): Int = 3
      }
      {
        class Bar(@delegate foo: Foo) extends Foo {
          def a(s: String) = "bar"
        }
        val inst = new Bar(new Foo {})
        assert(inst.a("") == "bar" && inst.a(0) == 3)
      }
    }
    it("should handle methods with same name but different signatures - 2") {
      abstract class Foo {
        def a(i: Int): Int = 3
      }
      abstract class Foo1 extends Foo {
        def a(s: String) = "bar"
      }
      {
        class Bar(@delegate foo: Foo1) extends Foo1
        val inst = new Bar(new Foo1 {})
        assert(inst.a("") == "bar" && inst.a(0) == 3)
      }
    }
  }

}
