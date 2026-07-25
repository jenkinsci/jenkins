# Update the getComment() method to return the entire commit message
public abstract class ChangeLogSet {
    // ...

    public String getComment() {
        String comment = this.comment;
        if (comment != null && !comment.isEmpty()) {
            return comment;
        } else {
            // If the comment is empty, try to get the entire commit message from the commit object
            // This assumes that the commit object has a method to get the entire commit message
            Commit commit = this.getCommit();
            if (commit != null) {
                return commit.getFullMessage();
            } else {
                return "";
            }
        }
    }
}