/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkins.ui.icon;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.apache.commons.jelly.JellyContext;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * An icon set.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.0
 */
public class IconSet {


    public static final IconSet icons = new IconSet();

    private Map<String, Icon> iconsByCSSSelector = new ConcurrentHashMap<>();
    private Map<String, Icon> iconsByUrl  = new ConcurrentHashMap<>();
    private Map<String, Icon> iconsByClassSpec = new ConcurrentHashMap<>();
    private Map<String, Icon> coreIcons = new ConcurrentHashMap<>();

    private static final Icon NO_ICON = new Icon("_", "_", "_");

    public IconSet() {
    }

    public Map<String, Icon> getCoreIcons() {
        return coreIcons;
    }

    public static void initPageVariables(JellyContext context) {
        context.setVariable("icons", icons);
    }

    // for Jelly
    @SuppressWarnings("unused")
    @Restricted(NoExternalUse.class)
    public static String getSymbol(String name, String title, String tooltip, String htmlTooltip, String classes, String pluginName, String id) {
        return Symbol.get(new SymbolRequest.Builder()
                                 .withName(IconSet.cleanName(name))
                                 .withTitle(title)
                                 .withTooltip(tooltip)
                                 .withHtmlTooltip(htmlTooltip)
                                 .withClasses(classes)
                                 .withPluginName(pluginName)
                                 .withId(id)
                                 .build()
        );
    }

    public IconSet addIcon(Icon icon) {
        iconsByCSSSelector.put(icon.getNormalizedSelector(), icon);
        if (icon.getUrl() != null) {
            iconsByUrl.put(icon.getUrl(), icon);
        }
        iconsByClassSpec.clear(); // regenerate, in case an icon has been redefined.
        return this;
    }

    /**
     * Get an icon instance from it's {@link  Icon#toNormalizedCSSSelector(String) normalized CSS selector}.
     * <br>
     * This {@link Object} based version allows the function to be resolvable e.g. from JEXL expressions that
     * are trying to perform reflective lookup using a GString (instead of a {@link String}).
     *
     * @param cssSelector The icon's normalized CSS selector.
     * @return The icon instance, or {@code null} if no such icon.
     */
    public Icon getIconByNormalizedCSSSelector(Object cssSelector) {
        if (cssSelector == null) {
            return null;
        }
        return getIconByNormalizedCSSSelector(cssSelector.toString());
    }

    /**
     * Get an icon instance from it's {@link  Icon#toNormalizedCSSSelector(String) normalized CSS selector}.
     * @param cssSelector The icon's normalized CSS selector.
     * @return The icon instance, or {@code null} if no such icon.
     */
    private Icon getIconByNormalizedCSSSelector(String cssSelector) {
        if (cssSelector == null) {
            return null;
        }
        return iconsByCSSSelector.get(cssSelector);
    }

    /**
     * Get an icon instance from a class specification.
     * <br>
     * This {@link Object} based version allows the function to be resolvable e.g. from JEXL expressions that
     * are trying to perform reflective lookup using a GString (instead of a {@link String}).
     *
     * @param iconClassSpec The icon's class spec as defined on the &lt;l:icon class&gt; attribute.
     * @return The icon instance, or {@code null} if no such icon.
     */
    public Icon getIconByClassSpec(Object iconClassSpec) {
        if (iconClassSpec == null) {
            return null;
        }
        return getIconByClassSpec(iconClassSpec.toString());
    }

    /**
     * Get an icon instance from a class specification.
     * @param iconClassSpec The icon's class spec as defined on the &lt;l:icon class&gt; attribute.
     * @return The icon instance, or {@code null} if no such icon.
     */
    private Icon getIconByClassSpec(String iconClassSpec) {
        if (iconClassSpec == null) {
            return null;
        }

        Icon icon = iconsByClassSpec.get(iconClassSpec);

        if (icon == NO_ICON) {
            return null;
        }

        if (icon != null) {
            return icon;
        }

        String normalizedCSSSelector = Icon.toNormalizedCSSSelector(iconClassSpec);

        icon = getIconByNormalizedCSSSelector(normalizedCSSSelector);
        if (icon != null) {
            iconsByClassSpec.put(iconClassSpec, icon);
            return icon;
        } else {
            iconsByClassSpec.put(iconClassSpec, NO_ICON);
            return null;
        }
    }

    /**
     * Get an icon instance from it's url.
     * <br>
     * This {@link Object} based version allows the function to be resolvable e.g. from JEXL expressions that
     * are trying to perform reflective lookup using a GString (instead of a {@link String}).
     *
     * @param url The icon url.
     * @return The icon instance, or {@code null} if no such icon.
     */
    public Icon getIconByUrl(Object url) {
        if (url == null) {
            return null;
        }
        return getIconByUrl(url.toString());
    }

