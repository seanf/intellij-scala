package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
  * @author t-kameyama
  */
class FilterSetContainsInspectionTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[FilterSetContainsInspection]

  override protected val hint: String =
    InspectionBundle.message("remove.redundant.contains")

  def testSetContains(): Unit = {
    doTest(
      s"Seq(1, 2, 3).filter(Set(1, 3).${START}contains$END)",
      "Seq(1, 2, 3).filter(Set(1, 3).contains)",
      "Seq(1, 2, 3).filter(Set(1, 3))"
    )
  }

  def testSetValContains(): Unit = {
    doTest(
      s"val s = Set(1, 3); Seq(1, 2, 3).filter(s.${START}contains$END)",
      "val s = Set(1, 3); Seq(1, 2, 3).filter(s.contains)",
      "val s = Set(1, 3); Seq(1, 2, 3).filter(s)"
    )
  }

  def testNotSetContains(): Unit = {
    checkTextHasNoErrors("Seq(1, 2, 3).filter(List(1, 3).contains)")
  }

}
