package hudson.model;

import jenkins.model.Jenkins;
import org.jvnet.localizer.LocaleProvider;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.Stapler;

/**
 * {@link StatusIcon} for stock icon in Hudson.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.390.
 */
public final class StockStatusIcon extends AbstractStatusIcon {
    private final Localizable description;
    private final String image;

    /**
     * @param image
     *      Short file name like "folder.gif" that points to a stock icon in Hudson.
     * @param description
     *      Used as {@link #getDescription()}.
     */
    public StockStatusIcon(String image, Localizable description) {
        this.image = image;
        this.description = description;
    }

    @Override
    public String getImageOf(String size) {
        if (image.endsWith(".svg")) {
            return Stapler.getCurrentRequest2().getContextPath() + Jenkins.RESOURCE_PATH + "/images/svgs/" + image;
        } else {
            return Stapler.getCurrentRequest2().getContextPath() + Jenkins.RESOURCE_PATH +
                "/images/" + size + "/" + image;
        }
    }

    @Override
    public String getDescription() {
        return description.toString(LocaleProvider.getLocale());
    }
}
