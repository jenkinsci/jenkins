package hudson.matrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * List of {@link Axis}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class AxisList extends ArrayList<Axis> {
    public AxisList() {
    }

    public AxisList(Collection<Axis> c) {
        super(c);
    }

    public Axis find(String name) {
        for (Axis a : this) {
            if(a.name.equals(name))
                return a;
        }
        return null;
    }

    public boolean add(Axis axis) {
        return axis!=null && super.add(axis);
    }

    /**
     * List up all the possible combinations of this list.
     */
    public Iterable<Combination> list() {
        final int[] base = new int[size()];

        int b = 1;
        for( int i=size()-1; i>=0; i-- ) {
            base[i] = b;
            b *= get(i).size();
        }

        final int total = b;    // number of total combinations

        return new Iterable<Combination>() {
            public Iterator<Combination> iterator() {
                return new Iterator<Combination>() {
                    private int counter = 0;

                    public boolean hasNext() {
                        return counter<total;
                    }

                    public Combination next() {
                        String[] data = new String[size()];
                        int x = counter++;
                        for( int i=0; i<data.length; i++) {
                            data[i] = get(i).value(x/base[i]);
                            x %= base[i];
                        }
                        assert x==0;
                        return new Combination(AxisList.this,data);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }
}