    /**
     * Get an icon instance from it's url.
     * @param url The icon url.
     * @return The icon instance, or {@code null} if no such icon.
     */
    private Icon getIconByUrl(String url) {
        if (url == null) {
            return null;
        }

        url = Icon.toNormalizedIconUrl(url);

        return iconsByUrl.get(url);
    }

    /**
     * Normalize the supplied string to an Icon name class e.g. "blue_anime" to "icon-blue-anime".
     * <br>
     * This {@link Object} based version allows the function to be resolvable e.g. from JEXL expressions that
     * are trying to perform reflective lookup using a GString (instead of a {@link String}).
     *
     *
     * @param string The string to be normalized.
     * @return The normalized icon name class.
     */
    public static String toNormalizedIconNameClass(Object string) {
        if (string == null) {
            return null;
        }
        return toNormalizedIconNameClass(string.toString());
    }

    /**
     * Normalize the supplied string to an Icon name class e.g. "blue_anime" to "icon-blue-anime".
     *
     * @param string The string to be normalized.
     * @return The normalized icon name class.
     */
    private static String toNormalizedIconNameClass(String string) {
        return Icon.toNormalizedIconNameClass(string);
    }

    /**
     * Normalize the supplied string to an Icon size class e.g. "16x16" to "icon-sm".
     * <br>
     * This {@link Object} based version allows the function to be resolvable e.g. from JEXL expressions that
     * are trying to perform reflective lookup using a GString (instead of a {@link String}).
     *
     *
     * @param string The string to be normalized.
     * @return The normalized icon size class, or the unmodified {@code string} arg
     *         if it was an unrecognised icon size.
     */
    public static String toNormalizedIconSizeClass(Object string) {
        if (string == null) {
            return null;
        }
        return toNormalizedIconSizeClass(string.toString());
    }

    /**
     * Normalize the supplied string to an Icon size class e.g. "16x16" to "icon-sm".
     *
     * @param string The string to be normalized.
     * @return The normalized icon size class, or the unmodified {@code string} arg
     *         if it was an unrecognised icon size.
     */
    private static String toNormalizedIconSizeClass(String string) {
        return Icon.toNormalizedIconSizeClass(string);
    }

    /**
     * Normalize the supplied url.
     * <br>
     * This {@link Object} based version allows the function to be resolvable e.g. from JEXL expressions that
     * are trying to perform reflective lookup using a GString (instead of a {@link String}).
     *
     *
     * @param url The url to be normalized.
     * @return The normalized url.
     */
    public static String toNormalizedIconUrl(Object url) {
        if (url == null) {
            return null;
        }
        return toNormalizedIconUrl(url.toString());
    }

    /**
     * Normalize the supplied url.
     *
     * @param url The url to be normalized.
     * @return The normalized url.
     */
    private static String toNormalizedIconUrl(String url) {
        return Icon.toNormalizedIconUrl(url);
    }

