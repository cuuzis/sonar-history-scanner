import org.apache.commons.cli.*
import org.apache.commons.cli.Options

object ScanOptions {

    var  startFromRevision = ""
    var  repositoryPath = ""
    var  propertiesFile = "sonar.properties"
    var  changeProperties = ArrayList<String>()
    var  changeRevisions = ArrayList<String>()

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
                            "\n(e.g. -$OPT_CHANGE properties1.properties,properties2.properties hash1,hash2)")
                    .build()

            val options = Options()
            options.addOption(repositoryOption)
            options.addOption(firstRevisionOption)
            options.addOption(propertiesFileOption)
            options.addOption(changePropertiesFileOption)

        try {
            val parser = DefaultParser()
            val cmdLine = parser.parse(options, args)
            repositoryPath = cmdLine.getParsedOptionValue(OPT_REPOSITORY).toString()
            if (cmdLine.hasOption(OPT_SINCE))
                startFromRevision = cmdLine.getParsedOptionValue(OPT_SINCE).toString()
            if (cmdLine.hasOption(OPT_PROPERTIES))
                propertiesFile = cmdLine.getParsedOptionValue(OPT_PROPERTIES).toString()
            if (cmdLine.hasOption(OPT_CHANGE)) {
                changeProperties.addAll(cmdLine.getOptionValues(OPT_CHANGE)[0].split(","))
                changeRevisions.addAll(cmdLine.getOptionValues(OPT_CHANGE)[1].split(","))
                if (changeProperties.size != changeRevisions.size)
                    throw ParseException("Each changed property file should correspond to a revision hash.")
                for (filename in changeProperties)
                    if (filename == "")
                        throw ParseException("Change properties filename should not be empty.")
            }
        } catch (e: ParseException) {
            println("Please check the input parameters: \n${e.message}")
            ScanOptions.showHelp(options)
            return null
        }
        return this
    }

    private fun showHelp(options: Options) {
        val formatter = HelpFormatter()
        formatter.printHelp("sonar-history-scanner.jar", options)
    }

}