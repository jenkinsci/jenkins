package jenkins.views;

/**
 * {@link Header} that provides its own resources as full replacement. It does not
 * depends on any core resource (images, CSS, JS, etc.)
 *
 * Given this kind of header is totally independent, it will be compatible by default.
 *
 * @see Header
 */
public abstract class FullHeader extends Header {

    public boolean isCompatible() {
        return true;
    }
}
