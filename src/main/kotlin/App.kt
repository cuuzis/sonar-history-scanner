import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.InputStreamReader
import java.io.BufferedReader
import org.apache.commons.lang3.SystemUtils
import java.util.*
import java.time.temporal.ChronoUnit
import java.lang.ProcessBuilder.Redirect



fun main(args: Array<String>) {
    val scanOptions = parseOptions(args)
    if (scanOptions != null) {
        //val git = cloneRemoteRepository(repositoryURL)
        val git = openLocalRepository(scanOptions.repositoryPath)
        try {
            analyseAllRevisions(git, scanOptions)
        } finally {
            git.close()
        }
    }
}

fun analyseAllRevisions(git: Git, scanOptions: ScanOptions) {
    //println("Log is written to ${git.repository.directory.parent}/../full-log.out")
    var propertiesFileName = scanOptions.propertiesFile
    var changeIdx = 0
    val logEntries = git.log()
            .call()
    for (log in logEntries.reversed()) {
        val logDate = Instant.ofEpochSecond(log.commitTime.toLong())
        val logHash = log.name
        if (scanOptions.changeRevisions.size > changeIdx)
            if (logHash == scanOptions.changeRevisions.get(changeIdx)) {
                propertiesFileName = scanOptions.changeProperties.get(changeIdx)
                changeIdx++
            }
        if (hasReached(logHash, scanOptions.startFromRevision)) {
            val logDateFormatted = getSonarDate(logDate)
            print("Analysing revision: $logDateFormatted $logHash .. ")

            checkoutFromCmd(logHash, git)

            val scannerCmd: String
            if (SystemUtils.IS_OS_WINDOWS)
                scannerCmd = "sonar-scanner.bat"
            else
                scannerCmd = "sonar-scanner"
            val pb = ProcessBuilder(scannerCmd,
                    "-Dproject.settings=$propertiesFileName",
                    "-Dsonar.projectDate=$logDateFormatted")
            pb.directory(File(git.repository.directory.parent))
            val logFile = File("${git.repository.directory.parent}/../full-log.out")
            pb.redirectErrorStream(true)
            pb.redirectOutput(Redirect.appendTo(logFile))
            val p = pb.start()
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

fun checkoutFromCmd(logHash: String, git: Git) {
    val pb = ProcessBuilder("git","checkout","-f",logHash)
    pb.directory(File(git.repository.directory.parent))
    val logFile = File("${git.repository.directory.parent}/../full-log.out")
    pb.redirectErrorStream(true)
    pb.redirectOutput(Redirect.appendTo(logFile))
    val p = pb.start()
    val returnCode = p.waitFor()
    val reader = BufferedReader(InputStreamReader(p.inputStream))
    val allText = reader.use(BufferedReader::readText)
    print(allText)
    if (returnCode != 0)
        throw Exception("git checkout error")
}

/*
Formats timestamp for sonar-scanner parameter
If the timestamp is incorrect (older than a previous analysis or over a month younger)
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