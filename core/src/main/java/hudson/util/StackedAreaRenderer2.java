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

import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.AreaRendererEndType;
import org.jfree.chart.renderer.category.CategoryItemRendererState;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.chart.urls.CategoryURLGenerator;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleEdge;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;

/**
 * Modified {@link StackedAreaRenderer}.
 *
 * <ol>
 * <li>Built-in support for {@link #generateToolTip(CategoryDataset, int, int) tooltip}
 *     and {@link #generateURL(CategoryDataset, int, int) hyperlinks} for clickable map.
 * <li>Clickable map hit test is modified so that the entire area is clickable,
 *     not just the small area around each data point.
 * <li>Rendering algorithm is modified so that
 *     {@link #getItemPaint(int, int) different color on the same data series}
 *     will look more natural.
 * </ol>
 *
 * @author Kohsuke Kawaguchi
*/
public class StackedAreaRenderer2 extends StackedAreaRenderer
    implements CategoryToolTipGenerator, CategoryURLGenerator {

    public StackedAreaRenderer2() {
        setEndType(AreaRendererEndType.TRUNCATE);
        setItemURLGenerator(this);
        setToolTipGenerator(this);
    }

    /**
     * Override this method to specify the hyperlink target of the given data point.
     */
    public String generateURL(CategoryDataset dataset, int row, int column) {
        return null;
    }

    /**
     * Override this method to specify the tool tip text of the given data point.
     */
    public String generateToolTip(CategoryDataset dataset, int row, int column) {
        return null;
    }

    /**
     * Override this method to specify the color to draw the given area.
     */
    @Override
    public Paint getItemPaint(int row, int column) {
        return super.getItemPaint(row, column);
    }

    // Mostly copied from the base class.
    @Override
    public void drawItem(Graphics2D g2,
                         CategoryItemRendererState state,
                         Rectangle2D dataArea,
                         CategoryPlot plot,
                         CategoryAxis domainAxis,
                         ValueAxis rangeAxis,
                         CategoryDataset dataset,
                         int row,
                         int column,
                         int pass) {

        // plot non-null values...
        Number dataValue = dataset.getValue(row, column);
        if (dataValue == null) {
            return;
        }

        double value = dataValue.doubleValue();

        // leave the y values (y1, y0) untranslated as it is going to be be
        // stacked up later by previous series values, after this it will be
        // translated.
        double xx1 = domainAxis.getCategoryMiddle(column, getColumnCount(),
                dataArea, plot.getDomainAxisEdge());

        double previousHeightx1 = getPreviousHeight(dataset, row, column);
        double y1 = value + previousHeightx1;
        RectangleEdge location = plot.getRangeAxisEdge();
        double yy1 = rangeAxis.valueToJava2D(y1, dataArea, location);

        g2.setPaint(getItemPaint(row, column));
        g2.setStroke(getItemStroke(row, column));

        // add an item entity, if this information is being collected
        EntityCollection entities = state.getEntityCollection();

        // in column zero, the only job to do is draw any visible item labels
        // and this is done in the second pass...
        if (column == 0) {
            if (pass == 1) {
                // draw item labels, if visible
                if (isItemLabelVisible(row, column)) {
                    drawItemLabel(g2, plot.getOrientation(), dataset, row, column,
                            xx1, yy1, (y1 < 0.0));
                }
            }
        } else {
            Number previousValue = dataset.getValue(row, column - 1);
            if (previousValue != null) {

                double xx0 = domainAxis.getCategoryMiddle(column - 1,
                        getColumnCount(), dataArea, plot.getDomainAxisEdge());
                double y0 = previousValue.doubleValue();


                // Get the previous height, but this will be different for both
                // y0 and y1 as the previous series values could differ.
                double previousHeightx0 = getPreviousHeight(dataset, row,
                        column - 1);

                // Now stack the current y values on top of the previous values.
                y0 += previousHeightx0;

                // Now translate the previous heights
                double previousHeightxx0 = rangeAxis.valueToJava2D(
                        previousHeightx0, dataArea, location);
                double previousHeightxx1 = rangeAxis.valueToJava2D(
                        previousHeightx1, dataArea, location);

                // Now translate the current y values.
                double yy0 = rangeAxis.valueToJava2D(y0, dataArea, location);

                if (pass == 0) {
                    // left half
                    Polygon p = new Polygon();
                    p.addPoint((int) xx0, (int) yy0);
                    p.addPoint((int) (xx0+xx1)/2, (int) (yy0+yy1)/2);
                    p.addPoint((int) (xx0+xx1)/2, (int) (previousHeightxx0+previousHeightxx1)/2);
                    p.addPoint((int) xx0, (int) previousHeightxx0);

                    g2.setPaint(getItemPaint(row, column-1));
                    g2.setStroke(getItemStroke(row, column-1));
                    g2.fill(p);

                    if (entities != null)
                        addItemEntity(entities, dataset, row, column-1, p);

                    // right half
                    p = new Polygon();
                    p.addPoint((int) xx1, (int) yy1);
                    p.addPoint((int) (xx0+xx1)/2, (int) (yy0+yy1)/2);
                    p.addPoint((int) (xx0+xx1)/2, (int) (previousHeightxx0+previousHeightxx1)/2);
                    p.addPoint((int) xx1, (int) previousHeightxx1);

                    g2.setPaint(getItemPaint(row, column));
                    g2.setStroke(getItemStroke(row, column));
                    g2.fill(p);

                    if (entities != null)
                        addItemEntity(entities, dataset, row, column, p);
                } else {
                    if (isItemLabelVisible(row, column)) {
                        drawItemLabel(g2, plot.getOrientation(), dataset, row,
                                column, xx1, yy1, (y1 < 0.0));
                    }
                }
            }
        }
    }
}
