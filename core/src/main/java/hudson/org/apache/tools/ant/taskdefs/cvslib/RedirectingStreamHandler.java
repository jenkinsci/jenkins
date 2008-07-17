/*
 * Copyright  2002,2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package hudson.org.apache.tools.ant.taskdefs.cvslib;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A dummy stream handler that just passes stuff to the parser.
 *
 * @version $Revision$ $Date$
 */
class RedirectingStreamHandler extends PumpStreamHandler {
    RedirectingStreamHandler(final ChangeLogParser parser) {
        this(new RedirectingOutputStream(parser), null);
    }

    RedirectingStreamHandler(OutputStream out, InputStream in) {
        this(out, new ByteArrayOutputStream(), in);
    }

     RedirectingStreamHandler(OutputStream out, OutputStream err, InputStream in) {
        super(out, err, in);
    }

    String getErrors() {
        try {
            final ByteArrayOutputStream error
                = (ByteArrayOutputStream) getErr();

            return error.toString("ASCII");
        } catch (final Exception e) {
            return null;
        }
    }


    public void stop() {
        super.stop();
        try {
            getErr().close();
            getOut().close();
        } catch (final IOException e) {
            // plain impossible
            throw new BuildException(e);
        }
    }
}

