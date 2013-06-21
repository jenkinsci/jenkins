package jenkins.plugins.ui_samples;

import hudson.Extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Toomas Römer
 */
@Extension
public class VariableFun extends UISample {
    @Override
    public String getDescription() {
        return "Having fun with variables in your groovy template.";
    }

    public List<SourceFile> getSourceFiles() {
        return Arrays.asList(
                new SourceFile("index.groovy"));
    }
    
    public Map<String, String> getMovieCast() {
      return new HashMap<String, String>(){{
        put("Christian Bale", "Bruce Wayne");
        put("Gary Oldman", "Commissioner Gordon");
        put("Tom Hardy", "Bane");
        put("Joseph Gordon-Levitt", "Blake");
        put("Anne Hathaway", "Selina");
        put("Marion Cotillard", "Miranda");
        put("Morgan Freeman", "Fox");
        put("Michael Caine", "Alfred");
      }};
    }
    
    public List<String> getCharacters() {
      List<String> rtrn = new ArrayList<String>();
      rtrn.addAll(getMovieCast().values());
      return rtrn;
    }
    
    public String getRandomString() {
      String[] arr = (String[])getMovieCast().values().toArray(new String[]{});
      return arr[(new Random()).nextInt(arr.length)];
    }
    
    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }
}
