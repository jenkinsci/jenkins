/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package org.jvnet.hudson.test;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import org.apache.commons.digester.Digester;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Andrew Bayer
 */
public class ExtractChangeLogParser extends ChangeLogParser {
    @SuppressWarnings("rawtypes")
    @Override
    public ExtractChangeLogSet parse(AbstractBuild build, File changeLogFile) throws IOException, SAXException {
        if (changeLogFile.exists()) {
            FileInputStream fis = new FileInputStream(changeLogFile);
            ExtractChangeLogSet logSet = parse(build, fis);
            fis.close();
            return logSet;
        } else {
            return new ExtractChangeLogSet(build, new ArrayList<ExtractChangeLogEntry>());
        }
    }

    @SuppressWarnings("rawtypes")
    public ExtractChangeLogSet parse(AbstractBuild build, InputStream changeLogStream) throws IOException, SAXException {

        ArrayList<ExtractChangeLogEntry> changeLog = new ArrayList<ExtractChangeLogEntry>();

        Digester digester = new Digester();
        digester.setClassLoader(ExtractChangeLogSet.class.getClassLoader());
        digester.push(changeLog);
        digester.addObjectCreate("*/extractChanges/entry", ExtractChangeLogEntry.class);

        digester.addBeanPropertySetter("*/extractChanges/entry/zipFile");

        digester.addObjectCreate("*/extractChanges/entry/file",
                FileInZip.class);
        digester.addBeanPropertySetter("*/extractChanges/entry/file/fileName");
        digester.addSetNext("*/extractChanges/entry/file", "addFile");
        digester.addSetNext("*/extractChanges/entry", "add");

        digester.parse(changeLogStream);

        return new ExtractChangeLogSet(build, changeLog);
    }


    @ExportedBean(defaultVisibility = 999)
    public static class ExtractChangeLogEntry extends ChangeLogSet.Entry {
        private List<FileInZip> files = new ArrayList<FileInZip>();
        private String zipFile;

        public ExtractChangeLogEntry() {
        }

        public ExtractChangeLogEntry(String zipFile) {
            this.zipFile = zipFile;
        }

        public void setZipFile(String zipFile) {
            this.zipFile = zipFile;
        }

        @Exported
        public String getZipFile() {
            return zipFile;
        }

        @Override
        public void setParent(ChangeLogSet parent) {
            super.setParent(parent);
        }

        @Override
        public Collection<String> getAffectedPaths() {
            Collection<String> paths = new ArrayList<String>(files.size());
            for (FileInZip file : files) {
                paths.add(file.getFileName());
            }
            return paths;
        }

        @Override
        @Exported
        public User getAuthor() {
            return User.get("testuser");
        }

        @Override
        @Exported
        public String getMsg() {
            return "Extracted from " + zipFile;
        }

        public void addFile(FileInZip fileName) {
            files.add(fileName);
        }

        public void addFiles(Collection<FileInZip> fileNames) {
            this.files.addAll(fileNames);
        }
    }

    @ExportedBean(defaultVisibility = 999)
    public static class FileInZip {
        private String fileName = "";

        public FileInZip() {
        }

        public FileInZip(String fileName) {
            this.fileName = fileName;
        }

        @Exported
        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
    }

}
