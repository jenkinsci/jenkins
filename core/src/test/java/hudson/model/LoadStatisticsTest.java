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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.model.queue.SubTask;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

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

        File tempFile = File.createTempFile("chart-", "png");
        try (OutputStream os = Files.newOutputStream(tempFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE)) {
            ImageIO.write(image, "PNG", os);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void svg() throws Exception {
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

        String svg = ls.createTrendChart(TimeScale.SEC10).createSvg();

        assertTrue(svg.startsWith("<svg"), svg);
        // one line per plotted series
        assertTrue(svg.contains("<polyline"), svg);
        // theme-aware: colors reference CSS custom properties instead of baked-in values
        assertTrue(svg.contains("var(--"), svg);

        // the generated markup must be well-formed XML so it can be inlined into the DOM
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        assertEquals("svg", doc.getDocumentElement().getLocalName());
    }

    @Test
    void svgWithLargeValues() throws Exception {
        // Mirrors the memory usage chart, whose values are large (KB). The y-axis labels must be
        // compacted (k/M/G) rather than rendered as long raw integers that would clip.
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
            ls.onlineExecutors.update(2_000_000);
            ls.busyExecutors.update(1_500_000);
            ls.availableExecutors.update(500_000);
            ls.queueLength.update(250_000);
        }

        String svg = ls.createTrendChart(TimeScale.SEC10).createSvg();

        // compact labels, not the raw integer
        assertTrue(svg.contains("M</text>"), svg);
        assertFalse(svg.contains("2000000</text>"), svg);

        // still well-formed XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        assertEquals("svg", doc.getDocumentElement().getLocalName());
    }

    @Test
    void legend() throws Exception {
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

        MultiStageTimeSeries.TrendChart chart = ls.createTrendChart(TimeScale.SEC10);
        String legend = chart.createLegendHtml();

        // the legend is an HTML list with one item per plotted series (online/busy/available/queue)
        assertTrue(legend.contains("jenkins-svg-graph__legend"), legend);
        assertEquals(4, countOccurrences(legend, "<li"), legend);
        // each series label is present as real HTML text, and swatches are decorative
        assertTrue(legend.contains(Messages.LoadStatistics_Legends_BusyExecutors()), legend);
        assertTrue(legend.contains("aria-hidden=\"true\""), legend);

        // the combined fragment keeps the legend first and a single, still well-formed <svg>
        String combined = chart.createSvgWithLegend();
        assertTrue(combined.indexOf("<ul") < combined.indexOf("<svg"), combined);
        String svg = combined.substring(combined.indexOf("<svg"));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        assertEquals("svg", doc.getDocumentElement().getLocalName());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int idx = haystack.indexOf(needle); idx >= 0; idx = haystack.indexOf(needle, idx + needle.length())) {
            count++;
        }
        return count;
    }
}
