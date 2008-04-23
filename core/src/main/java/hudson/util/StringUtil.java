package hudson.util;

import java.util.Collection;
import java.util.Iterator;

public class StringUtil {

    /**
      * Takes a collection of string tokens and converts into separator-separated string.
      *
      * @param collection The array of strings input.
      * @param separator The string separator.
      * @return A string containing tokens separated by separator.
      */
     public static final String collectionToString(Collection<String> collection, String separator)
     {
         if (collection == null)
             return "";
         
         StringBuilder sb = new StringBuilder();         
         int i = 0;
         for (String string : collection) {
             if (i > 0) {
                 sb.append(separator);
             }
             sb.append(string);
             i++;
         }
         
         return sb.toString();
     }
}
