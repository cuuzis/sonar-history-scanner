import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.io.InputStreamReader
import java.io.BufferedReader




val logger = KotlinLogging.logger {}



fun  main(args: Array<String>) {
    //val repositoryURL = "https://github.com/apache/lucene-solr.git"
    val repositoryURL = "https://github.com/github/testrepo.git"
    val startFromHash = "f829883bcbc383e26b3428b268c981b9116370c0"

//val repositoryPath = "C:/Users/Gustavs/AppData/Local/Temp/TestGitRepo5885582128508151844.tmp/.git" //lucene
    val repositoryPath = "C:/Users/Gustavs/AppData/Local/Temp/TestGitRepo2921721111303119884.tmp/.git"  //test

    //val git = cloneRemoteRepository(repositoryURL)
    val git = openLocalRepository(repositoryPath)
    try {
        analyseAllRevisions(git, startFromHash)
    } finally {
        git.close()
    }
}

fun  analyseAllRevisions(git: Git, startFromHash: String) {
    val logEntries = git.log()
            .call()
    for (log in logEntries.reversed()) {
        val logDate = Instant.ofEpochSecond(log.commitTime.toLong())
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        val logDateFormatted = formatter.format(logDate)
                .removeRange(22, 23) // removes colon from time zone (to post timestamp to sonarqube analysis)
        val logHash = log.name
        if (hasReached(logHash, startFromHash)) {
            logger.info("Analysing revision: $logDateFormatted $logHash")
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
            Runtime.getRuntime().exec("sonar-scanner.bat" +
                    " -Dproject.settings=sonar.properties" +
                    " -Dsonar.projectDate=$logDateFormatted" +
                    " 2>&1 ${logDateFormatted.subSequence(0,10)}-$logHash.out")

            val p: Process = Runtime.getRuntime().exec(
                    "sonar-scanner.bat" +
                    " -Dproject.settings=sonar.properties" +
                    " -Dsonar.projectDate=$logDateFormatted" +
                    " >../${logDateFormatted.subSequence(0,10)}-$logHash.out 2>&1",
                    null,
                    File(git.repository.directory.parent))
            val returnCode = p.waitFor()
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val allText = reader.use(BufferedReader::readText)
            println(allText)
            println(returnCode)
        }
    }
}

var hasReached = false
fun  hasReached(hash: String, startFromHash: String): Boolean {
    if (startFromHash == null || startFromHash == hash)
        hasReached = true
    return hasReached
}

fun  openLocalRepository(repositoryPath: String): Git {
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

fun  cloneRemoteRepository(repositoryURL: String): Git {
    val localPath: File = createTempFile("TestGitRepo")
    if (!localPath.delete()) {
        throw Exception("Could not delete temporary file $localPath")
    }
    try {
        logger.info("Cloning repository from $repositoryURL\n...")
        val result: Git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(localPath)
                .call()
        logger.info("Repository cloned into ${result.repository.directory.parent}")
        return result
    } catch (e: Exception) {
        throw Exception("Could not clone the remote repository", e)
    }
}