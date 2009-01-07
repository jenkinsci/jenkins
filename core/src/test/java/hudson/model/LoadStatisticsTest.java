package hudson.model;

import hudson.model.MultiStageTimeSeries.TimeScale;
import junit.framework.TestCase;
import org.jfree.chart.JFreeChart;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class LoadStatisticsTest extends TestCase {
    public void testGraph() throws IOException {
        LoadStatistics ls = new LoadStatistics(0, 0) {
            public int computeIdleExecutors() {
                throw new UnsupportedOperationException();
            }

            public int computeTotalExecutors() {
                throw new UnsupportedOperationException();
            }

            public int computeQueueLength() {
                throw new UnsupportedOperationException();
            }
        };

        for(int i=0;i<50;i++) {
            ls.totalExecutors.update(4);
            ls.busyExecutors.update(3);
            ls.queueLength.update(3);
        }

        for(int i=0;i<50;i++) {
            ls.totalExecutors.update(0);
            ls.busyExecutors.update(0);
            ls.queueLength.update(1);
        }

        JFreeChart chart = ls.createChart(ls.createDataset(TimeScale.SEC10));
        BufferedImage image = chart.createBufferedImage(400,200);
        ImageIO.write(image, "PNG", new FileOutputStream("chart.png"));
    }
}
