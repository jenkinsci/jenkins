package jenkins.model.menu.event;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.model.Action;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.export.ExportConfig;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.export.ModelBuilder;
import org.kohsuke.stapler.export.TreePruner;

class SplitButtonEventTest {

    @Test
    void primaryEventKeepsPropertiesInContextMenu() throws Exception {
        ContextMenu menu = new ContextMenu().add(new SplitButtonAction());

        // Same pruning as ContextMenu#generateResponse, which serves the bean at the default depth
        StringWriter json = new StringWriter();
        new ModelBuilder()
                .get(ContextMenu.class)
                .writeTo(menu, new TreePruner.ByDepth(1), Flavor.JSON.createDataWriter(menu, json, new ExportConfig()));

        assertThat(json.toString(), containsString("\"href\":\"replay/rebuild\""));
        assertThat(json.toString(), containsString("replay.js"));
    }

    private static final class SplitButtonAction implements Action {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Replay";
        }

        @Override
        public String getUrlName() {
            return "replay";
        }

        @Override
        public Event getEvent() {
            return SplitButtonEvent.of(
                    JavaScriptEvent.of(Map.of("type", "replay", "href", "replay/rebuild"), "replay.js"), List.of());
        }
    }
}
