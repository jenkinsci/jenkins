package org.jenkins.ui.icon;

import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.TagSupport;
import org.apache.commons.jelly.XMLOutput;
import org.xml.sax.SAXException;

public class IoniconTag extends TagSupport {
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void doTag(XMLOutput output) throws JellyTagException {
        try {
            IconSet iconSet = new IconSet();
            output.writeCDATA(iconSet.getIonicon(name));
        } catch (SAXException e) {
            // ignore
        }
        invokeBody(output);
    }
}
