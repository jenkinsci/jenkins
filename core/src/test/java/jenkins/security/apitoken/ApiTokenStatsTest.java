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

import hudson.XmlFile;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Field;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ApiTokenPropertyConfiguration.class)
public class ApiTokenStatsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    @Before
    public void prepareConfig() throws Exception {
        // to separate completely the class under test from its environment
        ApiTokenPropertyConfiguration mockConfig = Mockito.mock(ApiTokenPropertyConfiguration.class);
        Mockito.when(mockConfig.isUsageStatisticsEnabled()).thenReturn(true);
        
        PowerMockito.mockStatic(ApiTokenPropertyConfiguration.class);
        PowerMockito.when(ApiTokenPropertyConfiguration.class, "get").thenReturn(mockConfig);
    }
    
    @Test
    public void regularUsage() throws Exception {
        final String ID_1 = UUID.randomUUID().toString();
        final String ID_2 = "other-uuid";
        
        { // empty stats can be saved
            ApiTokenStats tokenStats = new ApiTokenStats();
            tokenStats.setParent(tmp.getRoot());
            
            // can remove an id that does not exist
            tokenStats.removeId(ID_1);
            
            tokenStats.save();
        }
        
        { // and then loaded, empty stats is empty
            ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
            assertNotNull(tokenStats);
            
            ApiTokenStats.SingleTokenStats stats = tokenStats.findTokenStatsById(ID_1);
            assertEquals(0, stats.getUseCounter());
            assertNull(stats.getLastUseDate());
            assertEquals(0L, stats.getNumDaysUse());
        }
        
        Date lastUsage;
        { // then re-notify the same token
            ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
            
            ApiTokenStats.SingleTokenStats stats = tokenStats.updateUsageForId(ID_1);
            assertEquals(1, stats.getUseCounter());
            
            lastUsage = stats.getLastUseDate();
            assertNotNull(lastUsage);
            // to avoid flaky test in case the test is run at midnight, normally it's 0 
            assertTrue(stats.getNumDaysUse() <= 1);
        }
        
        // to enforce a difference in the lastUseDate
        Thread.sleep(10);
        
        { // then re-notify the same token
            ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
            
            ApiTokenStats.SingleTokenStats stats = tokenStats.updateUsageForId(ID_1);
            assertEquals(2, stats.getUseCounter());
            assertTrue(lastUsage.before(stats.getLastUseDate()));
            // to avoid flaky test in case the test is run at midnight, normally it's 0 
            assertTrue(stats.getNumDaysUse() <= 1);
        }
        
        { // check all tokens have separate stats, try with another ID
            ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
            
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
                assertTrue(stats.getNumDaysUse() <= 1);
            }
        }
        
        { // reload the stats, check the counter are correct
            ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
            
            ApiTokenStats.SingleTokenStats stats_1 = tokenStats.findTokenStatsById(ID_1);
            assertEquals(2, stats_1.getUseCounter());
            ApiTokenStats.SingleTokenStats stats_2 = tokenStats.findTokenStatsById(ID_2);
            assertEquals(1, stats_2.getUseCounter());
            
            tokenStats.removeId(ID_1);
        }
        
        { // after a removal, the existing must keep its value
            ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
            
            ApiTokenStats.SingleTokenStats stats_1 = tokenStats.findTokenStatsById(ID_1);
            assertEquals(0, stats_1.getUseCounter());
            ApiTokenStats.SingleTokenStats stats_2 = tokenStats.findTokenStatsById(ID_2);
            assertEquals(1, stats_2.getUseCounter());
        }
    }
    
    @Test
    public void testResilientIfFileDoesNotExist() throws Exception {
        ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
        assertNotNull(tokenStats);
    }
    
    @Test
    public void resistentToDuplicatedUuid() throws Exception {
        final String ID_1 = UUID.randomUUID().toString();
        final String ID_2 = UUID.randomUUID().toString();
        final String ID_3 = UUID.randomUUID().toString();
        
        { // put counter to 4 for ID_1 and to 2 for ID_2 and 1 for ID_3
            ApiTokenStats tokenStats = new ApiTokenStats();
            tokenStats.setParent(tmp.getRoot());
            
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
            XmlFile statsFile = ApiTokenStats.getConfigFile(tmp.getRoot());
            String content = FileUtils.readFileToString(statsFile.getFile());
            // now there are two time the same id in the file with different stats
            String newContentWithDuplicatedId = content.replace(ID_1, ID_2).replace(ID_3, ID_2);
            FileUtils.write(statsFile.getFile(), newContentWithDuplicatedId);
        }
        
        {
            ApiTokenStats tokenStats = ApiTokenStats.load(tmp.getRoot());
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
    
    @Test
    public void testDayDifference() throws Exception {
        final String ID = UUID.randomUUID().toString();
        ApiTokenStats tokenStats = new ApiTokenStats();
        tokenStats.setParent(tmp.getRoot());
        ApiTokenStats.SingleTokenStats stats = tokenStats.updateUsageForId(ID);
        assertTrue(stats.getNumDaysUse() <= 1);
        
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
        
        assertTrue(stats.getNumDaysUse() >= 2);
    }
}
