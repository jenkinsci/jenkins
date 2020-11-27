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

import org.apache.commons.jelly.JellyContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An icon set.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.0
 */
public class IconSet {

    public static final IconSet icons = new IconSet();

    private Map<String, Icon> iconsByCSSSelector = new ConcurrentHashMap<String, Icon>();
    private Map<String, Icon> iconsByUrl  = new ConcurrentHashMap<String, Icon>();
    private Map<String, Icon> iconsByClassSpec = new ConcurrentHashMap<String, Icon>();
    private Map<String, Icon> coreIcons = new ConcurrentHashMap<String, Icon>();

    private static final Icon NO_ICON = new Icon("_", "_", "_");

    public IconSet() {
    }

    public Map<String, Icon> getCoreIcons() {
        return coreIcons;
    }

    public static void initPageVariables(JellyContext context) {
        context.setVariable("icons", icons);
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

    // Initialize the core Jenkins icons.  We need all this stuff so as to maintain
    // backward compatibility.
    static {
        // Small icons.
        // .png versions will override .gif versions => only time a gif should be returned by
        // name is when it's an animated status icon ("-anime-")
        icons.addIcon(new Icon("icon-aborted icon-sm", "16x16/aborted.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-aborted-anime icon-sm", "16x16/aborted_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-blue icon-sm", "16x16/blue.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-blue-anime icon-sm", "16x16/blue_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-clock icon-sm", "16x16/clock.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-clock-anime icon-sm", "16x16/clock_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-computer-flash icon-sm", "16x16/computer-flash.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-sm", "16x16/computer-x.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-computer icon-sm", "16x16/computer.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-sm", "16x16/disabled.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-disabled-anime icon-sm", "16x16/disabled_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-add icon-sm", "16x16/document_add.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-delete icon-sm", "16x16/document_delete.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-edit icon-sm", "16x16/document_edit.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-edit-delete icon-sm", "16x16/edit-delete.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-edit-select-all icon-sm", "16x16/edit-select-all.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-empty icon-sm", "16x16/empty.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-error icon-sm", "16x16/error.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-fingerprint icon-sm", "16x16/fingerprint.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder-error icon-sm", "16x16/folder-error.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder-open icon-sm", "16x16/folder-open.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder icon-sm", "16x16/folder.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-gear2 icon-sm", "16x16/gear2.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-go-next icon-sm", "16x16/go-next.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-green icon-sm", "16x16/green.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-sm", "16x16/green_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-grey icon-sm", "16x16/grey.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-grey-anime icon-sm", "16x16/grey_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-sm", "16x16/health-00to19.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-sm", "16x16/health-20to39.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-sm", "16x16/health-40to59.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-sm", "16x16/health-60to79.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-sm", "16x16/health-80plus.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-help icon-sm", "16x16/help.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-hourglass icon-sm", "16x16/hourglass.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-lock icon-sm", "16x16/lock.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-sm", "16x16/nobuilt.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-nobuilt-anime icon-sm", "16x16/nobuilt_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-notepad icon-sm", "16x16/notepad.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-package icon-sm", "16x16/package.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-person icon-sm", "16x16/person.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-plugin icon-sm", "16x16/plugin.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-red icon-sm", "16x16/red.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-red-anime icon-sm", "16x16/red_anime.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-redo icon-sm", "16x16/redo.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-save icon-sm", "16x16/save.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-search icon-sm", "16x16/search.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-sm", "16x16/star-gold.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-star icon-sm", "16x16/star.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-stop icon-sm", "16x16/stop.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-terminal icon-sm", "16x16/terminal.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-text-error icon-sm", "16x16/text-error.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-text icon-sm", "16x16/text.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-user icon-sm", "16x16/user.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-warning icon-sm", "16x16/warning.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-sm", "16x16/yellow.gif", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-yellow-anime icon-sm", "16x16/yellow_anime.gif", Icon.ICON_SMALL_STYLE));

        icons.addIcon(new Icon("icon-aborted icon-sm", "16x16/aborted.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-accept icon-sm", "16x16/accept.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-attribute icon-sm", "16x16/attribute.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-blue icon-sm", "16x16/blue.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-clock icon-sm", "16x16/clock.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-collapse icon-sm", "16x16/collapse.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-sm", "16x16/computer-x.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-computer icon-sm", "16x16/computer.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-sm", "16x16/disabled.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-add icon-sm", "16x16/document_add.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-delete icon-sm", "16x16/document_delete.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-document-edit icon-sm", "16x16/document_edit.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-edit-delete icon-sm", "16x16/edit-delete.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-edit-select-all icon-sm", "16x16/edit-select-all.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-empty icon-sm", "16x16/empty.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-error icon-sm", "16x16/error.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-expand icon-sm", "16x16/expand.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-fingerprint icon-sm", "16x16/fingerprint.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder-error icon-sm", "16x16/folder-error.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder-open icon-sm", "16x16/folder-open.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-folder icon-sm", "16x16/folder.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-gear2 icon-sm", "16x16/gear2.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-go-next icon-sm", "16x16/go-next.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-grey icon-sm", "16x16/grey.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-sm", "16x16/health-00to19.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-sm", "16x16/health-20to39.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-sm", "16x16/health-40to59.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-sm", "16x16/health-60to79.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-sm", "16x16/health-80plus.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-help icon-sm", "16x16/help.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-hourglass icon-sm", "16x16/hourglass.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-lock icon-sm", "16x16/lock.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-sm", "16x16/nobuilt.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-notepad icon-sm", "16x16/notepad.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-orange-square icon-sm", "16x16/orange-square.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-package icon-sm", "16x16/package.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-person icon-sm", "16x16/person.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-plugin icon-sm", "16x16/plugin.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-red icon-sm", "16x16/red.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-redo icon-sm", "16x16/redo.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-save icon-sm", "16x16/save.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-search icon-sm", "16x16/search.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-secure icon-sm", "16x16/secure.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-setting icon-sm", "16x16/setting.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-sm", "16x16/star-gold.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-star icon-sm", "16x16/star.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-stop icon-sm", "16x16/stop.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-terminal icon-sm", "16x16/terminal.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-text-error icon-sm", "16x16/text-error.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-text icon-sm", "16x16/text.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-user icon-sm", "16x16/user.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-warning icon-sm", "16x16/warning.png", Icon.ICON_SMALL_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-sm", "16x16/yellow.png", Icon.ICON_SMALL_STYLE));

        // Medium icons.
        // .png versions will override .gif versions => only time a gif should be returned by
        // name is when it's an animated status icon ("-anime-")
        icons.addIcon(new Icon("icon-aborted icon-md", "24x24/aborted.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-aborted-anime icon-md", "24x24/aborted_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-blue icon-md", "24x24/blue.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-blue-anime icon-md", "24x24/blue_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-clipboard icon-md", "24x24/clipboard.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-clock icon-md", "24x24/clock.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-clock-anime icon-md", "24x24/clock_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-computer-flash icon-md", "24x24/computer-flash.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-md", "24x24/computer-x.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-computer icon-md", "24x24/computer.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-delete-document icon-md", "24x24/delete-document.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-md", "24x24/disabled.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-disabled-anime icon-md", "24x24/disabled_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-document-properties icon-md", "24x24/document-properties.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-document icon-md", "24x24/document.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-edit-delete icon-md", "24x24/edit-delete.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-empty icon-md", "24x24/empty.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-fingerprint icon-md", "24x24/fingerprint.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-folder-delete icon-md", "24x24/folder-delete.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-folder icon-md", "24x24/folder.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-gear icon-md", "24x24/gear.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-gear2 icon-md", "24x24/gear2.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-graph icon-md", "24x24/graph.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-green icon-md", "24x24/green.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-md", "24x24/green_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-grey icon-md", "24x24/grey.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-grey-anime icon-md", "24x24/grey_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-md", "24x24/health-00to19.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-md", "24x24/health-20to39.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-md", "24x24/health-40to59.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-md", "24x24/health-60to79.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-md", "24x24/health-80plus.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-help icon-md", "24x24/help.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-installer icon-md", "24x24/installer.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-monitor icon-md", "24x24/monitor.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-computer icon-md", "24x24/new-computer.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-document icon-md", "24x24/new-document.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-package icon-md", "24x24/new-package.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-user icon-md", "24x24/new-user.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-next icon-md", "24x24/next.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-md", "24x24/nobuilt.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-nobuilt-anime icon-md", "24x24/nobuilt_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-notepad icon-md", "24x24/notepad.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-orange-square icon-md", "24x24/orange-square.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-package icon-md", "24x24/package.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-previous icon-md", "24x24/previous.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-red icon-md", "24x24/red.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-red-anime icon-md", "24x24/red_anime.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-redo icon-md", "24x24/redo.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-refresh icon-md", "24x24/refresh.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-save icon-md", "24x24/save.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-search icon-md", "24x24/search.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-setting icon-md", "24x24/setting.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-md", "24x24/star-gold.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-star icon-md", "24x24/star.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-terminal icon-md", "24x24/terminal.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-up icon-md", "24x24/up.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-user icon-md", "24x24/user.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-md", "24x24/yellow.gif", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-yellow-anime icon-md", "24x24/yellow_anime.gif", Icon.ICON_MEDIUM_STYLE));

        icons.addIcon(new Icon("icon-aborted icon-md", "24x24/aborted.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-accept icon-md", "24x24/accept.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-attribute icon-md", "24x24/attribute.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-blue icon-md", "24x24/blue.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-clipboard icon-md", "24x24/clipboard.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-clock icon-md", "24x24/clock.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-md", "24x24/computer-x.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-computer icon-md", "24x24/computer.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-delete-document icon-md", "24x24/delete-document.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-md", "24x24/disabled.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-document-properties icon-md", "24x24/document-properties.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-document icon-md", "24x24/document.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-edit-delete icon-md", "24x24/edit-delete.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-empty icon-md", "24x24/empty.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-fingerprint icon-md", "24x24/fingerprint.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-folder-delete icon-md", "24x24/folder-delete.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-folder icon-md", "24x24/folder.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-gear icon-md", "24x24/gear.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-gear2 icon-md", "24x24/gear2.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-graph icon-md", "24x24/graph.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-grey icon-md", "24x24/grey.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-md", "24x24/health-00to19.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-md", "24x24/health-20to39.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-md", "24x24/health-40to59.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-md", "24x24/health-60to79.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-md", "24x24/health-80plus.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-help icon-md", "24x24/help.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-installer icon-md", "24x24/installer.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-lock icon-md", "24x24/lock.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-monitor icon-md", "24x24/monitor.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-computer icon-md", "24x24/new-computer.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-document icon-md", "24x24/new-document.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-package icon-md", "24x24/new-package.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-new-user icon-md", "24x24/new-user.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-next icon-md", "24x24/next.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-md", "24x24/nobuilt.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-notepad icon-md", "24x24/notepad.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-orange-square icon-md", "24x24/orange-square.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-package icon-md", "24x24/package.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-plugin icon-md", "24x24/plugin.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-previous icon-md", "24x24/previous.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-red icon-md", "24x24/red.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-redo icon-md", "24x24/redo.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-refresh icon-md", "24x24/refresh.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-save icon-md", "24x24/save.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-search icon-md", "24x24/search.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-secure icon-md", "24x24/secure.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-setting icon-md", "24x24/setting.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-md", "24x24/star-gold.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-star icon-md", "24x24/star.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-terminal icon-md", "24x24/terminal.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-up icon-md", "24x24/up.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-user icon-md", "24x24/user.png", Icon.ICON_MEDIUM_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-md", "24x24/yellow.png", Icon.ICON_MEDIUM_STYLE));

        // Large icons.
        // .png versions will override .gif versions => only time a gif should be returned by
        // name is when it's an animated status icon ("-anime")
        icons.addIcon(new Icon("icon-aborted icon-lg", "32x32/aborted.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-aborted-anime icon-lg", "32x32/aborted_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-blue icon-lg", "32x32/blue.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-blue-anime icon-lg", "32x32/blue_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-clipboard icon-lg", "32x32/clipboard.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-clock icon-lg", "32x32/clock.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-clock-anime icon-lg", "32x32/clock_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-computer-flash icon-lg", "32x32/computer-flash.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-lg", "32x32/computer-x.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-computer icon-lg", "32x32/computer.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-lg", "32x32/disabled.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-disabled-anime icon-lg", "32x32/disabled_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-empty icon-lg", "32x32/empty.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-error icon-lg", "32x32/error.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-folder icon-lg", "32x32/folder.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-graph icon-lg", "32x32/graph.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-green icon-lg", "32x32/green.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-lg", "32x32/green_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-grey icon-lg", "32x32/grey.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-grey-anime icon-lg", "32x32/grey_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-lg", "32x32/health-00to19.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-lg", "32x32/health-20to39.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-lg", "32x32/health-40to59.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-lg", "32x32/health-60to79.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-lg", "32x32/health-80plus.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-lg", "32x32/nobuilt.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-nobuilt-anime icon-lg", "32x32/nobuilt_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-plugin icon-lg", "32x32/plugin.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-red icon-lg", "32x32/red.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-red-anime icon-lg", "32x32/red_anime.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-setting icon-lg", "32x32/setting.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-lg", "32x32/star-gold.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-star icon-lg", "32x32/star.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-user icon-lg", "32x32/user.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-lg", "32x32/yellow.gif", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-yellow-anime icon-lg", "32x32/yellow_anime.gif", Icon.ICON_LARGE_STYLE));

        icons.addIcon(new Icon("icon-aborted icon-lg", "32x32/aborted.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-accept icon-lg", "32x32/accept.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-attribute icon-lg", "32x32/attribute.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-blue icon-lg", "32x32/blue.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-clipboard icon-lg", "32x32/clipboard.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-clock icon-lg", "32x32/clock.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-lg", "32x32/computer-x.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-computer icon-lg", "32x32/computer.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-lg", "32x32/disabled.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-empty icon-lg", "32x32/empty.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-error icon-lg", "32x32/error.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-folder icon-lg", "32x32/folder.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-gear2 icon-lg", "32x32/gear2.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-graph icon-lg", "32x32/graph.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-grey icon-lg", "32x32/grey.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-lg", "32x32/health-00to19.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-lg", "32x32/health-20to39.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-lg", "32x32/health-40to59.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-lg", "32x32/health-60to79.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-lg", "32x32/health-80plus.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-lock icon-lg", "32x32/lock.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-lg", "32x32/nobuilt.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-orange-square icon-lg", "32x32/orange-square.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-package icon-lg", "32x32/package.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-plugin icon-lg", "32x32/plugin.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-red icon-lg", "32x32/red.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-secure icon-lg", "32x32/secure.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-setting icon-lg", "32x32/setting.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-lg", "32x32/star-gold.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-star icon-lg", "32x32/star.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-user icon-lg", "32x32/user.png", Icon.ICON_LARGE_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-lg", "32x32/yellow.png", Icon.ICON_LARGE_STYLE));

        // X icon-lg icons.
        // .png versions will override .gif versions => only time a gif should be returned by
        // name is when it's an animated status icon ("-anime-")
        icons.addIcon(new Icon("icon-aborted icon-xlg", "48x48/aborted.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-aborted-anime icon-xlg", "48x48/aborted_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-blue icon-xlg", "48x48/blue.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-blue-anime icon-xlg", "48x48/blue_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-clipboard icon-xlg", "48x48/clipboard.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-computer-flash icon-xlg", "48x48/computer-flash.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-xlg", "48x48/computer-x.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-computer icon-xlg", "48x48/computer.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-xlg", "48x48/disabled.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-disabled-anime icon-xlg", "48x48/disabled_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-document icon-xlg", "48x48/document.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-empty icon-xlg", "48x48/empty.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-error icon-xlg", "48x48/error.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-fingerprint icon-xlg", "48x48/fingerprint.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-folder-delete icon-xlg", "48x48/folder-delete.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-folder icon-xlg", "48x48/folder.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-gear2 icon-xlg", "48x48/gear2.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-graph icon-xlg", "48x48/graph.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-green icon-xlg", "48x48/green.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-green-anime icon-xlg", "48x48/green_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-grey icon-xlg", "48x48/grey.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-grey-anime icon-xlg", "48x48/grey_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-xlg", "48x48/health-00to19.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-xlg", "48x48/health-20to39.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-xlg", "48x48/health-40to59.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-xlg", "48x48/health-60to79.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-xlg", "48x48/health-80plus.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-help icon-xlg", "48x48/help.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-installer icon-xlg", "48x48/installer.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-monitor icon-xlg", "48x48/monitor.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-network icon-xlg", "48x48/network.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-xlg", "48x48/nobuilt.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-nobuilt-anime icon-xlg", "48x48/nobuilt_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-notepad icon-xlg", "48x48/notepad.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-orange-square icon-xlg", "48x48/orange-square.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-package icon-xlg", "48x48/package.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-plugin icon-xlg", "48x48/plugin.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-red icon-xlg", "48x48/red.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-red-anime icon-xlg", "48x48/red_anime.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-redo icon-xlg", "48x48/redo.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-refresh icon-xlg", "48x48/refresh.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-search icon-xlg", "48x48/search.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-secure icon-xlg", "48x48/secure.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-setting icon-xlg", "48x48/setting.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-xlg", "48x48/star-gold.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-star icon-xlg", "48x48/star.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-system-log-out icon-xlg", "48x48/system-log-out.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-terminal icon-xlg", "48x48/terminal.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-user icon-xlg", "48x48/user.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-warning icon-xlg", "48x48/warning.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-xlg", "48x48/yellow.gif", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-yellow-anime icon-xlg", "48x48/yellow_anime.gif", Icon.ICON_XLARGE_STYLE));

        icons.addIcon(new Icon("icon-aborted icon-xlg", "48x48/aborted.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-accept icon-xlg", "48x48/accept.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-attribute icon-xlg", "48x48/attribute.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-blue icon-xlg", "48x48/blue.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-clipboard icon-xlg", "48x48/clipboard.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-computer-x icon-xlg", "48x48/computer-x.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-computer icon-xlg", "48x48/computer.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-disabled icon-xlg", "48x48/disabled.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-document icon-xlg", "48x48/document.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-empty icon-xlg", "48x48/empty.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-error icon-xlg", "48x48/error.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-fingerprint icon-xlg", "48x48/fingerprint.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-folder-delete icon-xlg", "48x48/folder-delete.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-folder icon-xlg", "48x48/folder.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-gear2 icon-xlg", "48x48/gear2.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-graph icon-xlg", "48x48/graph.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-grey icon-xlg", "48x48/grey.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-00to19 icon-xlg", "48x48/health-00to19.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-20to39 icon-xlg", "48x48/health-20to39.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-40to59 icon-xlg", "48x48/health-40to59.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-60to79 icon-xlg", "48x48/health-60to79.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-health-80plus icon-xlg", "48x48/health-80plus.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-help icon-xlg", "48x48/help.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-installer icon-xlg", "48x48/installer.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-lock icon-xlg", "48x48/lock.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-monitor icon-xlg", "48x48/monitor.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-network icon-xlg", "48x48/network.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-nobuilt icon-xlg", "48x48/nobuilt.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-notepad icon-xlg", "48x48/notepad.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-orange-square icon-xlg", "48x48/orange-square.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-package icon-xlg", "48x48/package.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-plugin icon-xlg", "48x48/plugin.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-red icon-xlg", "48x48/red.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-redo icon-xlg", "48x48/redo.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-refresh icon-xlg", "48x48/refresh.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-search icon-xlg", "48x48/search.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-secure icon-xlg", "48x48/secure.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-setting icon-xlg", "48x48/setting.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-star-gold icon-xlg", "48x48/star-gold.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-star icon-xlg", "48x48/star.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-system-log-out icon-xlg", "48x48/system-log-out.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-terminal icon-xlg", "48x48/terminal.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-user icon-xlg", "48x48/user.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-warning icon-xlg", "48x48/warning.png", Icon.ICON_XLARGE_STYLE));
        icons.addIcon(new Icon("icon-yellow icon-xlg", "48x48/yellow.png", Icon.ICON_XLARGE_STYLE));

        // Capture a list of the core icons.
        icons.coreIcons.putAll(icons.iconsByCSSSelector);
    }
}
