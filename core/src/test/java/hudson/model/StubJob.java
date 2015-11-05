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

import java.io.File;
import java.util.SortedMap;

/**
 * @author kingfai
 * @deprecated Does not behave very consistently. Either write a real functional test with {@code JenkinsRule}, or use PowerMock/Mockito.
 */
@Deprecated
@SuppressWarnings({ "rawtypes", "unchecked" })
class StubJob extends Job {

    public final static String DEFAULT_STUB_JOB_NAME = "StubJob";
    
    public StubJob() {
        super(null, DEFAULT_STUB_JOB_NAME);
    }
    
    @Override
    public boolean isBuildable() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    protected SortedMap _getRuns() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void removeRun(Run run) {
        // TODO Auto-generated method stub
        
    }

    @Override public File getBuildDir() {
        return new File(System.getProperty("java.io.tmpdir"));
    }
    
    /**
     * Override save so that nothig happens when setDisplayName() is called
     */
    @Override
    public void save() {
        
    }   

    @Override public ItemGroup getParent() {
        return null;
    }

}
