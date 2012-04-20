/*
 The MIT License

 Copyright (c) 2012, Dominik Bartholdi

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */
package hudson.maven.settings;

import java.io.Serializable;

/**
 * Represents a particular settings file and its content - actually its a shadow object to hold the config data coming from a ConfigProvider of the 'config-provider' plugin.
 * 
 * @author domi
 */
public class SettingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * a unique id along all providers!
     */
    public final String id; 

    public final String name;

    /**
     * Any note that the author of this configuration wants to associate with this.
     */
    public final String comment;

    /**
     * Content of the file as-is.
     */
    public final String content;

    public SettingConfig(String id, String name, String comment, String content) {
        this.id = id == null ? String.valueOf(System.currentTimeMillis()) : id;
        this.name = name;
        this.comment = comment;
        this.content = content;
    }

    @Override
    public String toString() {
        return "[SettingConfig: id=" + id + ", name=" + name + "]";
    }
}
