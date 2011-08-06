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

	/**
	 * The CodeMirror mode as defined in Stapler (
	 * <code>org.kohsuke.stapler.codemirror.mode.*</code>). <br>
	 * Currently Supported:
	 * <ul>
	 * <li>clike</li>
	 * <li>css</li>
	 * <li>diff</li>
	 * <li>haskell</li>
	 * <li>htmlmixed</li>
	 * <li>javascript</li>
	 * <li>lua</li>
	 * <li>php</li>
	 * <li>plsql</li>
	 * <li>python</li>
	 * <li>rst</li>
	 * <li>smaltalk</li>
	 * <li>stex</li>
	 * <li>xml</li>
	 * </ul>
	 * e.g. used in 
	 * <code>&lt;textarea name="config.content" codemirror-mode="${contentType.cmMode}" ... /&gt;</code>
	 * 
	 * @return the CodeMirror mode
	 */
	public String getCmMode();

	/**
	 * Actually the 'mode' attribute for the CodeMirror editor. As in:
	 * <code>&lt;textarea name="config.content" codemirror-config="mode:'${contentType.mime}',lineNumbers: true" ... /&gt;</code>
	 * 
	 * @return the mime.
	 */
	public String getMime();

	public enum DefinedType implements ContentType {
		XML("xml", "application/xml"), HTML("htmlmixed", "text/html"), GROOVY(
				"clike", "text/x-groovy");

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
