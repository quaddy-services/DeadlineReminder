package de.quaddy_services.deadlinereminder.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.quaddy_services.deadlinereminder.Deadline;
import junit.framework.TestCase;

public class FileStorageTest extends TestCase {
	private static final DecimalFormat TWO = new DecimalFormat("00");

	private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageTest.class);

	class TestFileStorage extends FileStorage {
		private String termin;

		public TestFileStorage(String aText) {
			termin = aText;
			LOGGER.info("termin=" + aText);
		}

		@Override
		protected BufferedReader createReader(File aTempFile) {
			if (aTempFile.getName().equals(TERMIN_DONE_TXT)) {
				return new BufferedReader(new StringReader(""));
			}
			if (aTempFile.getName().equals(TERMIN_GOOGLE_ADDED_TXT)) {
				return new BufferedReader(new StringReader(""));
			}
			return new BufferedReader(new StringReader(termin));
		}
	}

	public void testBirthday() {
		Calendar tempCal = Calendar.getInstance();
		tempCal.set(Calendar.YEAR, 1970);
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "." + TWO.format(tempCal.get(Calendar.MONTH) + 1) + "."
				+ TWO.format(tempCal.get(Calendar.YEAR)) + "*Max Mustermann";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 400);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);
		assertEquals(2, tempDeadlines.size());
		assertEquals("*Max Mustermann", tempDeadlines.get(0).getInfo());
	}

	public void test6Month() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "." + TWO.format(tempCal.get(Calendar.MONTH) + 1) + "."
				+ TWO.format(tempCal.get(Calendar.YEAR)) + "*6m Stefan";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 400);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);
		assertEquals(3, tempDeadlines.size());
	}

	public void testEveryWeek() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "." + TWO.format(tempCal.get(Calendar.MONTH) + 1) + "."
				+ TWO.format(tempCal.get(Calendar.YEAR)) + "*w Week";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 40);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);
		assertEquals(6, tempDeadlines.size());
	}

	public void testEvery1Week() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "." + TWO.format(tempCal.get(Calendar.MONTH) + 1) + "."
				+ TWO.format(tempCal.get(Calendar.YEAR)) + "*1w Week";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 40);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);
		assertEquals(6, tempDeadlines.size());
	}

	public void testPastWeek() {
		TestFileStorage tempTestFileStorage;
		if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
			// on monday one week less is returned as 18.03.2019 is monday.
			tempTestFileStorage = new TestFileStorage("17.03.2019*1w 16:00 Lisa Reitstunde");
		} else {
			tempTestFileStorage = new TestFileStorage("18.03.2019*1w 16:00 Lisa Reitstunde");
		}
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 42);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);

		assertEquals(9, tempDeadlines.size());

	}

	private void logDeadlines(List<Deadline> tempDeadlines) {
		LOGGER.info(tempDeadlines.size() + " deadlines:");
		for (Deadline tempDeadline : tempDeadlines) {
			LOGGER.info(tempDeadline.toString());
		}
		LOGGER.info(".");
	}

	public void testDaily() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = format(tempCal) + "*1d testDaily";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 40);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);
		assertEquals(30, tempDeadlines.size());
		assertEquals("*testDaily", tempDeadlines.get(0).getTextWithoutRepeatingInfo());
		assertEquals("*1d testDaily", tempDeadlines.get(0).getInfo());
		Date tempWhen = tempDeadlines.get(0).getWhen();
		Calendar tempWhenCal = Calendar.getInstance();
		tempWhenCal.setTime(tempWhen);
		assertEquals(0, tempWhenCal.get(Calendar.HOUR_OF_DAY));
	}

	public void testDailyWithTime() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = format(tempCal) + "*1d 11:00 testDaily";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 40);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);
		assertEquals(30, tempDeadlines.size());
		assertEquals("*testDaily", tempDeadlines.get(0).getTextWithoutRepeatingInfo());
		assertEquals("*1d testDaily", tempDeadlines.get(0).getInfo());
		Date tempWhen = tempDeadlines.get(0).getWhen();
		Calendar tempWhenCal = Calendar.getInstance();
		tempWhenCal.setTime(tempWhen);
		assertEquals(11, tempWhenCal.get(Calendar.HOUR_OF_DAY));
	}

	public void testElevenDays() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = format(tempCal) + "*11d testDaily";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 40);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		logDeadlines(tempDeadlines);
		assertEquals(4, tempDeadlines.size());
		assertEquals("*testDaily", tempDeadlines.get(0).getTextWithoutRepeatingInfo());
		assertEquals("*11d testDaily", tempDeadlines.get(0).getInfo());
	}

	/**
	 *
	 */
	private String format(Calendar tempCal) {
		String tempToDay = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "." + TWO.format(tempCal.get(Calendar.MONTH) + 1) + "."
				+ TWO.format(tempCal.get(Calendar.YEAR));
		return tempToDay;
	}

}
