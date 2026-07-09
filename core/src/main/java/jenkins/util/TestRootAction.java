package jenkins.util;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;

@Extension
public class TestRootAction implements UnprotectedRootAction {
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Test Redirect";
    }

    @Override
    public String getUrlName() {
        return "test-redirect";
    }

    public ClientHttpRedirect doUnsafe() {
        return new ClientHttpRedirect("javascript:alert('unsafe')");
    }

    public ClientHttpRedirect doSafe() {
        return new ClientHttpRedirect("https://www.jenkins.io");
    }
}
