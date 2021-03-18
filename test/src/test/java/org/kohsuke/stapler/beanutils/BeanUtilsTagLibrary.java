package org.kohsuke.stapler.beanutils;

import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Tag;
import org.apache.commons.jelly.TagLibrary;
import org.apache.commons.jelly.impl.TagScript;
import org.xml.sax.Attributes;

public class BeanUtilsTagLibrary extends TagLibrary {
    public BeanUtilsTagLibrary() {
        registerTag("tagWithStringTypedClassProperty", DynaTagWithStringProperty.class);
        registerTag("tagWithObjectTypedClassProperty", BasicTagWithObjectProperty.class);
    }

    @Override
    public Tag createTag(String name, Attributes attributes) throws JellyException {
        return super.createTag(name, attributes);
    }

    @Override
    public TagScript createTagScript(String name, Attributes attributes) throws JellyException {
        return super.createTagScript(name, attributes);
    }
}
