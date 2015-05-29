/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Yahoo!, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class DisplayNameListenerTest {

    @Test
    public void testOnCopied() throws Exception {
        DisplayNameListener listener = new DisplayNameListener();
        StubJob src = new StubJob();
        src.doSetName("src");
        StubJob dest = new StubJob();
        dest.doSetName("dest");
        dest.setDisplayName("this should be cleared");
        
        // make sure the displayname and the name are different at this point
        assertFalse(dest.getName().equals(dest.getDisplayName()));
        
        listener.onCopied(src, dest);
        // make sure the displayname is equals to the name as it should be null
        assertEquals(dest.getName(), dest.getDisplayName());
    }
    
    @Test
    public void testOnRenamedOldNameEqualsDisplayName() throws Exception {
        DisplayNameListener listener = new DisplayNameListener();
        final String oldName = "old job name";
        final String newName = "new job name";
        StubJob src = new StubJob();       
        src.doSetName(newName);
        src.setDisplayName(oldName);
        
        listener.onRenamed(src, oldName, newName);
        
        assertEquals(newName, src.getDisplayName());
    }

    @Test
    public void testOnRenamedOldNameNotEqualDisplayName() throws Exception {
        DisplayNameListener listener = new DisplayNameListener();
        final String oldName = "old job name";
        final String newName = "new job name";
        final String displayName = "the display name";
        StubJob src = new StubJob();       
        src.doSetName(newName);
        src.setDisplayName(displayName);
        
        listener.onRenamed(src, oldName, oldName);
        
        // make sure displayname is still intact
        assertEquals(displayName, src.getDisplayName());
    }
}
