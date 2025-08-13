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

package hudson.model;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.model.queue.SubTask;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import javax.imageio.ImageIO;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class LoadStatisticsTest {

    @Test
    void graph() throws IOException {
        assumeFalse(Functions.isWindows(), "TODO: Implement this test on Windows");
        LoadStatistics ls = new LoadStatistics(0, 0) {
            @Override
            protected Iterable<Node> getNodes() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected boolean matches(Queue.Item item, SubTask subTask) {
                throw new UnsupportedOperationException();
            }
        };

        for (int i = 0; i < 50; i++) {
            ls.onlineExecutors.update(4);
            ls.busyExecutors.update(3);
            ls.availableExecutors.update(1);
            ls.queueLength.update(3);
        }

        for (int i = 0; i < 50; i++) {
            ls.onlineExecutors.update(0);
            ls.busyExecutors.update(0);
            ls.availableExecutors.update(0);
            ls.queueLength.update(1);
        }

        JFreeChart chart = ls.createTrendChart(TimeScale.SEC10).createChart();
        BufferedImage image = chart.createBufferedImage(400, 200);

        File tempFile = Files.createTempFile("chart-", "png").toFile();
        try (OutputStream os = Files.newOutputStream(tempFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE)) {
            ImageIO.write(image, "PNG", os);
        } finally {
            tempFile.delete();
        }
    }
}
