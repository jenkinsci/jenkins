/*
 * The MIT License
 *
 * Copyright (c) 2013 Chris Frohoff
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
package jenkins.security.security218.ysoserial.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClassFiles {
	public static String classAsFile(final Class<?> clazz) {
		return classAsFile(clazz, true);
	}
	
	public static String classAsFile(final Class<?> clazz, boolean suffix) {
		String str;
		if (clazz.getEnclosingClass() == null) {
			str = clazz.getName().replace(".", "/");
		} else {
			str = classAsFile(clazz.getEnclosingClass(), false) + "$" + clazz.getSimpleName();
		}
		if (suffix) {
			str += ".class";			
		}
		return str;  
	}

	public static byte[] classAsBytes(final Class<?> clazz) {
		try {
			final byte[] buffer = new byte[1024];
			final String file = classAsFile(clazz);
			final InputStream in = ClassFiles.class.getClassLoader().getResourceAsStream(file);
			if (in == null) {
				throw new IOException("couldn't find '" + file + "'");
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
