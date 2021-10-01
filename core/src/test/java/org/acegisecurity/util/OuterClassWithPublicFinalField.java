package org.acegisecurity.util;

public class OuterClassWithPublicFinalField {

    public final String myField = "original";

    public String getMyField() {
        return myField;
    }
}
