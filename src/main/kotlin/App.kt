import org.apache.commons.lang3.SystemUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
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

/**
 * Runs Sonarqube analysis of past revisions for a git code repository
 */
fun main(args: Array<String>) {
    val scanOptions = parseOptions(args)
    val tempScanDirectory = File("temp-scan-directory")
    val git =
            if (scanOptions.repositoryPath.startsWith("http"))
                cloneRemoteRepository(scanOptions.repositoryPath, tempScanDirectory)
            else
                copyLocalRepository(scanOptions.repositoryPath, tempScanDirectory)
    try {
        analyseAllRevisions(git, scanOptions)
    } finally {
        git.repository.close()
    }

    if (tempScanDirectory.deleteRecursively())
        println("Deleted $tempScanDirectory")
    else
        throw Exception("Could not delete $tempScanDirectory")
}

/**
 * Analyses past revisions specified in scanOptions
 */
fun analyseAllRevisions(git: Git, scanOptions: ScanOptions) {
    var sonarPropertiesFile = scanOptions.propertiesFile
    copyPropertyFiles(git, scanOptions)

    val logEntries = git.log()
            .call()
            .reversed()
    val logDatesRaw = logEntries.map { Instant.ofEpochSecond(it.commitTime.toLong()) }
    val logDatesSonar = smoothDates(logDatesRaw)

    val logEntriesToScan = filterRevisions(logEntries, scanOptions)
    for ((index, value) in logEntriesToScan.withIndex()) {
        val logHash = value.name
        if (scanOptions.changePropertiesAtRevisions.containsKey(logHash)) {
            sonarPropertiesFile = scanOptions.changePropertiesAtRevisions.getValue(logHash)
        }
        if (index % scanOptions.analyseEvery == 0) {
            val logDateRaw = Instant.ofEpochSecond(value.commitTime.toLong())
            val logDateSonar = logDatesSonar[logDateRaw]!!
            if (logDateSonar != logDateRaw)
                println("Date changed from $logDateRaw to $logDateSonar")

            val sonarDateStr = getSonarDate(logDateSonar)
            print("Analysing revision: $sonarDateStr $logHash .. ")

            checkoutFromCmd(logHash, git)

            val scannerCmd: String
            if (SystemUtils.IS_OS_WINDOWS)
                scannerCmd = "sonar-scanner.bat"
            else
                scannerCmd = "sonar-scanner"
            val pb = ProcessBuilder(scannerCmd,
                    "-Dproject.settings=$sonarPropertiesFile",
                    "-Dsonar.projectDate=$sonarDateStr")
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

/**
 * Returns revisions that need to be scanned according scan options
 */
private fun filterRevisions(logEntries: List<RevCommit>, scanOptions: ScanOptions): List<RevCommit> {
    var sinceRevision = logEntries.indexOfFirst { it.name == scanOptions.sinceRevision }
    if (sinceRevision == -1)
        sinceRevision = 0
    var untilRevision = logEntries.indexOfFirst { it.name == scanOptions.untilRevision }
    if (untilRevision == -1)
        untilRevision = logEntries.lastIndex
    var entriesToScan = logEntries.subList(sinceRevision, untilRevision + 1)
    if (scanOptions.revisionFile != null) {
        val hashesToScan = readLinesFromFile(scanOptions.revisionFile)
        entriesToScan = entriesToScan.filter { hashesToScan.contains(it.name) }
    }
    return entriesToScan
}

/**
 * Copies property files to scan folder.
 */
fun  copyPropertyFiles(git: Git, scanOptions: ScanOptions) {
    val propertyFileList = mutableListOf<String>(scanOptions.propertiesFile)
    propertyFileList.addAll(scanOptions.changePropertiesAtRevisions.values)
    for (propertiesFile in propertyFileList) {
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

/**
 * Checks out the revision with specified hash, using the command line
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

/**
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

/**
 * Makes sure commit dates are in an increasing order for doing repeated analysis
 * Increases day offset until the last commit date does not get changed by smoothing
 */
fun smoothDates(logDates: List<Instant>): Map<Instant, Instant> {
    var daysOffset: Long = 1
    val result = mutableMapOf<Instant, Instant>()
    while (result.values.lastOrNull() != logDates.last()) {
        result.clear()
        result.put(logDates.first(), logDates.first())
        var previousDate = logDates.first()
        for (currentDate in logDates.subList(1, logDates.size)) {
            if (previousDate >= currentDate || previousDate.plus(daysOffset, ChronoUnit.DAYS) < currentDate)
                previousDate = previousDate.plusSeconds(1)
            else
                previousDate = currentDate
            result.put(currentDate, previousDate)
        }
        daysOffset++

        // if last date is screwed up
        if (logDates.last() <= logDates.first())
            break
    }
    return result
}

/**
 * Copies repository and its enclosing folder to destination.
 * Returns the newly created JGit repository in open state.
 */
fun copyLocalRepository(repositoryPath: String, destination: File): Git {
    val gitSource = File(repositoryPath).parentFile
    gitSource.copyRecursively(destination)
    println("Repository copied into ${destination.path}")
    return openLocalRepository(destination.path + File.separator + ".git")
}

/**
 * Returns an opened JGit repository from file path.
 */
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
        throw Exception("Could not open the repository $repositoryPath", e)
    }
}

/**
 * Clones remote repository from an URL into destination folder.
 * Returns the newly created JGit repository in open state.
 */
fun cloneRemoteRepository(repositoryURL: String, destination: File): Git {
    try {
        println("Cloning repository from $repositoryURL\n...")
        val result: Git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(destination)
                .call()
        println("Repository cloned into ${result.repository.directory.parent}")
        return result
    } catch (e: Exception) {
        throw Exception("Could not clone the remote repository", e)
    }
}