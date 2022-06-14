package org.jenkins.ui.symbol;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.PluginWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.IconSet;

/**
 * Helper class to load symbols from Jenkins core or plugins.
 * @since TODO
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
     * @since TODO
     */
    public static String get(@NonNull SymbolRequest request) {
        String name = request.getName();
        String title = request.getTitle();
        String tooltip = request.getTooltip();
        String classes = request.getClasses();
        String pluginName = request.getPluginName();
        String id = request.getId();
        String translatedName = IconSet.cleanName(name);

        String identifier = StringUtils.defaultIfBlank(pluginName, "core");
        Map<String, String> symbolsForLookup = SYMBOLS.computeIfAbsent(identifier, key -> new ConcurrentHashMap<>());

        if (symbolsForLookup.containsKey(translatedName)) {
            String symbol = symbolsForLookup.get(translatedName);
            return replaceAttributes(symbol, title, tooltip, classes, id);
        }

        String symbol = loadSymbol(identifier, translatedName);
        symbol = symbol.replaceAll("(<title>)[^&]*(</title>)", "$1$2");
        symbol = symbol.replaceAll("<svg", "<svg aria-hidden=\"true\"");
        symbol = symbol.replace("stroke:#000", "stroke:currentColor");
        symbol = replaceAttributes(symbol, title, tooltip, classes, id);

        symbolsForLookup.put(translatedName, symbol);

        return symbol;
    }

    @SuppressFBWarnings(value = {"NP_LOAD_OF_KNOWN_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE"}, justification = "Spotbugs doesn't grok try-with-resources")
    private static String loadSymbol(String namespace, String name) {
        ClassLoader classLoader = getClassLoader(namespace);
        if (classLoader != null) {
            try (InputStream inputStream = classLoader.getResourceAsStream("images/symbols/" + name + ".svg")) {
                if (inputStream != null) {
                    return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                } else {
                    LOGGER.log(Level.FINE, "Missing symbol " + name + " in " + namespace);
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to load symbol " + name, e);
            }
        }
        // Fallback to the placeholder symbol
        return PLACEHOLDER_SVG;
    }

    private static String replaceAttributes(String symbol, String title, String tooltip, String classes, String id) {
        String result = symbol;
        result = result.replaceAll("(class=\")[^&]*?(\")", "$1$2");
        result = result.replaceAll("(tooltip=\")[^&]*?(\")", "");
        result = result.replaceAll("(id=\")[^&]*?(\")", "");
        if (StringUtils.isNotBlank(tooltip)) {
            result = result.replaceAll("<svg", "<svg tooltip=\"" + tooltip + "\"");
        }
        if (StringUtils.isNotBlank(id)) {
            result = result.replaceAll("<svg", "<svg id=\"" + id + "\"");
        }
        if (StringUtils.isNotBlank(classes)) {
            result = result.replaceAll("<svg", "<svg class=\"" + classes + "\"");
        }
        if (StringUtils.isNotBlank(title)) {
            result = "<span class=\"jenkins-visually-hidden\">" + title + "</span>" + result;
        }
        return result;
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
