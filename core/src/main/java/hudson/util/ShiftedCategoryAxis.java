package hudson.util;

import org.jfree.chart.axis.CategoryAxis;
import org.jfree.ui.RectangleEdge;

import java.awt.geom.Rectangle2D;

/**
 * {@link CategoryAxis} shifted to left to eliminate redundant space
 * between area and the Y-axis.
 */
public final class ShiftedCategoryAxis extends CategoryAxis {
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
