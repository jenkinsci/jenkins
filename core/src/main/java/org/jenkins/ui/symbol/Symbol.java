package org.jenkins.ui.symbol;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Functions;
import hudson.PluginWrapper;
import hudson.Util;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;

/**
 * Helper class to load symbols from Jenkins core or plugins.
 * @since 2.383
 */
public final class Symbol {
    private static final Logger LOGGER = Logger.getLogger(Symbol.class.getName());
    // keyed by plugin name / core, and then symbol name returning the SVG as a string
    private static final Map<String, Map<String, String>> SYMBOLS = new ConcurrentHashMap<>();
    static final String PLACEHOLDER_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"ionicon\" height=\"48\" viewBox=\"0 0 512 512\"><title>Close</title><path fill=\"none\" stroke=\"currentColor\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"32\" d=\"M368 368L144 144M368 144L144 368\"/></svg>";

    /**
     * A substring of the placeholder so that tests can match it.
     */
    static final String PLACEHOLDER_MATCHER = "M368 368L144 144M368 144L144 368";

    private Symbol() {}

    /**
     * Generates the svg markup for the given symbol name and attributes.
     * @param request the symbol request object.
     * @return The svg markup for the symbol.
     * @since 2.383
     */
    public static String get(@NonNull SymbolRequest request) {
        String name = request.getName();
        String title = request.getTitle();
        String tooltip = request.getTooltip();
        String htmlTooltip = request.getHtmlTooltip();
        String classes = request.getClasses();
        String pluginName = request.getPluginName();
        String id = request.getId();

        String identifier = (pluginName == null || pluginName.isBlank()) ? "core" : pluginName;

        String symbol = SYMBOLS
                .computeIfAbsent(identifier, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, key -> loadSymbol(identifier, key));
        if ((tooltip != null && !tooltip.isBlank()) && (htmlTooltip == null || htmlTooltip.isBlank())) {
            symbol = symbol.replaceAll("<svg", Matcher.quoteReplacement("<svg tooltip=\"" + Functions.htmlAttributeEscape(tooltip) + "\""));
        }
        if (htmlTooltip != null && !htmlTooltip.isBlank()) {
            symbol = symbol.replaceAll("<svg", Matcher.quoteReplacement("<svg data-html-tooltip=\"" + Functions.htmlAttributeEscape(htmlTooltip) + "\""));
        }
        if (id != null && !id.isBlank()) {
            symbol = symbol.replaceAll("<svg", Matcher.quoteReplacement("<svg id=\"" + Functions.htmlAttributeEscape(id) + "\""));
        }
        if (classes != null && !classes.isBlank()) {
            symbol = symbol.replaceAll("<svg", "<svg class=\"" + Functions.htmlAttributeEscape(classes) + "\"");
        }
        if (title != null && !title.isBlank()) {
            symbol = "<span class=\"jenkins-visually-hidden\">" + Util.xmlEscape(title) + "</span>" + symbol;
        }
        return symbol;
    }


    @SuppressFBWarnings(value = {"NP_LOAD_OF_KNOWN_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE"}, justification = "Spotbugs doesn't grok try-with-resources")
    private static String loadSymbol(String namespace, String name) {
        String markup = PLACEHOLDER_SVG;
        ClassLoader classLoader = getClassLoader(namespace);
        if (classLoader != null) {
            try (InputStream inputStream = classLoader.getResourceAsStream("images/symbols/" + name + ".svg")) {
                if (inputStream != null) {
                    markup = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                } else {
                    LOGGER.log(Level.FINE, "Missing symbol " + name + " in " + namespace);
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to load symbol " + name, e);
            }
        }
        return markup.replaceAll("(<title>).*?(</title>)", "$1$2")
                     .replaceAll("<svg", "<svg aria-hidden=\"true\"")
                     .replaceAll("(class=\").*?(\")", "")
                     .replaceAll("(tooltip=\").*?(\")", "")
                     .replaceAll("(data-html-tooltip=\").*?(\")", "")
                     .replace("stroke:#000", "stroke:currentColor");
    }

    @CheckForNull
    private static ClassLoader getClassLoader(@NonNull String pluginName) {
        if ("core".equals(pluginName)) {
            return Symbol.class.getClassLoader();
        } else {
            PluginWrapper plugin = Jenkins.get().getPluginManager().getPlugin(pluginName);
            if (plugin != null) {
                return plugin.classLoader;
            } else {
                return null;
            }
        }
    }
}
