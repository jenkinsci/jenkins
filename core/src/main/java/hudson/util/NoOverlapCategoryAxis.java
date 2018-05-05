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
import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.CategoryTick;
import org.jfree.chart.axis.CategoryLabelPosition;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.CategoryLabelEntity;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleAnchor;
import org.jfree.text.TextBlock;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;
import java.util.*;

/**
 * This class implements X-axis label skipping algorithm to
 * avoid drawing overlapping labels.
 * 
 * @author Kohsuke Kawaguchi
 */
public class NoOverlapCategoryAxis extends CategoryAxis {
    public NoOverlapCategoryAxis(String label) {
        super(label);
    }

    @Override
    protected AxisState drawCategoryLabels(Graphics2D g2,
                                           Rectangle2D plotArea,
                                           Rectangle2D dataArea,
                                           RectangleEdge edge,
                                           AxisState state,
                                           PlotRenderingInfo plotState) {

        if (state == null) {
            throw new IllegalArgumentException("Null 'state' argument.");
        }

        if (isTickLabelsVisible()) {
            java.util.List ticks = refreshTicks(g2, state, plotArea, edge);
            state.setTicks(ticks);

            // remember the last drawn label so that we can avoid drawing overlapping labels.
            Rectangle2D r = null;

            int categoryIndex = 0;
            Iterator iterator = ticks.iterator();
            while (iterator.hasNext()) {

                CategoryTick tick = (CategoryTick) iterator.next();
                g2.setFont(getTickLabelFont(tick.getCategory()));
                g2.setPaint(getTickLabelPaint(tick.getCategory()));

                CategoryLabelPosition position
                    = this.getCategoryLabelPositions().getLabelPosition(edge);
                double x0 = 0.0;
                double x1 = 0.0;
                double y0 = 0.0;
                double y1 = 0.0;
                if (edge == RectangleEdge.TOP) {
                    x0 = getCategoryStart(categoryIndex, ticks.size(),
                            dataArea, edge);
                    x1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea,
                            edge);
                    y1 = state.getCursor() - this.getCategoryLabelPositionOffset();
                    y0 = y1 - state.getMax();
                }
                else if (edge == RectangleEdge.BOTTOM) {
                    x0 = getCategoryStart(categoryIndex, ticks.size(),
                            dataArea, edge);
                    x1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea,
                            edge);
                    y0 = state.getCursor() + this.getCategoryLabelPositionOffset();
                    y1 = y0 + state.getMax();
                }
                else if (edge == RectangleEdge.LEFT) {
                    y0 = getCategoryStart(categoryIndex, ticks.size(),
                            dataArea, edge);
                    y1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea,
                            edge);
                    x1 = state.getCursor() - this.getCategoryLabelPositionOffset();
                    x0 = x1 - state.getMax();
                }
                else if (edge == RectangleEdge.RIGHT) {
                    y0 = getCategoryStart(categoryIndex, ticks.size(),
                            dataArea, edge);
                    y1 = getCategoryEnd(categoryIndex, ticks.size(), dataArea,
                            edge);
                    x0 = state.getCursor() + this.getCategoryLabelPositionOffset();
                    x1 = x0 - state.getMax();
                }
                Rectangle2D area = new Rectangle2D.Double(x0, y0, (x1 - x0),
                        (y1 - y0));
                if(r==null || !r.intersects(area)) {
                    Point2D anchorPoint = RectangleAnchor.coordinates(area,
                            position.getCategoryAnchor());
                    TextBlock block = tick.getLabel();
                    block.draw(g2, (float) anchorPoint.getX(),
                            (float) anchorPoint.getY(), position.getLabelAnchor(),
                            (float) anchorPoint.getX(), (float) anchorPoint.getY(),
                            position.getAngle());
                    Shape bounds = block.calculateBounds(g2,
                            (float) anchorPoint.getX(), (float) anchorPoint.getY(),
                            position.getLabelAnchor(), (float) anchorPoint.getX(),
                            (float) anchorPoint.getY(), position.getAngle());
                    if (plotState != null && plotState.getOwner() != null) {
                        EntityCollection entities
                            = plotState.getOwner().getEntityCollection();
                        if (entities != null) {
                            String tooltip = getCategoryLabelToolTip(
                                    tick.getCategory());
                            entities.add(new CategoryLabelEntity(tick.getCategory(),
                                    bounds, tooltip, null));
                        }
                    }
                    r = bounds.getBounds2D();
                    // add margins in all directions
                    r.add(r.getMaxX() + r.getWidth()/2, r.getCenterY());
                    r.add(r.getMinX() - r.getWidth()/2, r.getCenterY());
                    r.add(r.getCenterX(), r.getMinY() - r.getHeight()/2);
                    r.add(r.getCenterX(), r.getMaxX() + r.getHeight()/2);
                }

                categoryIndex++;
            }

            if (edge.equals(RectangleEdge.TOP)) {
                double h = state.getMax();
                state.cursorUp(h);
            }
            else if (edge.equals(RectangleEdge.BOTTOM)) {
                double h = state.getMax();
                state.cursorDown(h);
            }
            else if (edge == RectangleEdge.LEFT) {
                double w = state.getMax();
                state.cursorLeft(w);
            }
            else if (edge == RectangleEdge.RIGHT) {
                double w = state.getMax();
                state.cursorRight(w);
            }
        }
        return state;
    }
}
