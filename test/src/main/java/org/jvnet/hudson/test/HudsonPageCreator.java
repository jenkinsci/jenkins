package org.jvnet.hudson.test;

import com.gargoylesoftware.htmlunit.DefaultPageCreator;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.PageCreator;

import java.io.IOException;

/**
 * {@link PageCreator} that understands JNLP file.
 * 
 * @author Kohsuke Kawaguchi
 */
public class HudsonPageCreator extends DefaultPageCreator {
    @Override
    public Page createPage(WebResponse webResponse, WebWindow webWindow) throws IOException {
        String contentType = webResponse.getContentType().toLowerCase();
        if(contentType.equals("application/x-java-jnlp-file"))
            return createXmlPage(webResponse, webWindow);
        return super.createPage(webResponse, webWindow);
    }

    public static final HudsonPageCreator INSTANCE = new HudsonPageCreator();
}
