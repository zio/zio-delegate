package com.schuwalow.delegate

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object Delegate extends App {

  def delegate[A, B](a: A, b: B): A = macro Macros.delegateImplementation[A, B]

}

class Macros(val c: Context) {
  import c.universe._

  final def fail(msg: String): Nothing =
    c.abort(c.enclosingPosition, msg)

  final def warn(msg: String): Unit =
    c.warning(c.enclosingPosition, msg)

  def isObjectLikeThing(t: Type): Boolean =
    t =:= typeOf[AnyRef] || t =:= typeOf[AnyVal] || t =:= typeOf[Any] || t =:= typeOf[Object]

  def filterOutObjectLikeThings(types: List[Type]): List[Type] =
    types filterNot isObjectLikeThing

  def getTypeComponents(t: Type): List[Type] = t.dealias match {
    case RefinedType(parents, _) => parents.flatMap( p => getTypeComponents(p) )
    case t => List(t)
  }

  def delegateImplementation[A: c.WeakTypeTag, B: c.WeakTypeTag](a: c.Expr[A], b: c.Expr[B]): c.Tree = {
    val tA = weakTypeOf[A]
    val tB = weakTypeOf[B]
    val a1 = Ident(TermName(c.freshName))
    val b1 = Ident(TermName(c.freshName))

    val decls = forwarderDecls0(a1, tA) ++ forwarderDecls0(b1, tB)

    val types = filterOutObjectLikeThings((getTypeComponents(tA) ++ getTypeComponents(tB)).distinct)
    val superType = types.map(_.toString).mkString(" with ")
    val resultTypeName = Ident(TypeName(c.freshName))
    val impl = q"""
    ${c.parse(s"class $resultTypeName extends $superType")}
    val $a1: $tA = $a
    val $b1: $tB = $b
    new $resultTypeName { ..$decls }
    """
    showInfo(impl.toString())
    impl
  }

  def forwarderDecls0(a: Ident, tA: Type) = {
    tA.baseClasses.map(_.info).flatMap(forwarderDecls(a, _))
  }

  def forwarderDecls(a: Ident, tA: Type) = {
    tA.decls.map(_.asMethod).filter(m => typeOf[AnyVal].decls.exists(m1 => !(m1.asMethod.name == m.name))).filter(m => !m.isConstructor && !m.isFinal && m.isPublic).map { m =>
      val name = m.name
      val rType = m.returnType
      if (m.isVal) {
        q"""
        override val $name: $rType = $a.$name
        """
      } else {
        val typeParams =  m.typeParams.map(internal.typeDef(_))
        val paramLists = m.paramLists.map(_.map(internal.valDef(_)))
        q"""
        override def $name[..${typeParams}](...$paramLists): $rType = {
          $a.${name}(...${paramLists.map(_.map(_.name))})
        }
        """
      }
    }
  }

  def showInfo(s: String) =
    c.info(c.enclosingPosition, s.split("\n").mkString("\n |---macro info---\n |", "\n |", ""), true)
}
