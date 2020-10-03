package jenkins.model;

import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RssFeedItemTest {

    private RssFeedItem itemToTest = null;
    
    private ChangeLogSet mockChangelog;
    private ChangeLogSet.Entry mockEntry;
    private Run<?, ?> mockRun;

    @BeforeEach
    void setup() {
        mockEntry = mock(ChangeLogSet.Entry.class);
        mockChangelog = mock(ChangeLogSet.class);
        mockRun = mock(Run.class);
        when(mockChangelog.getRun()).thenReturn(mockRun);
        when(mockEntry.getParent()).thenReturn(mockChangelog);
        itemToTest = new RssFeedItem(mockEntry, 0);
    }
    
    @Test
    void testGetRun() {
        assertEquals(mockRun, itemToTest.getRun());
        verify(mockChangelog, times(1)).getRun();
        verify(mockEntry, times(1)).getParent();
        verifyNoMoreInteractions(mockEntry, mockChangelog, mockRun);
    }

    @Test
    void testGetEntry() {
        assertEquals(mockEntry, itemToTest.getEntry());
    }

    @Test
    void testGetIndex() {
        assertEquals(0, itemToTest.getIndex());
    }
}