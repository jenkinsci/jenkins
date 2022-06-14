package org.jenkins.ui.symbol;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

public final class SymbolRequest {
    /**
     * The name of the symbol.
     */
    @NonNull
    private final String name;

    /**
     * The symbol title.
     */
    @CheckForNull
    private final String title;
    /**
     * The tooltip to display when hovering over the symbol.
     */
    @CheckForNull
    private final String tooltip;
    /**
     * Additional CSS classes to apply to the symbol.
     */
    @CheckForNull
    private final String classes;
    /**
     * The name of the plugin to load the symbol from. If null, the symbol will be resolved from core.
     */
    @CheckForNull
    private final String pluginName;

    /**
     * The html id of the symbol.
     */
    @CheckForNull
    private final String id;

    @NonNull
    public String getName() {
        return name;
    }

    @CheckForNull
    public String getTitle() {
        return title;
    }

    @CheckForNull
    public String getTooltip() {
        return tooltip;
    }

    @CheckForNull
    public String getClasses() {
        return classes;
    }

    @CheckForNull
    public String getPluginName() {
        return pluginName;
    }

    @CheckForNull
    public String getId() {
        return id;
    }

    private SymbolRequest(@NonNull String name, @CheckForNull String title, @CheckForNull String tooltip, @CheckForNull String classes, @CheckForNull String pluginName,
                          @CheckForNull String id) {
        this.name = name;
        this.title = title;
        this.tooltip = tooltip;
        this.classes = classes;
        this.pluginName = pluginName;
        this.id = id;
    }

    public static class Builder {
        @CheckForNull
        private String name;
        @CheckForNull
        private String title;
        @CheckForNull
        private String tooltip;
        @CheckForNull
        private String classes;
        @CheckForNull
        private String pluginName;
        @CheckForNull
        private String id;

        @CheckForNull
        public String getName() {
            return name;
        }

        public Builder withName(@NonNull String name) {
            this.name = name;
            return this;
        }

        @CheckForNull
        public String getTitle() {
            return title;
        }

        public Builder withTitle(@CheckForNull String title) {
            this.title = title;
            return this;
        }

        @CheckForNull
        public String getTooltip() {
            return tooltip;
        }

        public Builder withTooltip(@CheckForNull String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        @CheckForNull
        public String getClasses() {
            return classes;
        }

        public Builder withClasses(@CheckForNull String classes) {
            this.classes = classes;
            return this;
        }

        @CheckForNull
        public String getPluginName() {
            return pluginName;
        }

        public Builder withPluginName(@CheckForNull String pluginName) {
            this.pluginName = pluginName;
            return this;
        }

        @CheckForNull
        public String getId() {
            return id;
        }

        public Builder withId(@CheckForNull String id) {
            this.id = id;
            return this;
        }

        @NonNull
        public SymbolRequest build() {
            if (name == null) {
                throw new IllegalArgumentException("name cannot be null");
            }
            return new SymbolRequest(name, title, tooltip, classes, pluginName, id);
        }
    }
}
