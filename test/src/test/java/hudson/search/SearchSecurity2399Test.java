package hudson.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gargoylesoftware.htmlunit.Page;
import java.lang.reflect.Field;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class SearchSecurity2399Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-2399")
    @Test
    public void testSearchBound() throws Exception {

        final String projectName1 = "projectName1";
        final String projectName2 = "projectName2";
        final String projectName3 = "projectName3";

        j.createFreeStyleProject(projectName1);
        j.createFreeStyleProject(projectName2);
        j.createFreeStyleProject(projectName3);

        final JenkinsRule.WebClient wc = j.createWebClient();

        Page result = wc.goTo("search/suggest?query=projectName", "application/json");
        JSONArray suggestions = getSearchJson(result);
        assertEquals(3, suggestions.size());

        Field declaredField = Search.class.getDeclaredField("MAX_SEARCH_SIZE");
        declaredField.setAccessible(true);
        declaredField.set(null, 2);

        Page maximizedResult = wc.goTo("search/suggest?query=projectName", "application/json");
        JSONArray maximizedSuggestions = getSearchJson(maximizedResult);
        assertEquals(2, maximizedSuggestions.size());
    }

    private JSONArray getSearchJson(Page page) {
        assertNotNull(page);
        j.assertGoodStatus(page);
        String content = page.getWebResponse().getContentAsString();
        JSONObject jsonContent = (JSONObject) JSONSerializer.toJSON(content);
        assertNotNull(jsonContent);
        JSONArray jsonArray = jsonContent.getJSONArray("suggestions");
        assertNotNull(jsonArray);
        return jsonArray;
    }
}
