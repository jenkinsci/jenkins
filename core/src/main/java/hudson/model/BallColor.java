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
    RED("Failed"),
    RED_ANIME("In progress"),
    YELLOW("Unstable"),
    YELLOW_ANIME("In progress"),
    BLUE("Success"),
    BLUE_ANIME("In progress"),
    GREY("Pending"),
    GREY_ANIME("In progress");

    private final String description;

    BallColor(String description) {
        this.description = description;
    }

    /**
     * Gets the human-readable description used as img/@alt.
     */
    public String getDescription() {
        return description;
    }

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

    /**
     * Gets the unanimated version.
     */
    public BallColor noAnime() {
        if(name().endsWith("_ANIME"))   return valueOf(name().substring(0,name().length()-6));
        else                            return this;
    }
}
