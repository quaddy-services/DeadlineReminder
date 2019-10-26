package de.quaddy_services.deadlinereminder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import junit.framework.TestCase;

public class DeadlineTest extends TestCase {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineTest.class);

	public void testTime() {
		String tempInfo = "13:00 Test ob Google richtig angezeigt wird (mit Uhrzeit)";
		Deadline tempDeadline = new Deadline();
		tempDeadline.setInfo(tempInfo);
		Date tempZeroOClock = getZeroOClock();
		tempDeadline.setWhen(tempZeroOClock);

		tempDeadline.extractTimeFromInfo();

		assertEquals("Test ob Google richtig angezeigt wird (mit Uhrzeit)", tempDeadline.getInfo());
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(tempDeadline.getWhen());
		assertEquals(13, tempCal.get(Calendar.HOUR_OF_DAY));
		assertEquals("Test ob Google richtig angezeigt wird (mit Uhrzeit)", tempDeadline.getTextWithoutRepeatingInfo());
	}

	public void testTimeFromTo() {
		String tempInfo = "14:00-15:30 Test ob Google richtig angezeigt wird (mit Uhrzeit von bis)";
		Deadline tempDeadline = new Deadline();
		tempDeadline.setInfo(tempInfo);
		Date tempZeroOClock = getZeroOClock();
		tempDeadline.setWhen(tempZeroOClock);

		tempDeadline.extractTimeFromInfo();

		assertEquals("Test ob Google richtig angezeigt wird (mit Uhrzeit von bis)", tempDeadline.getInfo());
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(tempDeadline.getWhen());
		assertEquals(14, tempCal.get(Calendar.HOUR_OF_DAY));
		assertEquals(0, tempCal.get(Calendar.MINUTE));
		assertEquals("Test ob Google richtig angezeigt wird (mit Uhrzeit von bis)", tempDeadline.getTextWithoutRepeatingInfo());

		tempCal.setTime(tempDeadline.getWhenEndTime());
		assertEquals(15, tempCal.get(Calendar.HOUR_OF_DAY));
		assertEquals(30, tempCal.get(Calendar.MINUTE));
	}

	public void testRepeatingTime() {
		String tempInfo = "*14:00 Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)";
		Deadline tempDeadline = new Deadline();
		tempDeadline.setInfo(tempInfo);
		Date tempZeroOClock = getZeroOClock();
		tempDeadline.setWhen(tempZeroOClock);

		tempDeadline.extractTimeFromInfo();

		assertEquals("*Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)", tempDeadline.getInfo());
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(tempDeadline.getWhen());
		assertEquals(14, tempCal.get(Calendar.HOUR_OF_DAY));

		assertEquals("*Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)", tempDeadline.getTextWithoutRepeatingInfo());
	}

	public void testRepeatingTimeWithEnd() {
		String tempInfo = "*14:00-15:00 Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)";
		Deadline tempDeadline = new Deadline();
		tempDeadline.setInfo(tempInfo);
		Date tempZeroOClock = getZeroOClock();
		tempDeadline.setWhen(tempZeroOClock);

		tempDeadline.extractTimeFromInfo();

		assertEquals("*Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)", tempDeadline.getInfo());
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(tempDeadline.getWhen());
		assertEquals(14, tempCal.get(Calendar.HOUR_OF_DAY));

		assertEquals("*Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)", tempDeadline.getTextWithoutRepeatingInfo());
		tempCal.setTime(tempDeadline.getWhenEndTime());
		assertEquals(15, tempCal.get(Calendar.HOUR_OF_DAY));
		assertEquals(0, tempCal.get(Calendar.MINUTE));
	}

	public void testRepeatingTimeWithEndAndSpace() {
		String tempInfo = "*14:00 - 15:01 Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)";
		Deadline tempDeadline = new Deadline();
		tempDeadline.setInfo(tempInfo);
		Date tempZeroOClock = getZeroOClock();
		tempDeadline.setWhen(tempZeroOClock);

		tempDeadline.extractTimeFromInfo();

		assertEquals("*Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)", tempDeadline.getInfo());
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(tempDeadline.getWhen());
		assertEquals(14, tempCal.get(Calendar.HOUR_OF_DAY));

		assertEquals("*Test ob Google richtig angezeigt wird (jedes Jahr mit Uhrzeit)", tempDeadline.getTextWithoutRepeatingInfo());
		tempCal.setTime(tempDeadline.getWhenEndTime());
		assertEquals(15, tempCal.get(Calendar.HOUR_OF_DAY));
		assertEquals(1, tempCal.get(Calendar.MINUTE));
	}

	public void testRepeatingWeekTime() {
		String tempInfo = "*1w 16:00 Geigenunterricht";
		Deadline tempDeadline = new Deadline();
		tempDeadline.setInfo(tempInfo);
		tempDeadline.setTextWithoutRepeatingInfo("*16:00 Geigenunterricht");
		Date tempZeroOClock = getZeroOClock();
		tempDeadline.setWhen(tempZeroOClock);

		tempDeadline.extractTimeFromInfo();

		assertEquals("*1w Geigenunterricht", tempDeadline.getInfo());
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(tempDeadline.getWhen());
		assertEquals(16, tempCal.get(Calendar.HOUR_OF_DAY));

		assertEquals("*Geigenunterricht", tempDeadline.getTextWithoutRepeatingInfo());
	}

	private Date getZeroOClock() {
		Calendar tempCal = Calendar.getInstance();
		tempCal.set(Calendar.HOUR_OF_DAY, 0);
		tempCal.set(Calendar.MINUTE, 0);
		tempCal.set(Calendar.SECOND, 0);
		tempCal.set(Calendar.MILLISECOND, 0);
		Date tempZeroOClock = tempCal.getTime();
		return tempZeroOClock;
	}

	public void testHoursOnDaylightChange() {
		TimeZone tempTestTimeZone = TimeZone.getTimeZone("Europe/Berlin");
		ZoneId tempZoneId = ZoneId.of("Europe/Berlin");
		Deadline tempDeadline = createTestDeadline(tempTestTimeZone);
		// 2019-07-27 13:00 which is in daylight offset
		tempDeadline.setWhen(Date.from(ZonedDateTime.of(2019, 7, 27, 0, 0, 0, 0, tempZoneId).toInstant()));
		tempDeadline.setInfo("13:00 testHoursOnDaylightChange");
		tempDeadline.extractTimeFromInfo();
		Date tempWhen = tempDeadline.getWhen();
		Calendar tempTestCal = Calendar.getInstance();
		tempTestCal.setTimeZone(tempTestTimeZone);
		tempTestCal.setTime(tempWhen);
		assertEquals(13, tempTestCal.get(Calendar.HOUR_OF_DAY));
	}

	/**
	 *
	 */
	private Deadline createTestDeadline(TimeZone aTestTimeZone) {
		return new Deadline() {
			@Override
			int getZoneOffset(long aTime) {
				int tempOffset = aTestTimeZone.getOffset(aTime);
				LOGGER.info(new Date(aTime) + " ZoneOffest: " + tempOffset + " for info=" + getInfo());
				return tempOffset;
			}
		};
	}

	public void testHoursOnDaylightZoneWithoutOffsetStart() {
		TimeZone tempTestTimeZone = TimeZone.getTimeZone("Europe/Berlin");
		ZoneId tempZoneId = ZoneId.of("Europe/Berlin");
		Deadline tempDeadline = createTestDeadline(tempTestTimeZone);
		// 2019-10-27 13:00 which is on daylight offset start day
		tempDeadline.setWhen(Date.from(ZonedDateTime.of(2019, 3, 31, 0, 0, 0, 0, tempZoneId).toInstant()));
		tempDeadline.setInfo("13:00 testHoursOnDaylightZoneWithoutOffsetStart");
		tempDeadline.extractTimeFromInfo();
		Date tempWhen = tempDeadline.getWhen();
		Calendar tempTestCal = Calendar.getInstance();
		tempTestCal.setTimeZone(tempTestTimeZone);
		tempTestCal.setTime(tempWhen);
		assertEquals(13, tempTestCal.get(Calendar.HOUR_OF_DAY));
	}

	public void testHoursOnDaylightZoneWithoutOffsetEnd() {
		TimeZone tempTestTimeZone = TimeZone.getTimeZone("Europe/Berlin");
		ZoneId tempZoneId = ZoneId.of("Europe/Berlin");
		Deadline tempDeadline = createTestDeadline(tempTestTimeZone);
		// 2019-10-27 13:00 which is on daylight offset removal day
		tempDeadline.setWhen(Date.from(ZonedDateTime.of(2019, 10, 27, 0, 0, 0, 0, tempZoneId).toInstant()));
		tempDeadline.setInfo("13:00 testHoursOnDaylightZoneWithoutOffsetEnd");
		tempDeadline.extractTimeFromInfo();
		Date tempWhen = tempDeadline.getWhen();
		Calendar tempTestCal = Calendar.getInstance();
		tempTestCal.setTimeZone(tempTestTimeZone);
		tempTestCal.setTime(tempWhen);
		assertEquals(13, tempTestCal.get(Calendar.HOUR_OF_DAY));
	}

}
