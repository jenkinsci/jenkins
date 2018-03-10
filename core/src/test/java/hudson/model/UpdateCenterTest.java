package hudson.model;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class UpdateCenterTest {

    @Test
    public void toUpdateCenterCheckUrl_http_noQuery() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "http://updates.jenkins-ci.org/update-center.json").toExternalForm(),
                is("http://updates.jenkins-ci.org/update-center.json?uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_https_noQuery() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "https://updates.jenkins-ci.org/update-center.json").toExternalForm(),
                is("https://updates.jenkins-ci.org/update-center.json?uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_http_query() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "http://updates.jenkins-ci.org/update-center.json?version=2.7").toExternalForm(),
                is("http://updates.jenkins-ci.org/update-center.json?version=2.7&uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_https_query() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "https://updates.jenkins-ci.org/update-center.json?version=2.7").toExternalForm(),
                is("https://updates.jenkins-ci.org/update-center.json?version=2.7&uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_file() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "file://./foo.jar!update-center.json").toExternalForm(),
                is("file://./foo.jar!update-center.json"));
    }
}
