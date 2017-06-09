import org.apache.commons.cli.*
import org.apache.commons.cli.Options

data class ScanOptions(
        val repositoryPath: String,
        val startFromRevision: String,
        val propertiesFile: String,
        val changeProperties: List<String> = ArrayList<String>(),
        val changeRevisions: List<String> = ArrayList<String>())

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

    val options = Options()
    options.addOption(repositoryOption)
    options.addOption(firstRevisionOption)
    options.addOption(propertiesFileOption)
    options.addOption(changePropertiesFileOption)

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
        if (cmdLine.hasOption(OPT_CHANGE)) {
            val changeProperties = cmdLine.getOptionValues(OPT_CHANGE)[0].split(",")
            val changeRevisions = cmdLine.getOptionValues(OPT_CHANGE)[1].split(",")
            if (changeProperties.size != changeRevisions.size)
                throw ParseException("Each changed property file should correspond to a revision hash.")
            for (filename in changeProperties)
                if (filename == "")
                    throw ParseException("Change properties filename should not be empty.")
            return ScanOptions(
                    repositoryPath,
                    startFromRevision,
                    propertiesFile,
                    changeProperties,
                    changeRevisions)
        } else {
            return ScanOptions(
                    repositoryPath,
                    startFromRevision,
                    propertiesFile)
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