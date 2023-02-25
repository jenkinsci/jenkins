package hudson.cli.client;

import java.io.File;
import java.io.FileNotFoundException;

public class Messages extends Exception{

    public static String CLI_NoSuchFileExists(File file) throws FileNotFoundException {
        return "file doesn't exist" + file.getName();
    }

    public static String CLI_Usage (){
        return "bad";
    }

    public static String CLI_NoURL() {
        return "wrong url";
    }

    public static String CLI_BadAuth() {
        return "The username or password you entered is incorrect";
    }
}
