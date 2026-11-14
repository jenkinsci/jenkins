package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import hudson.PluginManager;
import hudson.security.ACL;
import java.io.IOException;
import java.util.List;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class UpdateCenterBadgeTest {
    private MockedStatic<Jenkins> jenkinsMock;
    private UpdateCenter updateCenter;
    private final UpdateSite site = new UpdateSite("default", "https://example.invalid/update-center.json");

    @BeforeEach
    void setUp() {
        Jenkins jenkins = mock(Jenkins.class);
        jenkinsMock = mockStatic(Jenkins.class);
        jenkinsMock.when(Jenkins::get).thenReturn(jenkins);
        when(jenkins.getPluginManager()).thenReturn(mock(PluginManager.class));
        updateCenter = mock(UpdateCenter.class);
        when(updateCenter.isSiteDataReady()).thenReturn(true);
        when(updateCenter.getBadge()).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() {
        jenkinsMock.close();
    }

    @Test
    void excludesInstalledUpdatesAwaitingRestart() {
        UpdateSite.Plugin installed = plugin("installed", "2");
        UpdateSite.Plugin available = plugin("available", "2");
        when(updateCenter.getUpdates()).thenReturn(List.of(installed, available));
        UpdateCenter.InstallationJob job = installation(installed);
        job.status = job.new SuccessButRequiresRestart(Messages._UpdateCenter_DownloadButNotActivated());

        assertEquals("1", updateCenter.getBadge().getText());
    }

    @Test
    void hidesBadgeWhenAllUpdatesAreInstalled() {
        UpdateSite.Plugin plugin = plugin("installed", "2");
        when(updateCenter.getUpdates()).thenReturn(List.of(plugin));
        UpdateCenter.InstallationJob job = installation(plugin);
        job.status = job.new Success();

        assertNull(updateCenter.getBadge());
    }

    @Test
    void countsPendingAndFailedInstallations() {
        UpdateSite.Plugin pending = plugin("pending", "2");
        UpdateSite.Plugin failed = plugin("failed", "2");
        when(updateCenter.getUpdates()).thenReturn(List.of(pending, failed));
        installation(pending);
        UpdateCenter.InstallationJob job = installation(failed);
        job.status = job.new Failure(new IOException("Installation failed"));

        assertEquals("2", updateCenter.getBadge().getText());
    }

    @Test
    void countsNewerUpdateAfterSuccessfulInstallation() {
        UpdateSite.Plugin installed = plugin("plugin", "2");
        UpdateSite.Plugin newer = plugin("plugin", "3");
        when(updateCenter.getUpdates()).thenReturn(List.of(newer));
        UpdateCenter.InstallationJob job = installation(installed);
        job.status = job.new Success();
        when(updateCenter.getJob(newer)).thenReturn(job);

        assertEquals("1", updateCenter.getBadge().getText());
    }

    @Test
    void excludesInstalledUpdatesFromTooltipAndSeverity() {
        UpdateSite.Plugin installed = plugin("installed", "2");
        UpdateSite.Plugin available = plugin("available", "2");
        when(installed.fixesSecurityVulnerabilities()).thenReturn(true);
        when(installed.isCompatibleWithInstalledVersion()).thenReturn(false);
        when(updateCenter.getUpdates()).thenReturn(List.of(installed, available));
        UpdateCenter.InstallationJob job = installation(installed);
        job.status = job.new Success();

        Badge badge = updateCenter.getBadge();
        assertEquals(jenkins.management.Messages.PluginsLink_updateAvailable(), badge.getTooltip());
        assertEquals("warning", badge.getSeverity());
    }

    private UpdateCenter.InstallationJob installation(UpdateSite.Plugin plugin) {
        UpdateCenter.InstallationJob job = updateCenter.new InstallationJob(plugin, site, ACL.SYSTEM2, false);
        when(updateCenter.getJob(plugin)).thenReturn(job);
        return job;
    }

    private UpdateSite.Plugin plugin(String name, String version) {
        JSONObject data = new JSONObject();
        data.put("name", name);
        data.put("version", version);
        data.put("url", "https://example.invalid/" + name + ".hpi");
        data.put("dependencies", new JSONArray());
        UpdateSite.Plugin plugin = spy(site.new Plugin("default", data));
        when(plugin.fixesSecurityVulnerabilities()).thenReturn(false);
        when(plugin.isCompatibleWithInstalledVersion()).thenReturn(true);
        return plugin;
    }
}
