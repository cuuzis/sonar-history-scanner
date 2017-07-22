import org.apache.commons.lang3.SystemUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.FileReader
import java.util.*
import java.time.temporal.ChronoUnit
import java.lang.ProcessBuilder.Redirect



fun main(args: Array<String>) {
    val scanOptions = parseOptions(args)
    if (scanOptions != null) {
        var tempScanDirectory: File? = null
        val git =
                if (scanOptions.repositoryPath.startsWith("http")) {
                    tempScanDirectory = File("temp-scan-directory")
                    cloneRemoteRepository(scanOptions.repositoryPath, tempScanDirectory)
                } else {
                    openLocalRepository(scanOptions.repositoryPath)
                }
        try {
            analyseAllRevisions(git, scanOptions)
        } finally {
            git.close()
        }

        if (tempScanDirectory != null) {
            if (tempScanDirectory.deleteRecursively())
                println("Deleted $tempScanDirectory")
            else
                println("Could not delete $tempScanDirectory")
        }
    }
}

/*
Analyses all past revisions for the specified project.
Runs from the first revision or options.startFromRevision up to current revision of the git file.
 */
fun analyseAllRevisions(git: Git, scanOptions: ScanOptions) {
    //println("Log is written to ${git.repository.directory.parent}/../full-log.out")
    var sonarProperties = scanOptions.propertiesFile

    copyPropertyFiles(git, scanOptions)

    val revisionsToScan =
            if (scanOptions.revisionFile == "")
                mutableListOf<String>()
            else
                readLinesFromFile(scanOptions.revisionFile)
    
    var changeIdx = 0
    val logEntries = git.log()
            .call()
            .reversed()

    val logDatesRaw = mutableListOf<Instant>()
    for(log in logEntries)
        logDatesRaw.add(Instant.ofEpochSecond(log.commitTime.toLong()))
    val logDates = smoothDates(logDatesRaw)

    for ((index, value) in logEntries.withIndex()) {
        val logHash = value.name
        if (scanOptions.changeRevisions.size > changeIdx)
            if (logHash == scanOptions.changeRevisions[changeIdx]) {
                sonarProperties = scanOptions.changeProperties[changeIdx]
                changeIdx++
            }
        if (hasReached(logHash, scanOptions.startFromRevision, index)) {
            if ((index-reachedAt) % scanOptions.analyzeEvery == 0
                    && (revisionsToScan.isEmpty() || revisionsToScan.contains(logHash)) )  {
                val logDateRaw = Instant.ofEpochSecond(value.commitTime.toLong())
                val logDate = logDates[index]
                if (logDate != logDateRaw)
                    println("Date changed from $logDateRaw")

                val sonarDate = getSonarDate(logDate)
                print("Analysing revision: $sonarDate $logHash .. ")

                checkoutFromCmd(logHash, git)

                val scannerCmd: String
                if (SystemUtils.IS_OS_WINDOWS)
                    scannerCmd = "sonar-scanner.bat"
                else
                    scannerCmd = "sonar-scanner"
                val pb = ProcessBuilder(scannerCmd,
                        "-Dproject.settings=$sonarProperties",
                        "-Dsonar.projectDate=$sonarDate")
                pb.directory(File(git.repository.directory.parent))
                val logFile = File("${git.repository.directory.parent + File.separator}..${File.separator}full-log.out")
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
}

fun  copyPropertyFiles(git: Git, scanOptions: ScanOptions) {
    for (propertiesFile in scanOptions.changeProperties + scanOptions.propertiesFile) {
        File(propertiesFile).copyTo(File(git.repository.directory.parent + File.separator + propertiesFile))
    }
}

fun readLinesFromFile(file: String): List<String> {
    val result = mutableListOf<String>()
    BufferedReader(FileReader(file)).use { br ->
        do {
            val line = br.readLine()
            if (line != null)
                result.add(line)
        } while (line != null)
    }
    return result
}

/*
Checks out the revision with specified hash, using the command line
 */
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
* Formats timestamp for using it as a sonar-scanner parameter
*/
fun getSonarDate(logDate: Instant): String {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withLocale(Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
    var dateStr = formatter.format(logDate)
    dateStr = dateStr.replace("Z", "+00:00") // for UTC time zone
    return dateStr.removeRange(22, 23) // removes colon from time zone (to post timestamp to sonarqube analysis)
}

/*
Makes sure commit dates are in an increasing order for doing repeated analysis
Increases day offset until the last commit date does not get changed by smoothing
*/
fun smoothDates(logDates: List<Instant>): List<Instant> {
    var daysOffset: Long = 1
    val result = mutableListOf<Instant>()
    while (result.lastOrNull() != logDates.last()) {
        result.clear()
        result.add(logDates.first())
        var previousDate = logDates.first()
        for (currentDate in logDates.subList(1, logDates.size)) {
            if (previousDate >= currentDate || previousDate.plus(daysOffset, ChronoUnit.DAYS) < currentDate)
                previousDate = previousDate.plusSeconds(1)
            else
                previousDate = currentDate
            result.add(previousDate)
        }
        daysOffset++

        // if last date is screwed up
        if (logDates.last() <= logDates.first())
            break
    }
    return result
}

var hasReached = false
var reachedAt = 0
fun  hasReached(hash: String, startFromHash: String, index: Int): Boolean {
    if (startFromHash == "" || startFromHash == hash) {
        hasReached = true
        reachedAt = index
    }
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

fun cloneRemoteRepository(repositoryURL: String, directory: File): Git {
    try {
        println("Cloning repository from $repositoryURL\n...")
        val result: Git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(directory)
                .call()
        println("Repository cloned into ${result.repository.directory.parent}")
        return result
    } catch (e: Exception) {
        throw Exception("Could not clone the remote repository", e)
    }
}