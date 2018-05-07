package jenkins.model;

import hudson.util.BootFailure;

public class InvalidBuildsDir extends BootFailure {
    private String message;

    public InvalidBuildsDir(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
