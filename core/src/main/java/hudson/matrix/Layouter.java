package hudson.matrix;

import java.util.List;
import java.util.ArrayList;
import java.util.AbstractList;

/**
 * Used to assist thegeneration of config table.
 *
 * <p>
 * {@link Axis Axes} are split into four groups.
 * {@link #x Ones that are displayed as columns},
 * {@link #x Ones that are displayed as rows},
 * {@link #z Ones that are listed as bullet items inside table cell},
 * and those which only have one value, and therefore doesn't show up
 * in the table. 
 *
 * @author Kohsuke Kawaguchi
 */
public final class Layouter {
    public final List<Axis> x,y,z;

    public Layouter(List<Axis> x, List<Axis> y, List<Axis> z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Automatically split axes to x,y, and z.
     */
    public Layouter(AxisList axisList) {
        x = new ArrayList<Axis>();
        y = new ArrayList<Axis>();
        z = new ArrayList<Axis>();

        List<Axis> nonTrivialAxes = new ArrayList<Axis>();
        for (Axis a : axisList) {
            if(a.size()>1)
                nonTrivialAxes.add(a);
        }

        switch(nonTrivialAxes.size()) {
        case 0:
            return;
        case 1:
            z.add(nonTrivialAxes.get(0));
            return;
        case 2:
            x.add(nonTrivialAxes.get(0));
            y.add(nonTrivialAxes.get(1));
            return;
        }

        // for size > 3, use x and y, and try to pack y more
        for( int i=0; i<nonTrivialAxes.size(); i++ )
            (i%3==1?x:y).add(nonTrivialAxes.get(i));
    }

    /**
     * Computes the width of n-th X-axis.
     */
    public int width(int n) {
        return calc(x,n);
    }

    /**
     * Computes the width of n-th X-axis.
     */
    public int height(int n) {
        return calc(y,n);
    }

    private int calc(List<Axis> l, int n) {
        int w = 1;
        for( n++ ; n<l.size(); n++ )
            w *= l.get(n).size();
        return w;
    }

    /**
     * Gets list of {@link Row}s to be displayed.
     *
     * The {@link Row} object is reused, so every value
     * in collection returns the same object (but with different values.)
     */
    public List<Row> getRows() {
        return new AbstractList<Row>() {
            final int size = calc(y,-1);
            final Row row = new Row();
            public Row get(int index) {
                row.index = index;
                return row;
            }

            public int size() {
                return size;
            }
        };
    }

    public final class Row {
        private int index;

        public String drawYHeader(int n) {
            int base = calc(y,n);
            if(index/base==(index-1)/base && index!=0)  return null;    // no need to draw a new value

            Axis axis = y.get(n);
            return axis.value((index/base)%axis.values.size());
        }

    }
}
