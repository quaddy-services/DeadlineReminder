package de.quaddy_services.deadlinereminder.file;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.List;

import junit.framework.TestCase;
import de.quaddy_services.deadlinereminder.Deadline;

public class FileStorageTest extends TestCase {
	private static final DecimalFormat TWO = new DecimalFormat("00");

	class TestFileStorage extends FileStorage {
		private String termin;

		public TestFileStorage(String aText) {
			termin = aText;
		}

		@Override
		protected Reader createReader(File aTempFile) {
			if (aTempFile.getName().equals(TERMIN_DONE_TXT)) {
				return new StringReader("");
			}
			return new StringReader(termin);
		}
	}

	public void testBirthday() {
		Calendar tempCal = Calendar.getInstance();
		tempCal.set(Calendar.YEAR, 1970);
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "."
				+ TWO.format(tempCal.get(Calendar.MONTH) + 1) + "." + TWO.format(tempCal.get(Calendar.YEAR))
				+ "*Stefan";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 400);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		System.out.println(tempDeadlines);
		assertEquals(2, tempDeadlines.size());
	}

	public void test6Month() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "."
				+ TWO.format(tempCal.get(Calendar.MONTH) + 1) + "." + TWO.format(tempCal.get(Calendar.YEAR))
				+ "*6m Stefan";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 400);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		System.out.println(tempDeadlines);
		assertEquals(3, tempDeadlines.size());
	}

	public void testEveryWeek() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "."
				+ TWO.format(tempCal.get(Calendar.MONTH) + 1) + "." + TWO.format(tempCal.get(Calendar.YEAR))
				+ "*w Week";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 40);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		System.out.println(tempDeadlines);
		assertEquals(6, tempDeadlines.size());
	}

	public void testEvery1Week() {
		Calendar tempCal = Calendar.getInstance();
		String tempText = TWO.format(tempCal.get(Calendar.DAY_OF_MONTH)) + "."
				+ TWO.format(tempCal.get(Calendar.MONTH) + 1) + "." + TWO.format(tempCal.get(Calendar.YEAR))
				+ "*1w Week";
		TestFileStorage tempTestFileStorage = new TestFileStorage(tempText);
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 40);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		System.out.println(tempDeadlines);
		assertEquals(6, tempDeadlines.size());
	}

	public void testPastWeek() {
		TestFileStorage tempTestFileStorage = new TestFileStorage("23.02.2015*1w 16:00 Lisa Reitstunde");
		Calendar tempTo = Calendar.getInstance();
		tempTo.add(Calendar.DAY_OF_YEAR, 42);
		List<Deadline> tempDeadlines = tempTestFileStorage.getOpenDeadlines(tempTo.getTime());
		System.out.println(tempDeadlines);
		assertEquals(9, tempDeadlines.size());

	}
}
