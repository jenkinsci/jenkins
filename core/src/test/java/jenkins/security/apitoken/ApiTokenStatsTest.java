/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.apitoken;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import hudson.XmlFile;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ApiTokenStatsTest {

    @TempDir
    private File tmp;

    @BeforeEach
    void prepareConfig() {
        // to separate completely the class under test from its environment
        ApiTokenPropertyConfiguration mockConfig = Mockito.mock(ApiTokenPropertyConfiguration.class);

    }

    @Test
    void regularUsage() throws Exception {
        final String ID_1 = UUID.randomUUID().toString();
        final String ID_2 = "other-uuid";

        ApiTokenPropertyConfiguration mockConfig = mock(ApiTokenPropertyConfiguration.class);
        try (MockedStatic<ApiTokenPropertyConfiguration> mocked = mockStatic(ApiTokenPropertyConfiguration.class)) {
            mocked.when(ApiTokenPropertyConfiguration::get).thenReturn(mockConfig);
            Mockito.when(mockConfig.isUsageStatisticsEnabled()).thenReturn(true);

            { // empty stats can be saved
                ApiTokenStats tokenStats = createFromFile(tmp);

                // can remove an id that does not exist
                tokenStats.removeId(ID_1);

                tokenStats.save();
            }

            { // and then loaded, empty stats is empty
                ApiTokenStats tokenStats = createFromFile(tmp);
                assertNotNull(tokenStats);

                ApiTokenStats.SingleTokenStats stats = tokenStats.findTokenStatsById(ID_1);
                assertEquals(0, stats.getUseCounter());
                assertNull(stats.getLastUseDate());
                assertEquals(0L, stats.getNumDaysUse());
            }

            Date lastUsage;
            { // then re-notify the same token
                ApiTokenStats tokenStats = createFromFile(tmp);

                ApiTokenStats.SingleTokenStats stats = tokenStats.updateUsageForId(ID_1);
                assertEquals(1, stats.getUseCounter());

                lastUsage = stats.getLastUseDate();
                assertNotNull(lastUsage);
                // to avoid flaky test in case the test is run at midnight, normally it's 0

                assertThat(stats.getNumDaysUse(), lessThanOrEqualTo(1L));
            }

            // to enforce a difference in the lastUseDate
            Thread.sleep(10);

            { // then re-notify the same token
                ApiTokenStats tokenStats = createFromFile(tmp);

                ApiTokenStats.SingleTokenStats stats = tokenStats.updateUsageForId(ID_1);
                assertEquals(2, stats.getUseCounter());
                assertThat(lastUsage, lessThan(stats.getLastUseDate()));

                // to avoid flaky test in case the test is run at midnight, normally it's 0
                assertThat(stats.getNumDaysUse(), lessThanOrEqualTo(1L));
            }

            { // check all tokens have separate stats, try with another ID
                ApiTokenStats tokenStats = createFromFile(tmp);

                {
                    ApiTokenStats.SingleTokenStats stats = tokenStats.findTokenStatsById(ID_2);
                    assertEquals(0, stats.getUseCounter());
                    assertNull(stats.getLastUseDate());
                    assertEquals(0L, stats.getNumDaysUse());
                }
                {
                    ApiTokenStats.SingleTokenStats stats = tokenStats.updateUsageForId(ID_2);
                    assertEquals(1, stats.getUseCounter());
                    assertNotNull(lastUsage);
                    assertThat(stats.getNumDaysUse(), lessThanOrEqualTo(1L));
                }
            }

            { // reload the stats, check the counter are correct
                ApiTokenStats tokenStats = createFromFile(tmp);

                ApiTokenStats.SingleTokenStats stats_1 = tokenStats.findTokenStatsById(ID_1);
                assertEquals(2, stats_1.getUseCounter());
                ApiTokenStats.SingleTokenStats stats_2 = tokenStats.findTokenStatsById(ID_2);
                assertEquals(1, stats_2.getUseCounter());

                tokenStats.removeId(ID_1);
            }

            { // after a removal, the existing must keep its value
                ApiTokenStats tokenStats = createFromFile(tmp);

                ApiTokenStats.SingleTokenStats stats_1 = tokenStats.findTokenStatsById(ID_1);
                assertEquals(0, stats_1.getUseCounter());
                ApiTokenStats.SingleTokenStats stats_2 = tokenStats.findTokenStatsById(ID_2);
                assertEquals(1, stats_2.getUseCounter());
            }
        }
    }

    @Test
    void testResilientIfFileDoesNotExist() {
        ApiTokenPropertyConfiguration mockConfig = mock(ApiTokenPropertyConfiguration.class);
        try (MockedStatic<ApiTokenPropertyConfiguration> mocked = mockStatic(ApiTokenPropertyConfiguration.class)) {
            mocked.when(ApiTokenPropertyConfiguration::get).thenReturn(mockConfig);
            Mockito.when(mockConfig.isUsageStatisticsEnabled()).thenReturn(true);
            ApiTokenStats tokenStats = createFromFile(tmp);
            assertNotNull(tokenStats);
        }
    }

    @Test
    void resistantToDuplicatedUuid() throws Exception {
        final String ID_1 = UUID.randomUUID().toString();
        final String ID_2 = UUID.randomUUID().toString();
        final String ID_3 = UUID.randomUUID().toString();

        ApiTokenPropertyConfiguration mockConfig = mock(ApiTokenPropertyConfiguration.class);
        try (MockedStatic<ApiTokenPropertyConfiguration> mocked = mockStatic(ApiTokenPropertyConfiguration.class)) {
            mocked.when(ApiTokenPropertyConfiguration::get).thenReturn(mockConfig);
            Mockito.when(mockConfig.isUsageStatisticsEnabled()).thenReturn(true);

            { // put counter to 4 for ID_1 and to 2 for ID_2 and 1 for ID_3
                ApiTokenStats tokenStats = createFromFile(tmp);

                tokenStats.updateUsageForId(ID_1);
                tokenStats.updateUsageForId(ID_1);
                tokenStats.updateUsageForId(ID_1);
                tokenStats.updateUsageForId(ID_1);
                tokenStats.updateUsageForId(ID_2);
                tokenStats.updateUsageForId(ID_3);
                // only the most recent information is kept
                tokenStats.updateUsageForId(ID_2);
            }

            { // replace the ID_1 with ID_2 in the file
                XmlFile statsFile = ApiTokenStats.getConfigFile(tmp);
                String content = Files.readString(statsFile.getFile().toPath(), Charset.defaultCharset());
                // now there are multiple times the same id in the file with different stats
                String newContentWithDuplicatedId = content.replace(ID_1, ID_2).replace(ID_3, ID_2);
                Files.writeString(statsFile.getFile().toPath(), newContentWithDuplicatedId, Charset.defaultCharset());
            }

            {
                ApiTokenStats tokenStats = createFromFile(tmp);
                assertNotNull(tokenStats);

                ApiTokenStats.SingleTokenStats stats_1 = tokenStats.findTokenStatsById(ID_1);
                assertEquals(0, stats_1.getUseCounter());

                // the most recent information is kept
                ApiTokenStats.SingleTokenStats stats_2 = tokenStats.findTokenStatsById(ID_2);
                assertEquals(2, stats_2.getUseCounter());

                ApiTokenStats.SingleTokenStats stats_3 = tokenStats.findTokenStatsById(ID_3);
                assertEquals(0, stats_3.getUseCounter());
            }
        }
    }

    @Test
    void resistantToDuplicatedUuid_withNull() throws Exception {
        final String ID = "ID";

        ApiTokenPropertyConfiguration mockConfig = mock(ApiTokenPropertyConfiguration.class);
        try (MockedStatic<ApiTokenPropertyConfiguration> mocked = mockStatic(ApiTokenPropertyConfiguration.class)) {
            mocked.when(ApiTokenPropertyConfiguration::get).thenReturn(mockConfig);
            Mockito.when(mockConfig.isUsageStatisticsEnabled()).thenReturn(true);

            { // prepare
                List<ApiTokenStats.SingleTokenStats> tokenStatsList = Arrays.asList(
                        /* A */ createSingleTokenStatsByReflection(ID, null, 0),
                        /* B */ createSingleTokenStatsByReflection(ID, "2018-05-01 09:10:59.234", 2),
                        /* C */ createSingleTokenStatsByReflection(ID, "2018-05-01 09:10:59.234", 3),
                        /* D */ createSingleTokenStatsByReflection(ID, "2018-05-01 09:10:59.235", 1)
                );

                ApiTokenStats stats = createFromFile(tmp);
                Field field = ApiTokenStats.class.getDeclaredField("tokenStats");
                field.setAccessible(true);
                field.set(stats, tokenStatsList);

                stats.save();
            }
            { // reload to see the effect
                ApiTokenStats stats = createFromFile(tmp);
                ApiTokenStats.SingleTokenStats tokenStats = stats.findTokenStatsById(ID);
                // must be D (as it was the last updated one)
                assertThat(tokenStats.getUseCounter(), equalTo(1));
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInternalComparator() throws Exception {
        List<ApiTokenStats.SingleTokenStats> tokenStatsList = Arrays.asList(
                createSingleTokenStatsByReflection("A", null, 0),
                createSingleTokenStatsByReflection("B", "2018-05-01 09:10:59.234", 2),
                createSingleTokenStatsByReflection("C", "2018-05-01 09:10:59.234", 3),
                createSingleTokenStatsByReflection("D", "2018-05-01 09:10:59.235", 1)
        );

        ApiTokenPropertyConfiguration mockConfig = mock(ApiTokenPropertyConfiguration.class);
        try (MockedStatic<ApiTokenPropertyConfiguration> mocked = mockStatic(ApiTokenPropertyConfiguration.class)) {
            mocked.when(ApiTokenPropertyConfiguration::get).thenReturn(mockConfig);
            Mockito.when(mockConfig.isUsageStatisticsEnabled()).thenReturn(true);

            Field field = ApiTokenStats.SingleTokenStats.class.getDeclaredField("COMP_BY_LAST_USE_THEN_COUNTER");
            field.setAccessible(true);
            Comparator<ApiTokenStats.SingleTokenStats> comparator = (Comparator<ApiTokenStats.SingleTokenStats>) field.get(null);

            // to be not impacted by the declaration order
            Collections.shuffle(tokenStatsList, new Random(42));
            tokenStatsList.sort(comparator);

            List<String> idList = tokenStatsList.stream()
                    .map(ApiTokenStats.SingleTokenStats::getTokenUuid)
                    .collect(Collectors.toList());

            assertThat(idList, contains("A", "B", "C", "D"));
        }
    }

    private ApiTokenStats.SingleTokenStats createSingleTokenStatsByReflection(String uuid, String dateString, Integer counter) throws Exception {
        Class<ApiTokenStats.SingleTokenStats> clazz = ApiTokenStats.SingleTokenStats.class;
        Constructor<ApiTokenStats.SingleTokenStats> constructor = clazz.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);

        ApiTokenStats.SingleTokenStats result = constructor.newInstance(uuid);

        {
            Field field = clazz.getDeclaredField("useCounter");
            field.setAccessible(true);
            field.set(result, counter);
        }
        if (dateString != null) {
            Field field = clazz.getDeclaredField("lastUseDate");
            field.setAccessible(true);
            field.set(result, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(dateString));
        }

        return result;
    }

    @Test
    void testDayDifference() throws Exception {
        final String ID = UUID.randomUUID().toString();
        ApiTokenPropertyConfiguration mockConfig = mock(ApiTokenPropertyConfiguration.class);
        try (MockedStatic<ApiTokenPropertyConfiguration> mocked = mockStatic(ApiTokenPropertyConfiguration.class)) {
            mocked.when(ApiTokenPropertyConfiguration::get).thenReturn(mockConfig);
            Mockito.when(mockConfig.isUsageStatisticsEnabled()).thenReturn(true);
            ApiTokenStats tokenStats = createFromFile(tmp);
            ApiTokenStats.SingleTokenStats stats = tokenStats.updateUsageForId(ID);
            assertThat(stats.getNumDaysUse(), lessThan(1L));

            Field field = ApiTokenStats.SingleTokenStats.class.getDeclaredField("lastUseDate");
            field.setAccessible(true);
            field.set(stats, new Date(
                            new Date().toInstant()
                                    .minus(2, ChronoUnit.DAYS)
                                    // to ensure we have more than 2 days
                                    .minus(5, ChronoUnit.MINUTES)
                                    .toEpochMilli()
                    )
            );

            assertThat(stats.getNumDaysUse(), greaterThanOrEqualTo(2L));
        }
    }

    private ApiTokenStats createFromFile(File file) {
        ApiTokenStats result = ApiTokenStats.internalLoad(file);
        if (result == null) {
            result = new ApiTokenStats();
            result.parent = file;
        }

        return result;
    }
}
