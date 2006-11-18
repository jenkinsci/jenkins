package hudson.model;

/**
 * Ball color used for the build status indication.
 *
 * <p>
 * There are four basic colors, plus their animated "bouncy" versions.
 * {@link #ordinal()} is the sort order. 
 *
 * @author Kohsuke Kawaguchi
 */
public enum BallColor {
    RED, RED_ANIME, YELLOW, YELLOW_ANIME, BLUE, BLUE_ANIME, GREY, GREY_ANIME;

    /**
     * Also used as a final name.
     */
    public String toString() {
        return name().toLowerCase();
    }

    /**
     * Gets the animated version.
     */
    public BallColor anime() {
        if(name().endsWith("_ANIME"))   return this;
        else                            return valueOf(name()+"_ANIME");
    }
}
