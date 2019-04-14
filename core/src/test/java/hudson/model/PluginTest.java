package hudson.model;

import hudson.PluginWrapper;
import net.sf.json.JSONObject;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;


/**
 * Unit tests for the {@link UpdateSite.Plugin} class.
 * @author Romén Rodríguez-Gil
 */
public class PluginTest {

    @Test
    public void pluginUpdateNotCompatible() throws IOException, ExecutionException, InterruptedException {
        URL url = PluginTest.class.getResource("plugins-update.json");
        UpdateSite us = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toExternalForm());

        String jsonp = DownloadService.loadJSON(url);
        JSONObject json = JSONObject.fromObject(jsonp);
        UpdateSite.Data usData = us.new Data(json);
        UpdateSite usSpy = spy(us);
        doReturn(usData).when(usSpy).getData();

        UpdateSite.Plugin credentialsPlugin = usSpy.getPlugin("credentials");
        UpdateSite.Plugin credentialsPluginSpy = spy(credentialsPlugin);
        PluginWrapper pluginWrapper = mock(PluginWrapper.class);
        when(pluginWrapper.getVersion()).thenReturn("1.0");
        doReturn(pluginWrapper).when(credentialsPluginSpy).getInstalled();

        assertThat("Plugin has warnings, it is NOT compatible", credentialsPluginSpy.isCompatible(), is(false));
    }

}
