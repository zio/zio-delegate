package com.schuwalow.delegate

import scala.reflect.macros.blackbox.Context
import scala.reflect.macros.TypecheckException

class Macros(val c: Context) {
  import c.universe._

  def delegateImpl(annottees: c.Expr[Any]*): c.Tree = {

    case class Arguments(verbose: Boolean, forwardObjectMethods: Boolean)

    val args: Arguments = c.prefix.tree match {
      case Apply(_, args) =>
        val verbose: Boolean = args.collectFirst { case q"verbose = $cfg" =>
          c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(false)
        val forwardObjectMethods = args.collectFirst { case q"forwardObjectMethods = $cfg" =>
          c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(false)
        Arguments(verbose, forwardObjectMethods)
      case other => c.abort(c.enclosingPosition, "not possible - macro invoked on type that does not have @delegate: " + showRaw(other))
    }

    def isBlackListed(m: MethodSymbol) = {
      val blackListed = if (!args.forwardObjectMethods) {
        Set(
          "java.lang.Object.clone",
          "java.lang.Object.hashCode",
          "java.lang.Object.finalize",
          "java.lang.Object.equals",
          "java.lang.Object.toString"
        )
      } else Set[String]()
      blackListed.contains(m.fullName)
    }

    def modifiedClass(classDecl: ClassDef, a: TermName, extensions: Set[(TermName, MethodSymbol)]): c.Tree = {
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

      val toAdd = extensions.filterNot {
        case (n, _) =>
          existingMethods.contains(n)
      }

      val ms = toAdd.map {
        case (name, m) =>
          val rType = m.returnType

          val mods = if (!m.isAbstract) Modifiers(Flag.OVERRIDE)
                     else Modifiers()

          if (m.isVal) {
            q"""
          $mods val $name: $rType = $a.$name
          """
          } else {
            val typeParams = m.typeParams.map(internal.typeDef(_))
            val paramLists = m.paramLists.map(_.map(internal.valDef(_)))
            q"""
          $mods def $name[..${typeParams}](...$paramLists): $rType = {
            $a.${name}(...${paramLists.map(_.map(_.name))})
          }
          """
          }
      }
      q"class $className(..$fields) extends ..$bases { ..${body ++ ms} }"
    }

    def delegationCandidates(valDecl: ValDef): (TermName, Set[(TermName, MethodSymbol)]) = {
      val (tpt, tname, expr) = try {
        val q"$mods val $tname: $tpt = $expr" = valDecl
        (tpt, tname, expr)
      } catch {
        case _: MatchError => c.abort(c.enclosingPosition, "Only val members are supported.")
      }

      val tpe = try {
        c.typecheck(q"${tpt.duplicate}", c.TYPEmode).tpe
      } catch {
        case _: TypecheckException => c.abort(c.enclosingPosition, s"Type ${tpt.toString()} needs a stable reference.")
      }

      val methods =
        tpe.members.map(_.asMethod).filter(m => !m.isConstructor && !m.isFinal && m.isPublic && !isBlackListed(m))
      (tname, methods.map(m => (m.name, m)).toSet)
    }

    annottees.map(_.tree) match {
      case (valDecl: ValDef) :: (classDecl: ClassDef) :: Nil =>
        val modified = delegationCandidates(valDecl) match { case (a, m) => modifiedClass(classDecl, a, m) }
        if (args.verbose) showInfo(modified.toString())
        modified
      case _ => c.abort(c.enclosingPosition, "Invalid annottee")
    }
  }

  def showInfo(s: String) =
    c.info(c.enclosingPosition, s.split("\n").mkString("\n |---macro info---\n |", "\n |", ""), true)

}
