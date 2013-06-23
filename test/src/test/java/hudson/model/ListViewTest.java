/*
 * The MIT License
 *
 * Copyright 2013 lucinka.
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
package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import hudson.matrix.MatrixProject;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.views.WeatherColumn;
import java.io.IOException;
import java.util.List;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;

/**
 *
 * @author lucinka
 */
public class ListViewTest extends HudsonTestCase{
    
     public void testGetItems() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "free");
        MatrixProject job2 = jenkins.createProject(MatrixProject.class, "matrix");
        FreeStyleProject job3 = jenkins.createProject(FreeStyleProject.class, "not-contained");
        view.jobNames.add(job1.getDisplayName());
        view.jobNames.add(job2.getDisplayName());
        assertTrue("View " + view.getDisplayName() + " should contains job " + job1.getDisplayName(), view.getItems().contains(job1));
        assertTrue("View " + view.getDisplayName() + " should contains job " + job2.getDisplayName(), view.getItems().contains(job2));
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job3.getDisplayName(), view.getItems().contains(job3));
        
    }
     
    private void setPattern(String pattern, String nameView) throws Exception{
        HtmlForm form = new WebClient().goTo("view/" + nameView + "/configure").getFormByName("viewConfig");
        form.getInputByName("useincluderegex").setChecked(true);
        form.getInputByName("includeRegex").setValueAttribute(pattern);
        submit(form);
    }
     
    public void testGetItemsByPattern() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "job1");
        MatrixProject job2 = jenkins.createProject(MatrixProject.class, "job2");
        FreeStyleProject job3 = jenkins.createProject(FreeStyleProject.class, "not-contained");
        setPattern("job.*", "foo");
        assertTrue("View " + view.getDisplayName() + " should contains job " + job1.getDisplayName(), view.getItems().contains(job1));
        assertTrue("View " + view.getDisplayName() + " should contains job " + job2.getDisplayName(), view.getItems().contains(job2));
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job3.getDisplayName(), view.getItems().contains(job3));
        
    }
    
    public void testContains() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "job1");
        FreeStyleProject job2 = jenkins.createProject(FreeStyleProject.class, "not-contained");
        setPattern("job.*", "foo");
        assertTrue("View " + view.getDisplayName() + " should contains job " + job1.getDisplayName(), view.getItems().contains(job1));
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job2.getDisplayName(), view.getItems().contains(job2));
    }
    
    public void testAddItem() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "job1");
        MatrixProject job2 = jenkins.createProject(MatrixProject.class, "job2");
        FreeStyleProject job3 = jenkins.createProject(FreeStyleProject.class, "not-contained");
        setPattern("job.*", "foo");
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job3.getDisplayName(), view.getItems().contains(job3));
        view.add(job3);
        assertTrue("View " + view.getDisplayName() + " should contains job " + job3.getDisplayName(), view.getItems().contains(job3));
        
    }
    
    public void testJobRename() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "job1");
        FreeStyleProject job3 = jenkins.createProject(FreeStyleProject.class, "not-contained");
        setPattern("job.*", "foo");
        job1.renameTo("renamed");
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job1.getDisplayName(), view.getItems().contains(job1));
        job1.renameTo("job1");
        assertTrue("View " + view.getDisplayName() + " should contains job " + job1.getDisplayName(), view.getItems().contains(job1));
        
    }
    
    public void testDoAddJobToView() throws IOException, SAXException, Exception{
        jenkins.setCrumbIssuer(null);
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "job");
        assertFalse("View " + view.getDisplayName() + " should contains job " + job1.getDisplayName(), view.getItems().contains(job1));
        PostMethod m = new PostMethod();
        m.setURI(new URI(getURL().toString() + "/view/foo/addJobToView",true));
        m.addParameter("name", "job");
        HttpClient client = new HttpClient();
        client.executeMethod(m);
        assertTrue("View " + view.getDisplayName() + " should contains job " + job1.getDisplayName(), view.getItems().contains(job1));
    }
    
    public void testDoCreateItem() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        HtmlForm form = new WebClient().goTo("view/" + view.getDisplayName() + "/newJob").getFormByName("createItem");
        form.getInputsByValue("hudson.model.FreeStyleProject").get(0).setChecked(true);
        form.getInputByName("name").setValueAttribute("job");
        submit(form);
        Item item = jenkins.getItem("job");
        assertTrue("View " + view.getDisplayName() + " should not contains job " + item.getDisplayName(), view.getItems().contains(item));
    }
    
    public void testGetIncludeRegex() throws Exception{
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        setPattern("job.*", "foo");
        assertTrue("View " + view.getDisplayName() + " should contains regex pattern job.*", view.getIncludeRegex().equals("job.*"));
    }
    
    public void testGetStatusFilter() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        assertTrue("View " + view.getDisplayName() + " should not have status filter.", view.getStatusFilter()==null);
        HtmlForm form = new WebClient().goTo("view/foo/configure").getFormByName("viewConfig");
        form.getSelectByName("statusFilter").getOptionByValue("1").setSelected(true);
        submit(form);
        assertTrue("View " + view.getDisplayName() + " should have status filter: enabled jobs only.", view.getStatusFilter().booleanValue()==true);
    }
    
    public void testDoRemoveJobFromView() throws Exception {
        jenkins.setCrumbIssuer(null);
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        FreeStyleProject job1 = jenkins.createProject(FreeStyleProject.class, "job");
        view.jobNames.add(job1.getDisplayName());
        assertTrue("View " + view.getDisplayName() + " should contains job " + job1.getDisplayName(), view.getItems().contains(job1));
        PostMethod m = new PostMethod();
        m.setURI(new URI(getURL().toString() + "/view/foo/removeJobFromView",true));
        m.addParameter("name", "job");
        HttpClient client = new HttpClient();
        client.executeMethod(m);
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job1.getDisplayName(), view.getItems().contains(job1));
    
    }
    
    public void testGetColumns() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);
        HtmlForm form = new WebClient().goTo("view/foo/configure").getFormByName("viewConfig");    
        List<HtmlElement> elements = form.getByXPath("//button[text()='Add column']");
        elements.get(0).click();
        elements = form.getByXPath("//a[text()='Weather']");
        elements.get(0).click();
        submit(form);
        assertTrue("View " + view.getDisplayName() + " should have weather columns.", view.getColumns().get(WeatherColumn.class)!=null);        
    }
    
}
