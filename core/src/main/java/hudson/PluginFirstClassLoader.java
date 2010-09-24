/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Olivier Lamy
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
package hudson;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import org.apache.tools.ant.AntClassLoader;

/**
 * classLoader which use first /WEB-INF/lib/*.jar and /WEB-INF/classes before core classLoader
 * <b>you must use the pluginFirstClassLoader true in the maven-hpi-plugin</b>
 * @author olamy
 * @since 1.371
 */
public class PluginFirstClassLoader
    extends AntClassLoader
    implements Closeable
{
    
    private List<URL> urls = new ArrayList<URL>();

    public void addPathFiles( Collection<File> paths )
        throws IOException
    {
        for ( File f : paths )
        {
            urls.add( f.toURI().toURL() );
            addPathFile( f );
        }
    }

    /**
     * @return List of jar used by the plugin /WEB-INF/lib/*.jar and classes directory /WEB-INF/classes
     */
    public List<URL> getURLs() 
    {
        return urls;
    }
    
    public void close()
        throws IOException
    {
        cleanup();
    }

    @Override
    protected Enumeration findResources( String arg0, boolean arg1 )
        throws IOException
    {
        Enumeration enu = super.findResources( arg0, arg1 );
        return enu;
    }

    @Override
    protected Enumeration findResources( String name )
        throws IOException
    {
        Enumeration enu = super.findResources( name );
        return enu;
    }

    @Override
    public URL getResource( String arg0 )
    {
        URL url = super.getResource( arg0 );
        return url;
    }

    @Override
    public InputStream getResourceAsStream( String name )
    {
        InputStream is = super.getResourceAsStream( name );
        return is;
    }   
    
}
