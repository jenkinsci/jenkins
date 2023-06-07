package org.jenkins.ui.symbol;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.logging.Logger;

/**
 * <p>A Symbol specification, to be passed to {@link Symbol#get(SymbolRequest)}.
 *
 * <p>Create an instance using {@link Builder}.
 *
 * @since 2.383
 */
public final class SymbolRequest {
    private static final Logger LOGGER = Logger.getLogger(SymbolRequest.class.getName());

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
     * The tooltip to display when hovering over the symbol. Only displayed if {@link #htmlTooltip} is not set.
     */
    @CheckForNull
    private final String tooltip;
    /**
     * An HTML tooltip to display when hovering over the symbol. Overrides any value of the {@link #tooltip} field.
     */
    @CheckForNull
    private final String htmlTooltip;
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
    public String getHtmlTooltip() {
        return htmlTooltip;
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

    private SymbolRequest(@NonNull String name, @CheckForNull String title, @CheckForNull String tooltip, @CheckForNull String htmlTooltip, @CheckForNull String classes, @CheckForNull String pluginName,
                          @CheckForNull String id) {
        this.name = name;
        this.title = title;
        this.tooltip = tooltip;
        this.htmlTooltip = htmlTooltip;
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
        private String htmlTooltip;
        @CheckForNull
        private String classes;
        @CheckForNull
        private String pluginName;
        @CheckForNull
        private String id;
        @CheckForNull
        private String raw;

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
        public String getHtmlTooltip() {
            return htmlTooltip;
        }

        public Builder withHtmlTooltip(@CheckForNull String htmlTooltip) {
            this.htmlTooltip = htmlTooltip;
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

        @CheckForNull
        public String getRaw() {
            return raw;
        }

        public Builder withRaw(@CheckForNull String raw) {
            this.raw = raw;
            return this;
        }

        @NonNull
        public SymbolRequest build() {
            if (name == null && pluginName == null && raw != null) {
                parseRaw(raw);
                LOGGER.fine(() -> "\"" + raw + "\" parsed to name: " + name + " and pluginName: " + pluginName);
            }
            if (name == null) {
                throw new IllegalArgumentException("name cannot be null");
            }
            return new SymbolRequest(name, title, tooltip, htmlTooltip, classes, pluginName, id);
        }

        private void parseRaw(@NonNull String raw) {
            String[] s = raw.split(" ");
            if (s.length <= 2) {
                for (String element : s) {
                    if (element.startsWith("symbol-")) {
                        name = element.substring("symbol-".length());
                    }
                    if (element.startsWith("plugin-")) {
                        pluginName = element.substring("plugin-".length());
                    }
                    if (name != null && pluginName != null) {
                        break;
                    }
                }
            } else {
                throw new IllegalArgumentException("raw must be in the format \"symbol-<name> plugin-<pluginName>\"");
            }
        }
    }
}
