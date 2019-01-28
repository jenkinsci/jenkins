package hudson.model;

import jenkins.model.IdStrategy;

import java.io.File;
import java.io.IOException;

class TestUserIdMapper extends UserIdMapper {

    private File usersDirectory;
    private IdStrategy idStrategy;

    TestUserIdMapper(File usersDirectory, IdStrategy idStrategy) throws IOException {
        this.usersDirectory = usersDirectory;
        this.idStrategy = idStrategy;
    }

    @Override
    protected File getUsersDirectory() {
        return usersDirectory;
    }

    @Override
    protected IdStrategy getIdStrategy() {
        return idStrategy;
    }
}
