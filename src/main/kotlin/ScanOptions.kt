import org.apache.commons.cli.*
import org.apache.commons.cli.Options

data class ScanOptions(
        val repositoryPath: String,
        val startFromRevision: String,
        val propertiesFile: String,
        val changeProperties: List<String> = ArrayList<String>(),
        val changeRevisions: List<String> = ArrayList<String>(),
        val analyzeEvery: Int = 1)

fun parseOptions(args: Array<String>): ScanOptions? {
    val OPT_REPOSITORY = "git"
    val repositoryOption = Option.builder("g")
            .longOpt(OPT_REPOSITORY)
            .numberOfArgs(1)
            .required(true)
            .type(String::class.java)
            .desc("path to project's git repository (mandatory)" +
                    "\n(e.g. \"C:\\...\\project1\\.git\")")
            .build()

    val OPT_SINCE = "since"
    val firstRevisionOption = Option.builder("s")
            .longOpt(OPT_SINCE)
            .numberOfArgs(1)
            .required(false)
            .type(String::class.java)
            .desc("analyse only revisions since this commit")
            .build()

    val OPT_PROPERTIES = "properties"
    val propertiesFileOption = Option.builder("p")
            .longOpt(OPT_PROPERTIES)
            .numberOfArgs(1)
            .required(false)
            .type(String::class.java)
            .desc("specify sonar scanner properties filename" +
                    "\n(default is \"sonar.properties\")")
            .build()

    val OPT_CHANGE = "change"
    val changePropertiesFileOption = Option.builder("c")
            .longOpt(OPT_CHANGE)
            .numberOfArgs(2)
            .required(false)
            .type(String::class.java)
            .desc("use different properties since this revision" +
                    "\n<arg0> is sonar.properties files, separated by commas" +
                    "\n<arg1> is revisions to start using these property files from, separated by commas" +
                    "\n(e.g. -$OPT_CHANGE properties1,properties2 hash1,hash2)")
            .build()

    val OPT_EVERY = "every"
    val scanEveryOption = Option.builder("e")
            .longOpt(OPT_EVERY)
            .numberOfArgs(1)
            .required(false)
            .type(Int::class.java)
            .desc("scan only every <arg0> revision " +
                    "\n(e.g. -$OPT_EVERY 10)")
            .build()

    val options = Options()
    options.addOption(repositoryOption)
    options.addOption(firstRevisionOption)
    options.addOption(propertiesFileOption)
    options.addOption(changePropertiesFileOption)
    options.addOption(scanEveryOption)

    try {
        val parser = DefaultParser()
        val cmdLine = parser.parse(options, args)
        val repositoryPath = cmdLine.getParsedOptionValue(OPT_REPOSITORY).toString()
        val startFromRevision =
                if (cmdLine.hasOption(OPT_SINCE))
                    cmdLine.getParsedOptionValue(OPT_SINCE).toString()
                else
                    ""
        val propertiesFile =
                if (cmdLine.hasOption(OPT_PROPERTIES))
                    cmdLine.getParsedOptionValue(OPT_PROPERTIES).toString()
                else
                    "sonar.properties"
        val analyseEvery =
                if (cmdLine.hasOption(OPT_EVERY)) {
                    try {
                        Integer.valueOf(cmdLine.getOptionValue(OPT_EVERY))
                    } catch (e: Exception) {
                        -1
                    }
                } else
                    1
        if (analyseEvery < 1)
            throw Exception("-$OPT_EVERY Analyse every revision should be a number greater or equal to 1")
        if (cmdLine.hasOption(OPT_CHANGE)) {
            val changeProperties = cmdLine.getOptionValues(OPT_CHANGE)[0].split(",")
            val changeRevisions = cmdLine.getOptionValues(OPT_CHANGE)[1].split(",")
            if (changeProperties.size != changeRevisions.size)
                throw ParseException("-$OPT_CHANGE Each changed property file should correspond to a revision hash.")
            for (filename in changeProperties)
                if (filename == "")
                    throw ParseException("-$OPT_CHANGE Change properties filename should not be empty.")
            return ScanOptions(
                    repositoryPath,
                    startFromRevision,
                    propertiesFile,
                    changeProperties,
                    changeRevisions,
                    analyzeEvery = analyseEvery)
        } else {
            return ScanOptions(
                    repositoryPath,
                    startFromRevision,
                    propertiesFile,
                    analyzeEvery = analyseEvery)
        }

    } catch (e: ParseException) {
        println("Please check the input parameters: \n${e.message}")
        showHelp(options)
        return null
    }
}

private fun showHelp(options: Options) {
    val formatter = HelpFormatter()
    formatter.printHelp("sonar-history-scanner.jar", options)
}