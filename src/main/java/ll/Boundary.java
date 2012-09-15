package ll;

/**
 * ceil/floor/lower/higher implementations
 * based on the result from a binary search.
 *
 * @author Kohsuke Kawaguchi
 */
public enum Boundary {
    LOWER(-1,-1),
    HIGHER(1,0),
    FLOOR(0,-1),
    CEIL(0,0);

    private final int offsetOfExactMatch, offsetOfInsertionPoint;

    private Boundary(int offsetOfExactMatch, int offsetOfInsertionPoint) {
        this.offsetOfExactMatch = offsetOfExactMatch;
        this.offsetOfInsertionPoint = offsetOfInsertionPoint;
    }

    public int apply(int binarySearchOutput) {
        int r = binarySearchOutput;
        if (r>=0)    return r+offsetOfExactMatch;

        int ip = -(r+1);
        return ip+offsetOfInsertionPoint;
    }
}
