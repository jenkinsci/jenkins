package org.kohsuke.stapler.beanutils;

import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.MissingAttributeException;
import org.apache.commons.jelly.TagSupport;
import org.apache.commons.jelly.XMLOutput;
import org.xml.sax.SAXException;

public class BasicTagWithObjectProperty extends TagSupport {
    private Object clazz;

    public void setClass(Object clazz) {
        this.clazz = clazz;
    }

    @Override
    public void doTag(XMLOutput output) throws MissingAttributeException, JellyTagException {
        try {
            output.writeComment("Tag with object property\n");
            output.writeCDATA(getClass().getName() + ":" + clazz);
        } catch (SAXException e) {
            // ignore
        }
        invokeBody(output);
    }
}
