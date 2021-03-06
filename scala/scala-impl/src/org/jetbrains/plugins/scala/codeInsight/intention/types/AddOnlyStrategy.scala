package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ScTypeText}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScType, Signature}
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.annotations.Implementation

class AddOnlyStrategy(editor: Option[Editor] = None) extends Strategy {

  def functionWithType(function: ScFunctionDefinition,
                       typeElement: ScTypeElement): Boolean = true

  def valueWithType(value: ScPatternDefinition,
                    typeElement: ScTypeElement): Boolean = true

  def variableWithType(variable: ScVariableDefinition,
                       typeElement: ScTypeElement): Boolean = true

  override def patternWithType(pattern: ScTypedPattern): Boolean = true

  override def parameterWithType(param: ScParameter): Boolean = true

  override def underscoreSectionWithType(underscore: ScUnderscoreSection) = true

  override def functionWithoutType(function: ScFunctionDefinition): Boolean = {
    typeForMember(function).foreach {
      addTypeAnnotation(_, function, function.paramClauses)
    }

    true
  }

  override def valueWithoutType(value: ScPatternDefinition): Boolean = {
    typeForMember(value).foreach {
      addTypeAnnotation(_, value, value.pList)
    }

    true
  }

  override def variableWithoutType(variable: ScVariableDefinition): Boolean = {
    typeForMember(variable).foreach {
      addTypeAnnotation(_, variable, variable.pList)
    }

    true
  }

  override def patternWithoutType(pattern: ScBindingPattern): Boolean = {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }

    true
  }

  override def wildcardPatternWithoutType(pattern: ScWildcardPattern): Boolean = {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }

    true
  }

  override def parameterWithoutType(param: ScParameter): Boolean = {
    param.parentsInFile.instanceOf[ScFunctionExpr] match {
      case Some(func) =>
        val index = func.parameters.indexOf(param)
        func.expectedType() match {
          case Some(FunctionType(_, params)) =>
            if (index >= 0 && index < params.length) {
              val paramExpectedType = params(index)
              addTypeAnnotation(paramExpectedType, param.getParent, param)
            }
          case _ =>
        }
      case _ =>
    }

    true
  }

  override def underscoreSectionWithoutType(underscore: ScUnderscoreSection): Boolean = {
    underscore.`type`().foreach {
      addTypeAnnotation(_, underscore.getParent, underscore)
    }

    true
  }

  def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement): Unit = {
    import AddOnlyStrategy._
    val tps = annotationsFor(t)
    val validVariants = tps.reverse.flatMap(_.`type`().toOption).map(ScTypeText)

    val added = addActualType(tps.head, anchor)

    editor match {
      case Some(e) if validVariants.size > 1 =>
        val expr = new ChooseTypeTextExpression(validVariants)
        // TODO Invoke the simplification
        startTemplate(added, context, expr, e)
      case _ =>
        ScalaPsiUtil.adjustTypes(added)

        val maybeExpression = context match {
          case variable: ScVariableDefinition => variable.expr
          case pattern: ScPatternDefinition => pattern.expr
          case function: ScFunctionDefinition => function.body
          case _ => None
        }

        maybeExpression.collect {
          case call@Implementation.EmptyCollectionFactoryCall(ref) =>
            (call, createElementFromText(ref.getText)(ref.projectContext))
        }.foreach {
          case (expression, replacement) => expression.replace(replacement)
        }
    }
  }

  private def typeForMember(element: ScMember): Option[ScType] = {

    def signatureType(sign: Signature): Option[ScType] = {
      val substitutor = sign.substitutor
      sign.namedElement match {
        case f: ScFunction =>
          f.returnType.toOption.map(substitutor)
        case m: PsiMethod =>
          implicit val ctx: Project = m.getProject
          Option(m.getReturnType).map(psiType => substitutor(psiType.toScType()))
        case t: ScTypedDefinition =>
          t.`type`().toOption.map(substitutor)
        case _ => None
      }
    }

    def superSignatures(member: ScMember): Seq[Signature] = {
      val named = member match {
        case n: ScNamedElement => n
        case v: ScValueOrVariable if v.declaredElements.size == 1 => v.declaredElements.head
        case _ => return Seq.empty
      }

      val aClass = member match {
        case ContainingClass(c) => c
        case _ => return Seq.empty
      }

      val signatureMap = TypeDefinitionMembers.getSignatures(aClass)
      val signaturesForNamed =
        signatureMap
          .forName(named.name)._1
          .filter(sign => sign._1.namedElement == named)
      signaturesForNamed.flatMap(_._2.supers.map(_.info))
    }

    val computedType = element match {
      case function: ScFunctionDefinition =>
        function.returnType.toOption
      case value: ScPatternDefinition =>
        value.`type`().toOption
      case variable: ScVariableDefinition =>
        variable.`type`().toOption
      case _ =>
        None
    }

    val shouldTrySuperMember = computedType match {
      case None => true
      case Some(t) => t.isNothing || t.isAny
    }

    if (shouldTrySuperMember) {
      val supers = superSignatures(element).iterator
      supers
        .map(signatureType)
        .find(_.nonEmpty)
        .flatten
    }
    else computedType
  }
}