    // Initialize the core Jenkins icons.
    // We need all this stuff to maintain backward compatibility.
    // SVG will override PNG, PNG will override GIF
    // Only time a gif should be returned by name is when it's an animated status icon ("-anime-")
    static {
        // Small icons
        icons.addIcon(new BuildStatusIcon("icon-aborted icon-sm", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-aborted-anime icon-sm", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_SMALL_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-blue icon-sm", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-blue-anime icon-sm", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_SMALL_STYLE, true));
        icons.addIcon(new Icon("icon-clock-anime icon-sm", "16x16/clock_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-disabled icon-sm", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-disabled-anime icon-sm", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_SMALL_STYLE, true));
        icons.addIcon(new Icon("icon-document-add icon-sm", "16x16/document_add.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-delete icon-sm", "16x16/document_delete.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-edit icon-sm", "16x16/document_edit.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-edit-delete icon-sm", "16x16/edit-delete.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-edit-select-all icon-sm", "16x16/edit-select-all.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-empty icon-sm", "16x16/empty.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder-open icon-sm", "16x16/folder-open.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-green icon-sm", "16x16/green.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-sm", "16x16/green_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-sm", "build-status/build-status-sprite.svg#never-built", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey-anime icon-sm", "build-status/build-status-sprite.svg#never-built", Icon.ICON_SMALL_STYLE, true));
        icons.addIcon(new WeatherIcon("icon-health-00to19 icon-sm", Icon.ICON_SMALL_STYLE, WeatherIcon.Status.POURING));
        icons.addIcon(new WeatherIcon("icon-health-20to39 icon-sm", Icon.ICON_SMALL_STYLE, WeatherIcon.Status.RAINY));
        icons.addIcon(new WeatherIcon("icon-health-40to59 icon-sm", Icon.ICON_SMALL_STYLE, WeatherIcon.Status.CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-60to79 icon-sm", Icon.ICON_SMALL_STYLE, WeatherIcon.Status.PARTLY_CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-80plus icon-sm", Icon.ICON_SMALL_STYLE, WeatherIcon.Status.SUNNY));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt icon-sm", "build-status/build-status-sprite.svg#never-built", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt-anime icon-sm", "build-status/build-status-sprite.svg#never-built", Icon.ICON_SMALL_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-red icon-sm", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-red-anime icon-sm", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_SMALL_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-yellow icon-sm", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-yellow-anime icon-sm", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_SMALL_STYLE, true));
        icons.addIcon(new Icon("icon-collapse icon-sm", "16x16/collapse.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-add icon-sm", "16x16/document_add.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-delete icon-sm", "16x16/document_delete.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-edit icon-sm", "16x16/document_edit.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-edit-select-all icon-sm", "16x16/edit-select-all.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-empty icon-sm", "16x16/empty.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-expand icon-sm", "16x16/expand.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder-open icon-sm", "16x16/folder-open.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-go-next icon-sm", "16x16/go-next.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-sm", "build-status/build-status-sprite.svg#never-built", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-text-error icon-sm", "16x16/text-error.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-text icon-sm", "16x16/text.png", Icon.ICON_SMALL_STYLE));

        // Medium icons
        icons.addIcon(new BuildStatusIcon("icon-aborted icon-md", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-aborted-anime icon-md", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_MEDIUM_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-blue icon-md", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-blue-anime icon-md", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_MEDIUM_STYLE, true));
        icons.addIcon(new Icon("icon-clock-anime icon-md", "24x24/clock_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-disabled icon-md", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-disabled-anime icon-md", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_MEDIUM_STYLE, true));
        icons.addIcon(new Icon("icon-empty icon-md", "24x24/empty.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-green icon-md", "24x24/green.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-md", "24x24/green_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-md", "build-status/build-status-sprite.svg#never-built", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey-anime icon-md", "build-status/build-status-sprite.svg#never-built", Icon.ICON_MEDIUM_STYLE, true));
        icons.addIcon(new WeatherIcon("icon-health-00to19 icon-md", Icon.ICON_MEDIUM_STYLE, WeatherIcon.Status.POURING));
        icons.addIcon(new WeatherIcon("icon-health-20to39 icon-md", Icon.ICON_MEDIUM_STYLE, WeatherIcon.Status.RAINY));
        icons.addIcon(new WeatherIcon("icon-health-40to59 icon-md", Icon.ICON_MEDIUM_STYLE, WeatherIcon.Status.CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-60to79 icon-md",  Icon.ICON_MEDIUM_STYLE, WeatherIcon.Status.PARTLY_CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-80plus icon-md",  Icon.ICON_MEDIUM_STYLE, WeatherIcon.Status.SUNNY));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt icon-md", "build-status/build-status-sprite.svg#never-built", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt-anime icon-md", "build-status/build-status-sprite.svg#never-built", Icon.ICON_MEDIUM_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-red icon-md", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-red-anime icon-md", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_MEDIUM_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-yellow icon-md", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-yellow-anime icon-md", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_MEDIUM_STYLE, true));
        icons.addIcon(new Icon("icon-empty icon-md", "24x24/empty.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-md", "build-status/build-status-sprite.svg#never-built", Icon.ICON_MEDIUM_STYLE));

        // Large icons
        icons.addIcon(new BuildStatusIcon("icon-aborted icon-lg", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-aborted-anime icon-lg", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_LARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-blue icon-lg", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-blue-anime icon-lg", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_LARGE_STYLE, true));
        icons.addIcon(new Icon("icon-clock-anime icon-lg", "32x32/clock_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-disabled icon-lg", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-disabled-anime icon-lg", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_LARGE_STYLE, true));
        icons.addIcon(new Icon("icon-empty icon-lg", "32x32/empty.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-green icon-lg", "32x32/green.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-lg", "32x32/green_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-lg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey-anime icon-lg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_LARGE_STYLE, true));
        icons.addIcon(new Icon("icon-empty icon-lg", "32x32/empty.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new WeatherIcon("icon-health-00to19 icon-lg", Icon.ICON_LARGE_STYLE, WeatherIcon.Status.POURING));
        icons.addIcon(new WeatherIcon("icon-health-20to39 icon-lg", Icon.ICON_LARGE_STYLE, WeatherIcon.Status.RAINY));
        icons.addIcon(new WeatherIcon("icon-health-40to59 icon-lg", Icon.ICON_LARGE_STYLE, WeatherIcon.Status.CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-60to79 icon-lg",  Icon.ICON_LARGE_STYLE, WeatherIcon.Status.PARTLY_CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-80plus icon-lg", Icon.ICON_LARGE_STYLE, WeatherIcon.Status.SUNNY));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt icon-lg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt-anime icon-lg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_LARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-red icon-lg", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-red-anime icon-lg", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_LARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-yellow icon-lg", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-yellow-anime icon-lg", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_LARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-lg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_LARGE_STYLE));

        // Extra-large icons
        icons.addIcon(new BuildStatusIcon("icon-aborted icon-xlg", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-aborted-anime icon-xlg", "build-status/build-status-sprite.svg#last-aborted", Icon.ICON_XLARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-blue icon-xlg", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-blue-anime icon-xlg", "build-status/build-status-sprite.svg#last-successful", Icon.ICON_XLARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-disabled icon-xlg", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-disabled-anime icon-xlg", "build-status/build-status-sprite.svg#last-disabled", Icon.ICON_XLARGE_STYLE, true));
        icons.addIcon(new Icon("icon-green icon-xlg", "48x48/green.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-xlg", "48x48/green_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-xlg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey-anime icon-xlg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_XLARGE_STYLE, true));
        icons.addIcon(new WeatherIcon("icon-health-00to19 icon-xlg", Icon.ICON_XLARGE_STYLE, WeatherIcon.Status.POURING));
        icons.addIcon(new WeatherIcon("icon-health-20to39 icon-xlg", Icon.ICON_XLARGE_STYLE, WeatherIcon.Status.RAINY));
        icons.addIcon(new WeatherIcon("icon-health-40to59 icon-xlg", Icon.ICON_XLARGE_STYLE, WeatherIcon.Status.CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-60to79 icon-xlg", Icon.ICON_XLARGE_STYLE, WeatherIcon.Status.PARTLY_CLOUDY));
        icons.addIcon(new WeatherIcon("icon-health-80plus icon-xlg", Icon.ICON_XLARGE_STYLE, WeatherIcon.Status.SUNNY));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt icon-xlg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-nobuilt-anime icon-xlg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_XLARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-red icon-xlg", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-red-anime icon-xlg", "build-status/build-status-sprite.svg#last-failed", Icon.ICON_XLARGE_STYLE, true));
        icons.addIcon(new BuildStatusIcon("icon-yellow icon-xlg", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-yellow-anime icon-xlg", "build-status/build-status-sprite.svg#last-unstable", Icon.ICON_XLARGE_STYLE, true));
        icons.addIcon(new Icon("icon-empty icon-xlg", "48x48/empty.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new BuildStatusIcon("icon-grey icon-xlg", "build-status/build-status-sprite.svg#never-built", Icon.ICON_XLARGE_STYLE));

        initializeSVGs();

        // Capture a list of the core icons.
        icons.coreIcons.putAll(icons.iconsByCSSSelector);
    }

    private static void initializeSVGs() {
        Map<String, String> sizes = new HashMap<>();
        sizes.put("icon-sm", Icon.ICON_SMALL_STYLE);
        sizes.put("icon-md", Icon.ICON_MEDIUM_STYLE);
        sizes.put("icon-lg", Icon.ICON_LARGE_STYLE);
        sizes.put("icon-xlg", Icon.ICON_XLARGE_STYLE);

        List<String> images = new ArrayList<>();
        images.add("computer");
        images.add("delete-document");
        images.add("accept");
        images.add("application-certificate");
        images.add("attribute");
        images.add("bookmark-new");
        images.add("certificate");
        images.add("clipboard-list-solid");
        images.add("clipboard");
        images.add("clock");
        images.add("computer-user-offline");
        images.add("computer-x");
        images.add("document");
        images.add("edit-delete");
        images.add("emblem-urgent");
        images.add("error");
        images.add("fingerprint");
        images.add("folder-delete");
        images.add("folder");
        images.add("gear");
        images.add("gear2");
        images.add("go-down");
        images.add("go-up");
        images.add("graph");
        images.add("headless");
        images.add("headshot");
        images.add("hourglass");
        images.add("installer");
        images.add("keys");
        images.add("lock");
        images.add("logo");
        images.add("monitor");
        images.add("network");
        images.add("new-computer");
        images.add("new-document");
        images.add("new-package");
        images.add("new-user");
        images.add("next");
        images.add("notepad");
        images.add("orange-square");
        images.add("package");
        images.add("person");
        images.add("plugin");
        images.add("previous");
        images.add("redo");
        images.add("refresh");
        images.add("save-new");
        images.add("save");
        images.add("search");
        images.add("secure");
        images.add("setting");
        images.add("shield");
        images.add("star-gold");
        images.add("star-large-gold");
        images.add("star-large");
        images.add("star");
        images.add("stop");
        images.add("system-log-out");
        images.add("terminal");
        images.add("undo");
        images.add("up");
        images.add("user");
        images.add("video");
        images.add("warning");
        images.add("document-properties");
        images.add("help");

        for (Map.Entry<String, String> size : sizes.entrySet()) {
            for (String image : images) {
                icons.addIcon(new Icon("icon-" + image + " " + size.getKey(),
                        "svgs/" + image + ".svg", size.getValue()));
            }
        }
    }

    private static final Map<String, String> ICON_TO_SYMBOL_TRANSLATIONS;

    static {
        Map<String, String> translations = new HashMap<>();
        translations.put("icon-application-certificate", "symbol-ribbon");
        translations.put("icon-document", "symbol-document-text");
        translations.put("icon-clipboard", "symbol-logs");
        translations.put("icon-clock", "symbol-play");
        translations.put("icon-edit-delete", "symbol-trash");
        translations.put("icon-fingerprint", "symbol-fingerprint");
        translations.put("icon-folder", "symbol-folder");
        translations.put("icon-gear", "symbol-settings");
        translations.put("icon-gear2", "symbol-settings");
        translations.put("icon-health-00to19", "symbol-weather-icon-health-00to19");
        translations.put("icon-health-20to39", "symbol-weather-icon-health-20to39");
        translations.put("icon-health-40to59", "symbol-weather-icon-health-40to59");
        translations.put("icon-health-60to79", "symbol-weather-icon-health-60to79");
        translations.put("icon-health-80plus", "symbol-weather-icon-health-80plus");
        translations.put("icon-help", "symbol-help-circle");
        translations.put("icon-keys", "symbol-key");
        translations.put("icon-monitor", "symbol-terminal");
        translations.put("icon-new-package", "symbol-add");
        translations.put("icon-next", "symbol-arrow-right");
        translations.put("icon-plugin", "symbol-plugins");
        translations.put("icon-previous", "symbol-arrow-left");
        translations.put("icon-search", "symbol-search");
        translations.put("icon-setting", "symbol-build");
        translations.put("icon-terminal", "symbol-terminal");
        translations.put("icon-text", "symbol-details");
        translations.put("icon-up", "symbol-arrow-up");
        translations.put("icon-user", "symbol-people");
        translations.put("icon-undo", "symbol-undo");
        translations.put("icon-redo", "symbol-redo");
        translations.put("icon-hourglass", "symbol-hourglass");
        ICON_TO_SYMBOL_TRANSLATIONS = translations;
    }

    /**
     * This is a temporary function to replace Tango icons across Jenkins and plugins with
     * appropriate Jenkins Symbols
     *
     * @param tangoIcon A tango icon in the format 'icon-* size-*', e.g. 'icon-gear icon-lg'
     * @return a Jenkins Symbol (if one exists) otherwise null
     */
    @Restricted(NoExternalUse.class)
    public static String tryTranslateTangoIconToSymbol(@CheckForNull String tangoIcon) {
        return tryTranslateTangoIconToSymbol(tangoIcon, () -> null);
    }

    /**
     * This is a temporary function to replace Tango icons across Jenkins and plugins with
     * appropriate Jenkins Symbols
     *
     * @param tangoIcon A tango icon in the format 'icon-* size-*', e.g. 'icon-gear icon-lg'
     * @param defaultValueSupplier A supplier function that will be called if no icon translation is found
     * @return a Jenkins Symbol (if one exists) otherwise the value returned by the supplier
     */
    @Restricted(NoExternalUse.class)
    public static String tryTranslateTangoIconToSymbol(@CheckForNull String tangoIcon, @NonNull Supplier<String> defaultValueSupplier) {
        return tangoIcon == null ? null : ICON_TO_SYMBOL_TRANSLATIONS.getOrDefault(cleanName(tangoIcon), defaultValueSupplier.get());
    }

    private static String cleanName(String tangoIcon) {
        if (tangoIcon != null) {
            tangoIcon = tangoIcon.split(" ")[0];
        }
        return tangoIcon;
    }
}
