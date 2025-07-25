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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import jenkins.model.IdStrategy;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

class UserIdMapperTest {

    private TestInfo info;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        info = testInfo;
    }

    @Test
    void testNonexistentFileLoads() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
    }

    @Test
    void testEmptyGet() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        assertThat(mapper.getDirectory("anything"), nullValue());
    }

    @Test
    void testSimplePutGet() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        assertThat(directory, is(mapper.getDirectory(user1)));
    }

    @Test
    void testMultiple() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory1 = mapper.putIfAbsent(user1);
        String user2 = "user2";
        File directory2 = mapper.putIfAbsent(user2);
        String user3 = "user3";
        File directory3 = mapper.putIfAbsent(user3);
        assertThat(directory1, is(mapper.getDirectory(user1)));
        assertThat(directory2, is(mapper.getDirectory(user2)));
        assertThat(directory3, is(mapper.getDirectory(user3)));
    }

    @Test
    void testMultipleSaved() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        IdStrategy idStrategy = IdStrategy.CASE_INSENSITIVE;
        UserIdMapper mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        String user1 = "user1";
        File directory1 = createUser(mapper, user1);
        String user2 = "user2";
        File directory2 = createUser(mapper, user2);
        String user3 = "user3";
        File directory3 = createUser(mapper, user3);
        mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        mapper.load();
        assertThat(directory1, is(mapper.getDirectory(user1)));
        assertThat(directory2, is(mapper.getDirectory(user2)));
        assertThat(directory3, is(mapper.getDirectory(user3)));
    }

    private static File createUser(UserIdMapper mapper, String id) throws IOException {
        File directory = mapper.putIfAbsent(id);
        FileUtils.write(new File(directory, User.CONFIG_XML), "<user><id>" + id + "</id></user>", StandardCharsets.UTF_8);
        return directory;
    }

    @Test
    void testRepeatPut() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory1 = mapper.putIfAbsent(user1);
        File directory2 = mapper.putIfAbsent(user1);
        assertThat(directory1, is(directory2));
    }

    @Test
    void testIsNotMapped() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        assertThat(mapper.isMapped("anything"), is(false));
    }

    @Test
    void testIsMapped() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        assertThat(mapper.isMapped(user1), is(true));
    }

    @Test
    void testInitialUserIds() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        assertThat(mapper.getConvertedUserIds(), empty());
    }

    @Test
    void testSingleUserIds() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        assertThat(mapper.getConvertedUserIds(), hasSize(1));
        assertThat(mapper.getConvertedUserIds().iterator().next(), is(user1));
    }

    @Test
    void testMultipleUserIds() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        String user2 = "user2";
        File directory2 = mapper.putIfAbsent(user2);
        assertThat(mapper.getConvertedUserIds(), hasSize(2));
        assertThat(mapper.getConvertedUserIds(), hasItems(user1, user2));
    }

    @Test
    void testRemove() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        mapper.remove(user1);
        assertThat(mapper.isMapped(user1), is(false));
    }

    @Test
    void testRemoveAfterSave() throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        IdStrategy idStrategy = IdStrategy.CASE_INSENSITIVE;
        UserIdMapper mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        mapper.remove(user1);
        mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        assertThat(mapper.isMapped(user1), is(false));
    }

    @Test
    void testPutGetCaseInsensitive() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        assertThat(mapper.getDirectory(user1.toUpperCase()), notNullValue());
    }

    @Test
    void testPutGetCaseSensitive() throws IOException {
        IdStrategy idStrategy = new IdStrategy.CaseSensitive();
        UserIdMapper mapper = createUserIdMapper(idStrategy);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        assertThat(mapper.getDirectory(user1.toUpperCase()), nullValue());
    }

    @Test
    void testIsMappedCaseInsensitive() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        assertThat(mapper.isMapped(user1.toUpperCase()), is(true));
    }

    @Test
    void testIsMappedCaseSensitive() throws IOException {
        IdStrategy idStrategy = new IdStrategy.CaseSensitive();
        UserIdMapper mapper = createUserIdMapper(idStrategy);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        assertThat(mapper.isMapped(user1.toUpperCase()), is(false));
    }

    @Test
    void testRemoveCaseInsensitive() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        mapper.remove(user1.toUpperCase());
        assertThat(mapper.isMapped(user1), is(false));
    }

    @Test
    void testRemoveCaseSensitive() throws IOException {
        IdStrategy idStrategy = new IdStrategy.CaseSensitive();
        UserIdMapper mapper = createUserIdMapper(idStrategy);
        String user1 = "user1";
        File directory = mapper.putIfAbsent(user1);
        mapper.remove(user1.toUpperCase());
        assertThat(mapper.isMapped(user1), is(true));
    }

    @Test
    void testRepeatRemove() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory1 = mapper.putIfAbsent(user1);
        mapper.remove(user1);
        mapper.remove(user1);
        assertThat(mapper.isMapped(user1), is(false));
    }

    @Test
    void testClear() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory1 = mapper.putIfAbsent(user1);
        mapper.clear();
        assertThat(mapper.isMapped(user1), is(false));
        assertThat(mapper.getConvertedUserIds(), empty());
    }

    @Test
    void testReload() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "user1";
        File directory1 = createUser(mapper, user1);
        mapper.clear();
        mapper.load();
        assertThat(mapper.isMapped(user1), is(true));
        assertThat(mapper.getConvertedUserIds(), hasSize(1));
    }

    @Test
    void testDirectoryFormatBasic() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "1user";
        File directory1 = mapper.putIfAbsent(user1);
        assertThat(directory1.getName(), startsWith(user1 + '_'));
    }

    @Test
    void testDirectoryFormatLongerUserId() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "muchlongeruserid";
        File directory1 = mapper.putIfAbsent(user1);
        assertThat(directory1.getName(), startsWith("muchlongeruser_"));
    }

    @Test
    void testDirectoryFormatAllSuppressedCharacters() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "!@#$%^";
        File directory1 = mapper.putIfAbsent(user1);
        assertThat(directory1.getName(), startsWith("_"));
    }

    @Test
    void testDirectoryFormatSingleCharacter() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = ".";
        File directory1 = mapper.putIfAbsent(user1);
        assertThat(directory1.getName(), startsWith("_"));
    }

    @Test
    void testDirectoryFormatMixed() throws IOException {
        UserIdMapper mapper = createUserIdMapper(IdStrategy.CASE_INSENSITIVE);
        String user1 = "a$b!c^d~e@f";
        File directory1 = mapper.putIfAbsent(user1);
        assertThat(directory1.getName(), startsWith("abcdef_"));
    }

    private UserIdMapper createUserIdMapper(IdStrategy idStrategy) throws IOException {
        File usersDirectory = createTestDirectory(getClass(), info);
        TestUserIdMapper mapper = new TestUserIdMapper(usersDirectory, idStrategy);
        mapper.init();
        return mapper;
    }

    private static File createTestDirectory(Class clazz, TestInfo info) throws IOException {
        // TODO better to use java.io.tmpdir, and TestName rule; or TemporaryFolder rule
        File tempDirectory = Files.createTempDirectory(Paths.get("target"), "userIdMapperTest").toFile();
        tempDirectory.deleteOnExit();
        copyTestDataIfExists(clazz, info, tempDirectory);
        return new File(tempDirectory, "users");
    }

    private static final String BASE_RESOURCE_PATH = "src/test/resources/hudson/model/";

    private static void copyTestDataIfExists(Class clazz, TestInfo info, File tempDirectory) throws IOException {
        File resourcesDirectory = new File(BASE_RESOURCE_PATH + clazz.getSimpleName(), info.getTestMethod().orElseThrow().getName());
        if (resourcesDirectory.exists()) {
            FileUtils.copyDirectory(resourcesDirectory, tempDirectory);
        }
    }
}
