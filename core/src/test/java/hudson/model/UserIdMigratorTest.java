/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import jenkins.model.IdStrategy;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

class UserIdMigratorTest {

    private static final String BASE_RESOURCE_PATH = "src/test/resources/hudson/model/";

    private TestInfo info;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        info = testInfo;
    }


    @Test
    void needsMigrationBasic() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        assertThat(migrator.needsMigration(), is(true));
    }

    @Test
    void needsMigrationFalse() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        assertThat(migrator.needsMigration(), is(false));
    }

    @Test
    void needsMigrationNoneExisting() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        assertThat(migrator.needsMigration(), is(false));
    }

    @Test
    void needsMigrationNoUserConfigFiles() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        assertThat(migrator.needsMigration(), is(false));
    }

    @Test
    void scanExistingUsersNone() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        UserIdMigrator migrator = new UserIdMigrator(usersDirectory, IdStrategy.CASE_INSENSITIVE);
        Map<String, File> userMappings = migrator.scanExistingUsers();
        assertThat(userMappings.keySet(), empty());
    }

    @Test
    void scanExistingUsersNoUsersDirectory() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        Map<String, File> userMappings = migrator.scanExistingUsers();
        assertThat(userMappings.keySet(), empty());
    }

    @Test
    void scanExistingUsersBasic() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        Map<String, File> userMappings = migrator.scanExistingUsers();
        assertThat(userMappings.keySet(), hasSize(2));
        assertThat(userMappings.keySet(), hasItems("admin", "jane"));
    }

    @Test
    void scanExistingUsersLegacy() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        Map<String, File> userMappings = migrator.scanExistingUsers();
        assertThat(userMappings.keySet(), hasSize(8));
        assertThat(userMappings.keySet(), hasItems("foo/bar", "foo/bar/baz", "/", "..", "bla$phem.us", "make$1000000", "big$money", "~com1"));
    }

    @Test
    void scanExistingUsersOldLegacy() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        Map<String, File> userMappings = migrator.scanExistingUsers();
        assertThat(userMappings.keySet(), hasSize(4));
        assertThat(userMappings.keySet(), hasItems("make\u1000000", "\u306f\u56fd\u5185\u3067\u6700\u5927", "\u1000yyy", "zzz\u1000"));
    }

    @Test
    void emptyUsernameConfigScanned() throws IOException {
        UserIdMigrator migrator = createUserIdMigrator();
        Map<String, File> userMappings = migrator.scanExistingUsers();
        assertThat(userMappings.keySet(), hasSize(2));
        assertThat(userMappings.keySet(), hasItems("admin", ""));
    }

    @Test
    void scanExistingUsersCaseSensitive() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        UserIdMigrator migrator = new UserIdMigrator(usersDirectory, new IdStrategy.CaseSensitive());
        Map<String, File> userMappings = migrator.scanExistingUsers();
        assertThat(userMappings.keySet(), hasSize(3));
        assertThat(userMappings.keySet(), hasItems("admin", "Fred", "Jane"));
    }

    @Test
    void migrateSimpleUser() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        IdStrategy idStrategy = IdStrategy.CASE_INSENSITIVE;
        UserIdMigrator migrator = new UserIdMigrator(usersDirectory, idStrategy);
        TestUserIdMapper mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        assertThat(migrator.needsMigration(), is(false));
        mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        assertThat(mapper.getConvertedUserIds().size(), is(1));
        assertThat(mapper.isMapped("fred"), is(true));
    }

    @Test
    void migrateMultipleUsers() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        IdStrategy idStrategy = IdStrategy.CASE_INSENSITIVE;
        UserIdMigrator migrator = new UserIdMigrator(usersDirectory, idStrategy);
        TestUserIdMapper mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        assertThat(migrator.needsMigration(), is(false));
        mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        assertThat(mapper.getConvertedUserIds().size(), is(3));
        assertThat(mapper.isMapped("fred"), is(true));
        assertThat(mapper.isMapped("foo/bar"), is(true));
        assertThat(mapper.isMapped("zzz\u1000"), is(true));
    }

    @Test
    void migrateUsersXml() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        IdStrategy idStrategy = IdStrategy.CASE_INSENSITIVE;
        UserIdMigrator migrator = new UserIdMigrator(usersDirectory, idStrategy);
        TestUserIdMapper mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        assertThat(migrator.needsMigration(), is(false));
        mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        assertThat(mapper.getConvertedUserIds().size(), is(1));
        assertThat(mapper.isMapped("users.xml"), is(true));
    }

    @Test
    void migrateEntireDirectory() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        IdStrategy idStrategy = IdStrategy.CASE_INSENSITIVE;
        UserIdMigrator migrator = new UserIdMigrator(usersDirectory, idStrategy);
        TestUserIdMapper mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        File fredDirectory = mapper.getDirectory("fred");
        File otherFile = new File(fredDirectory, "otherfile.txt");
        assertThat(otherFile.exists(), is(true));
        File originalFredDirectory = new File(usersDirectory, "fred");
        assertThat(originalFredDirectory.exists(), is(false));
    }

    static File createTestDirectory(Class clazz, TestInfo info) throws IOException {
        File tempDirectory = Files.createTempDirectory(Paths.get("target"), "userIdMigratorTest").toFile();
        tempDirectory.deleteOnExit();
        copyTestDataIfExists(clazz, info, tempDirectory);
        return new File(tempDirectory, "users");
    }

    static void copyTestDataIfExists(Class clazz, TestInfo info, File tempDirectory) throws IOException {
        File resourcesDirectory = new File(BASE_RESOURCE_PATH + clazz.getSimpleName(), info.getTestMethod().orElseThrow().getName());
        if (resourcesDirectory.exists()) {
            FileUtils.copyDirectory(resourcesDirectory, tempDirectory);
        }
    }

    private UserIdMigrator createUserIdMigrator() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        return new UserIdMigrator(usersDirectory, IdStrategy.CASE_INSENSITIVE);
    }

}
