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

/**
 * Describes the content of a {@link Config} in a more technical way. In fact it
 * provides information to determine how the content should be rendered in a
 * editor (e.g. CodeMirror).
 * 
 * @author domi
 * 
 */
public interface ContentType {

	public String getCmMode();

	public String getMime();

	public enum DefinedType implements ContentType {
		XML("xml", "application/xml"), HTML("htmlmixed", "text/html");

		public final String cmMode;
		public final String mime;

		private DefinedType(String cmMode, String mime) {
			this.cmMode = cmMode;
			this.mime = mime;
		}

		public String getCmMode() {
			return cmMode;
		}

		public String getMime() {
			return mime;
		}
	}

}
