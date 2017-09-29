/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
package jenkins.slaves;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AdministrativeMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.AgentProtocol;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;


/**
 * Monitors enabled protocols and warns if an {@link AgentProtocol} is deprecated. 
 * 
 * @author Oleg Nenashev
 * @since TODO
 * @see AgentProtocol
 */
@Extension
@Symbol("deprecatedAgentProtocol")
@Restricted(NoExternalUse.class)
public class DeprecatedAgentProtocolMonitor extends AdministrativeMonitor {
    
    private static final Logger LOGGER = Logger.getLogger(DeprecatedAgentProtocolMonitor.class.getName());

    public DeprecatedAgentProtocolMonitor() {
        super();
    }

    @Override
    public String getDisplayName() {
        return Messages.DeprecatedAgentProtocolMonitor_displayName();
    }

    @Override
    public boolean isActivated() {
        final Set<String> agentProtocols = Jenkins.getInstance().getAgentProtocols();
        for (String name : agentProtocols) {
            AgentProtocol pr = AgentProtocol.of(name);
            if (pr != null && pr.isDeprecated()) {
                return true;
            }
        }
        return false;
    }
    
    @Restricted(NoExternalUse.class)
    public String getDeprecatedProtocols() {
        String res = getDeprecatedProtocolsString();
        return res != null ? res : "N/A";
    }
    
    @CheckForNull
    public static String getDeprecatedProtocolsString() {
        final List<String> deprecatedProtocols = new ArrayList<>();
        final Set<String> agentProtocols = Jenkins.getInstance().getAgentProtocols();
        for (String name : agentProtocols) {
            AgentProtocol pr = AgentProtocol.of(name);
            if (pr != null && pr.isDeprecated()) {
                deprecatedProtocols.add(name);
            }
        }
        if (deprecatedProtocols.isEmpty()) {
            return null;
        }
        return StringUtils.join(deprecatedProtocols, ',');
    }
    
    @Initializer(after = InitMilestone.JOB_LOADED)
    @Restricted(NoExternalUse.class)
    public static void initializerCheck() {
        String protocols = getDeprecatedProtocolsString();
        if(protocols != null) {
            LOGGER.log(Level.WARNING, "This Jenkins instance uses deprecated Remoting protocols: {0}"
                    + "It may impact stability of the instance. " 
                    + "If newer protocol versions are supported by all system components "
                    + "(agents, CLI and other clients), "
                    + "it is highly recommended to disable the deprecated protocols.", protocols);
        } 
    }
}
