package jenkins.model.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Action;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.junit.jupiter.api.Test;

class MenuActionsTest {

    @Test
    void hasIconWithIconFileName() {
        assertTrue(MenuActions.hasIcon(new FileIconAction("symbol-settings")));
    }

    @Test
    void hasIconWithIconClassName() {
        assertTrue(MenuActions.hasIcon(new ClassIconAction(null, "symbol-settings")));
    }

    @Test
    void hasNoIconWithoutEither() {
        assertFalse(MenuActions.hasIcon(new FileIconAction(null)));
        assertFalse(MenuActions.hasIcon(new ClassIconAction(null, null)));
    }

    @Test
    void getIconPrefersIconClassName() {
        assertEquals("symbol-terminal", MenuActions.getIcon(new ClassIconAction("symbol-settings", "symbol-terminal")));
    }

    @Test
    void getIconFallsBackToIconFileNameWithoutIconClassName() {
        assertEquals("symbol-settings", MenuActions.getIcon(new ClassIconAction("symbol-settings", null)));
    }

    @Test
    void getIconKeepsSymbol() {
        assertEquals("symbol-settings", MenuActions.getIcon(new FileIconAction("symbol-settings")));
    }

    @Test
    void getIconTranslatesLegacyIconToSymbol() {
        IconSet.icons.addIcon(new Icon("icon-help icon-md", "24x24/help.png", Icon.ICON_MEDIUM_STYLE));

        assertEquals("symbol-help-circle", MenuActions.getIcon(new FileIconAction("help.png")));
    }

    @Test
    void getIconKeepsUnknownIcon() {
        assertEquals("/plugin/example/images/unknown.png", MenuActions.getIcon(new FileIconAction("/plugin/example/images/unknown.png")));
    }

    @Test
    void getIconIsNullWithoutIcon() {
        assertNull(MenuActions.getIcon(new FileIconAction(null)));
    }

    private record FileIconAction(String iconFileName) implements Action {
        @Override
        public String getIconFileName() {
            return iconFileName;
        }

        @Override
        public String getDisplayName() {
            return "Example";
        }

        @Override
        public String getUrlName() {
            return "example";
        }
    }

    private record ClassIconAction(String iconFileName, String iconClassName) implements Action, IconSpec {
        @Override
        public String getIconFileName() {
            return iconFileName;
        }

        @Override
        public String getIconClassName() {
            return iconClassName;
        }

        @Override
        public String getDisplayName() {
            return "Example";
        }

        @Override
        public String getUrlName() {
            return "example";
        }
    }
}
