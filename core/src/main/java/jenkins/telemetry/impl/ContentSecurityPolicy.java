package jenkins.telemetry.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import jenkins.security.csp.AdvancedConfigurationDescriptor;
import jenkins.security.csp.Contributor;
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
    @NonNull
    @Override
    public String getDisplayName() {
        return "Content Security Policy";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2025, 12, 15);
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

        data.put("components", buildComponentInformation());
        return data;
    }
}
