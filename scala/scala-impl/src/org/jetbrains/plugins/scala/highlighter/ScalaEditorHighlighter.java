/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.highlighter;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.scaladoc.highlighter.ScalaDocSyntaxHighlighter;
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx.SCALA_XML_CONTENT;


/**
 * @author ilyas
 */
public class ScalaEditorHighlighter extends LayeredLexerEditorHighlighter {

  public ScalaEditorHighlighter(Project project, VirtualFile virtualFile, EditorColorsScheme scheme) {
    super(createSyntaxHighlighter(project), scheme);

    //Register XML highlighter
    final SyntaxHighlighter xmlHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(StdLanguages.XML, project, virtualFile);

    final LayerDescriptor xmlLayer = new LayerDescriptor(xmlHighlighter, "\n", HighlighterColors.TEXT);
    registerLayer(SCALA_XML_CONTENT, xmlLayer);

    final SyntaxHighlighter scaladocHighlighter = new ScalaDocSyntaxHighlighter();
    final LayerDescriptor scaladocLayer = new LayerDescriptor(scaladocHighlighter, "\n", DefaultHighlighter.DOC_COMMENT);
    registerLayer(ScalaDocElementTypes.SCALA_DOC_COMMENT, scaladocLayer);
  }

  private static ScalaSyntaxHighlighter createSyntaxHighlighter(Project project) {
    boolean treatDocCommentAsBlockComment =
            project != null && ScalaProjectSettings.getInstance(project).isTreatDocCommentAsBlockComment();
    return new ScalaSyntaxHighlighter(treatDocCommentAsBlockComment);
  }

  // workaround for an apparent bug in IntelliJ platform which applies
  // the background attribute (e.g. Scaladoc Commnent) *after* the foreground attribute (e.g. Scaladoc Tag)
  //
  // See https://youtrack.jetbrains.net/issue/SCL-3122
  //
  // Either LayeredLexerEditorHighlighter#convertAttributes or LayeredLexerEditorHighlighter#Mapper#getAttributes
  // should be fixed instead.
//  @Override
//  protected TextAttributes convertAttributes(@NotNull TextAttributesKey[] keys) {
//    TextAttributesKey[] reversed = ArrayUtil.reverseArray(keys);
//    return super.convertAttributes(reversed);
//  }
}
