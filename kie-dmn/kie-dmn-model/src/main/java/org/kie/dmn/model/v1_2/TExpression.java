//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.08.06 at 04:40:00 PM CEST 
//


package org.kie.dmn.model.v1_2;

import javax.xml.namespace.QName;

import org.kie.dmn.model.v1x.Expression;

public class TExpression extends TDMNElement implements Expression {

    /**
     * align with internal model
     */
    protected QName typeRef;

    @Override
    public QName getTypeRef() {
        return this.typeRef;
    }

    @Override
    public void setTypeRef(QName value) {
        this.typeRef = value;
    }

}
