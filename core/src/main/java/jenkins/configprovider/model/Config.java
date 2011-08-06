/*
 The MIT License

 Copyright (c) 2011, Dominik Bartholdi

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
package jenkins.configprovider.model;

import jenkins.configprovider.ConfigProvider;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * Represents a configuration file. A Config object is always managed by one
 * specific {@link ConfigProvider}
 * 
 * @author domi
 * 
 */
public class Config implements Serializable {

	/**
	 * a unique id along all providers!
	 */
	public final String id;
	public final String name;
	public final String comment;
	public final String content;

	@DataBoundConstructor
	public Config(String id, String name, String comment, String content) {
		this.id = id == null ? String.valueOf(System.currentTimeMillis()) : id;
		this.name = name;
		this.comment = comment;
		this.content = content;
	}

	@Override
	public String toString() {
		return "[Config: id=" + id + ", name=" + name + "]";
	}
}
