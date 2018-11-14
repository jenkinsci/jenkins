package jenkins.data.tree;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */

public final class Mapping extends HashMap<String, TreeNode> implements TreeNode {

    public static final Mapping EMPTY = new Mapping();
    private Source source;

    public Mapping() {
        super();
    }

    public Mapping(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public Type getType() {
        return Type.MAPPING;
    }

    @Override
    public Mapping asMapping() {
        return this;
    }

    public Optional<TreeNode> getValue(Object key) {
        return Optional.ofNullable(super.get(key));
    }

    public void put(String key, String value) {
        super.put(key, new Scalar(value));
    }

    public void put(String key, Number value) {
        super.put(key, new Scalar(String.valueOf(value)));
    }

    public void put(String key, Boolean value) {
        super.put(key, new Scalar(String.valueOf(value)));
    }

    public void putIfNotNull(String key, TreeNode node) {
        if (node != null) super.put(key, node);
    }

    public void putIfNotEmpry(String key, Sequence seq) {
        if (!seq.isEmpty()) super.put(key, seq);
    }

    public String getScalarValue(String key) {
        return remove(key).asScalar().getValue();
    }

    public void setSource(Source source) {
        this.source = source;
    }

    @Override
    public Source getSource() {
        return source;
    }

    @Override
    public Mapping clone() {
        final Mapping clone = new Mapping();
        entrySet().stream().forEach(e -> clone.put(e.getKey(), e.getValue().clone()));
        return clone;
    }
}
