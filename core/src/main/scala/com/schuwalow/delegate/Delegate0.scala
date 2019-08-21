package com.schuwalow.delegate

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.annotation.StaticAnnotation

class Delegate0[T] extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Delegate0.impl
}

object Delegate0 {

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Tree = {
    import c.universe._

    def showInfo(s: String) =
      c.info(c.enclosingPosition, s.split("\n").mkString("\n |---macro info---\n |", "\n |", ""), true)

    // def reportInvalidAnnotationTarget(): Unit = {
    //   c.error(c.enclosingPosition, "This annotation can only be used on vals")
    // }

    // def forwarderDecls(a: Ident, tA: Type) = {
    //   tA.decls.map(_.asMethod).filter(m => typeOf[AnyVal].decls.exists(m1 => !(m1.asMethod.name == m.name))).filter(m => !m.isConstructor && !m.isFinal && m.isPublic).map { m =>
    //     val name = m.name
    //     showInfo(m.asMethod. name.toString())
    //     val rType = m.returnType
    //     if (m.isVal) {
    //       q"""
    //       override val $name: $rType = $a.$name
    //       """
    //     } else {
    //       val typeParams =  m.typeParams.map(internal.typeDef(_))
    //       val paramLists = m.paramLists.map(_.map(internal.valDef(_)))
    //       q"""
    //       override def $name[..${typeParams}](...$paramLists): $rType = {
    //         $a.${name}(...${paramLists.map(_.map(_.name))})
    //       }
    //       """
    //     }
    //   }
    // }

    // def typeCheckExpressionOfType(typeTree: Tree): Type = {
    //   val someValueOfTypeString = reify {
    //     def x[T](): T = throw new Exception
    //     x[String]()
    //   }

    //   val Expr(Block(stats, Apply(TypeApply(someValueFun, _), someTypeArgs))) = someValueOfTypeString

    //   val someValueOfGivenType = Block(stats, Apply(TypeApply(someValueFun, List(typeTree)), someTypeArgs))
    //   val someValueOfGivenTypeChecked = c.typecheck(someValueOfGivenType)

    //   someValueOfGivenTypeChecked.tpe
    // }

    // def computeType(tpt: Tree): Type = {
    //   if (tpt.tpe != null) {
    //     tpt.tpe
    //   } else {
    //     val calculatedType = c.typecheck(tpt.duplicate, silent = true, withMacrosDisabled = true).tpe
    //     val result = if (tpt.tpe == null) calculatedType else tpt.tpe

    //     if (result == NoType) {
    //       typeCheckExpressionOfType(tpt)
    //     } else {
    //       result
    //     }
    //   }
    // }

    def modifiedClass(classDecl: ClassDef, a: c.universe.TermName, extensions: Set[(c.universe.TermName, c.universe.MethodSymbol)]): c.Tree = {
      val (className, fields, bases, body) = try {
        val q"class $className(..$fields) extends ..$bases { ..$body }" = classDecl
        (className, fields, bases, body)
      } catch {
        case _: MatchError => c.abort(c.enclosingPosition, "Annotation is only supported on case class")
      }
      val existingMethods = body.flatMap(tree => tree match {
        case a @ DefDef(_, n, _, _, _, _) => Some(n)
        case a @ ValDef(_, n, _, _) => Some(n)
        case _ => None
      }).toSet
      showInfo(existingMethods.toString())
      val toAdd = extensions.filterNot { case (n, _) =>
        showInfo(n.toString())
        existingMethods.contains(n) }
      val ms = toAdd.map { case (_, m) =>
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
      q"class $className(..$fields) extends ..$bases { ..${body ++ ms} }"
    }

    def modifiedVal(valDecl: ValDef): (c.universe.TermName, Set[(c.universe.TermName, c.universe.MethodSymbol)]) = {
      val (tpt, tname, expr) = try {
        val q"$mods val $tname: $tpt = $expr" = valDecl
        (tpt, tname, expr)
      } catch {
        case _: MatchError => c.abort(c.enclosingPosition, "boom")
      }
      val tree = tpt.duplicate
      val tpe = c.typecheck(q"${tree}", c.TYPEmode).tpe
      val methods = tpe.members.map(_.asMethod).filter(m => !m.isConstructor && !m.isFinal && m.isPublic)
      (tname, methods.map(m => (m.name, m)).toSet)
    }

    annottees.map(_.tree) match {
      case (classDecl: ClassDef) :: Nil => ???
      case (classDecl: ClassDef) :: (compDecl: ModuleDef) :: Nil => ???
      case (valDecl: ValDef) :: (classDecl: ClassDef) :: Nil =>
        val r = modifiedVal(valDecl) match { case (a, m) => modifiedClass(classDecl, a, m) }
        showInfo(r.toString())
        r
      case _ => c.abort(c.enclosingPosition, "Invalid annottee")
    }

    // def computeType(tpt: Tree): Type = {
    //   if (tpt.tpe != null) {
    //     tpt.tpe
    //   } else {
    //     val calculatedType = c.typecheck(tpt.duplicate, silent = true, withMacrosDisabled = true).tpe
    //     val result = if (tpt.tpe == null) calculatedType else tpt.tpe

    //     if (result == NoType) {
    //       typeCheckExpressionOfType(tpt)
    //     } else {
    //       result
    //     }
    //   }
    // }
    // // ... until here

    // def addDelegateMethods(valDef: ValDef, addToClass: ClassDef) = {
    //   def allMethodsInDelegate = computeType(valDef.tpt).decls

    //   val ClassDef(mods, name, tparams, Template(parents, self, body)) = addToClass

    //   // TODO better filtering - allow overriding
    //   val existingMethods = body.flatMap(tree => tree match {
    //     case DefDef(_, n, _, _, _, _) => Some(n)
    //     case _ => None
    //   }).toSet
    //   val methodsToAdd = allMethodsInDelegate.filter(method => !existingMethods.contains(method.asTerm.name))

    //   val newMethods = for {
    //     methodToAdd <- methodsToAdd
    //   } yield {
    //     val methodSymbol = methodToAdd.asMethod

    //     val vparamss = methodSymbol.paramLists.map(_.map {
    //       paramSymbol => ValDef(
    //         Modifiers(Flag.PARAM, typeNames.EMPTY, List()),
    //         paramSymbol.name.toTermName,
    //         TypeTree(paramSymbol.typeSignature),
    //         EmptyTree)
    //     })

    //     val delegateInvocation = Apply(
    //       Select(Ident(valDef.name), methodSymbol.name),
    //       methodSymbol.paramLists.flatMap(_.map(param => Ident(param.name)))) // TODO - multi params list

    //     DefDef(Modifiers(),
    //       methodSymbol.name,
    //       List(), // TODO - type parameters
    //       vparamss,
    //       TypeTree(methodSymbol.returnType),
    //       delegateInvocation)
    //   }

    //   ClassDef(mods, name, tparams, Template(parents, self, body ++ newMethods))
    // }

    // val inputs = annottees.map(_.tree).toList
    // showInfo(inputs.toString())
    // val (_, expandees) = inputs match {
    //   case (param: ValDef) :: (enclosing: ClassDef) :: rest => {
    //     val newEnclosing = addDelegateMethods(param, enclosing)
    //     (param, newEnclosing :: rest)
    //   }
    //   case (param: TypeDef) :: (rest @ (_ :: _)) => reportInvalidAnnotationTarget(); (param, rest)
    //   case _ => reportInvalidAnnotationTarget(); (EmptyTree, inputs)
    // }
    // val outputs = expandees
    // c.Expr[Any](Block(outputs, Literal(Constant(()))))
  }
}
