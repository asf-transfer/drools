//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.08.06 at 04:40:00 PM CEST 
//


package org.kie.dmn.model.v1_2;

import org.kie.dmn.model.v1x.Association;
import org.kie.dmn.model.v1x.AssociationDirection;
import org.kie.dmn.model.v1x.DMNElementReference;

public class TAssociation extends TArtifact implements Association {

    protected DMNElementReference sourceRef;
    protected DMNElementReference targetRef;
    protected AssociationDirection associationDirection;

    @Override
    public DMNElementReference getSourceRef() {
        return sourceRef;
    }

    @Override
    public void setSourceRef(DMNElementReference value) {
        this.sourceRef = value;
    }

    @Override
    public DMNElementReference getTargetRef() {
        return targetRef;
    }

    @Override
    public void setTargetRef(DMNElementReference value) {
        this.targetRef = value;
    }

    @Override
    public AssociationDirection getAssociationDirection() {
        if (associationDirection == null) {
            return AssociationDirection.NONE;
        } else {
            return associationDirection;
        }
    }

    @Override
    public void setAssociationDirection(AssociationDirection value) {
        this.associationDirection = value;
    }

}
