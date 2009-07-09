#!/usr/bin/env groovy
/*
    Generate index from short/full class name to the javadoc page, in the form of .htaccess.
    This serves http://hudson-ci.org/javadoc/byShortName/

    Push it as scp .htaccess hudson-ci.org:~/www/hudson-ci.org/javadoc/byShortName
 */
index = new TreeMap();
base = new File("./target/site/apidocs");
base.eachFileRecurse { File f ->
    if(f.path.contains("-"))            return;   // non class files produced by javadoc
    if(!f.path.endsWith(".html"))       return;   // directories and others
    tail = f.path.substring(base.path.length()+1);

    shortClassName = f.name.substring(0,f.name.length()-5); // cut off ".html"
    fullClassName = tail.substring(0,tail.length()-5).replace('/','.');

    index[shortClassName] = tail;
    index[fullClassName] = tail;
}

index.each { k,v ->
    println "Redirect 302 /javadoc/byShortName/${k} https://hudson.dev.java.net/nonav/javadoc/index.html?${v}"
}
