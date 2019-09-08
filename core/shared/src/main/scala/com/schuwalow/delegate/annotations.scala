package zio.delegate

import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly
import scala.language.experimental.macros

@compileTimeOnly("delegate annotation should have been removed.")
class delegate(verbose: Boolean = false, forwardObjectMethods: Boolean = false, generateTraits: Boolean = true)
    extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Macros.delegateImpl
}
