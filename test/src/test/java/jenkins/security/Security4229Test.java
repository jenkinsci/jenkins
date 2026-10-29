package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertFalse;

import hudson.model.User;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("SECURITY-4229")
@WithJenkins
class Security4229Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void breadcrumbOverflowTooltipHasNoXss() throws Exception {
        var reader = User.get("reader", true);
        reader.setFullName("Reader McReaderson, Platform Engineering <img src=x id=pwned "
                + "onerror=\"alert('PWNED-BY-READER')\">"
                + "<details open ontoggle=\"alert('PWNED-BY-READER')\"></details>");
        reader.save();

        var alertTriggered = new AtomicBoolean(false);
        try (var wc = j.createWebClient()) {
            wc.setAlertHandler((page, message) -> alertTriggered.set(true));
            var page = wc.goTo("user/" + reader.getId() + "/");

            // Simulate a window resize causing the breadcrumb bar to overflow in HtmlUnit.
            page.executeJavaScript(
                    "var bar = document.getElementById('breadcrumbBar');"
                            + "Object.defineProperty(bar, 'scrollWidth', { value: 1000, configurable: true });"
                            + "Object.defineProperty(bar, 'offsetWidth', { value: 500, configurable: true });"
                            + "Object.defineProperty(window, 'innerWidth', { value: 500, configurable: true });"
                            + "window.dispatchEvent(new Event('resize'));");
            // Wait for overflow menu generation triggered by resize listener.
            wc.waitForBackgroundJavaScript(2000L);

            // Simulate hovering to display the tooltip since Tippy renders tooltip content lazily.
            page.executeJavaScript(
                    "document.querySelector('.jenkins-breadcrumbs__list-item .jenkins-button')"
                            + "._tippy.popper.querySelector('.jenkins-dropdown__item')._tippy.show();");
            // Wait for lazy tooltip DOM rendering and script execution.
            wc.waitForBackgroundJavaScript(2000L);

            assertFalse(alertTriggered.get(), "No alert popup expected");
        }
    }
}
