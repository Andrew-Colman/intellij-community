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
package org.intellij.lang.xpath.psi;

public class XPathType {
    public static final XPathType UNKNOWN = new XPathType("unknown", true);
    public static final XPathType ANY = new XPathType("any", true);

    public static final XPathType NUMBER = new XPathType("number", false);
    public static final XPathType BOOLEAN = new XPathType("boolean", false);
    public static final XPathType STRING = new XPathType("string", false);
    public static final XPathType NODESET = new XPathType("nodeset", false);

    private final String type;
    private final boolean myAbstract;

    private XPathType(String s, boolean isAbstract) {
        type = s;
        myAbstract = isAbstract;
    }

    public String toString() {
        return "XPathType: " + type;
    }

    public String getName() {
        return type;
    }

    public boolean isAbstract() {
        return myAbstract;
    }

    public static XPathType fromString(String value) {
        if ("string".equals(value)) {
            return XPathType.STRING;
        } else if ("number".equals(value)) {
            return XPathType.NUMBER;
        } else if ("boolean".equals(value)) {
            return XPathType.BOOLEAN;
        } else if ("nodeset".equals(value)) {
            return XPathType.NODESET;
        }
        return XPathType.UNKNOWN;
    }
}