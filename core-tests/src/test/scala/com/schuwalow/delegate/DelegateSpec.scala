package com.schuwalow.delegate

class DelegateSpec extends UnitSpec {

  describe("delegate annotation") {
    it("should automatically extend traits") {
      trait Foo {
        def a: Int
      }
      {
        class Bar(@delegate foo: Foo)
        assert((new Bar(new Foo { def a = 3 })).a == 3)
      }
    }
    it("should allow overrides") {
      trait Foo {
        def a: Int = 3
      }
      {
        class Bar(@delegate foo: Foo) {
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
        class Bar(@delegate foo: Foo)
        assert((new Bar(new Foo {})).a == 3)
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
  }

}
