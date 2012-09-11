import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class SortedStringList extends AbstractList<String> {
    private List<String> data;

    public SortedStringList(List<String> data) {
        this.data = data;
    }
    
    public int find(String probe) {
        return Collections.binarySearch(data, probe);
    }
    
    public String get(int idx) {
        return data.get(idx);
    }

    @Override
    public int size() {
        return data.size();
    }

    /**
     * Finds the index of the entry lower than v.
     */
    public int lower(String v) {
        int r = find(v);
        if (r>0)    return r-1;

        int ip = -(r+1);
        return r-1;
    }

    public int higher(String v) {
        int r = find(v);
        if (r>0)    return r+1;

        int ip = -(r+1);
        return r;
    }

    public int floor(String v) {
        throw new UnsupportedOperationException();
    }

    public int ceil(String v) {
        throw new UnsupportedOperationException();
    }

    public List<String> safeSubList(int from, int to) {
        return subList(inside(from),inside(to));
    }

}
