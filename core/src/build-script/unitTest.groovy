// run unit tests
org.apache.maven.project.MavenProject p = project;

ant.project.setBaseDir(p.basedir)

ant.taskdef(resource:"tasks.properties")

new File("target/cobertura-classes").mkdirs();
ant."cobertura-instrument"(todir:"target/cobertura-classes") {
    fileset(dir:"target/classes");
}

ant.junit(fork:true, forkMode:"once", failureproperty:"failed", printsummary:true) {
    classpath {
        p.getTestClasspathElements().each{ pathelement(path:it) }
    }
    def reportDir = "target/surefire-reports";
    new File(p.basedir,reportDir).mkdirs();
    batchtest(todir:reportDir) {
        fileset(dir:"src/test/java") {
            include(name:"**/*Test.java")
        }
        formatter(type:"xml")
    }
    if(ant.project.getProperty("failed")!=null)
        throw new Exception("tests failed");
}