object AddOnlyStrategy {

  def addActualType(annotation: ScTypeElement, anchor: PsiElement): PsiElement = {
    implicit val ctx: ProjectContext = anchor

    anchor match {
      case p: ScParameter =>
        val parameter = p.getParent match {
          case Parent(Parent(Parent(_: ScBlockExpr))) => p
          // ensure  that the parameter is wrapped in parentheses before we add the type annotation.
          case clause: ScParameterClause if clause.parameters.length == 1 =>
            clause.replace(createClauseForFunctionExprFromText(p.getText.parenthesize()))
              .asInstanceOf[ScParameterClause].parameters.head
          case _ => p
        }

        parameter.nameId.appendSiblings(createColon, createWhitespace, createParameterTypeFromText(annotation.getText)).last

      case underscore: ScUnderscoreSection =>
        val needsParentheses = underscore.getParent match {
          case ScParenthesisedExpr(content) if content == underscore => false
          case _: ScArgumentExprList => false
          case _ => true
        }
        val e = createScalaFileFromText(s"(_: ${annotation.getText})").getFirstChild.asInstanceOf[ScParenthesisedExpr]
        underscore.replace(if (needsParentheses) e else e.innerElement.get)

      case _ =>
        anchor.appendSiblings(createColon, createWhitespace, annotation).last
    }
  }

  def annotationsFor(`type`: ScType): Seq[ScTypeElement] =
    canonicalTypes(`type`)
      .map(createTypeElementFromText(_)(`type`.projectContext))

  private[this] def canonicalTypes(tpe: ScType): Seq[String] = {
    import BaseTypes.get

    tpe.canonicalCodeText +: (tpe.extractClass match {
      case Some(sc: ScTypeDefinition) if sc.qualifiedName == "scala.Some" =>
        get(tpe).map(_.canonicalCodeText)
          .filter(_.startsWith("_root_.scala.Option"))
      case Some(sc: ScTypeDefinition) if sc.qualifiedName.startsWith("scala.collection") =>
        val goodTypes = Set(
          "_root_.scala.collection.mutable.Seq[",
          "_root_.scala.collection.immutable.Seq[",
          "_root_.scala.collection.mutable.Set[",
          "_root_.scala.collection.immutable.Set[",
          "_root_.scala.collection.mutable.Map[",
          "_root_.scala.collection.immutable.Map["
        )

        get(tpe).map(_.canonicalCodeText)
          .filter(t => goodTypes.exists(t.startsWith))
      case Some(sc: ScTypeDefinition) if (sc +: sc.supers).exists(_.isSealed) =>
        get(tpe).find(_.extractClass.exists(_.isSealed)).toSeq
          .map(_.canonicalCodeText)
      case _ => Seq.empty
    })
  }
}
