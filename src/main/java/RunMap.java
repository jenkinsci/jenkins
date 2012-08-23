import java.util.AbstractMap;
import java.util.Comparator;
import java.util.SortedMap;

/**
 * @author Kohsuke Kawaguchi
 */
public class RunMap<R> extends AbstractMap<Integer,R> implements SortedMap<Integer,R> {

    public Comparator<? super Integer> comparator() {
        return COMPARATOR;
    }



    public static final Comparator<Comparable> COMPARATOR = new Comparator<Comparable>() {
        public int compare(Comparable o1, Comparable o2) {
            return -o1.compareTo(o2);
        }
    };
}
