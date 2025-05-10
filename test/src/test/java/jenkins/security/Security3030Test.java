/*
 * The MIT License
 *
 * Copyright (c) 2023, CloudBees, Inc.
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

package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.ExtensionList;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import hudson.util.MultipartFormDataParser;
import jakarta.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.apache.commons.fileupload2.core.FileUploadByteCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadFileCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

@WithJenkins
class Security3030Test {
    // TODO Consider parameterizing with Stapler (RequestImpl/StaplerRequest2FormAction) + Jenkins (MultipartFormDataParser/MultipartFormDataParserAction)
    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void fewFilesStapler() throws IOException {
        assertSubmissionOK(StaplerRequest2FormAction.instance(), 20, 10, 1024 * 1024);
        assertSubmissionOK(StaplerRequest2FormAction.instance(), 10, 41, 10);
    }

    @Test
    void tooManyFilesStapler() throws Exception {
        ServletException ex = assertSubmissionThrows(StaplerRequest2FormAction.instance(), 10, 1000, 20, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        ex = assertSubmissionThrows(StaplerRequest2FormAction.instance(), 1000, 10, 10, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        try (FieldValue v = withStaticField(RequestImpl.class, "FILEUPLOAD_MAX_FILES", 10_000)) {
            assertSubmissionOK(StaplerRequest2FormAction.instance(), 1000, 10, 10);
            ex = assertSubmissionThrows(StaplerRequest2FormAction.instance(), 10_000, 10, 10, FileUploadFileCountLimitException.class);
            assertThat(ex.getMessage(), containsString(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        }
        ex = assertSubmissionThrows(StaplerRequest2FormAction.instance(), 10, 1000, 20, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        ex = assertSubmissionThrows(StaplerRequest2FormAction.instance(), 1000, 10, 10, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILES"));
    }

    @Test
    void tooLargeFilesStapler() throws Exception {
        assertSubmissionOK(StaplerRequest2FormAction.instance(), 1, 50, 10 * 1024 * 1024);
        try (FieldValue v = withStaticField(RequestImpl.class, "FILEUPLOAD_MAX_FILE_SIZE", 1024 * 1024)) {
            assertSubmissionOK(StaplerRequest2FormAction.instance(), 200, 100, 1024);
            ServletException ex = assertSubmissionThrows(StaplerRequest2FormAction.instance(), 1, 50, 10 * 1024 * 1024, FileUploadByteCountLimitException.class);
            assertThat(ex.getMessage(), containsString(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILE_SIZE"));
        }
        assertSubmissionOK(StaplerRequest2FormAction.instance(), 1, 50, 10 * 1024 * 1024);
    }

    @Test
    void tooLargeSubmissionStapler() throws Exception {
        assertSubmissionOK(StaplerRequest2FormAction.instance(), 1, 50, 10 * 1024 * 1024);
        try (FieldValue v = withStaticField(RequestImpl.class, "FILEUPLOAD_MAX_SIZE", 1024 * 1024)) {
            assertSubmissionOK(StaplerRequest2FormAction.instance(), 200, 100, 1024);
            ServletException ex = assertSubmissionThrows(StaplerRequest2FormAction.instance(), 1, 50, 10 * 1024 * 1024, FileUploadSizeException.class);
            assertThat(ex.getMessage(), containsString(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_SIZE"));
        }
        assertSubmissionOK(StaplerRequest2FormAction.instance(), 1, 50, 10 * 1024 * 1024);
    }

    @Test
    void fewFilesParser() throws IOException {
        assertSubmissionOK(MultipartFormDataParserAction.instance(), 20, 10, 1024 * 1024);
        assertSubmissionOK(MultipartFormDataParserAction.instance(), 200, 100, 1024);
    }

    @Test
    void tooManyFilesParser() throws Exception {
        ServletException ex = assertSubmissionThrows(MultipartFormDataParserAction.instance(), 10, 1000, 20, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        ex = assertSubmissionThrows(MultipartFormDataParserAction.instance(), 1000, 10, 10, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        try (FieldValue v = withStaticField(MultipartFormDataParser.class, "FILEUPLOAD_MAX_FILES", 10_000)) {
            assertSubmissionOK(MultipartFormDataParserAction.instance(), 1000, 10, 10);
            ex = assertSubmissionThrows(MultipartFormDataParserAction.instance(), 10_000, 10, 10, FileUploadFileCountLimitException.class);
            assertThat(ex.getMessage(), containsString(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        }
        ex = assertSubmissionThrows(MultipartFormDataParserAction.instance(), 10, 1000, 20, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES"));
        ex = assertSubmissionThrows(MultipartFormDataParserAction.instance(), 1000, 10, 10, FileUploadFileCountLimitException.class);
        assertThat(ex.getMessage(), containsString(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILES"));
    }

    @Test
    void tooLargeFilesParser() throws Exception {
        assertSubmissionOK(MultipartFormDataParserAction.instance(), 1, 50, 10 * 1024 * 1024);
        try (FieldValue v = withStaticField(MultipartFormDataParser.class, "FILEUPLOAD_MAX_FILE_SIZE", 1024 * 1024)) {
            assertSubmissionOK(MultipartFormDataParserAction.instance(), 200, 100, 1024);
            ServletException ex = assertSubmissionThrows(MultipartFormDataParserAction.instance(), 1, 50, 10 * 1024 * 1024, FileUploadByteCountLimitException.class);
            assertThat(ex.getMessage(), containsString(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_FILE_SIZE"));
        }
        assertSubmissionOK(MultipartFormDataParserAction.instance(), 1, 50, 10 * 1024 * 1024);
    }

    @Test
    void tooLargeSubmissionParser() throws Exception {
        assertSubmissionOK(MultipartFormDataParserAction.instance(), 1, 50, 10 * 1024 * 1024);
        try (FieldValue v = withStaticField(MultipartFormDataParser.class, "FILEUPLOAD_MAX_SIZE", 1024 * 1024)) {
            assertSubmissionOK(MultipartFormDataParserAction.instance(), 200, 100, 1024);
            ServletException ex = assertSubmissionThrows(MultipartFormDataParserAction.instance(), 1, 50, 10 * 1024 * 1024, FileUploadSizeException.class);
            assertThat(ex.getMessage(), containsString(MultipartFormDataParser.class.getName() + ".FILEUPLOAD_MAX_SIZE"));
        }
        assertSubmissionOK(MultipartFormDataParserAction.instance(), 1, 50, 10 * 1024 * 1024);
    }

    // HTTP needs CRLF
    private static void println(PrintWriter pw, String s) {
        pw.print(s + "\r\n");
    }

    private static Object getStaticFieldValue(Class<?> clazz, String field) throws IllegalAccessException, NoSuchFieldException {
        final Field declaredField = clazz.getDeclaredField(field);
        declaredField.setAccessible(true);
        return declaredField.get(null);
    }

    private static void setStaticFieldValue(Class<?> clazz, String field, Object value) throws IllegalAccessException, NoSuchFieldException {
        final Field declaredField = clazz.getDeclaredField(field);
        declaredField.setAccessible(true);
        declaredField.set(null, value);
    }

    private static FieldValue withStaticField(Class<?> clazz, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
        return new FieldValue(clazz, field, value);
    }

    private static class FieldValue implements AutoCloseable {
        private final Class<?> clazz;
        private final String field;
        private final Object oldValue;

        private FieldValue(Class<?> clazz, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
            this.clazz = clazz;
            this.field = field;
            oldValue = getStaticFieldValue(clazz, field);
            setStaticFieldValue(clazz, field, value);
        }

        @Override
        public void close() throws Exception {
            setStaticFieldValue(clazz, field, oldValue);
        }
    }

    private <T extends Exception> ServletException assertSubmissionThrows(FileUploadAction<T> endpoint, int files, int other, int fileSize, Class<? extends T> expected) throws IOException {
        final ServletException actual = testSubmission(endpoint, files, other, fileSize, expected);
        assertEquals(expected, actual.getCause().getClass());
        return actual;
    }

    private <T extends Exception> void assertSubmissionOK(FileUploadAction<T> endpoint, int files, int other, int fileSize) throws IOException {
        assertNull(testSubmission(endpoint, files, other, fileSize, null));
    }

    private <T extends Exception> ServletException testSubmission(FileUploadAction<T> endpoint, int files, int other, int fileSize, Class<? extends T> expected) throws IOException {
        endpoint.setExpectedWrapped(expected);
        final JenkinsRule.WebClient wc = j.createWebClient();
        final URL url = wc.createCrumbedUrl(endpoint.getUrlName() + "/submitMultipart");
        WebRequest request = new WebRequest(url, HttpMethod.POST);
        final String boundary = "---------------------------" + System.nanoTime();
        request.setAdditionalHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeMultipartFormDataBody(baos, boundary, files, other, fileSize);
        request.setRequestBody(baos.toString());
        wc.getPage(request);
        return endpoint.getActual();
    }

    private static void writeMultipartFormDataBody(OutputStream os, String boundary, int files, int other, int fileSize) throws IOException {
        try (OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8); PrintWriter pw = new PrintWriter(osw)) {
            final Random random = new Random();
            while (files + other > 0) {
                println(pw, "--" + boundary);
                if (files > other) {
                    println(pw, "Content-Disposition: form-data; name=\"file" + files + "\"; filename=\"file" + files + "\"");
                    println(pw, "Content-Type: application/octet-stream");
                    println(pw, "");
                    pw.flush();
                    byte[] content = new byte[fileSize];
                    random.nextBytes(content);
                    os.write(content);
                    os.flush();
                    println(pw, "");
                    files--;
                } else {
                    println(pw, "Content-Disposition: form-data; name=\"field" + other + "\"");
                    println(pw, "");
                    println(pw, "Value " + random.nextLong());
                    other--;
                }
            }
            println(pw, "--" + boundary + "--");
        }
    }

    public abstract static class FileUploadAction<T extends Exception> implements RootAction {
        protected Class<? extends T> expectedWrapped;
        protected Throwable actualWrapped;
        protected ServletException actual;

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return getClass().getSimpleName();
        }

        @POST
        public HttpResponse doSubmitMultipart(StaplerRequest2 req) throws FileUploadException, ServletException, IOException {
            if (expectedWrapped == null) {
                actualWrapped = null;
                actual = null;
                return processMultipartAndUnwrap(req);
            } else {
                actualWrapped = assertThrows(expectedWrapped, () -> processMultipartAndUnwrap(req));

                // The client might still be sending us more of the request, but we have had enough of it already and
                // have decided to stop processing it. Drain the read end of the socket so that the client can finish
                // sending its request in order to read the response we are about to provide.
                try (OutputStream os = OutputStream.nullOutputStream()) {
                    req.getInputStream().transferTo(os);
                }

                return HttpResponses.ok();
            }
        }

        private HttpResponse processMultipartAndUnwrap(StaplerRequest2 req) throws FileUploadException, ServletException, IOException {
            try {
                return processMultipart(req);
            } catch (ServletException ex) {
                // unwrap
                actual = ex;
                final Throwable cause = ex.getCause();
                if (cause instanceof FileUploadException) {
                    throw (FileUploadException) cause;
                }
                throw ex;
            }
        }

        protected abstract HttpResponse processMultipart(StaplerRequest2 req) throws ServletException, IOException;

        public void setExpectedWrapped(Class<? extends T> expectedWrapped) {
            this.expectedWrapped = expectedWrapped;
        }

        public Throwable getActualWrapped() {
            return actualWrapped;
        }

        public ServletException getActual() {
            return actual;
        }
    }

    @TestExtension
    public static class StaplerRequest2FormAction extends FileUploadAction<FileUploadException> {
        public static StaplerRequest2FormAction instance() {
            return ExtensionList.lookupSingleton(StaplerRequest2FormAction.class);
        }

        protected HttpResponse processMultipart(StaplerRequest2 req) throws ServletException, IOException {
            req.getFileItem2("any-name");
            return HttpResponses.ok();
        }
    }

    @TestExtension
    public static class MultipartFormDataParserAction extends FileUploadAction<FileUploadException>  {
        public static MultipartFormDataParserAction instance() {
            return ExtensionList.lookupSingleton(MultipartFormDataParserAction.class);
        }

        protected HttpResponse processMultipart(StaplerRequest2 req) throws ServletException {
            new MultipartFormDataParser(req);
            return HttpResponses.ok();
        }
    }
}
