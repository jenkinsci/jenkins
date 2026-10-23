package hudson.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CollectionSearchIndexTest {

    @Test
    void suggestIsCaseInsensitive() {
        SearchableModelObject item = item("CI-Integration");
        SearchableModelObject other = item("deploy");
        CollectionSearchIndex<SearchableModelObject> index = index(List.of(item, other));

        assertEquals(List.of(item), suggest(index, "inTEGration"));
        assertEquals(List.of(), suggest(index, "missing"));
    }

    @Test
    void suggestIsCaseInsensitiveIndependentOfDefaultLocale() {
        SearchableModelObject ciIntegration = item("CI-Integration");
        SearchableModelObject infraBuild = item("infra-build");
        SearchableModelObject other = item("deploy");
        CollectionSearchIndex<SearchableModelObject> index = index(List.of(ciIntegration, infraBuild, other));

        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(List.of(ciIntegration), suggest(index, "integration"));
            assertEquals(List.of(infraBuild), suggest(index, "INFRA"));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static List<SearchItem> suggest(SearchIndex index, String token) {
        List<SearchItem> result = new ArrayList<>();
        index.suggest(token, result);
        return result;
    }

    private static SearchableModelObject item(String displayName) {
        SearchableModelObject item = mock(SearchableModelObject.class);
        when(item.getDisplayName()).thenReturn(displayName);
        return item;
    }

    private static CollectionSearchIndex<SearchableModelObject> index(Collection<SearchableModelObject> items) {
        return new CollectionSearchIndex<>() {
            @Override
            protected SearchItem get(String key) {
                return null;
            }

            @Override
            protected Collection<SearchableModelObject> all() {
                return items;
            }
        };
    }
}
