// Usage: groovy move-l10n.groovy hudson/model/OldClass/old-view jenkins/model/NewClass/new-view 'Some\ Translatable\ Text'

def oldview = args[0];
def newview = args[1];
def key = args[2];

def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent;
def resDir = new File(scriptDir, 'src/main/resources');

def basename = new File(resDir, oldview).name;
for (p in new File(resDir, oldview).parentFile.listFiles()) {
    def n = p.name;
    if (n == "${basename}.properties" || n.startsWith("${basename}_") && n.endsWith(".properties")) {
        def lines = p.readLines('ISO-8859-1');
        def matches = lines.findAll({it.startsWith("${key}=")});
        if (!matches.isEmpty()) {
            def nue = new File(resDir, newview + n.substring(basename.length()));
            println("moving ${matches.size()} matches from ${n} to ${nue.name}");
            lines.removeAll(matches);
            p.withWriter('ISO-8859-1') {out ->
                lines.each {line -> out.writeLine(line)}
            }
            nue.withWriterAppend('ISO-8859-1') {out ->
                matches.each {line -> out.writeLine(line)}
            }
        }
    }
}
