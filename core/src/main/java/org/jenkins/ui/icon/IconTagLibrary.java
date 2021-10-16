package org.jenkins.ui.icon;

import org.apache.commons.jelly.TagLibrary;

public class IconTagLibrary extends TagLibrary {
    public IconTagLibrary() {
        registerTag("ionicon", IoniconTag.class);
    }
}
