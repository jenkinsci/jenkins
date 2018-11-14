package jenkins.data;

import jenkins.data.tree.Mapping;
import jenkins.data.tree.Scalar;
import jenkins.data.tree.Sequence;
import jenkins.data.tree.TreeNode;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;

/**
 * @author Kohsuke Kawaguchi
 */
public class JsonSerializer extends Serializer {

    @Override
    protected TreeNode unstring(Reader in) throws IOException {
        Mapping mapping = new Mapping();
        String json = IOUtils.toString(in);
        JSONObject o = JSONObject.fromObject(json);
        return fromJSONObject(o);
    }


    @Override
    protected void stringify(TreeNode tree, Writer out) throws IOException {
        out.write(JSONSerializer.toJSON(tree.asMapping()).toString());
    }

    private TreeNode fromJSONObject(JSONObject o) {
        Mapping mapping = new Mapping();

        if (o.isNullObject() || o.isEmpty()) {
            // correct?
            return mapping;
        }

        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {

            String key = keys.next();
            Object value = o.get(key);

            if (value instanceof String) {
                mapping.put(key, new Scalar((String) value));
            } else if (value instanceof Boolean) {
                mapping.put(key, new Scalar((Boolean) value));
            } else if (value instanceof Number) {
                mapping.put(key, new Scalar((Number) value));
            } else if (value instanceof JSONObject) {
                mapping.put(key, fromJSONObject((JSONObject) value));
            } else if (value instanceof JSONArray) {
                // iterate
                mapping.put(key, fromJSONArray((JSONArray) value));

            }
        }
        return mapping;
    }

    private Sequence fromJSONArray(JSONArray array) {
        Sequence seq = new Sequence();
        for (Object e : array) {
            if (e instanceof String) {
                seq.add(new Scalar((String) e));
            } else if (e instanceof Boolean) {
                seq.add(new Scalar((Boolean) e));
            } else if (e instanceof Number) {
                seq.add(new Scalar((Number) e));
            } else if (e instanceof JSONObject) {
                seq.add(fromJSONObject((JSONObject) e));
            } else if (e instanceof JSONArray) {
                fromJSONArray((JSONArray) e);
            }
        }
        return seq;
    }

}
