package com.schuwalow.delegate

import scala.reflect.macros.blackbox.Context
import scala.reflect.macros.TypecheckException

class Macros(val c: Context) {
  import c.universe._

  def delegateImpl(annottees: c.Expr[Any]*): c.Tree = {

    case class Arguments(verbose: Boolean, forwardObjectMethods: Boolean, generateTraits: Boolean)

    val args: Arguments = c.prefix.tree match {
      case Apply(_, args) =>
        val verbose: Boolean = args.collectFirst { case q"verbose = $cfg" =>
          c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(false)
        val forwardObjectMethods = args.collectFirst { case q"forwardObjectMethods = $cfg" =>
          c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(false)
        val generateTraits = args.collectFirst { case q"generateTraits = $cfg" =>
          c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(true)
        Arguments(verbose, forwardObjectMethods, generateTraits)
      case other => c.abort(c.enclosingPosition, "not possible - macro invoked on type that does not have @delegate: " + showRaw(other))
    }

    def isBlackListed(m: MethodSymbol) = {
      val blackListed = if (!args.forwardObjectMethods) {
        Set(
          "java.lang.Object.clone",
          "java.lang.Object.hashCode",
          "java.lang.Object.finalize",
          "java.lang.Object.equals",
          "java.lang.Object.toString",
          "scala.Any.getClass"
        )
      } else Set[String]()
      blackListed.contains(m.fullName)
    }

    def modifiedClass(classDecl: ClassDef, delegateTo: ValDef): c.Tree = {
      val (className, fields, bases, body) = try {
        val q"class $className(..$fields) extends ..$bases { ..$body }" = classDecl
        (className, fields, bases, body)
      } catch {
        case _: MatchError => c.abort(c.enclosingPosition, "Annotation is only supported on classes")
      }
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
      val additionalTraits = if (args.generateTraits) getTraits(toType) -- bases.flatMap(b => getTraits(c.typecheck(b, c.TYPEmode).tpe)).toSet
                             else Set.empty
      val resultType = parseTypeString((bases.map(_.toString()) ++ additionalTraits.map(_.fullName).toList).mkString(" with "))
      val extensions = overlappingMethods(toType, resultType)
        .filterNot { case (n, _) =>
          existingMethods.contains(n)
        } map { case (name, m) =>
          val rType = m.returnType

          val mods = if (!m.isAbstract) Modifiers(Flag.OVERRIDE)
                     else Modifiers()

          if (m.isVal) {
            q"""
          $mods val $name: $rType = $toName.$name
          """
          } else {
            val typeParams = m.typeParams.map(internal.typeDef(_))
            val paramLists = m.paramLists.map(_.map(internal.valDef(_)))
            q"""
          $mods def $name[..${typeParams}](...$paramLists): $rType = {
            $toName.${name}(...${paramLists.map(_.map(_.name))})
          }
          """
          }
      }
      val resultTypeName = TypeName(c.freshName)
      q"""
      ${c.parse(s"abstract class $resultTypeName extends $resultType")}
      class $className(..$fields) extends $resultTypeName { ..${body ++ extensions} }
      """
    }

    def getTraits(t: Type): Set[ClassSymbol] = {
      def loop(stack: List[ClassSymbol], traits: Vector[ClassSymbol] = Vector()): Vector[ClassSymbol] = stack match {
        case x :: xs => {
          if (x.isTrait) {
            loop(xs, traits :+ x)
          }
          else {
            loop(xs, traits)
          }
        }
        case Nil => traits
      }
      loop(t.baseClasses.map(_.asClass)).toSet
    }

    def typeCheckVal(valDecl: ValDef): (TermName, Type) = {
      val (tname, tpt) = try {
        val q"$mods val $tname: $tpt = $expr" = valDecl
        (tname, tpt)
      } catch {
        case _: MatchError => c.abort(c.enclosingPosition, "Only val members are supported.")
      }

      val tpe = try {
        c.typecheck(q"${tpt.duplicate}", c.TYPEmode).tpe
      } catch {
        case _: TypecheckException => c.abort(c.enclosingPosition, s"Type ${tpt.toString()} needs a stable reference.")
      }
      (tname, tpe)
    }

    def parseTypeString(str: String): Type = {
      try {
        c.typecheck(c.parse(s"null.asInstanceOf[$str]"), c.TYPEmode).tpe
      } catch {
        case _: TypecheckException => c.abort(c.enclosingPosition, s"Failed typechecking calculated type $str")
      }
    }

    def overlappingMethods(from: Type, to: Type): Map[TermName, MethodSymbol] = {
      val enclosing = c.enclosingClass match {
        case clazz if clazz.isEmpty => c.enclosingPackage.symbol.fullName
        case clazz => clazz.symbol.fullName
      }
      def isVisible(m: MethodSymbol) = {
        enclosing.startsWith(m.privateWithin.fullName)
      }

      to.baseClasses.map(_.asClass.selfType).filter(from <:< _).flatMap { s =>
        s.members.flatMap(m => to.member(m.name).alternatives.map(_.asMethod)).filter(m => !m.isConstructor && !m.isFinal && (m.isPublic || isVisible(m)) && !isBlackListed(m))
      }.map(m => (m.name -> m)).toMap
    }

    annottees.map(_.tree) match {
      case (valDecl: ValDef) :: (classDecl: ClassDef) :: Nil =>
        val modified = modifiedClass(classDecl, valDecl)
        if (args.verbose) showInfo(modified.toString())
        modified
      case _ => c.abort(c.enclosingPosition, "Invalid annottee")
    }
  }

  def showInfo(s: String) =
    c.info(c.enclosingPosition, s.split("\n").mkString("\n |---macro info---\n |", "\n |", ""), true)

}
