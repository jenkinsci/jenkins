package hudson.util;

import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.CategoryTick;
import org.jfree.chart.axis.CategoryLabelPosition;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.CategoryLabelEntity;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleAnchor;
import org.jfree.text.TextBlock;

import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.List;
import java.util.Iterator;

/**
 * {@link CategoryAxis} shifted to left to eliminate redundant space
 * between area and the Y-axis.
 */
public final class ShiftedCategoryAxis extends NoOverlapCategoryAxis {
    public ShiftedCategoryAxis(String label) {
        super(label);
    }

    protected double calculateCategorySize(int categoryCount, Rectangle2D area, RectangleEdge edge) {
        // we cut the left-half of the first item and the right-half of the last item,
        // so we have more space
        return super.calculateCategorySize(categoryCount-1, area, edge);
    }

    public double getCategoryEnd(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
        return super.getCategoryStart(category, categoryCount, area, edge)
           + calculateCategorySize(categoryCount, area, edge) / 2;
    }

    public double getCategoryMiddle(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
        return super.getCategoryStart(category, categoryCount, area, edge);
    }

    public double getCategoryStart(int category, int categoryCount, Rectangle2D area, RectangleEdge edge) {
        return super.getCategoryStart(category, categoryCount, area, edge)
            - calculateCategorySize(categoryCount, area, edge) / 2;
    }

}
