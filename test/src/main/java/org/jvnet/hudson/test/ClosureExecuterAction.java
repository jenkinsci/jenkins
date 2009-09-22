package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.model.RootAction;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Server-side logic that implements {@link HudsonTestCase#executeOnServer(Callable)}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public final class ClosureExecuterAction implements RootAction {
    private final Map<UUID,Runnable> runnables = Collections.synchronizedMap(new HashMap<UUID, Runnable>());

    public void add(UUID uuid, Runnable r) {
        runnables.put(uuid,r);
    }

    public void doIndex(StaplerResponse rsp, @QueryParameter("uuid") String uuid) throws IOException {
        Runnable r = runnables.get(UUID.fromString(uuid));
        if (r!=null) {
            r.run();
            rsp.sendError(200);
        } else {
            rsp.sendError(404);
        }
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "closures";
    }
}
