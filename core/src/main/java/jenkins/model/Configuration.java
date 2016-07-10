/*
 * The MIT License
 *
 * Copyright (c) Contributors to Jenkins (http://www.jenkins-ci.org)
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
package jenkins.model;

import jenkins.util.SystemProperties;
import hudson.model.Hudson;


public class Configuration {

    public static boolean getBooleanConfigParameter(String name, boolean defaultValue) {
        String value = getStringConfigParameter(name,null);
        return (value==null)?defaultValue:Boolean.valueOf(value);
    }

    public static String getStringConfigParameter(String name, String defaultValue) {
        String value = SystemProperties.getString(Jenkins.class.getName()+"." + name);
        if( value == null )
            value = SystemProperties.getString(Hudson.class.getName()+"." + name);
        return (value==null)?defaultValue:value;
    }
}

