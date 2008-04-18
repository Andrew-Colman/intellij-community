/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.psi.impl;

import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XsltParameterImpl extends XsltVariableImpl implements XsltParameter {
    XsltParameterImpl(XmlTag attribute) {
        super(attribute);
    }

    @Override
    public Icon getIcon(int i) {
        return Icons.PARAMETER_ICON;
    }

    public boolean hasDefault() {
        return getValue() != null || !getTag().isEmpty();
    }

    public boolean isAbstract() {
        final boolean b = "true".equals(getTag().getAttributeValue("abstract", XsltSupport.PLUGIN_EXTENSIONS_NS));
        if (!b) {
            final XsltTemplate template = getTemplate();
            return template != null && template.isAbstract();
        }
        return b;
    }

    @Nullable
    public XsltTemplate getTemplate() {
        return XsltCodeInsightUtil.getTemplate(getTag(), false);
    }

    @Override
    public String toString() {
        return "XsltParam: " + getName();
    }

    public static XsltParameter getInstance(@NotNull XmlTag target) {
        return XsltElementFactory.getInstance().wrapElement(target, XsltParameter.class);
    }
}