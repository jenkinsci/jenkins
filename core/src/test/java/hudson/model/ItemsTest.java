package hudson.model;

import org.junit.BeforeClass;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ItemsTest {

    private static ItemGroup root;
    private static ItemGroup foo;
    private static ItemGroup foo_bar;

    @BeforeClass
    public static void itemGroups() {
        root = mock(ItemGroup.class);
        when(root.getFullName()).thenReturn("");

        foo = mock(ItemGroup.class);
        when(foo.getFullName()).thenReturn("foo");

        foo_bar = mock(ItemGroup.class);
        when(foo_bar.getFullName()).thenReturn("foo/bar");

    }

    @Test
    public void getCanonicalName() {
        assertEquals("foo", Items.getCanonicalName(root, "foo"));
        assertEquals("foo", Items.getCanonicalName(root, "/foo"));
        assertEquals("foo/bar", Items.getCanonicalName(root, "foo/bar"));
        assertEquals("foo/bar", Items.getCanonicalName(foo, "bar"));
        assertEquals("bar", Items.getCanonicalName(foo, "/bar"));
        assertEquals("bar", Items.getCanonicalName(foo, "../bar"));
        assertEquals("foo/bar", Items.getCanonicalName(foo, "./bar"));
        assertEquals("foo/bar/baz/qux", Items.getCanonicalName(foo_bar, "baz/qux"));
        assertEquals("foo/baz/qux", Items.getCanonicalName(foo_bar, "../baz/qux"));

        try {
            Items.getCanonicalName(root, "..");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Illegal relative path '..' within context ''", ex.getMessage());
        }

        try {
            Items.getCanonicalName(foo, "../..");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Illegal relative path '../..' within context 'foo'", ex.getMessage());
        }

        try {
            Items.getCanonicalName(root, "foo/../..");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Illegal relative path 'foo/../..' within context ''", ex.getMessage());
        }
    }

    @Test
    public void computeRelativeNamesAfterRenaming() {
        assertEquals("meu,bu,zo", Items.computeRelativeNamesAfterRenaming("ga", "meu", "ga,bu,zo", root ));
        assertEquals("ga,bu,zo", Items.computeRelativeNamesAfterRenaming("ga", "meu", "ga,bu,zo", foo_bar ));
        assertEquals("meu,bu,zo", Items.computeRelativeNamesAfterRenaming("foo/ga", "foo/meu", "ga,bu,zo", foo ));

        assertEquals("/meu,/bu,/zo", Items.computeRelativeNamesAfterRenaming("ga", "meu", "/ga,/bu,/zo", root ));
        assertEquals("/meu,/bu,/zo", Items.computeRelativeNamesAfterRenaming("ga", "meu", "/ga,/bu,/zo", foo_bar ));

        assertEquals("../meu,../bu,../zo", Items.computeRelativeNamesAfterRenaming("ga", "meu", "../ga,../bu,../zo", foo ));
        assertEquals("../qux/ga,bu,zo", Items.computeRelativeNamesAfterRenaming("foo/baz", "foo/qux", "../baz/ga,bu,zo", foo_bar ));

        assertEquals("foo-renamed,foo_bar", Items.computeRelativeNamesAfterRenaming("foo", "foo-renamed", "foo,foo_bar", root ));

        // Handle moves too:
        assertEquals("../nue/dir/j", Items.computeRelativeNamesAfterRenaming("dir", "nue/dir", "../dir/j", foo));
        assertEquals("../dir/j", Items.computeRelativeNamesAfterRenaming("nue/dir", "dir", "../nue/dir/j", foo));
        assertEquals("../top2/dir/j", Items.computeRelativeNamesAfterRenaming("top1/dir", "top2/dir", "../top1/dir/j", foo));
        assertEquals("nue/dir/j", Items.computeRelativeNamesAfterRenaming("dir", "nue/dir", "dir/j", root));
        assertEquals("dir/j", Items.computeRelativeNamesAfterRenaming("nue/dir", "dir", "nue/dir/j", root));
        assertEquals("top2/dir/j", Items.computeRelativeNamesAfterRenaming("top1/dir", "top2/dir", "top1/dir/j", root));
        assertEquals("/nue/dir/j", Items.computeRelativeNamesAfterRenaming("dir", "nue/dir", "/dir/j", foo));
        assertEquals("/dir/j", Items.computeRelativeNamesAfterRenaming("nue/dir", "dir", "/nue/dir/j", foo));
        assertEquals("/top2/dir/j", Items.computeRelativeNamesAfterRenaming("top1/dir", "top2/dir", "/top1/dir/j", foo));
        assertEquals("sister", Items.computeRelativeNamesAfterRenaming("fooq", "foo", "sister", foo));
        assertEquals("/foo/sister", Items.computeRelativeNamesAfterRenaming("fooq", "foo", "/fooq/sister", foo));
    }

    @Test public void getRelativeNameFrom() {
        assertEquals("foo", Items.getRelativeNameFrom("foo", ""));
        assertEquals("foo/bar", Items.getRelativeNameFrom("foo/bar", ""));
        assertEquals("../bar", Items.getRelativeNameFrom("bar", "foo"));
        assertEquals("../baz", Items.getRelativeNameFrom("foo/baz", "foo/bar"));
        assertEquals("bar", Items.getRelativeNameFrom("foo/bar", "foo"));
        assertEquals(".", Items.getRelativeNameFrom("foo/bar", "foo/bar"));
        assertEquals("../..", Items.getRelativeNameFrom("foo", "foo/bar/baz"));
        assertEquals("bar/baz", Items.getRelativeNameFrom("foo/bar/baz", "foo"));
        assertEquals("../quux/hey", Items.getRelativeNameFrom("foo/bar/quux/hey", "foo/bar/baz"));
    }

}
