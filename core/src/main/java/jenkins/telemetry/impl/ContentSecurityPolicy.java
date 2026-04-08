/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.telemetry.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.security.csp.AdvancedConfigurationDescriptor;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.CspHeader;
import jenkins.security.csp.CspHeaderDecider;
import jenkins.security.csp.impl.CspConfiguration;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Collect information about Content Security Policy configuration.
 *
 */
@Restricted(NoExternalUse.class)
@Extension
public class ContentSecurityPolicy extends Telemetry {

    private static final Logger LOGGER = Logger.getLogger(ContentSecurityPolicy.class.getName());

    @NonNull
    @Override
    public String getDisplayName() {
        return "Content Security Policy";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2025, 12, 1);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2026, 6, 1);
    }

    @Override
    public JSONObject createContent() {
        final JSONObject data = new JSONObject();
        data.put("enforce", ExtensionList.lookupSingleton(CspConfiguration.class).isEnforce());
        final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
        data.put("decider", decider.map(Object::getClass).map(Class::getName).orElse(null));
        data.put("header", decider.map(CspHeaderDecider::decide).filter(Optional::isPresent).map(Optional::get).map(CspHeader::getHeaderName).orElse(null));

        Set<String> contributors = new TreeSet<>();
        ExtensionList.lookup(Contributor.class).stream().map(Contributor::getClass).map(Class::getName).forEach(contributors::add);
        data.put("contributors", contributors);

        Set<String> configurations = new TreeSet<>();
        ExtensionList.lookup(AdvancedConfigurationDescriptor.class).stream().map(AdvancedConfigurationDescriptor::getClass).map(Class::getName).forEach(configurations::add);
        data.put("configurations", configurations);

        try {
            Map<String, Map<String, Integer>> directivesSize = new CspBuilder().withDefaultContributions().getMergedDirectives().stream()
                    .map(d -> Map.entry(d.name(), Map.of("entries", d.values().size(), "chars", String.join(" ", d.values()).length())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            data.put("directivesSize", directivesSize);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "Error during directive processing", ex);
        }

        data.put("components", buildComponentInformation());
        return data;
    }
}
