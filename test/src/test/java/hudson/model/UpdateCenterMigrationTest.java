package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;

class UpdateCenterMigrationTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new CustomUpdateCenterExtension();

    @Issue("JENKINS-73760")
    @LocalData
    @Test
    void updateCenterMigration() throws Throwable {
        session.then(j -> {
            UpdateSite site = j.jenkins.getUpdateCenter().getSites().stream()
                    .filter(s -> UpdateCenter.PREDEFINED_UPDATE_SITE_ID.equals(s.getId()))
                    .findFirst()
                    .orElseThrow();
            assertFalse(site.isLegacyDefault());
            assertEquals(j.jenkins.getUpdateCenter().getDefaultBaseUrl() + "update-center.json", site.getUrl());
        });
    }

    private static final class CustomUpdateCenterExtension extends JenkinsSessionExtension {

        private int port;
        private Description description;

        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            description = Description.createTestDescription(
                    context.getTestClass().map(Class::getName).orElse(null),
                    context.getTestMethod().map(Method::getName).orElse(null),
                    context.getTestMethod().map(Method::getAnnotations).orElse(null));
        }

        @Override
        public void then(Step s) throws Throwable {
            CustomJenkinsRule r = new CustomJenkinsRule(getHome(), port);
            r.apply(
                    new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            port = r.getPort();
                            s.run(r);
                        }
                    },
                    description
            ).evaluate();
        }

        private static final class CustomJenkinsRule extends JenkinsRule {

            CustomJenkinsRule(File home, int port) {
                with(() -> home);
                localPort = port;
            }

            int getPort() {
                return localPort;
            }

            @Override
            protected void configureUpdateCenter() {
                // Avoid reverse proxy
                DownloadService.neverUpdate = true;
                UpdateSite.neverUpdate = true;
            }
        }
    }
}
