package de.quaddy_services.deadlinereminder.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.quaddy_services.deadlinereminder.Deadline;
import de.quaddy_services.deadlinereminder.Storage;

public class FileStorage implements Storage {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileStorage.class);

	public static final String TERMIN_TXT = "termin.txt";
	public static final String TERMIN_GOOGLE_ADDED_TXT = "termin-added-by-google.txt";
	private static final String INFO_PREFIX = "--";
	private static final String INFO_PREFIX2 = "#";
	private static final String ID_PREFIX = "ID:";
	public static final String TERMIN_DONE_TXT = "termin-done.txt";
	private static DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
	private static DateFormat timeFormat = new SimpleDateFormat("HH:mm");

	private static final Object MONITOR = new Object();

	public static final String TERMIN_LAST_SYNC_TXT = "termin-last-sync.txt";

	private static final String DELETED_MARKER = "DELETED_MARKER";

	public FileStorage() {
		super();
	}

	private File getDirectory() {
		File tempUserHome = new File(System.getProperty("user.home"));
		File tempDir = new File(tempUserHome.getAbsolutePath() + "/" + "DeadlineReminder");
		tempDir.mkdirs();
		return tempDir;
	}

	@Override
	public String getSourceInfo() {
		return getDirectory().getAbsolutePath() + "/" + TERMIN_TXT;
	}

	@Override
	public List<Deadline> getOpenDeadlines(Date to) {
		synchronized (MONITOR) {
			try {
				// Read all google deadlines
				List<Deadline> tempDeadlinesFromGoogle = readDeadlines(null, TERMIN_GOOGLE_ADDED_TXT);
				for (Deadline tempDeadlineFromGoogle : tempDeadlinesFromGoogle) {
					tempDeadlineFromGoogle.setAddedByGoogle(true);
				}
				List<Deadline> tempDeadlinesFromTerminTxt = readDeadlines(to, TERMIN_TXT);

				List<Deadline> tempDoneDeadlines = readDeadlines(null, TERMIN_DONE_TXT);
				List<Deadline> tempFound = new ArrayList<Deadline>();
				tempFound.addAll(tempDeadlinesFromGoogle);
				tempFound.addAll(tempDeadlinesFromTerminTxt);

				tempFound.removeAll(tempDoneDeadlines);

				return tempFound;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public File getLastSyncFile() {
		return new File(getDirectory().getAbsolutePath() + "/" + TERMIN_LAST_SYNC_TXT);
	}

	private List<Deadline> readDeadlines(Date to, String tempFileName) throws FileNotFoundException, IOException {
		List<Deadline> tempMatchingDeadlines = new ArrayList<Deadline>();
		File tempFile = new File(getDirectory().getAbsolutePath() + "/" + tempFileName);
		BufferedReader tempReader = createReader(tempFile);
		Map<String, Deadline> tempIdsAdded = new HashMap<>();
		String tempLine;
		while (null != (tempLine = tempReader.readLine())) {
			List<Deadline> tempDeadlines = parseDeadline(tempLine, to != null);
			for (Deadline tempDeadline : tempDeadlines) {
				if (tempDeadline != null) {
					if (to != null && to.before(tempDeadline.getWhen())) {
						LOGGER.debug("Skip tooFarAway from " + tempFileName + " " + tempDeadline.getInfo());
					} else {
						String tempId = tempDeadline.getId();
						if (tempId != null) {
							Deadline tempPreviouslyAdded = tempIdsAdded.put(tempId, tempDeadline);
							if (tempPreviouslyAdded != null) {
								// Remove the outdated one (termin-added-from-google is appended always)
								tempMatchingDeadlines.remove(tempPreviouslyAdded);
							}
						}
						tempMatchingDeadlines.add(tempDeadline);
					}
				}
			}
		}
		tempReader.close();
		removeAllDeleted(tempMatchingDeadlines);
		return tempMatchingDeadlines;
	}

	/**
	 * While adding the IDs it may be non-deleted or deleted. With
	 * tempPreviouslyAdded above we found the latest state of google event.
	 * 
	 * @param aMatchingDeadlines
	 */
	private void removeAllDeleted(List<Deadline> aMatchingDeadlines) {
		for (Iterator<Deadline> i = aMatchingDeadlines.iterator(); i.hasNext();) {
			Deadline tempDeadline = i.next();
			if (tempDeadline.isDeleted()) {
				i.remove();
			}
		}
	}

	protected BufferedReader createReader(File tempFile) throws IOException {
		if (!tempFile.exists()) {
			LOGGER.info("File does not exist: " + tempFile.getAbsolutePath());
			tempFile.createNewFile();
			return new BufferedReader(new StringReader(""));
		}
		String tempEncoding = "UTF-8";
		// https://dzone.com/articles/java-may-use-utf-8-as-its-default-charset
		UnicodeReader tempIn = new UnicodeReader(new FileInputStream(tempFile), tempEncoding);
		LOGGER.debug("Read " + tempFile + " with encoding=" + tempIn.getEncoding());
		return new BufferedReader(tempIn);
	}

	private List<Deadline> parseDeadline(String aLine, boolean aRepeating) {
		List<Deadline> tempDeadlines = new ArrayList<Deadline>();
		String tempLine = aLine;
		if (tempLine.startsWith(INFO_PREFIX)) {
			return tempDeadlines;
		}
		if (tempLine.startsWith(INFO_PREFIX2)) {
			return tempDeadlines;
		}
		if (tempLine.trim().length() == 0) {
			return tempDeadlines;
		}
		String tempId;
		if (tempLine.startsWith(ID_PREFIX)) {
			int tempTabPos = tempLine.indexOf("\t");
			tempId = tempLine.substring(ID_PREFIX.length(), tempTabPos);
			tempLine = tempLine.substring(tempTabPos + 1);
			if (DELETED_MARKER.equals(tempLine)) {
				// return deleted deadline as previously the un.deleted one was added
				// while parsing the file
				Deadline tempDeadline = new Deadline();
				tempDeadline.setId(tempId);
				tempDeadline.setDeleted(true);
				tempDeadline.setWhen(new Date());
				tempDeadlines.add(tempDeadline);
				return tempDeadlines;
			}
		} else {
			tempId = null;
		}
		try {
			String tempDateChars = tempLine.substring(0, 10);
			tempDateChars = tempDateChars.replace('?', '0');
			if (tempDateChars.endsWith("0000")) {
				Calendar tempCal = Calendar.getInstance();
				int tempCurrentYear = tempCal.get(Calendar.YEAR) - 1;
				tempDateChars = tempDateChars.substring(0, tempDateChars.length() - 4) + tempCurrentYear;
			}
			Date tempDate = dateFormat.parse(tempDateChars);
			String tempInfo = tempLine.substring(10);
			if (tempInfo.startsWith("*") && aRepeating) {
				// repeating
				addRepeating(tempDeadlines, tempDate, tempInfo);
			} else {
				Deadline tempDeadline = new Deadline();
				tempDeadline.setId(tempId);
				tempDeadline.setWhen(tempDate);
				tempDeadline.setInfo(tempInfo);
				tempDeadline.extractTimeFromInfo();
				tempDeadlines.add(tempDeadline);
			}
		} catch (Exception e) {
			LOGGER.error("Ignore '" + tempLine + "'", e);
			if (tempLine.trim().length() > 0) {
				Deadline tempDeadline = new Deadline();
				tempDeadline.setWhen(new Date());
				tempDeadline.setInfo(tempLine + " " + e);
				tempDeadlines.add(tempDeadline);
			}
		}
		return tempDeadlines;
	}

	private class UnitAndStep {
		int unit;
		int step;
		String textWithoutRepeatingInfo;

		public UnitAndStep(int aUnit, int aStep, String aTextWithoutRepeatingInfo) {
			super();
			unit = aUnit;
			step = aStep;
			textWithoutRepeatingInfo = aTextWithoutRepeatingInfo;
		}

		/**
		 *
		 */
		@Override
		public String toString() {
			StringBuilder tempBuilder = new StringBuilder();
			tempBuilder.append("UnitAndStep [unit=");
			tempBuilder.append(unit);
			tempBuilder.append(", step=");
			tempBuilder.append(step);
			tempBuilder.append(", ");
			if (textWithoutRepeatingInfo != null) {
				tempBuilder.append("textWithoutRepeatingInfo=");
				tempBuilder.append(textWithoutRepeatingInfo);
				tempBuilder.append(", ");
			}
			tempBuilder.append("]");
			return tempBuilder.toString();
		}
	}

	private void addRepeating(List<Deadline> tempDeadlines, Date tempDate, String tempInfo) {
		Calendar tempStartingPoint = Calendar.getInstance();
		UnitAndStep tempStepAndUnit = getStepAndUnit(tempInfo);
		LOGGER.debug("stepAndUnit=" + tempStepAndUnit);
		int tempMaxAddCount;
		if (tempStepAndUnit.unit == Calendar.YEAR) {
			tempStartingPoint.add(Calendar.YEAR, -1);
			tempMaxAddCount = Math.max(1, 4 / tempStepAndUnit.step);
		} else if (tempStepAndUnit.unit == Calendar.MONTH) {
			tempStartingPoint.add(Calendar.MONTH, -3);
			tempMaxAddCount = Math.max(3, 12 / tempStepAndUnit.step);
		} else if (tempStepAndUnit.unit == Calendar.WEEK_OF_YEAR) {
			tempStartingPoint.add(Calendar.WEEK_OF_YEAR, -3);
			tempMaxAddCount = Math.max(10, 40 / tempStepAndUnit.step);
		} else if (tempStepAndUnit.unit == Calendar.DAY_OF_YEAR) {
			tempStartingPoint.add(Calendar.DAY_OF_YEAR, -3);
			tempMaxAddCount = 30;
		} else {
			LOGGER.error("No valid range " + tempInfo);
			return;
		}
		Calendar tempCal = Calendar.getInstance();
		tempCal.set(Calendar.HOUR_OF_DAY, 0);
		tempCal.set(Calendar.MINUTE, 0);
		tempCal.set(Calendar.SECOND, 0);
		tempCal.set(Calendar.MILLISECOND, 0);
		int tempPreviousYear = tempCal.get(Calendar.YEAR) - 1;
		tempCal.setTime(tempDate);
		// Begin one year before
		int tempDateYear = tempCal.get(Calendar.YEAR);
		while (tempDateYear < tempPreviousYear) {
			tempCal.add(tempStepAndUnit.unit, tempStepAndUnit.step);
			tempDateYear = tempCal.get(Calendar.YEAR);
		}
		// LOGGER.info("tempCal="+tempCal.getTime());
		LOGGER.debug("tempStartingPoint=" + tempStartingPoint.getTime());
		int tempAddCount = 0;
		while (true) {
			if (tempStartingPoint.before(tempCal)) {
				LOGGER.debug("Match " + tempCal.getTime() + " for " + tempDate + " " + tempInfo);
				Deadline tempDeadline = new Deadline();
				Date tempWhen = tempCal.getTime();
				tempDeadline.setWhen(tempWhen);
				tempDeadline.setInfo(tempInfo);
				tempDeadline.setTextWithoutRepeatingInfo(tempStepAndUnit.textWithoutRepeatingInfo);
				tempDeadline.extractTimeFromInfo();
				if (tempStepAndUnit.unit == Calendar.YEAR) {
					tempDeadline.setRepeating(tempDate);
				}
				tempDeadlines.add(tempDeadline);
				tempAddCount++;
				if (tempAddCount >= tempMaxAddCount) {
					break;
				}
			}
			tempCal.add(tempStepAndUnit.unit, tempStepAndUnit.step);
		}
	}

	private UnitAndStep getStepAndUnit(String anInfo) {
		int tempSpace = anInfo.indexOf(' ');
		if (tempSpace > 0) {
			char tempType;
			Integer tempCount;
			String tempTextWithoutRepeatingInfo = "*" + anInfo.substring(tempSpace + 1);
			if (tempSpace == 1) {
				// Annual event
				tempType = 'Y';
				tempCount = 1;
			} else {
				String tempNextWord = anInfo.substring(1, tempSpace);
				try {
					String tempCountString = tempNextWord.substring(0, tempNextWord.length() - 1);
					try {
						tempCount = new Integer(tempCountString);
						if (tempCount < 1) {
							tempCount = 1;
						}
						tempType = tempNextWord.charAt(tempNextWord.length() - 1);
					} catch (NumberFormatException e) {
						tempCount = 1;
						if (tempNextWord.length() == 1) {
							tempType = tempNextWord.charAt(0);
						} else {
							// Annual event
							tempType = 'Y';
							tempTextWithoutRepeatingInfo = anInfo;
						}
					}
				} catch (Exception e) {
					LOGGER.info("Ignore " + anInfo);
					LOGGER.debug("Ignore", e);
					// Annual event
					tempType = 'Y';
					tempCount = 1;
				}
			}
			int tempUnit;
			switch (tempType) {
			case 'd':
				tempUnit = Calendar.DAY_OF_YEAR;
				break;
			case 'D':
				tempUnit = Calendar.DAY_OF_YEAR;
				break;
			case 'w':
				tempUnit = Calendar.WEEK_OF_YEAR;
				break;
			case 'W':
				tempUnit = Calendar.WEEK_OF_YEAR;
				break;
			case 'm':
				tempUnit = Calendar.MONTH;
				break;
			case 'M':
				tempUnit = Calendar.MONTH;
				break;
			case 'y':
				tempUnit = Calendar.YEAR;
				break;
			case 'Y':
				tempUnit = Calendar.YEAR;
				break;
			default:
				tempUnit = Calendar.YEAR;
				break;
			}
			UnitAndStep tempUnitAndStep = new UnitAndStep(tempUnit, tempCount, tempTextWithoutRepeatingInfo);
			return tempUnitAndStep;
		}
		return new UnitAndStep(Calendar.YEAR, 1, anInfo);
	}

	@Override
	public void saveConfirmedTasks(List<Deadline> aDeadlines) {
		try {
			List<Deadline> tempDones = new ArrayList<Deadline>();
			for (Deadline tempDeadline : aDeadlines) {
				if (tempDeadline.isDone()) {
					tempDones.add(tempDeadline);
				}
			}
			if (tempDones.size() > 0) {
				try (PrintWriter tempDone = new PrintWriter(
						createFileWriter(new File(getDirectory() + "/" + TERMIN_DONE_TXT)))) {
					tempDone.println(INFO_PREFIX + new Date());
					for (Deadline tempDeadline : tempDones) {
						StringBuilder tempDeadlineText = new StringBuilder();
						tempDeadlineText.append(dateFormat.format(tempDeadline.getWhen()));
						if (tempDeadline.isWholeDayEvent()) {
							// Wholeday event
						} else {
							tempDeadlineText.append(" ");
							Date tempWhen = tempDeadline.getWhen();
							tempDeadlineText.append(timeFormat.format(tempWhen));
						}
						tempDeadlineText.append(" ");
						tempDeadlineText.append(tempDeadline.getInfo());
						String tempLine = tempDeadlineText.toString();
						tempDone.println(tempLine);
						LOGGER.info(new Date() + ": Confirmed: '" + tempLine + "'");
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void addFromGroogle(List<Deadline> aDeadlines) throws IOException {
		synchronized (MONITOR) {
			File tempFile = new File(getDirectory().getAbsolutePath() + "/" + TERMIN_GOOGLE_ADDED_TXT);
			try (BufferedWriter tempFileWriter = createFileWriter(tempFile)) {
				for (Deadline tempDeadline : aDeadlines) {
					StringBuilder tempDeadlineText = new StringBuilder();
					if (tempDeadline.getId() != null) {
						tempDeadlineText.append(ID_PREFIX);
						tempDeadlineText.append(tempDeadline.getId());
						tempDeadlineText.append("\t");
					}
					Date tempWhen = tempDeadline.getWhen();

					tempDeadlineText.append(dateFormat.format(tempWhen));
					if (tempDeadline.isWholeDayEvent()) {
						// Wholeday event
					} else {
						tempDeadlineText.append(" ");
						tempDeadlineText.append(timeFormat.format(tempWhen));
						Date tempWhenEndTime = tempDeadline.getWhenEndTime();
						if (tempWhenEndTime != null) {
							tempDeadlineText.append("-");
							tempDeadlineText.append(timeFormat.format(tempWhenEndTime));
						}
					}
					tempDeadlineText.append(" ");
					tempDeadlineText.append(tempDeadline.getTextWithoutRepeatingInfo());

					tempFileWriter.append(tempDeadlineText.toString());
					tempFileWriter.append(System.lineSeparator());
				}
			}
		}
	}

	@Override
	public void removeFromGroogle(List<Deadline> aDeadlines) throws IOException {
		synchronized (MONITOR) {
			File tempFile = new File(getDirectory().getAbsolutePath() + "/" + TERMIN_GOOGLE_ADDED_TXT);
			try (BufferedWriter tempFileWriter = createFileWriter(tempFile)) {
				for (Deadline tempDeadline : aDeadlines) {
					StringBuilder tempDeadlineText = new StringBuilder();
					tempDeadlineText.append(ID_PREFIX);
					tempDeadlineText.append(tempDeadline.getId());
					tempDeadlineText.append("\t");
					tempDeadlineText.append(DELETED_MARKER);

					tempFileWriter.append(tempDeadlineText.toString());
					tempFileWriter.append(System.lineSeparator());
				}
			}
		}
	}

	private BufferedWriter createFileWriter(File aFile) throws IOException {

		boolean tempWriteBOM = false;
		if (!aFile.exists()) {
			tempWriteBOM = true;
		}
		LOGGER.debug("Write to " + aFile + " with encoding=UTF-16LE");
		// https://dzone.com/articles/java-may-use-utf-8-as-its-default-charset
		FileOutputStream tempOut = new FileOutputStream(aFile, true);
		if (tempWriteBOM) {
			// ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
			tempOut.write(new byte[] { (byte) 0xFF, (byte) 0xFE });
		}
		OutputStreamWriter tempOutputStreamWriter = new OutputStreamWriter(tempOut, "UTF-16LE");
		return new BufferedWriter(tempOutputStreamWriter);
	}
}
