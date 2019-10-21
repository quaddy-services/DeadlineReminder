package de.quaddy_services.deadlinereminder;

import java.util.Calendar;
import java.util.Date;

import junit.framework.TestCase;

public class DeadlineTest extends TestCase {

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
}
