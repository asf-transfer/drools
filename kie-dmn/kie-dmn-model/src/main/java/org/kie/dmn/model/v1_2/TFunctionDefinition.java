//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.08.06 at 04:40:00 PM CEST 
//


package org.kie.dmn.model.v1_2;

import java.util.ArrayList;
import java.util.List;

import org.kie.dmn.model.v1x.Expression;
import org.kie.dmn.model.v1x.FunctionDefinition;
import org.kie.dmn.model.v1x.FunctionKind;
import org.kie.dmn.model.v1x.InformationItem;

public class TFunctionDefinition extends TExpression implements FunctionDefinition {

    protected List<InformationItem> formalParameter;
    protected Expression expression;
    protected FunctionKind kind;

    @Override
    public List<InformationItem> getFormalParameter() {
        if (formalParameter == null) {
            formalParameter = new ArrayList<InformationItem>();
        }
        return this.formalParameter;
    }

    @Override
    public Expression getExpression() {
        return expression;
    }

    @Override
    public void setExpression(Expression value) {
        this.expression = value;
    }

    @Override
    public FunctionKind getKind() {
        if (kind == null) {
            return FunctionKind.FEEL;
        } else {
            return kind;
        }
    }

    @Override
    public void setKind(FunctionKind value) {
        this.kind = value;
    }

}
