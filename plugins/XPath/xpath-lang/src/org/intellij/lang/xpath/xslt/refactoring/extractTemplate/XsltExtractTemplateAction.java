/*
 * Copyright 2002-2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.lang.xpath.xslt.refactoring.extractTemplate;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.refactoring.RefactoringUtil;
import org.intellij.lang.xpath.xslt.refactoring.XsltRefactoringActionBase;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 10.06.2007
 */
@SuppressWarnings({ "ComponentNotRegistered" })
public class XsltExtractTemplateAction extends XsltRefactoringActionBase {
    public String getRefactoringName() {
        return "Extract Template";
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        if (!invokeImpl(editor, file)) {
            super.invoke(project, editor, file, dataContext);
        }
    }
    public boolean invokeImpl(Editor editor, PsiFile file) {
        final SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            return false;
        }

        int startOffset = selectionModel.getSelectionStart();
        PsiElement start = file.findElementAt(startOffset);
        if (start instanceof PsiWhiteSpace) {
            if ((start = PsiTreeUtil.nextLeaf(start)) != null) {
                startOffset = start.getTextOffset();
            }
        }

        int endOffset = selectionModel.getSelectionEnd();
        PsiElement end = file.findElementAt(endOffset - 1);
        if (end instanceof PsiWhiteSpace) {
            if ((end = PsiTreeUtil.prevLeaf(end)) != null) {
                endOffset = end.getTextRange().getEndOffset();
            }
        }
        
        if (start == null || end == null) {
            return false;
        }
        if (!(start.getParent() instanceof XmlTag)) {
            return false;
        }

        if (start == end) {
            if (start.getTextRange().getStartOffset() != startOffset) {
                return false;
            }
            if (end.getTextRange().getEndOffset() != endOffset) {
                return false;
            }
            return extractFrom(start, end);
        } else {
            final XmlTag startTag = PsiTreeUtil.getParentOfType(start, XmlTag.class);
            if (startTag == null) {
                return false;
            }
            if (startTag.getTextRange().getStartOffset() != startOffset) {
                return false;
            }

            final XmlTag endTag = PsiTreeUtil.getParentOfType(end, XmlTag.class);
            if (endTag == null) {
                return false;
            }
            if (endTag.getTextRange().getEndOffset() != endOffset) {
                return false;
            }

            if (startTag != endTag) {
                if (startTag.getParent() != endTag.getParent()) {
                    return false;
                }
            }
            return extractFrom(startTag, endTag);
        }
    }

    private boolean extractFrom(final @NotNull PsiElement start, final PsiElement end) {
        final XmlTag outerTemplate = XsltCodeInsightUtil.getTemplateTag(start, false);
        final XmlTag parentScope = PsiTreeUtil.getParentOfType(start, XmlTag.class, true);

        final StringBuilder sb = new StringBuilder("\n");
        final Set<XPathVariableReference> refs = new HashSet<XPathVariableReference>();

        final int startOffset = start.getTextRange().getStartOffset();
        final int endOffset = end.getTextRange().getEndOffset();

        PsiElement e = start;
        while (e != null) {
            if (e instanceof XmlTag) {
                final XmlTag tag = (XmlTag)e;
                if (XsltSupport.isVariable(tag)) {
                    final XsltVariable variable = XsltElementFactory.getInstance().wrapElement(tag, XsltVariable.class);
                    final LocalSearchScope searchScope = new LocalSearchScope(parentScope);
                    final Query<PsiReference> query = ReferencesSearch.search(variable, searchScope);
                    for (PsiReference reference : query) {
                        final XmlElement context = PsiTreeUtil.getContextOfType(reference.getElement(), XmlElement.class, true);
                        if (context == null || context.getTextRange().getStartOffset() > endOffset) {
                            return false;
                        }
                    }
                }
            }
            sb.append(e.getText());

            final Set<XPathVariableReference> references = RefactoringUtil.collectVariableReferences(e);
            for (XPathVariableReference reference : references) {
                final XPathVariable variable = reference.resolve();
                if (variable != null) {
                    final XmlTag var = ((XsltVariable)variable).getTag();
                    if (var.getTextRange().getStartOffset() < startOffset) {
                        // don't pass through global parameters and variables
                        if (XsltCodeInsightUtil.getTemplateTag(variable, false) != null) {
                            refs.add(reference);
                        }
                    }
                } else {
                    // TODO just copy unresolved refs or fail?
                    refs.add(reference);
                }
            }

            if (e == end) {
                break;
            }
            e = e.getNextSibling();
        }
        sb.append("\n");

        final String s = Messages.showInputDialog(start.getProject(), "Template Name: ", getRefactoringName(), Messages.getQuestionIcon());
        if (s != null) {
            new WriteCommandAction(start.getProject()) {
                protected void run(Result result) throws Throwable {
                    final PsiFile containingFile = start.getContainingFile();

                    XmlTag templateTag = parentScope.createChildTag("template", XsltSupport.XSLT_NS, sb.toString(), false);
                    templateTag.setAttribute("name", s);

                    final PsiElement dummy = XmlElementFactory.getInstance(start.getProject()).createDisplayText(" ");
                    final PsiElement outerParent = outerTemplate.getParent();
                    final PsiElement element = outerParent.addAfter(dummy, outerTemplate);
                    templateTag = (XmlTag)outerParent.addAfter(templateTag, element);

                    final TextRange adjust = templateTag.getTextRange();

                    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(start.getProject());
                    final Document doc = psiDocumentManager.getDocument(containingFile);
                    assert doc != null;
                    psiDocumentManager.doPostponedOperationsAndUnblockDocument(doc);
                    start.getManager().getCodeStyleManager().adjustLineIndent(containingFile, adjust);

                    final PsiElement parent = start.getParent();
                    XmlTag callTag = parentScope.createChildTag("call-template", XsltSupport.XSLT_NS, null, false);
                    callTag.setAttribute("name", s);
                    
                    if (start instanceof XmlToken && ((XmlToken)start).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
                        assert start == end;
                        callTag = (XmlTag)start.replace(callTag);
                    } else {
                        callTag = (XmlTag)parent.addBefore(callTag, start);
                        parent.deleteChildRange(start, end);
                    }

                    for (XPathVariableReference ref : refs) {
                        final XmlTag param = templateTag.createChildTag("param", XsltSupport.XSLT_NS, null, false);
                        final String varName = ref.getReferencedName();
                        param.setAttribute("name", varName);
                        RefactoringUtil.addParameter(templateTag, param);

                        final XmlTag arg = RefactoringUtil.addWithParam(callTag);
                        arg.setAttribute("name", varName);
                        arg.setAttribute("select", "$" + varName);
                    }
                }
            }.execute().logException(Logger.getInstance(getClass().getName()));
        }
        return true;
    }

    protected boolean actionPerformedImpl(PsiFile file, Editor editor, XmlAttribute context, int offset) {
        return false;
    }

    @Override
    @Nullable
    public String getErrorMessage(Editor editor, PsiFile file, XmlAttribute context) {
        if (!editor.getSelectionModel().hasSelection()) {
            return "Please select the code that should be extracted.";
        }
        return null;
    }
}
