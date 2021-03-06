package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationExpr}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */
trait ScAnnotationStub extends StubElement[ScAnnotation] {
  def name: Option[String]
  def typeElement: Option[ScTypeElement]
  def annotationText: String
  def annotationExpr: Option[ScAnnotationExpr]
}