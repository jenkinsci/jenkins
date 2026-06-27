package jenkins.util;

import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;

public final class QueryUtils {

    private QueryUtils() {}

    /**
     * Retrieves the first button element from the given HtmlPage with the specified caption text.
     *
     * @param page the HtmlPage to search for the button
     * @param caption the text content of the button to match
     * @return the HtmlButton with the specified caption, or null if no matching button is found
     */
    public static HtmlButton getButtonByCaption(HtmlPage page, String caption) {
        for (DomElement b : page.getElementsByTagName("button")) {
            if (b.getTextContent().trim().equals(caption)) {
                return (HtmlButton) b;
            }
        }
        return null;
    }

    /**
     * Waits until the given query selector is visible on the page, otherwise returns null
     *
     * @param page the page
     * @param query the query selector for the element
     */
    public static <T extends HtmlElement> T waitUntilElementIsPresent(HtmlPage page, String query) {
        for (int i = 0; i < 30; i++) {
            T element = page.querySelector(query);
            if (element != null) {
                return element;
            } else {
                System.out.println("Looking again for element: " + query);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        return null;
    }
}
