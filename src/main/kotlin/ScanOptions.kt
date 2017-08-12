import org.apache.commons.cli.*
import org.apache.commons.cli.Options

data class ScanOptions(
        val repositoryPath: String,
        val sinceRevision: String?,
        val untilRevision: String?,
        val propertiesFile: String,
        val changePropertiesAtRevisions: Map<String, String>,
        val analyseEvery: Int,
        val revisionFile: String?)

/**
 * Saves command line arguments to ScanOptions data object
 */
fun parseOptions(args: Array<String>): ScanOptions {
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
    val sinceRevisionOption = Option.builder("s")
            .longOpt(OPT_SINCE)
            .numberOfArgs(1)
            .required(false)
            .type(String::class.java)
            .desc("analyse only revisions since this commit")
            .build()

    val OPT_UNTIL = "until"
    val untilRevisionOption = Option.builder("u")
            .longOpt(OPT_UNTIL)
            .numberOfArgs(1)
            .required(false)
            .type(String::class.java)
            .desc("analyse only revisions until this commit (included)")
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
                    "\n(e.g. --$OPT_CHANGE properties1,properties2 hash1,hash2)")
            .build()

    val OPT_EVERY = "every"
    val scanEveryOption = Option.builder("e")
            .longOpt(OPT_EVERY)
            .numberOfArgs(1)
            .required(false)
            .type(Int::class.java)
            .desc("scan only every <arg> revision " +
                    "\n(e.g. --$OPT_EVERY 10)")
            .build()

    val OPT_FILE = "file"
    val revisionFileOption = Option.builder("f")
            .longOpt(OPT_FILE)
            .numberOfArgs(1)
            .required(false)
            .type(String::class.java)
            .desc("scan only revisions specified in <arg> file (each row in file must be a revision hash)")
            .build()

    val options = Options()
    options.addOption(repositoryOption)
    options.addOption(sinceRevisionOption)
    options.addOption(untilRevisionOption)
    options.addOption(propertiesFileOption)
    options.addOption(changePropertiesFileOption)
    options.addOption(scanEveryOption)
    options.addOption(revisionFileOption)

    try {
        val parser = DefaultParser()
        val arguments = parser.parse(options, args)
        val repositoryPath = arguments.getParsedOptionValue(OPT_REPOSITORY).toString()
        val sinceRevision = arguments.getParsedOptionValue(OPT_SINCE)?.toString()
        val untilRevision = arguments.getParsedOptionValue(OPT_UNTIL)?.toString()
        val propertiesFile = arguments.getParsedOptionValue(OPT_PROPERTIES)?.toString() ?: "sonar.properties"
        val analyseEvery = (arguments.getOptionValue(OPT_EVERY) ?: "1").toIntOrNull() ?: -1
        if (analyseEvery < 1)
            throw ParseException("--$OPT_EVERY: Analyse every revision should be a number greater or equal to 1")
        val revisionFile = arguments.getParsedOptionValue(OPT_FILE)?.toString()
        var changePropertiesAtRevisions = mapOf<String, String>()
        if (arguments.hasOption(OPT_CHANGE)) {
            val changeProperties = arguments.getOptionValues(OPT_CHANGE)[0].split(",")
            val changeRevisions = arguments.getOptionValues(OPT_CHANGE)[1].split(",")
            if (changeProperties.size != changeRevisions.size)
                throw ParseException("--$OPT_CHANGE: Each changed property file should correspond to a revision hash.")
            if (changeProperties.contains(""))
                throw ParseException("--$OPT_CHANGE: Change properties filename should not be empty.")
            changePropertiesAtRevisions = changeRevisions.zip(changeProperties).toMap()
        }
        return ScanOptions(
                repositoryPath,
                sinceRevision,
                untilRevision,
                propertiesFile,
                changePropertiesAtRevisions,
                analyseEvery,
                revisionFile)

    } catch (e: ParseException) {
        println("Please check the input parameters:")
        println(e.message)
        println()
        showHelp(options)
        throw Exception()
    }
}

private fun showHelp(options: Options) {
    val formatter = HelpFormatter()
    formatter.printHelp("sonar-history-scanner.jar", options)
}