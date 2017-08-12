import org.junit.Test
import java.io.File
import java.time.Instant



class AppKtTest {

    @Test fun testSmoothDates() {
        val logDates = mutableMapOf<Instant, Instant>()
        logDates.put(Instant.parse("2001-11-01T01:31:42Z"), Instant.parse("2001-11-01T01:31:42Z"))
        logDates.put(Instant.parse("2002-11-02T01:34:36Z"), Instant.parse("2002-11-02T01:34:36Z"))
        logDates.put(Instant.parse("2003-11-03T01:38:17Z"), Instant.parse("2003-11-03T01:38:17Z"))
        logDates.put(Instant.parse("2014-11-14T01:47:53Z"), Instant.parse("2003-11-03T01:38:18Z")) // bad date smoothed
        logDates.put(Instant.parse("2005-11-05T23:55:29Z"), Instant.parse("2005-11-05T23:55:29Z"))
        logDates.put(Instant.parse("2004-11-06T02:25:37Z"), Instant.parse("2005-11-05T23:55:30Z")) // bad date smoothed
        logDates.put(Instant.parse("2010-11-10T19:16:27Z"), Instant.parse("2010-11-10T19:16:27Z"))

        val logDatesSmoothed = smoothDates(logDates.keys.toList())

        for ((key, value) in logDates) {
            assert(value == logDatesSmoothed[key])
        }
    }

    @Test
    fun testGitCloning() {
        val testDirPath = "temp-test-dir"
        val tmpDir = File(testDirPath)
        try {
            val tmpCloneDir = File(testDirPath + File.separator + "clone")
            val tmpCopyDir = File(testDirPath + File.separator + "copy")
            val gitClone = cloneRemoteRepository("https://github.com/cuuzis/java-project-for-sonar-scanner-testing.git", tmpCloneDir)
            val gitCopy = copyLocalRepository(gitClone.repository.directory.parent + File.separator + ".git", tmpCopyDir)
            assert(gitClone.log().call().toList() == gitCopy.log().call().toList())
            gitClone.repository.close()
            gitCopy.repository.close()
        } finally {
            tmpDir.deleteRecursively()
            if (!tmpDir.deleteRecursively())
                throw Exception("Could not delete $testDirPath")
        }
    }

    /*@Test
    fun testOptions() {
        val args = arrayOf(
                "--git", "https://github.com/cuuzis/java-project-for-sonar-scanner-testing.git",
                "--since", "8523f8cd3a5dd79b37ce1ed9718fce3f5fb04fb5",
                //"--every", "1",
                "--properties", "my.properties",
                "--change", "my2.properties,my3.properties", "e589d2a8065966aa3cdec64038ca3d4a652ffb7c,1e5fd9f8b1c7be6221b55772a39b16970509a0c4"
        )
        val options = parseOptions(args)
        //println(options.changeRevisions)
    }*/
}
