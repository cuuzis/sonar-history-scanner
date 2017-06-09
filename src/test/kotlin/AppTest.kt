import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.ArrayList



class AppTest {

    @Test fun testSmoothDates() {
        val logDates = mutableListOf<Instant>()
        logDates.add(Instant.parse("2001-11-01T01:31:42Z"))
        logDates.add(Instant.parse("2002-11-02T01:34:36Z"))
        logDates.add(Instant.parse("2003-11-03T01:38:17Z"))
        logDates.add(Instant.parse("2014-11-14T01:47:53Z")) // bad date
        logDates.add(Instant.parse("2005-11-05T23:55:29Z"))
        logDates.add(Instant.parse("2004-11-06T02:25:37Z")) // bad date
        logDates.add(Instant.parse("2010-11-10T19:16:27Z"))

        val logDatesSmoothed = mutableListOf<Instant>()
        logDatesSmoothed.add(Instant.parse("2001-11-01T01:31:42Z"))
        logDatesSmoothed.add(Instant.parse("2002-11-02T01:34:36Z"))
        logDatesSmoothed.add(Instant.parse("2003-11-03T01:38:17Z"))
        logDatesSmoothed.add(Instant.parse("2003-11-03T01:38:18Z")) // smoothed
        logDatesSmoothed.add(Instant.parse("2005-11-05T23:55:29Z"))
        logDatesSmoothed.add(Instant.parse("2005-11-05T23:55:30Z")) // smoothed
        logDatesSmoothed.add(Instant.parse("2010-11-10T19:16:27Z"))

        val logDatesToTest = smoothDates(logDates)

        for (dateToTest in logDatesToTest.withIndex()) {
            assert(dateToTest.value == logDatesSmoothed.get(dateToTest.index))
        }
    }
}
