package com.schuwalow.delegate

import scala.reflect.macros.blackbox.Context

class Macros(val c: Context) {
  import c.universe._

  def delegateImpl(annottees: c.Expr[Any]*): c.Tree = {

    case class Arguments(verbose: Boolean)

    val args: Arguments = c.prefix.tree match {
      case Apply(_, args) =>
        val verbose: Boolean = args.collectFirst { case q"verbose = $cfg" =>
          c.eval(c.Expr[Boolean](cfg))
        }.getOrElse(false)
        Arguments(verbose)
      case other => c.abort(c.enclosingPosition, "not possible - macro invoked on type that does not have @delegate: " + showRaw(other))
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

          if (m.isVal) {
            q"""
          override val $name: $rType = $a.$name
          """
          } else {
            val typeParams = m.typeParams.map(internal.typeDef(_))
            val paramLists = m.paramLists.map(_.map(internal.valDef(_)))
            q"""
          override def $name[..${typeParams}](...$paramLists): $rType = {
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
      val tree = tpt.duplicate
      val tpe  = c.typecheck(q"${tree}", c.TYPEmode).tpe
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

  def isBlackListed(m: MethodSymbol) = m.fullName match {
    case s if s == "java.lang.Object.clone"    => true
    case s if s == "java.lang.Object.hashCode" => true
    case s if s == "java.lang.Object.finalize" => true
    case s if s == "java.lang.Object.equals"   => true
    case s if s == "java.lang.Object.toString" => true
    case _                                     => false
  }

  def showInfo(s: String) =
    c.info(c.enclosingPosition, s.split("\n").mkString("\n |---macro info---\n |", "\n |", ""), true)

}
