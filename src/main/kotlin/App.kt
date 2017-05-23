import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.time.format.DateTimeFormatter


val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    //val repositoryURL = "https://github.com/apache/lucene-solr.git"
    val repositoryURL = "https://github.com/github/testrepo.git"
    val repositoryPath = "C:/Users/Gustavs/AppData/Local/Temp/TestGitRepo5885582128508151844.tmp/.git"

    //val git = cloneRemoteRepository(repositoryURL)
    val git = openLocalRepository(repositoryPath)
    val logEntries = git.log()
            .call()
    for (log in logEntries.reversed()) {
        val logDate = Instant.ofEpochSecond(log.commitTime.toLong())
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        val logDateFormatted = formatter.format(logDate)
        val logHash = log.name
        println("${logDateFormatted} ${logHash}")
    }
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
        throw Exception("Could not delete temporary file ${localPath}")
    }
    try {
        logger.info("Cloning repository from ${repositoryURL}\n...")
        val result: Git = Git.cloneRepository()
                .setURI(repositoryURL)
                .setDirectory(localPath)
                .call()
        logger.info("Repository cloned into ${result.repository.directory}")
        return result
    } catch (e: Exception) {
        throw Exception("Could not clone the remote repository", e)
    }
}