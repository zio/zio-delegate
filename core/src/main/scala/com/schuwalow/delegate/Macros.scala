package com.schuwalow.delegate

import scala.reflect.macros.blackbox.Context
import scala.reflect.macros.TypecheckException

private[delegate] class Macros(val c: Context) {
  import c.universe._

  def mixImpl[A: WeakTypeTag, B: WeakTypeTag]: c.Tree = {
    val aTT = weakTypeOf[A]
    val bTT = weakTypeOf[B]

    // aT may extends a class bT may not as it will be mixed in
    preconditions(
      !aTT.typeSymbol.isFinal        -> s"${aTT.typeSymbol.toString()} must be nonfinal class or trait.",
      bTT.typeSymbol.asClass.isTrait -> s"${bTT.typeSymbol.toString()} needs to be a trait."
    )

    val aName          = TermName(c.freshName)
    val bName          = TermName(c.freshName)
    val resultType     = parseTypeString(s"${aTT.toString()} with ${bTT.toString()}")
    val resultTypeName = TypeName(c.freshName)
    q"""
    ${c.parse(s"abstract class $resultTypeName extends $resultType")}
    new Mix[$aTT, $bTT] {
      def mix($aName: $aTT, $bName: $bTT): ${resultType} = {
        new ${resultTypeName} {
          ..${(
      overlappingMethods(aTT, resultType).mapValues((aName, _)) ++
        overlappingMethods(bTT, resultType).mapValues((bName, _))
    ).filterNot { case (_, (_, m)) => isObjectMethod(m) }.map {
      case (name, (owner, m)) => delegateMethodDef(name, m, owner)
    }}
        }
      }
    }
    """
  }

  def delegateImpl(annottees: c.Expr[Any]*): c.Tree = {

    case class Arguments(verbose: Boolean, forwardObjectMethods: Boolean, generateTraits: Boolean)

    val args: Arguments = c.prefix.tree match {
      case Apply(_, args) =>
        val verbose: Boolean = args.collectFirst {
          case q"verbose = $cfg" =>
            c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(false)
        val forwardObjectMethods = args.collectFirst {
          case q"forwardObjectMethods = $cfg" =>
            c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(false)
        val generateTraits = args.collectFirst {
          case q"generateTraits = $cfg" =>
            c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(true)
        Arguments(verbose, forwardObjectMethods, generateTraits)
      case other => abort("not possible - macro invoked on type that does not have @delegate: " + showRaw(other))
    }

    def isBlackListed(m: MethodSymbol) =
      if (!args.forwardObjectMethods) isObjectMethod(m) else false

    def modifiedClass(classDecl: ClassDef, delegateTo: ValDef): c.Tree = {
      val q"..$mods class $className(..$fields) extends ..$bases { ..$body }" = classDecl
      val existingMethods = body
        .flatMap(
          tree =>
            tree match {
              case a @ DefDef(_, n, _, _, _, _) => Some(n)
              case a @ ValDef(_, n, _, _)       => Some(n)
              case _                            => None
            }
        )
        .toSet

      val (toName, toType) = typeCheckVal(delegateTo)
      val additionalTraits =
        if (args.generateTraits)
          getTraits(toType) -- bases.flatMap(b => getTraits(c.typecheck(b, c.TYPEmode).tpe)).toSet
        else Set.empty
      val resultType = parseTypeString(
        (bases.map(_.toString()) ++ additionalTraits.map(_.fullName).toList).mkString(" with ")
      )
      val extensions = overlappingMethods(toType, resultType, !isBlackListed(_)).filterNot {
        case (n, _) =>
          existingMethods.contains(n)
      }.map { case (name, m) => delegateMethodDef(name, m, toName) }

      val resultTypeName = TypeName(c.freshName)
      q"""
      ${c.parse(s"abstract class $resultTypeName extends $resultType")}
      $mods class $className(..$fields) extends $resultTypeName { ..${body ++ extensions} }
      """
    }

    annottees.map(_.tree) match {
      case (valDecl: ValDef) :: (classDecl: ClassDef) :: Nil =>
        val modified = modifiedClass(classDecl, valDecl)
        if (args.verbose) showInfo(modified.toString())
        modified
      case _ => abort("Invalid annottee")
    }
  }

  private[this] def delegateMethodDef(name: TermName, m: MethodSymbol, to: TermName) = {
    val rType = m.returnType
    val mods =
      if (!m.isAbstract) Modifiers(Flag.OVERRIDE)
      else Modifiers()

    if (m.isVal) {
      q"$mods val $name: $rType = $to.$name"
    } else {
      val typeParams = m.typeParams.map(internal.typeDef(_))
      val paramLists = m.paramLists.map(_.map(internal.valDef(_)))
      q"""
      $mods def $name[..${typeParams}](...$paramLists): $rType = {
        $to.${name}(...${paramLists.map(_.map(_.name))})
      }
      """
    }
  }

  private[this] def isObjectMethod(m: MethodSymbol): Boolean =
    Set(
      "java.lang.Object.clone",
      "java.lang.Object.hashCode",
      "java.lang.Object.finalize",
      "java.lang.Object.equals",
      "java.lang.Object.toString",
      "scala.Any.getClass"
    ).contains(m.fullName)

  private[this] def getTraits(t: Type): Set[ClassSymbol] = {
    def loop(stack: List[ClassSymbol], traits: Vector[ClassSymbol] = Vector()): Vector[ClassSymbol] = stack match {
      case x :: xs =>
        loop(xs, if (x.isTrait) traits :+ x else traits)
      case Nil => traits
    }
    loop(t.baseClasses.map(_.asClass)).toSet
  }

  private[this] val typeCheckVal: ValDef => (TermName, Type) = {
    case ValDef(_, tname, tpt, _) =>
      val tpe = try {
        c.typecheck(tpt.duplicate, c.TYPEmode).tpe
      } catch {
        case e: TypecheckException => abort(s"Type ${tpt.toString()} needs a stable reference.")
      }
      (tname, tpe)
  }

  private[this] def parseTypeString(str: String): Type =
    try {
      c.typecheck(c.parse(s"null.asInstanceOf[$str]"), c.TYPEmode).tpe
    } catch {
      case _: TypecheckException =>
        abort(s"Failed typechecking calculated type $str")
    }

  private[this] def overlappingMethods(
    from: Type,
    to: Type,
    filter: MethodSymbol => Boolean = _ => true
  ): Map[TermName, MethodSymbol] = {
    val enclosing = c.enclosingClass match {
      case clazz if clazz.isEmpty => c.enclosingPackage.symbol.fullName
      case clazz                  => clazz.symbol.fullName
    }
    def isVisible(m: MethodSymbol) =
      m.isPublic || enclosing.startsWith(m.privateWithin.fullName)

    to.baseClasses
      .map(_.asClass.selfType)
      .filter(from <:< _)
      .flatMap { s =>
        s.members
          .flatMap(m => to.member(m.name).alternatives.map(_.asMethod))
          .filter(m => !m.isConstructor && !m.isFinal && isVisible(m) && filter(m))
      }
      .map(m => (m.name -> m))
      .toMap
  }

  private[this] def showInfo(s: String) =
    c.info(c.enclosingPosition, s.split("\n").mkString("\n |---macro info---\n |", "\n |", ""), true)

  private[this] def abort(s: String) =
    c.abort(c.enclosingPosition, s)

  private[this] def preconditions(conds: (Boolean, String)*): Unit =
    conds.foreach {
      case (cond, s) =>
        if (!cond) abort(s)
    }

}
