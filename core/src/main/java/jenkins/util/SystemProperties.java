/*
 * The MIT License
 *
 * Copyright 2015 Johannes Ernst http://upon2020.com/
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
package jenkins.util;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;

/**
 * Centralizes calls to <code>System.getProperty()</code> and related calls.
 * This allows us to get values not just from environment variables but also from
 * the <code>ServletContext</code>, so properties like <code>hudson.DNSMultiCast.disabled</code>
 * can be set in <code>context.xml</code> and the app server's boot script does not
 * have to be changed.
 * 
 * <p>While it looks like it on first glance, this cannot be mapped to <code>EnvVars.java</code>
 * because <code>EnvVars.java</code> is only for build variables, not Jenkins itself variables. 
 * 
 * <p>This should be used to obtain hudson/jenkins "app"-level parameters
 * (e.g. <code>hudson.DNSMultiCast.disabled</code>), but not for system parameters
 * (e.g. <code>os.name</code>).
 * 
 * @author Johannes Ernst
 * @since 1.639
 */
public class SystemProperties {
    /**
     * The ServletContext to get the "init" parameters from.
     */
    private static ServletContext theContext;

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SystemProperties.class.getName());

    /**
     * This class should never be instantiated.
     */
    private SystemProperties() {}

    /**
     * Gets the system property indicated by the specified key.
     * This behaves just like <code>System.getProperty(String)</code>, except that it
     * also consults the <code>ServletContext</code>'s "init" parameters.
     * 
     * @param      key   the name of the system property.
     * @return     the string value of the system property,
     *             or <code>null</code> if there is no property with that key.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertyAccess</code> method doesn't allow
     *              access to the specified system property.
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     */
    public static String getString(String key) {
        String value = System.getProperty(key); // keep passing on any exceptions
        if (value != null) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "Property (system): {0} => {1}", new Object[] {key, value});
            }
        } else if (theContext != null) {
            value = theContext.getInitParameter(key);
            if (value != null) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO, "Property (context): {0} => {1}", new Object[] {key, value});
                }
            }
        } else {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "Property (not found): {0} => {1}", new Object[] {key, value});
            }
        }
        return value;
    }

    /**
     * Gets the system property indicated by the specified key, or a default value.
     * This behaves just like <code>System.getProperty(String,String)</code>, except
     * that it also consults the <code>ServletContext</code>'s "init" parameters.
     * 
     * @param      key   the name of the system property.
     * @param      def   a default value.
     * @return     the string value of the system property,
     *             or <code>null</code> if there is no property with that key.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertyAccess</code> method doesn't allow
     *              access to the specified system property.
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     */
    public static String getString(String key, String def) {
        String value = System.getProperty(key); // keep passing on any exceptions
        if (value != null) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "Property (system): {0} => {1}", new Object[] {key, value});
            }
        } else if (theContext != null) {
            value = theContext.getInitParameter(key);
            if (value != null) {
                if(LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO, "Property (context): {0} => {1}", new Object[] {key, value});
                }
            }
        }
        if (value == null) {
            value = def;
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "Property (default): {0} => {1}", new Object[] {key, value});
            }
        }
        return value;        
    }

    /**
      * Returns {@code true} if the system property
      * named by the argument exists and is equal to the string
      * {@code "true"}. If the system property does not exist, return
      * {@code "true"} if a property by this name exists in the <code>ServletContext</code>
      * and is equal to the string {@code "true"}.
      * 
      * This behaves just like <code>Boolean.getBoolean(String)</code>, except that it
      * also consults the <code>ServletContext</code>'s "init" parameters.
      * 
      * @param   name   the system property name.
      * @return  the {@code boolean} value of the system property.
      */  
    public static boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    /**
      * Returns {@code true} if the system property
      * named by the argument exists and is equal to the string
      * {@code "true"}, or a default value. If the system property does not exist, return
      * {@code "true"} if a property by this name exists in the <code>ServletContext</code>
      * and is equal to the string {@code "true"}. If that property does not
      * exist either, return the default value.
      * 
      * This behaves just like <code>Boolean.getBoolean(String)</code> with a default
      * value, except that it also consults the <code>ServletContext</code>'s "init" parameters.
      * 
      * @param   name   the system property name.
      * @param   def   a default value.
      * @return  the {@code boolean} value of the system property.
      */
    public static boolean getBoolean(String name, boolean def) {
        String v = getString(name);
       
        if (v != null) {
            return Boolean.parseBoolean(v);
        }
        return def;
    }
    
    /**
      * Determines the integer value of the system property with the
      * specified name.
      * 
      * This behaves just like <code>Integer.getInteger(String)</code>, except that it
      * also consults the <code>ServletContext</code>'s "init" parameters.
      * 
      * @param   name property name.
      * @return  the {@code Integer} value of the property.
      */
    public static Integer getInteger(String name) {
        return getInteger(name, null);
    }

    /**
      * Determines the integer value of the system property with the
      * specified name, or a default value.
      * 
      * This behaves just like <code>Integer.getInteger(String,Integer)</code>, except that it
      * also consults the <code>ServletContext</code>'s "init" parameters. If neither exist,
      * return the default value.
      * 
      * @param   name property name.
      * @param   def   a default value.
      * @return  the {@code Integer} value of the property.
      */
    public static Integer getInteger(String name, Integer def) {
        String v = getString(name);
       
        if (v != null) {
            try {
                return Integer.decode(v);
            } catch (NumberFormatException e) {
            }
        }
        return def;
    }

    /**
     * Invoked by WebAppMain, tells us where to get the "init" parameters from.
     * 
     * @param context the <code>ServletContext</code> obtained from <code>contextInitialized</code>
     */
    public static void initialize(ServletContext context) {
        theContext = context;
    }
}
