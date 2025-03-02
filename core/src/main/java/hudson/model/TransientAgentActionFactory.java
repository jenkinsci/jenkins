package hudson.model;

import hudson.slaves.SlaveComputer;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;
import jenkins.security.AgentSecretAction;

public abstract class TransientAgentActionFactory extends TransientActionFactory<SlaveComputer> {

    @Override
    public Class<SlaveComputer> type() {
        return SlaveComputer.class;
    }

    @Override
    public Collection<? extends Action> createFor(SlaveComputer target) {
        if (!(target.getLauncher() instanceof hudson.slaves.JNLPLauncher)) {
            throw new IllegalStateException("AgentSecretAction can only be created for inbound (JNLP) agents.");
        }
        return Collections.singleton(new AgentSecretAction(target));
    }
}
