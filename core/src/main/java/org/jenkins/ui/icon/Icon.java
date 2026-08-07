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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.jelly.JellyContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Simple icon metadata class.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.0
 */
public class Icon {

    public static final String ICON_SMALL_STYLE = "width: 16px; height: 16px;";
    public static final String ICON_MEDIUM_STYLE = "width: 24px; height: 24px;";
    public static final String ICON_LARGE_STYLE = "width: 32px; height: 32px;";
    public static final String ICON_XLARGE_STYLE = "width: 48px; height: 48px;";

    private static final String[] SUPPORTED_FORMATS = new String[] {".svg", ".png", ".gif"};
    private static final Map<String, String> iconDims = new HashMap<>();

    static {
        iconDims.put("16x16", "icon-sm");
        iconDims.put("24x24", "icon-md");
        iconDims.put("32x32", "icon-lg");
        iconDims.put("48x48", "icon-xlg");
    }

    private final String classSpec;
    private final String normalizedSelector;
    private final String url;
    private final String style;
    private IconType iconType;
    private IconFormat iconFormat;

    /**
     * Creates a {@link IconType#CORE core} icon.
     *
     * @param classSpec The icon class names. Expected to start with `icon-`.
     * @param style     The icon style.
     */
    public Icon(String classSpec, String style) {
        this(classSpec, null, style, IconType.CORE);
    }

    /**
     * Creates a {@link IconType#CORE core} icon.
     *
     * @param classSpec The icon class names. Expected to start with `icon-`.
     * @param url       The icon image url.
     * @param style     The icon style.
     */
    public Icon(String classSpec, String url, String style) {
        this(classSpec, url, style, IconType.CORE);
        if (url != null) {
            if (url.startsWith("images/")) {
                this.iconType = IconType.CORE;
            } else if (url.startsWith("plugin/")) {
                this.iconType = IconType.PLUGIN;
            }
        }
    }

    /**
     * Icon instance.
     *
     * @param classSpec The icon class specification. Expected to start with `icon-`.
     * @param url       The icon image url.
     * @param style     The icon style.
     * @param iconType  The icon type.
     */
    public Icon(String classSpec, String url, String style, IconType iconType) {
        this(classSpec, url, style, iconType, IconFormat.IMG);
    }

    /**
     * Creates an icon.
     *
     * @param classSpec The icon class names. Expected to start with `icon-`.
     * @param url       The icon image url.
     * @param style     The icon style.
     * @param iconFormat the {@link IconFormat}.
     * @since 2.283
     */
    public Icon(String classSpec, String url, String style, IconFormat iconFormat) {
        this(classSpec, url, style, IconType.CORE, iconFormat);
        if (url != null) {
            if (url.startsWith("images/")) {
                this.iconType = IconType.CORE;
            } else if (url.startsWith("plugin/")) {
                this.iconType = IconType.PLUGIN;
            }
        }
    }

    @Restricted(NoExternalUse.class)
    public Icon(String classSpec, String url, String style, IconType iconType, IconFormat iconFormat) {
        this.classSpec = classSpec;
        this.normalizedSelector = toNormalizedCSSSelector(classSpec);
        this.url = toNormalizedIconUrl(url);
        this.style = style;
        this.iconType = iconType;
        this.iconFormat = iconFormat;
    }

    /**
     * Get the class specification for this Icon.
     * @return The class specification for this Icon.
     */
    public String getClassSpec() {
        return classSpec;
    }

    /**
     * Is the Icon an SVG?
     * @since 2.283
     */
    public boolean isSvgSprite() {
        return iconFormat == IconFormat.EXTERNAL_SVG_SPRITE;
    }

    /**
     * Get the icon's normalized CSS selector.
     *
     * @return The icon normalized CSS selector.
     * @see #toNormalizedCSSSelector(String)
     */
    public String getNormalizedSelector() {
        return normalizedSelector;
    }

