/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Simon Wiest
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
package hudson.model;

import hudson.util.ColorPalette;
import jenkins.model.Jenkins;
import org.jenkins.ui.icon.Icon;
import org.jvnet.localizer.LocaleProvider;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.Stapler;

import java.awt.*;
import java.util.Locale;

/**
 * Ball color used for the build status indication.
 *
 * <p>
 * There are four basic colors, plus their animated "bouncy" versions.
 * {@link #ordinal()} is the sort order. 
 *
 * <p>
 * Note that multiple {@link BallColor} instances may map to the same
 * RGB color, to avoid the rainbow effect.
 *
 * <h2>Historical Note</h2>
 * <p>
 * Hudson started to overload colors &mdash; for example grey could mean
 * either disabled, aborted, or not yet built. As a result, {@link BallColor}
 * becomes more like a "logical" color, in the sense that different {@link BallColor}
 * values can map to the same RGB color. See issue #956.
 *
 * @author Kohsuke Kawaguchi
 */
public enum BallColor implements StatusIcon {
    RED("red",Messages._BallColor_Failed(), ColorPalette.RED),
    RED_ANIME("red_anime",Messages._BallColor_InProgress(), ColorPalette.RED),
    YELLOW("yellow",Messages._BallColor_Unstable(), ColorPalette.YELLOW),
    YELLOW_ANIME("yellow_anime",Messages._BallColor_InProgress(), ColorPalette.YELLOW),
    BLUE("blue",Messages._BallColor_Success(), ColorPalette.BLUE),
    BLUE_ANIME("blue_anime",Messages._BallColor_InProgress(), ColorPalette.BLUE),
    // for historical reasons they are called grey.
    GREY("grey",Messages._BallColor_Pending(), ColorPalette.GREY),
    GREY_ANIME("grey_anime",Messages._BallColor_InProgress(), ColorPalette.GREY),

    DISABLED("disabled",Messages._BallColor_Disabled(), ColorPalette.GREY),
    DISABLED_ANIME("disabled_anime",Messages._BallColor_InProgress(), ColorPalette.GREY),
    ABORTED("aborted",Messages._BallColor_Aborted(), ColorPalette.GREY),
    ABORTED_ANIME("aborted_anime",Messages._BallColor_InProgress(), ColorPalette.GREY),
    NOTBUILT("nobuilt",Messages._BallColor_NotBuilt(), ColorPalette.GREY),
    NOTBUILT_ANIME("nobuilt_anime",Messages._BallColor_InProgress(), ColorPalette.GREY),
    ;

    private final Localizable description;
    private final String iconName;
    private final String iconClassName;
    private final String image;
    private final Color baseColor;

    BallColor(String image, Localizable description, Color baseColor) {
        this.iconName = Icon.toNormalizedIconName(image);
        this.iconClassName = Icon.toNormalizedIconNameClass(image);
        this.baseColor = baseColor;
        // name() is not usable in the constructor, so I have to repeat the name twice
        // in the constants definition.
        this.image = image+ (image.endsWith("_anime")?".gif":".png");
        this.description = description;
    }

    /**
     * Get the status ball icon name.
     * @return The status ball icon name.
     */
    public String getIconName() {
        return iconName;
    }

    /**
     * Get the status ball icon class spec name.
     * @return The status ball icon class spec name.
     */
    public String getIconClassName() {
        return iconClassName;
    }

    /**
     * String like "red.png" that represents the file name of the image.
     */
    public String getImage() {
        return image;
    }

    public String getImageOf(String size) {
        return Stapler.getCurrentRequest().getContextPath()+ Jenkins.RESOURCE_PATH+"/images/"+size+'/'+image;
    }

    /**
     * Gets the human-readable description used as img/@alt.
     */
    public String getDescription() {
        return description.toString(LocaleProvider.getLocale());
    }

    /**
     * Gets the RGB color of this color. Animation effect is not reflected to this value.
     */
    public Color getBaseColor() {
        return baseColor;
    }

    /**
     * Returns the {@link #getBaseColor()} in the "#RRGGBB" format.
     */
    public String getHtmlBaseColor() {
        return String.format("#%06X", baseColor.getRGB() & 0xFFFFFF);
    }

    /**
     * Also used as a final name.
     */
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * Gets the animated version.
     */
    public BallColor anime() {
        if(isAnimated())   return this;
        else               return valueOf(name()+"_ANIME");
    }

    /**
     * Gets the unanimated version.
     */
    public BallColor noAnime() {
        if(isAnimated())   return valueOf(name().substring(0,name().length()-"_ANIME".length()));
        else               return this;
    }

    /**
     * True if the icon is animated.
     */
    public boolean isAnimated() {
        return name().endsWith("_ANIME");
    }
}
