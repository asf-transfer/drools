//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.08.06 at 04:40:00 PM CEST 
//


package org.kie.dmn.model.v1_2;

import java.util.ArrayList;

import org.kie.dmn.model.v1x.Expression;
import org.kie.dmn.model.v1x.List;

public class TList extends TExpression implements List {

    protected java.util.List<Expression> expression;

    @Override
    public java.util.List<Expression> getExpression() {
        if (expression == null) {
            expression = new ArrayList<Expression>();
        }
        return this.expression;
    }

}
