/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
 *
 * @param <Row>
 *      Names that identify different graphs drawn in the same chart.
 * @param <Column>
 *      X-axis.
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
