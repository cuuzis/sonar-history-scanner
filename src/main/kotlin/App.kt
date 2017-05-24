import org.apache.commons.cli.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.InputStreamReader
import java.io.BufferedReader
import org.apache.commons.cli.DefaultParser
import org.apache.commons.lang3.SystemUtils
import java.util.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.temporal.ChronoUnit


fun main(args: Array<String>) {
    val gitRepoOption = Option.builder("g")
            .longOpt("git")
            .numberOfArgs(1)
            .required(true)
            .type(String::class.java)
            .desc("path to git repository")
            .build()

    val firstRevisionOption = Option.builder("s")
            .longOpt("since")
            .numberOfArgs(1)
            .required(false)
            .type(String::class.java)
            .desc("analyse only revisions since this")
            .build()

    val options = Options()
    options.addOption(firstRevisionOption)
    options.addOption(gitRepoOption)

    val parser = DefaultParser()
    try {
        val cmdLine = parser.parse(options, args)
        val repositoryPath = cmdLine.getParsedOptionValue("git").toString()
        val startFromHash =
                if (cmdLine.hasOption("since"))
                    cmdLine.getParsedOptionValue("since").toString()
                else
                    ""

        //val git = cloneRemoteRepository(repositoryURL)
        val git = openLocalRepository(repositoryPath)
        try {
            analyseAllRevisions(git, startFromHash)
        } finally {
            git.close()
        }
    } catch (e: ParseException) {
        println(e.message)
        showHelp(options)
    }
}

fun showHelp(options: Options) {
    val formatter = HelpFormatter()
    formatter.printHelp("sonar-history-scanner.jar", options)
}

fun analyseAllRevisions(git: Git, startFromHash: String) {
    val logEntries = git.log()
            .call()
    for (log in logEntries.reversed()) {
        val logDate = Instant.ofEpochSecond(log.commitTime.toLong())
        val logHash = log.name
        if (hasReached(logHash, startFromHash)) {
            val logDateFormatted = getSonarDate(logDate)
            print("Analysing revision: $logDateFormatted $logHash .. ")

            git.add()
                    .addFilepattern("sonar.properties")
                    .call()
            val stash = git.stashCreate()
                    .call()
            if (stash == null)
                throw Exception("Missing sonar.properties file")
            git.checkout()
                    .setForce(true)
                    .setName(git.repository.branch)
                    .setStartPoint(log)
                    .call()
            git.stashApply()
                    .setStashRef(stash.name)
                    .call()
            git.stashDrop()
                    .setStashRef(0)
                    .call()

            val loggingPath = Paths.get("${git.repository.directory.parent}/../sonar-scanner-logs/")
            if (!Files.exists(loggingPath))
                Files.createDirectories(loggingPath)
            val scannerCmd: String
            if (SystemUtils.IS_OS_WINDOWS)
                scannerCmd = "sonar-scanner.bat"
            else
                scannerCmd = "sonar-scanner"
            val p: Process = Runtime.getRuntime().exec(
                    scannerCmd + " -D project.settings=sonar.properties" +
                            " -D sonar.projectDate=$logDateFormatted" +
                            " >../sonar-scanner-logs/${logDateFormatted.subSequence(0,10)}-$logHash.out 2>&1",
                    null,
                    File(git.repository.directory.parent))
            val returnCode = p.waitFor()
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val allText = reader.use(BufferedReader::readText)
            print(allText)
            if (returnCode == 0)
                println("${Calendar.getInstance().time}: EXECUTION SUCCESS")
            else
                println("${Calendar.getInstance().time}: EXECUTION FAILURE, return code $returnCode")
        }
    }
}

/*
Formats timestamp for sonar-scanner parameter
If the timestamp is incorrect (older than a previous analysis or over a week younger)
then previous timestamp+1 is used
 */
var previousDate: Instant? = null
fun getSonarDate(logDate: Instant): String {
    val sonarDate: Instant
    if (previousDate != null && (previousDate!!.isAfter(logDate) || previousDate!!.plus(30, ChronoUnit.DAYS) < logDate)) {
        sonarDate = previousDate!!.plusSeconds(1)
        println("Date changed from:  $logDate")
    } else
        sonarDate = logDate
    previousDate = sonarDate
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    val result = formatter.format(sonarDate)
            .removeRange(22, 23) // removes colon from time zone (to post timestamp to sonarqube analysis)
    return result
}

var hasReached = false
fun  hasReached(hash: String, startFromHash: String): Boolean {
    if (startFromHash == "" || startFromHash == hash)
        hasReached = true
    return hasReached
}

fun openLocalRepository(repositoryPath: String): Git {
    val builder: FileRepositoryBuilder = FileRepositoryBuilder()
    try {
        val repository = builder.setGitDir(File(repositoryPath))
                .readEnvironment()
                .findGitDir()
                .build()
        val result = Git(repository)
        result.log()
                .call() //tests the repository
        return result
    } catch (e: Exception) {
        throw Exception("Could not open the repository ${repositoryPath}", e)
    }
}

fun cloneRemoteRepository(repositoryURL: String): Git {
    val localPath: File = createTempFile("TestGitRepo")
    if (!localPath.delete()) {
        throw Exception("Could not delete temporary file $localPath")
    }
    try {
        println("Cloning repository from $repositoryURL\n...")
        val result: Git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(localPath)
                .call()
        println("Repository cloned into ${result.repository.directory.parent}")
        return result
    } catch (e: Exception) {
        throw Exception("Could not clone the remote repository", e)
    }
}