package hudson.util;

import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Builds {@link CategoryDataset}.
 *
 * <p>
 * This code works around an issue in {@link DefaultCategoryDataset} where
 * order of addition changes the way they are drawn. 
 */
public final class DataSetBuilder<Row extends Comparable,Column extends Comparable> {

    private List<Number> values = new ArrayList<Number>();
    private List<Row> rows = new ArrayList<Row>();
    private List<Column> columns = new ArrayList<Column>();

    public void add( Number value, Row rowKey, Column columnKey ) {
        values.add(value);
        rows.add(rowKey);
        columns.add(columnKey);
    }

    public CategoryDataset build() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();

        TreeSet<Row> rowSet = new TreeSet<Row>(rows);
        TreeSet<Column> colSet = new TreeSet<Column>(columns);

        Comparable[] _rows = rowSet.toArray(new Comparable[rowSet.size()]);
        Comparable[] _cols = colSet.toArray(new Comparable[colSet.size()]);

        // insert rows and columns in the right order
        for (Comparable r : _rows)
            ds.setValue(null, r, _cols[0]);
        for (Comparable c : _cols)
            ds.setValue(null, _rows[0], c);

        for( int i=0; i<values.size(); i++ )
            ds.addValue( values.get(i), rows.get(i), columns.get(i) );
        return ds;
    }
}