    /**
     * Get the icon url.
     *
     * @return The icon url.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Get the qualified icon url.
     * <br>
     * Qualifying the URL involves prefixing it depending on whether the icon is a core or plugin icon.
     *
     * @param context The JellyContext.
     * @return The qualified icon url.
     */
    public String getQualifiedUrl(JellyContext context) {
        if (url != null) {
            return iconType.toQualifiedUrl(url, context.getVariable("resURL").toString());
        } else {
            return "";
        }
    }

    /**
     * Get the qualified icon url.
     * <br>
     * Qualifying the URL involves prefixing it depending on whether the icon is a core or plugin icon.
     *
     * @param resUrl The url of resources.
     * @return The qualified icon url.
     */
    public String getQualifiedUrl(String resUrl) {
        if (url != null) {
            return iconType.toQualifiedUrl(url, resUrl);
        } else {
            return "";
        }
    }

    /**
     * Get the icon style.
     *
     * @return The icon style.
     */
    public String getStyle() {
        return style;
    }

    /**
     * Normalize the supplied string to an Icon name class e.g. "blue_anime" to "icon-blue-anime".
     *
     * @param string The string to be normalized.
     * @return The normalized icon name class.
     */
    public static String toNormalizedIconNameClass(String string) {
        if (string == null) {
            return null;
        }
        String iconName = toNormalizedIconName(string);
        if (iconName.startsWith("icon-")) {
            return iconName;
        }
        return "icon-" + iconName;
    }

    /**
     * Normalize the supplied string to an Icon name e.g. "blue_anime" to "blue-anime".
     *
     * @param string The string to be normalized.
     * @return The normalized icon name.
     */
    public static String toNormalizedIconName(String string) {
        if (string == null) {
            return null;
        }
        if (Arrays.stream(SUPPORTED_FORMATS).anyMatch(string::endsWith)) {
            string = string.substring(0, string.length() - 4);
        }
        return string.replace('_', '-');
    }

    /**
     * Normalize the supplied string to an Icon size class e.g. "16x16" to "icon-sm".
     *
     * @param string The string to be normalized.
     * @return The normalized icon size class, or the unmodified {@code string} arg
     *         if it was an unrecognised icon size.
     */
    public static String toNormalizedIconSizeClass(String string) {
        if (string == null) {
            return null;
        }
        String normalizedSizeClass = iconDims.get(string.trim());
        return normalizedSizeClass != null ? normalizedSizeClass : string;
    }

    /**
     * Generate a normalized CSS selector from the space separated list of icon class names.
     * <br>
     * The normalized CSS selector is the list of class names, alphabetically sorted and dot separated.
     * This means that "icon-help icon-xlg" and "icon-xlg icon-help" have the same normalized
     * selector ".icon-help.icon-xlg". Spaces are not relevant etc.
     *
     * @param classNames The space separated list of icon class names.
     * @return The normalized CSS selector.
     */
    public static String toNormalizedCSSSelector(String classNames) {
        if (classNames == null) {
            return null;
        }

        String[] classNameTokA = classNames.split(" ");
        List<String> classNameTokL = new ArrayList<>();

        // Trim all tokens first
        for (String classNameTok : classNameTokA) {
            String trimmedToken = classNameTok.trim();
            if (!trimmedToken.isEmpty()) {
                classNameTokL.add(trimmedToken);
            }
        }

        // Refill classNameTokA
        classNameTokA = new String[classNameTokL.size()];
        classNameTokL.toArray(classNameTokA);

        // Sort classNameTokA
        Arrays.sort(classNameTokA, Comparator.comparing(String::toString));

        // Build the compound name
        StringBuilder stringBuilder = new StringBuilder();
        for (String classNameTok : classNameTokA) {
            stringBuilder.append(".").append(classNameTok);
        }

        return stringBuilder.toString();
    }

    /**
     * Normalize the supplied url.
     *
     * @param url The url to be normalized.
     * @return The normalized url.
     */
    public static String toNormalizedIconUrl(String url) {
        if (url == null) {
            return null;
        }

        final String originalUrl = url;

        if (url.startsWith("/")) {
            url = url.substring(1);
        }
        if (url.startsWith("images/")) {
            return url.substring("images/".length());
        }
        if (url.startsWith("plugin/")) {
            return url.substring("plugin/".length());
        }

        return originalUrl;
    }
}
