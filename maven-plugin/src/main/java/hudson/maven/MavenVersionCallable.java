package hudson.maven;

/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc. Olivier Lamy
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

import hudson.AbortException;
import hudson.remoting.Callable;

import java.io.File;
import java.io.IOException;

import org.kohsuke.stapler.framework.io.IOException2;

/**
 * 
 * @author Olivier Lamy
 * @since 3.0
 *
 */
public class MavenVersionCallable
    implements Callable<MavenInformation, IOException>
{
    private static final long serialVersionUID = -2644951622080930034L;
    
    private final String mavenHome;
    
    public MavenVersionCallable( String mavenHome )
    {
        this.mavenHome = mavenHome;
    }

    public MavenInformation call()
        throws IOException
    {
        try
        {
            File home = new File(mavenHome);
            if(!home.isDirectory())
            {
                if (home.exists())
                    throw new AbortException(Messages.MavenVersionCallable_MavenHomeIsNotDirectory(home));
                else
                    throw new AbortException(Messages.MavenVersionCallable_MavenHomeDoesntExist(home));
            }
            return MavenEmbedderUtils.getMavenVersion(home);
        }
        catch ( MavenEmbedderException e )
        {
            throw new IOException2( e );
        }
    }

}
