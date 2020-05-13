/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package jenkins.security.s2m;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import javax.inject.Inject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.FilePath;
import hudson.model.Slave;
import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class AdminFilePathFilterTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Inject
    AdminWhitelistRule rule;

    @Before
    public void setUp() {
        r.jenkins.getInjector().injectMembers(this);
        rule.setMasterKillSwitch(false);
    }

    @Issue({"JENKINS-27055", "SECURITY-358"})
    @Test
    public void matchBuildDir() throws Exception {
        File buildDir = r.buildAndAssertSuccess(r.createFreeStyleProject()).getRootDir();
        assertTrue(rule.checkFileAccess("write", new File(buildDir, "whatever")));
        assertFalse(rule.checkFileAccess("write", new File(buildDir, "build.xml")));
        // WorkflowRun:
        assertFalse(rule.checkFileAccess("write", new File(buildDir, "program.dat")));
        assertFalse(rule.checkFileAccess("write", new File(buildDir, "workflow/23.xml")));
    }
    
    @Test
    public void slaveCannotReadFileFromSecrets_butCanFromUserContent() throws Exception {
        Slave s = r.createOnlineSlave();
        FilePath root = r.jenkins.getRootPath();
        
        { // agent can read userContent folder
            FilePath rootUserContentFolder = root.child("userContent");
            FilePath rootTargetPublic = rootUserContentFolder.child("target_public.txt");
            rootTargetPublic.write("target_public", null);
            checkSlave_can_readFile(s, rootTargetPublic);
        }
        
        { // agent cannot read files inside secrets
            FilePath rootSecretFolder = root.child("secrets");
            FilePath rootTargetPrivate = rootSecretFolder.child("target_private.txt");
            rootTargetPrivate.write("target_private", null);
            
            checkSlave_cannot_readFile(s, rootTargetPrivate);
        }
        
        rule.setMasterKillSwitch(true);
    
        { // with the master kill switch activated, agent can read files inside secrets
            FilePath rootSecretFolder = root.child("secrets");
            FilePath rootTargetPrivate = rootSecretFolder.child("target_private.txt");
        
            checkSlave_can_readFile(s, rootTargetPrivate);
        }
    }
    
    private static class ReadFileS2MCallable implements Callable<String,Exception> {
        private final FilePath p;
        ReadFileS2MCallable(FilePath p) {
            this.p = p;
        }
        @Override
        public String call() throws Exception {
            assertTrue(p.isRemote());
            return p.readToString();
        }
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // simulate legacy Callable impls
            throw new NoSuchMethodError();
        }
    }
    
    @Test
    @Issue("SECURITY-788")
    public void slaveCannotUse_dotDotSlashStuff_toBypassRestriction() throws Exception {
        Slave s = r.createOnlineSlave();
        FilePath root = r.jenkins.getRootPath();
    
        { // use ../ to access a non-restricted folder
            FilePath rootUserContentFolder = root.child("userContent");
            FilePath rootTargetPublic = rootUserContentFolder.child("target_public.txt");
            rootTargetPublic.write("target_public", null);

            FilePath dotDotSlashTargetPublic = root.child("logs/target_public.txt");
            replaceRemote(dotDotSlashTargetPublic, "logs", "logs/../userContent");

            checkSlave_can_readFile(s, dotDotSlashTargetPublic);
        }
        
        { // use ../ to try to bypass the rules
            FilePath rootSecretFolder = root.child("secrets");
            FilePath rootTargetPrivate = rootSecretFolder.child("target_private.txt");
            rootTargetPrivate.write("target_private", null);
            
            FilePath dotDotSlashTargetPrivate = root.child("userContent/target_private.txt");
            replaceRemote(dotDotSlashTargetPrivate, "userContent", "userContent/../secrets");
        
            checkSlave_cannot_readFile(s, dotDotSlashTargetPrivate);
        }
    }
    
    @Test
    @Issue("SECURITY-788")
    public void slaveCannotUse_encodedCharacters_toBypassRestriction() throws Exception {
        Slave s = r.createOnlineSlave();
        FilePath root = r.jenkins.getRootPath();
        
        // \u002e is the Unicode of . and is interpreted directly by Java as .
        
        { // use ../ to access a non-restricted folder
            FilePath rootUserContentFolder = root.child("userContent");
            FilePath rootTargetPublic = rootUserContentFolder.child("target_public.txt");
            rootTargetPublic.write("target_public", null);
            
            FilePath dotDotSlashTargetPublic = root.child("logs/target_public.txt");
            replaceRemote(dotDotSlashTargetPublic, "logs", "logs/\u002e\u002e/userContent");
            
            checkSlave_can_readFile(s, dotDotSlashTargetPublic);
        }
        
        { // use ../ to try to bypass the rules
            FilePath rootSecretFolder = root.child("secrets");
            FilePath rootTargetPrivate = rootSecretFolder.child("target_private.txt");
            rootTargetPrivate.write("target_private", null);
            
            FilePath dotDotSlashTargetPrivate = root.child("userContent/target_private.txt");
            replaceRemote(dotDotSlashTargetPrivate, "userContent", "userContent/\u002e\u002e/secrets");
            
            checkSlave_cannot_readFile(s, dotDotSlashTargetPrivate);
        }
    }
    
    private void checkSlave_can_readFile(Slave s, FilePath target) throws Exception {
        // The agent can read file from userContent
        String content = s.getChannel().call(new ReadFileS2MCallable(target));
        // and the master can directly reach it
        assertEquals(target.readToString(), content);
    }
    
    private void checkSlave_cannot_readFile(Slave s, FilePath target) throws Exception {
        try {
            s.getChannel().call(new ReadFileS2MCallable(target));
            fail("Slave should not be able to read file in " + target.getRemote());
        } catch (IOException e){
            Throwable t = e.getCause();
            assertTrue(t instanceof SecurityException);
            SecurityException se = (SecurityException) t;
            StringWriter sw = new StringWriter();
            se.printStackTrace(new PrintWriter(sw));
            assertTrue(sw.toString().contains("agent may not read"));
        }
    }
    
    // to bypass the normalization done in constructor
    private void replaceRemote(FilePath p, String before, String after) throws Exception {
        Field field = FilePath.class.getDeclaredField("remote");
        field.setAccessible(true);
        String currentRemote = (String) field.get(p);
        String newRemote = currentRemote.replace(before, after);
        field.set(p, newRemote);
    }
}
