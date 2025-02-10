package jenkins.search;

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

    @Extension
    class UnclassifiedSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return "Other";
        }
    }

    @Extension
    class JobSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return "Projects";
        }
    }

    @Extension
    class ComputerSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return "Computers";
        }
    }

    @Extension
    class ViewSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return "Views";
        }
    }

    @Extension
    class UserSearchGroup implements SearchGroup {

        @Override
        public String getDisplayName() {
            return "Users";
        }
    }
}
