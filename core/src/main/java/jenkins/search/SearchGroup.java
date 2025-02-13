package jenkins.search;

import static jenkins.search.Messages.SearchGroup_ComputerSearchGroup_DisplayName;
import static jenkins.search.Messages.SearchGroup_JobSearchGroup_DisplayName;
import static jenkins.search.Messages.SearchGroup_UnclassifiedSearchGroup_DisplayName;
import static jenkins.search.Messages.SearchGroup_UserSearchGroup_DisplayName;
import static jenkins.search.Messages.SearchGroup_ViewSearchGroup_DisplayName;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;

public interface SearchGroup extends ExtensionPoint, ModelObject {

    static ExtensionList<SearchGroup> all() {
        return ExtensionList.lookup(SearchGroup.class);
    }

    static @NonNull <T extends SearchGroup> T get(Class<T> type) {
        T category = all().get(type);
        if (category == null) {
            throw new AssertionError("Group not found. It seems the " + type + " is not annotated with @Extension and so not registered");
        }
        return category;
    }

    @Extension(ordinal = -1)
    class UnclassifiedSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return SearchGroup_UnclassifiedSearchGroup_DisplayName();
        }
    }

    @Extension(ordinal = 999)
    class JobSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return SearchGroup_JobSearchGroup_DisplayName();
        }
    }

    @Extension
    class ComputerSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return SearchGroup_ComputerSearchGroup_DisplayName();
        }
    }

    @Extension
    class ViewSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return SearchGroup_ViewSearchGroup_DisplayName();
        }
    }

    @Extension
    class UserSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return SearchGroup_UserSearchGroup_DisplayName();
        }
    }
}
