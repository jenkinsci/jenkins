package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class UpdateCenterMigrationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule() {
        @Override
        protected void configureUpdateCenter() {
            // Avoid reverse proxy
            DownloadService.neverUpdate = true;
            UpdateSite.neverUpdate = true;
        }
    };

    @Issue("JENKINS-73760")
    @LocalData
    @Test
    public void updateCenterMigration() {
        UpdateSite site = j.jenkins.getUpdateCenter().getSites().stream()
                .filter(s -> UpdateCenter.PREDEFINED_UPDATE_SITE_ID.equals(s.getId()))
                .findFirst()
                .orElseThrow();
        assertFalse(site.isLegacyDefault());
        assertEquals(j.jenkins.getUpdateCenter().getDefaultBaseUrl() + "update-center.json", site.getUrl());
    }
}
