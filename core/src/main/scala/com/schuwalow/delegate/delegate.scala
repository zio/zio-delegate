package com.schuwalow.delegate

import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly
import scala.language.experimental.macros

@compileTimeOnly("delegate annotation should have been removed.")
class delegate(verbose: Boolean = true) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Macros.delegateImpl
}
