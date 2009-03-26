// run unit tests

ant.project.setBaseDir(project.basedir)
ser=new File(project.basedir,"target/cobertura.ser"); // store cobertura data file in a module-specific location

cob = new Cobertura(project,maven,ant,ser);

cob.instrument([])
cob.runTests()
cob.report([])
cob.makeBuildFailIfTestFail();
