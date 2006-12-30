package hudson.util;

import org.jfree.chart.JFreeChart;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.io.IOException;

import hudson.model.Build;

/**
 * See issue 93. Detect an error in X11 and handle it gracefully.
 *
 * @author Kohsuke Kawaguchi
 */
public class ChartUtil {
    /**
     * Can be used as a graph label. Only displays numbers.
     */
    public static final class NumberOnlyBuildLabel implements Comparable<NumberOnlyBuildLabel> {
        private final Build build;

        public NumberOnlyBuildLabel(Build build) {
            this.build = build;
        }

        public int compareTo(NumberOnlyBuildLabel that) {
            return this.build.number-that.build.number;
        }

        public boolean equals(Object o) {
            NumberOnlyBuildLabel that = (NumberOnlyBuildLabel) o;
            return build==that.build;
        }

        public int hashCode() {
            return build.hashCode();
        }

        public String toString() {
            return build.getDisplayName();
        }
    }

    /**
     * See issue 93. Detect an error in X11 and handle it gracefully.
     */
    public static boolean awtProblem = false;

    public static void generateGraph(StaplerRequest req, StaplerResponse rsp, JFreeChart chart, int defaultW, int defaultH) throws IOException {
        try {
            String w = req.getParameter("width");
            if(w==null)     w=String.valueOf(defaultW);
            String h = req.getParameter("height");
            if(h==null)     h=String.valueOf(defaultH);
            BufferedImage image = chart.createBufferedImage(Integer.parseInt(w),Integer.parseInt(h));
            rsp.setContentType("image/png");
            ServletOutputStream os = rsp.getOutputStream();
            ImageIO.write(image, "PNG", os);
            os.close();
        } catch(HeadlessException e) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
        }
    }

    static {
        try {
            new Font("SansSerif",Font.BOLD,18).toString();
        } catch (Throwable t) {
            awtProblem = true;
        }
    }
}
