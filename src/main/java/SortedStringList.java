import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class SortedStringList<T> extends AbstractList<String> {
    private String[] data;

    public SortedStringList(String[] data) {
        this.data = data;
    }
    
    public int find(String probe) {
        return Arrays.binarySearch(data,probe);
    }
    
    public String get(int idx) {
        return data[idx];
    }

    @Override
    public int size() {
        return data.length;
    }
}
