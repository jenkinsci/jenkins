package org.kohsuke.stapler.beanutils;

import org.apache.commons.jelly.DynaTag;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.TagSupport;
import org.apache.commons.jelly.XMLOutput;
import org.xml.sax.SAXException;

public class DynaTagWithStringProperty extends TagSupport implements DynaTag {
    private String clazz;

    public void setClass(String clazz) {
        this.clazz = clazz;
    }

    @Override
    public void doTag(XMLOutput output) throws JellyTagException {
        try {
            output.writeComment("Tag with string property\n");
            output.writeCDATA(getClass().getName() + ":" + clazz);
        } catch (SAXException e) {
            // ignore
        }
        invokeBody(output);
    }

    @Override
    public void setAttribute(String name, Object value) throws JellyTagException {
        if ("class".equals(name)) {
            this.setClass((String) value);
        }
    }

    @Override
    public Class getAttributeType(String name) throws JellyTagException {
        if ("class".equals(name)) {
            return String.class;
        }
        return null;
    }
}
