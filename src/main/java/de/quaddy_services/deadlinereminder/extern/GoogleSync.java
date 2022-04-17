package de.quaddy_services.deadlinereminder.extern;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar.Events.Delete;
import com.google.api.services.calendar.Calendar.Events.Insert;
import com.google.api.services.calendar.Calendar.Events.Patch;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Event.ExtendedProperties;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import de.quaddy_services.deadlinereminder.Deadline;
import de.quaddy_services.deadlinereminder.DeadlineComparator;
import de.quaddy_services.deadlinereminder.Storage;
import de.quaddy_services.deadlinereminder.file.FileStorage;
import de.quaddy_services.deadlinereminder.gui.DeadlineGui;

/**
 * https://code.google.com/apis/console/
 *
 * https://console.developers.google.com/apis/credentials?project=api-project-85684967233
 *
 * @author User
 *
 */
public class GoogleSync {
	private static final String OVERDUE_MARKER = "! ";
	private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSync.class);
	private static final boolean DEBUG = false;

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yy");
	private DateTime lastSyncStarted = null;
	private int syncErrorCount = 0;

	private Thread t = null;
	private LogListener logListener = null;
	private TimeZone timeZone;

	public GoogleSync() {
		super();
		timeZone = TimeZone.getDefault();
		LOGGER.info("Use timeZone=" + timeZone);
	}

	public synchronized void pushToGoogle(final List<Deadline> aOpenDeadlines, final DoneSelectionListener aDoneSelectionListener) {
		if (t != null) {
			logWarn("Already active: " + t);
			return;
		}
		t = new Thread() {
			@Override
			public void run() {
				try {
					logInfo("Start");
					if (push(aOpenDeadlines, aDoneSelectionListener)) {
						logInfo("Finished");
					} else {
						// Keep last log statement.
					}
					syncErrorCount = 0;
				} catch (SocketTimeoutException e) {
					logError("SocketTimeoutException: Retry later...", e);
				} catch (java.net.UnknownHostException e) {
					// May happen in case network is completely down (flightmode)
					logError("UnknownHostException: Retry later...", e);
				} catch (Exception e) {
					syncErrorCount++;
					logError("Error on sync (syncErrorCount=" + syncErrorCount + ")", e);
					if (syncErrorCount > 10) {
						logInfo("Next time request new authentication token.");
						String tempUserName = System.getProperty("user.name", "-");
						PersistentCredentialStore tempPersistentCredentialStore = new PersistentCredentialStore();
						tempPersistentCredentialStore.delete(tempUserName);
					}
					OAuth2Native.clearCache();
				} finally {
					t = null;
				}

			}

		};
		t.setName("GoogleSync-" + t.getName());
		t.start();
	}

	private void logWarn(String aString) {
		LOGGER.warn(aString);
		if (getLogListener() != null) {
			getLogListener().warn(aString);
		}
	}

	private void logError(String aString, Exception aE) {
		LOGGER.error(aString, aE);
		if (getLogListener() != null) {
			getLogListener().error(aString + " " + aE);
		}

	}

	/**
	 * see de.quaddy_services.deadlinereminder.DeadlineReminder.createModel()
	 *
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Calendar tempCal = Calendar.getInstance();
		// Date tempFrom = tempCal.getTime();
		tempCal.add(Calendar.DAY_OF_YEAR, 400);
		Date tempTo = tempCal.getTime();
		Storage tempStorage = new FileStorage();
		List<Deadline> tempDeadlines = tempStorage.getOpenDeadlines(tempTo);
		Collections.sort(tempDeadlines, new DeadlineComparator());
		DoneSelectionListener tempDoneSelectionListener = new DoneSelectionListener() {

			@Override
			public void deadlineDone(Deadline aDeadline) {
				LOGGER.info("deadlineDone: " + aDeadline);
			}

			@Override
			public void addNewDeadline(Deadline aDeadline) {
				LOGGER.info("addNewDeadline: " + aDeadline);
			}

			@Override
			public void removeDeadlines(List<Deadline> aDeadlines) {
				LOGGER.info("removeDeadlines: " + aDeadlines);
			}
		};
		new GoogleSync().push(tempDeadlines, tempDoneSelectionListener);
		System.exit(0);
	}

	/**
	 * https://developers.google.com/google-apps/calendar/
	 *
	 * @param aOpenDeadlines
	 * @param aDoneSelectionListener
	 * @throws Exception
	 */
	protected boolean push(List<Deadline> aOpenDeadlines, DoneSelectionListener aDoneSelectionListener) throws Exception {
		/** Global instance of the HTTP transport. */
		HttpTransport HTTP_TRANSPORT = createNetHttpTransport();

		/** Global instance of the JSON factory. */
		JacksonFactory JSON_FACTORY = new JacksonFactory();
		// authorization
		Credential credential = OAuth2Native.authorize(HTTP_TRANSPORT, JSON_FACTORY, new LocalServerReceiver(), Arrays.asList(CalendarScopes.CALENDAR));
		// set up global Calendar instance
		com.google.api.services.calendar.Calendar client = new com.google.api.services.calendar.Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName("DeadlineReminder/1.0").setHttpRequestInitializer(credential).build();

		try {
			return push(client, aOpenDeadlines, aDoneSelectionListener);
		} catch (com.google.api.client.auth.oauth2.TokenResponseException e) {
			logError("OAuth error", e);
			logInfo("Next time request new authentication token.");
			String tempUserName = System.getProperty("user.name", "-");
			PersistentCredentialStore tempPersistentCredentialStore = new PersistentCredentialStore();
			tempPersistentCredentialStore.delete(tempUserName);
			OAuth2Native.clearCache();
			throw e;
		}
	}

	/**
	 *
	 */
	private NetHttpTransport createNetHttpTransport() {
		return new NetHttpTransport.Builder().build();
	}

	private boolean push(com.google.api.services.calendar.Calendar client, List<Deadline> aOpenDeadlines, DoneSelectionListener aDoneSelectionListener)
			throws IOException {

		long tempStartMillis = System.currentTimeMillis();

		com.google.api.services.calendar.Calendar.CalendarList.List tempGoogleCalendarList = client.calendarList().list();
		// https://developers.google.com/calendar/v3/reference/calendarList/list
		CalendarList tempCalendarList = config(tempGoogleCalendarList).execute();
		String tempDeadlineCalendarId = null;
		if (tempCalendarList.getItems() != null) {
			for (CalendarListEntry tempEntry : tempCalendarList.getItems()) {
				String tempSummary = tempEntry.getSummary();
				String tempSummaryOverride = tempEntry.getSummaryOverride();
				if (tempSummaryOverride != null) {
					tempSummary = tempSummaryOverride;
				}
				if (tempSummary.startsWith("Deadline")) {
					LOGGER.info("Found calendar " + tempSummary);
					tempDeadlineCalendarId = tempEntry.getId();
				}
			}
		}
		if (tempDeadlineCalendarId == null) {
			String tempString = "Did not find Calendar with name Deadline* in Account.";
			logWarn(tempString);
			return false;
		}

		Calendar tempTooFarAway = Calendar.getInstance();
		tempTooFarAway.add(Calendar.YEAR, 2);

		ArrayList<Event> tempCurrentGoogleEvents;
		tempCurrentGoogleEvents = getCurrentItems(client, tempDeadlineCalendarId);
		logInfo("Already at Google (including history+deleted): " + tempCurrentGoogleEvents.size());

		List<Deadline> tempRemovedDeadlines = new ArrayList<>();
		for (Iterator<Event> iCurrent = tempCurrentGoogleEvents.iterator(); iCurrent.hasNext();) {
			Event tempGoogleEvent = iCurrent.next();
			String tempStatus = tempGoogleEvent.getStatus();
			if ("cancelled".equals(tempStatus)) {
				// deleted
				iCurrent.remove();
				// and remove from deadlines shown in the gui
				String tempGoogleId = tempGoogleEvent.getId();
				for (Iterator<Deadline> iDeadline = aOpenDeadlines.iterator(); iDeadline.hasNext();) {
					Deadline tempDeadline = iDeadline.next();
					String tempDeadlineId = tempDeadline.getId();
					if (tempDeadlineId != null && tempGoogleId.equals(tempDeadlineId)) {
						iDeadline.remove();
						tempRemovedDeadlines.add(tempDeadline);
					}
				}
			}
		}
		if (tempRemovedDeadlines.size() > 0) {
			aDoneSelectionListener.removeDeadlines(tempRemovedDeadlines);
		}
		logInfo("Already at Google (including history): " + tempCurrentGoogleEvents.size());

		Map<Event, Deadline> tempNewEvents = new IdentityHashMap<>();
		for (Deadline tempDeadline : aOpenDeadlines) {
			if (tempDeadline.getWhen().after(tempTooFarAway.getTime())) {
				continue;
			}
			Event event = createGoogleEventFromDeadline(tempDeadline);
			tempNewEvents.put(event, tempDeadline);
		}
		logInfo("Matching local events: " + tempNewEvents.size());
		List<Event> tempAlreadyKeptEvents = new ArrayList<>();
		Set<Event> tempDuplicateGoogleEvents = new HashSet<>();
		long tempNow = System.currentTimeMillis();
		for (Iterator<Event> iCurrent = tempCurrentGoogleEvents.iterator(); iCurrent.hasNext();) {
			Event tempGoogleEvent = iCurrent.next();
			if ("transparent".equals(tempGoogleEvent.getTransparency())) {
				Event tempSameEvent = getSameEvent(tempNewEvents.keySet(), tempGoogleEvent);
				if (tempSameEvent != null) {
					Deadline tempDeadline = tempNewEvents.get(tempSameEvent);
					logInfo("Google calendar entry was marked available and so make it done. tempDeadline=" + tempDeadline);
					tempDeadline.setDone(true);
					aDoneSelectionListener.deadlineDone(tempDeadline);
				}
			}
		}
		for (Iterator<Event> iCurrent = tempCurrentGoogleEvents.iterator(); iCurrent.hasNext();) {
			Event tempGoogleEvent = iCurrent.next();
			EventDateTime tempStart = tempGoogleEvent.getStart();
			DateTime tempDate = tempStart.getDate();
			String tempSummary = getSummary(tempGoogleEvent);
			if (tempSummary.startsWith(OVERDUE_MARKER)) {
				// Overdue events are deleted and recreated next day. The original event is
				// already kept in calendar.
				Event tempSameEvent = getSameEvent(tempNewEvents.keySet(), tempGoogleEvent);
				if (tempSameEvent != null) {
					LOGGER.info("Overdue " + tempSummary + " already correct.");
					iCurrent.remove(); // do not delete on Google
					// do not add
					if (tempNewEvents.remove(tempSameEvent) == null) {
						throw new RuntimeException("Could not remove from map: " + tempSameEvent);
					}
					continue;
				}
			} else if (isContainedIn(tempNewEvents.keySet(), tempGoogleEvent)) {
				// Is still open. Avoid adding past events twice.
				if (isContainedIn(tempAlreadyKeptEvents, tempGoogleEvent)) {
					LOGGER.info("Duplicate Google entry: " + tempSummary + " event=" + tempGoogleEvent);
					tempDuplicateGoogleEvents.add(tempGoogleEvent);
				} else {
					tempAlreadyKeptEvents.add(tempGoogleEvent);
				}
			} else {
				if (tempDate != null && tempDate.getValue() < tempNow) {
					// Keep finished event.
					iCurrent.remove();
					continue;
				}
				DateTime tempDateTime = tempStart.getDateTime();
				if (tempDateTime != null && tempDateTime.getValue() < tempNow) {
					// Keep finished event.
					iCurrent.remove();
					continue;
				}
				LOGGER.info("Found a new Event: " + tempSummary + " Date=" + tempDate + " DateTime=" + tempDateTime + " " + tempGoogleEvent);
			}
		}
		logInfo("Already at Google tempAlreadyKeptEvents: " + tempAlreadyKeptEvents.size());
		logInfo("Already at Google to be synced: " + tempCurrentGoogleEvents.size());
		tempCurrentGoogleEvents.removeAll(tempAlreadyKeptEvents);
		logInfo("Already at Google without kept: " + tempCurrentGoogleEvents.size());
		for (Iterator<Event> iCurrent = tempCurrentGoogleEvents.iterator(); iCurrent.hasNext();) {
			Event tempEvent = iCurrent.next();
			if (tempDuplicateGoogleEvents.contains(tempEvent)) {
				LOGGER.debug("Will be deleted below: {}", tempEvent);
				continue;
			}
			boolean tempRemoved = false;
			for (Iterator<Map.Entry<Event, Deadline>> iNew = tempNewEvents.entrySet().iterator(); iNew.hasNext();) {
				Map.Entry<Event, Deadline> tempMapEntry = iNew.next();
				Event tempNewEvent = tempMapEntry.getKey();
				boolean tempSameId = isSameId(tempEvent, tempNewEvent);
				if (tempSameId || isSame(tempEvent, tempNewEvent)) {
					if (tempSameId) {
						if (isUpdated(tempNewEvent, tempEvent)) {
							Deadline tempDeadline = createDeadlineFromGoogleEvent(tempEvent);
							logInfo("Add the updated values to from-google file " + tempDeadline.getTextWithoutRepeatingInfo());
							aDoneSelectionListener.addNewDeadline(tempDeadline);
						}
					}
					// Nothing to do, just keep both entries
					iCurrent.remove();
					iNew.remove();
					tempRemoved = true;
					break;
				}
			}
			if (tempRemoved) {
				continue;
			}
			EventDateTime tempStart = tempEvent.getStart();
			String tempSummary = getSummary(tempEvent);
			DateTime tempLastSyncStarted = getLastSyncStarted();
			if (tempSummary.startsWith(OVERDUE_MARKER)) {
				// Do not add self generated files 
				LOGGER.debug("Do not add self generated files " + tempSummary);
				// e.g. when restarting deadline-reminder (tempLastSyncStarted == null)
				// or when suspended more than one day
			} else if (isManuallyCreatedEntry(tempLastSyncStarted, tempEvent)) {
				logInfo("Looks like it is a manual created event in Google=" + tempStart + " " + tempSummary);
				Deadline tempDeadline = createDeadlineFromGoogleEvent(tempEvent);
				if (tempDeadline.isWholeDayEvent()) {
					if ("transparent".equals(tempEvent.getTransparency())) {
						// "transparent" will be ticked as done on next sync.
						logInfo("Make the wholeday event not checked: " + tempSummary);
						googleUpdateEventTransparency(client, tempDeadlineCalendarId, tempEvent, "opaque");
					}
				}
				// tempNewEvents needs not to be updated as event is already at google.
				// and next time it will be detected as normally added one.
				aDoneSelectionListener.addNewDeadline(tempDeadline);
				iCurrent.remove();
				continue;
			}

			logInfo("No matching current event found for Google=" + tempStart + " " + tempSummary + " " + tempEvent);
		}
		logInfo("Already at Google to be deleted: " + tempCurrentGoogleEvents.size());
		logInfo("To be added to Google (later): " + tempNewEvents.size());
		for (Event tempEvent : tempNewEvents.keySet()) {
			googleInsertEvent(client, tempDeadlineCalendarId, tempEvent);
			slowDown();
		}
		logInfo("Now delete: " + tempCurrentGoogleEvents.size());
		for (Event tempEvent : tempCurrentGoogleEvents) {
			googleDeleteEvent(client, tempDeadlineCalendarId, tempEvent);
			slowDown();
		}
		setLastSyncStarted(new DateTime(tempStartMillis));
		return true;
	}

	private void googleDeleteEvent(com.google.api.services.calendar.Calendar aClient, String aDeadlineCalendarId, Event anEvent)
			throws IOException, GoogleJsonResponseException {
		String tempSummary = getSummary(anEvent);
		EventDateTime tempStart = anEvent.getStart();

		Delete tempDelete = aClient.events().delete(aDeadlineCalendarId, anEvent.getId());
		try {
			config(tempDelete).execute();
			logInfo("Deleted " + tempStart + " " + tempSummary + " " + anEvent);
		} catch (GoogleJsonResponseException e) {
			logError("Error deleting " + tempStart + " " + getSummary(anEvent) + " " + anEvent, e);
			throw e;
		}
	}

	private void googleInsertEvent(com.google.api.services.calendar.Calendar aClient, String aDeadlineCalendarId, Event anEvent)
			throws IOException, GoogleJsonResponseException {
		EventDateTime tempStart = anEvent.getStart();
		Insert tempConfig = config(aClient.events().insert(aDeadlineCalendarId, anEvent));
		try {
			Event tempResult = tempConfig.execute();
			logInfo("Added " + tempStart + " " + getSummary(anEvent) + " " + tempResult);
		} catch (GoogleJsonResponseException e) {
			logError("Error adding " + tempStart + " " + getSummary(anEvent) + " " + anEvent, e);
			throw e;
		}
	}

	private void googleUpdateEventTransparency(com.google.api.services.calendar.Calendar aClient, String aDeadlineCalendarId, Event anEvent,
			String aTransparency) throws IOException, GoogleJsonResponseException {
		EventDateTime tempStart = anEvent.getStart();
		Event tempPatch = new Event();
		tempPatch.setTransparency(aTransparency);
		Patch tempConfig = config(aClient.events().patch(aDeadlineCalendarId, anEvent.getId(), tempPatch));
		try {
			Event tempResult = tempConfig.execute();
			logInfo("Updated " + tempStart + " " + getSummary(anEvent) + " Transparence=" + aTransparency + " " + tempResult);
		} catch (GoogleJsonResponseException e) {
			logError("Error adding " + tempStart + " " + getSummary(anEvent) + " " + anEvent, e);
			throw e;
		}
	}

	private boolean isSameId(Event aEvent, Event aNewEvent) {
		String tempId = aEvent.getId();
		if (tempId == null) {
			return false;
		}
		if (tempId.equals(aNewEvent.getId())) {
			return true;
		}
		return false;
	}

	/** For better debugging (drop to frame) 
	 */
	private boolean isUpdated(Event aFileEvent, Event aEvent) {
		if (isUpdateDetection(aFileEvent, aEvent)) {
			return true;
		}
		return false;
	}

	private boolean isUpdateDetection(Event aFileEvent, Event aEvent) {
		String tempSummaryFile = removeOverdue(getSummary(aFileEvent));
		String tempSummaryGoogle = removeOverdue(getSummary(aEvent));
		if (!tempSummaryFile.equals(tempSummaryGoogle)) {
			LOGGER.info("Summary changed: " + tempSummaryFile);
			LOGGER.info("Summary changed: " + tempSummaryGoogle);
			return true;
		}
		EventDateTime tempStart1 = aEvent.getStart();
		EventDateTime tempStart2 = aFileEvent.getStart();
		if (tempStart1 == null && tempStart2 != null) {
			LOGGER.info("EventDateTime changed: tempStart1=" + tempStart1);
			LOGGER.info("EventDateTime changed: tempStart2=" + tempStart2);
			return true;
		}
		if (tempStart1 != null && tempStart2 == null) {
			LOGGER.info("EventDateTime changed: tempStart1=" + tempStart1);
			LOGGER.info("EventDateTime changed: tempStart2=" + tempStart2);
			return true;
		}
		if (tempStart1 != null && tempStart2 != null) {
			DateTime tempDate1 = tempStart1.getDate();
			DateTime tempDate2 = tempStart2.getDate();
			if (tempDate1 == null && tempDate2 != null) {
				LOGGER.info("getDate().DateTime changed: tempDate1=" + tempDate1);
				LOGGER.info("getDate().DateTime changed: tempDate2=" + tempDate2);
				return true;
			}
			if (tempDate1 != null && tempDate2 == null) {
				LOGGER.info("getDate().DateTime changed: tempDate1=" + tempDate1);
				LOGGER.info("getDate().DateTime changed: tempDate2=" + tempDate2);
				return true;
			}
			if (tempDate1 != null && tempDate2 != null) {
				if (!tempDate1.equals(tempDate2)) {
					LOGGER.info("getDate().DateTime changed: tempDate1=" + tempDate1);
					LOGGER.info("getDate().DateTime changed: tempDate2=" + tempDate2);
					return true;
				}
			}
			tempDate1 = tempStart1.getDateTime();
			tempDate2 = tempStart2.getDateTime();
			if (tempDate1 == null && tempDate2 != null) {
				LOGGER.info("getDateTime().DateTime changed: tempDate1=" + tempDate1);
				LOGGER.info("getDateTime().DateTime changed: tempDate2=" + tempDate2);
				return true;
			}
			if (tempDate1 != null && tempDate2 == null) {
				LOGGER.info("getDateTime().DateTime changed: tempDate1=" + tempDate1);
				LOGGER.info("getDateTime().DateTime changed: tempDate2=" + tempDate2);
				return true;
			}
			if (tempDate1 != null && tempDate2 != null) {
				if (!tempDate1.equals(tempDate2)) {
					LOGGER.info("getDateTime().DateTime changed: tempDate1=" + tempDate1);
					LOGGER.info("getDateTime().DateTime changed: tempDate2=" + tempDate2);
					return true;
				}
			}
		}
		return false;
	}

	private String removeOverdue(String aSummary) {
		String tempSummary;
		if (aSummary.startsWith(OVERDUE_MARKER)) {
			// Remove the overdue marker for comparing.
			tempSummary = aSummary.substring(OVERDUE_MARKER.length());
		} else {
			tempSummary = aSummary;
		}
		return tempSummary;
	}

	private boolean isManuallyCreatedEntry(DateTime aLastSyncStarted, Event aEvent) {
		ExtendedProperties tempExtendedProperties = aEvent.getExtendedProperties();
		if (tempExtendedProperties != null) {
			String tempTextWithoutRepeatingInfo = (String) tempExtendedProperties.get("TextWithoutRepeatingInfo");
			if (tempTextWithoutRepeatingInfo != null) {
				// created by DeadlineReminder.
				return false;
			}
		}
		String tempSummary = getSummary(aEvent);
		if (tempSummary.startsWith(OVERDUE_MARKER)) {
			return false;
		}
		// try to guess:
		if (aLastSyncStarted == null) {
			// assume all "unknown" events are manual
			return true;
		}
		DateTime tempCreated = aEvent.getCreated();
		long tempLastSyncStartedMs = aLastSyncStarted.getValue();
		long tempCreatedMs = tempCreated.getValue();
		// Give Google some time to sync the global persistence stores
		long tempLastSyncStartedMsCompare = tempLastSyncStartedMs - 30000;
		if (tempLastSyncStartedMsCompare < tempCreatedMs) {
			return true;
		}
		return false;
	}

	/**
	 *
	 */
	private void setLastSyncStarted(DateTime aDateTime) {
		lastSyncStarted = aDateTime;
		File tempLastSyncFile = new FileStorage().getLastSyncFile();
		try (BufferedWriter tempOut = new BufferedWriter(new FileWriter(tempLastSyncFile))) {
			String tempString = new Timestamp(System.currentTimeMillis()).toString();
			tempOut.write(tempString);
		} catch (IOException e) {
			LOGGER.error("Ignore " + tempLastSyncFile.getAbsolutePath(), e);
		}
		LOGGER.info("setLastSyncStarted=" + lastSyncStarted);
	}

	/**
	 *
	 */
	private DateTime getLastSyncStarted() {
		if (lastSyncStarted == null) {
			File tempFile = new FileStorage().getLastSyncFile();
			if (tempFile.exists()) {
				try (BufferedReader tempIn = new BufferedReader(new FileReader(tempFile))) {
					String tempLine = tempIn.readLine();
					LOGGER.info("getLastSyncStarted: Found via file: " + tempFile + " tempLine=" + tempLine);
					lastSyncStarted = new DateTime(Timestamp.valueOf(tempLine));
					LOGGER.info("getLastSyncStarted: Found via file: lastSyncStarted=" + lastSyncStarted);
				} catch (IOException e) {
					logError("Ignore " + tempFile.getAbsolutePath(), e);
					lastSyncStarted = null;
				} catch (RuntimeException e) {
					logError("Ignore " + tempFile.getAbsolutePath(), e);
					lastSyncStarted = null;
				}
			}
		}
		return lastSyncStarted;
	}

	private boolean isContainedIn(Collection<Event> aNewEvents, Event aEvent) {
		for (Event tempNewEvent : aNewEvents) {
			if (isSame(aEvent, tempNewEvent)) {
				return true;
			}
		}
		return false;
	}

	private Event getSameEvent(Collection<Event> aNewEvents, Event aEvent) {
		for (Event tempNewEvent : aNewEvents) {
			if (isSame(aEvent, tempNewEvent)) {
				return tempNewEvent;
			}
		}
		return null;
	}

	private void logInfo(String aString) {
		LOGGER.info(aString);
		if (getLogListener() != null) {
			getLogListener().info(aString);
		}
	}

	private void logDebug(String aString) {
		LOGGER.debug(aString);
	}

	/**
	 * Avoid
	 *
	 * com.google.api.client.googleapis.json.GoogleJsonResponseException: 403
	 * Forbidden { "code" : 403, "errors" : [ { "domain" : "usageLimits", "message"
	 * : "Rate Limit Exceeded", "reason" : "rateLimitExceeded" } ], "message" :
	 * "Rate Limit Exceeded"
	 *
	 */
	private void slowDown() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			logWarn("Error", e);
		}
	}

	private void logWarn(String aString, Throwable aE) {
		LOGGER.warn(aString, aE);
		if (getLogListener() != null) {
			getLogListener().warn(aString + " " + aE);
		}
	}

	private boolean isSame(Event aOldEvent, Event aNewEvent) {
		if (isSameId(aOldEvent, aNewEvent)) {
			return true;
		}
		String tempOldSummary = getSummary(aOldEvent);
		String tempNewSummary = getSummary(aNewEvent);
		if (tempOldSummary.equals(tempNewSummary) || tempOldSummary.trim().equals(tempNewSummary.trim())) {
			EventDateTime tempOldStart = aOldEvent.getStart();
			DateTime tempDT1 = tempOldStart.getDateTime();
			EventDateTime tempNewStart = aNewEvent.getStart();
			DateTime tempDT2 = tempNewStart.getDateTime();
			if (tempDT1 != null && tempDT2 != null) {
				if (tempDT1.equals(tempDT2)) {
					return true;
				}
				logDebug("Same summary " + tempOldSummary + " but different startdate: " + tempDT1 + " " + tempDT2);
			}
			DateTime tempOldStartDate = tempOldStart.getDate();
			DateTime tempNewStartDate = tempNewStart.getDate();
			if (tempOldStartDate != null && tempNewStartDate != null) {
				String tempD1 = tempOldStartDate.toString();
				String tempD2 = tempNewStartDate.toString();
				if (tempD1 != null && tempD2 != null) {
					if (tempD1.equals(tempD2)) {
						return true;
					}
					logDebug("Same summary " + tempOldSummary + " but different date: " + tempD1 + " " + tempD2);
				}
			}
		}
		return false;
	}

	private ArrayList<Event> getCurrentItems(com.google.api.services.calendar.Calendar client, String tempDeadlineCalendarId) throws IOException {
		com.google.api.services.calendar.Calendar.Events.List tempList = client.events().list(tempDeadlineCalendarId);
		// https://developers.google.com/calendar/v3/reference/events/list
		tempList.setShowDeleted(true);

		ArrayList<Event> tempCurrentEvents = new ArrayList<Event>();
		while (true) {
			if (DEBUG) {
				InputStream tempExecuteAsInputStream = tempList.executeAsInputStream();
				int a = 0;
				while (0 < (a = tempExecuteAsInputStream.available())) {
					byte[] tempB = new byte[a];
					tempExecuteAsInputStream.read(tempB, 0, a);
					System.out.print(new String(tempB));
				}
			}
			Events tempExecute = config(tempList).execute();
			List<Event> tempItems = tempExecute.getItems();
			if (tempItems != null) {
				tempCurrentEvents.addAll(tempItems);
			}
			String tempNextPageToken = tempExecute.getNextPageToken();
			if (tempNextPageToken == null) {
				break;
			}
			logInfo("Next page currentSize=" + tempCurrentEvents.size());
			tempList.setPageToken(tempNextPageToken);
		}
		return tempCurrentEvents;
	}

	private <R extends AbstractGoogleClientRequest> R config(R aRequest) {
		return (R) aRequest.setDisableGZipContent(true);
	}

	/**
	 * opposite of {@link #createDeadlineFromGoogleEvent(Event)}
	 */
	private Event createGoogleEventFromDeadline(Deadline aDeadline) {
		Date tempTodayMorning = midnight(new Date());
		Event event = new Event();
		ExtendedProperties tempExtendedProperties = event.getExtendedProperties();
		if (tempExtendedProperties == null) {
			tempExtendedProperties = new ExtendedProperties();
			event.setExtendedProperties(tempExtendedProperties);
		}
		String tempTextWithoutRepeatingInfo = aDeadline.getTextWithoutRepeatingInfo();
		String tempText;
		Date tempStartDateTime;
		boolean tempIsWholeDayEvent;
		boolean tempIsOverdue;
		if (aDeadline.getWhen().before(tempTodayMorning)) {
			tempIsOverdue = true;
			tempText = OVERDUE_MARKER + tempTextWithoutRepeatingInfo;
			tempStartDateTime = tempTodayMorning;
			tempIsWholeDayEvent = true; // avoid 400 Bad Request "The specified time range is empty." #5
		} else {
			tempIsOverdue = false;
			tempText = tempTextWithoutRepeatingInfo;
			tempStartDateTime = aDeadline.getWhen();
			tempIsWholeDayEvent = aDeadline.isWholeDayEvent();
		}
		String tempId = aDeadline.getId();
		if (tempId != null) {
			if (tempIsOverdue) {
				// Create a new calendar entry in the google calendar (on next day)
				logInfo(tempText + " is overdue and so do not takeover Google Id " + tempId);
			} else {
				event.setId(tempId);
			}
		}
		tempExtendedProperties.put("TextWithoutRepeatingInfo", tempText);
		event.setSummary(tempText.trim());
		if (tempIsWholeDayEvent) {
			String tempDateOnlyString = new java.sql.Date(tempStartDateTime.getTime()).toString();
			event.setStart(new EventDateTime().setDate(new DateTime(tempDateOnlyString))); // force DateTime.dateOnly=true
			Calendar tempEndDateTimeCal = Calendar.getInstance();
			tempEndDateTimeCal.setTime(tempStartDateTime);
			tempEndDateTimeCal.setTimeZone(timeZone);
			tempEndDateTimeCal.add(Calendar.DAY_OF_YEAR, 1);
			tempEndDateTimeCal.add(Calendar.HOUR_OF_DAY, 2); // add 2 hours to ensure with DST it gives next date.
			tempDateOnlyString = new java.sql.Date(tempEndDateTimeCal.getTimeInMillis()).toString();
			event.setEnd(new EventDateTime().setDate(new DateTime(tempDateOnlyString)));
			// event.setEnd(null); //  "message" : "Missing end time.",
		} else {
			DateTime start = new DateTime(tempStartDateTime, timeZone);
			event.setStart(new EventDateTime().setDateTime(start));

			Date tempWhenEndDateTime;
			tempWhenEndDateTime = aDeadline.getWhenEndTime();
			if (tempWhenEndDateTime == null) {
				//event.setEnd(null); //  "message" : "Missing end time.",
				tempWhenEndDateTime = aDeadline.getWhen();
			}
			Date endDate = new Date(tempWhenEndDateTime.getTime());
			DateTime end = new DateTime(endDate, timeZone);
			event.setEnd(new EventDateTime().setDateTime(end));
		}
		String tempDescription = "";
		tempDescription += "READ-ONLY. See termin.txt";
		if (aDeadline.getRepeating() != null) {
			tempDescription += "\n\n - since (" + DeadlineGui.dateFormatWithDay.format(aDeadline.getRepeating()) + ")";
			tempDescription += "\n\n " + aDeadline.getInfo();
		}
		event.setDescription(tempDescription);
		return event;
	}

	private Date midnight(Date aDate) {
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(aDate);
		tempCal.set(Calendar.HOUR_OF_DAY, 0);
		tempCal.set(Calendar.MINUTE, 0);
		tempCal.set(Calendar.SECOND, 0);
		tempCal.set(Calendar.MILLISECOND, 0);
		return tempCal.getTime();
	}

	/**
	 * Opposite of {@link #createDeadlineFromGoogleEvent(Event)}
	 */
	private Deadline createDeadlineFromGoogleEvent(Event anEvent) {
		String tempSummary = getSummary(anEvent);
		Deadline tempDeadline = new Deadline();
		ExtendedProperties tempExtendedProperties = anEvent.getExtendedProperties();
		if (tempExtendedProperties != null) {
			String tempTextWithoutRepeatingInfo = (String) tempExtendedProperties.get("TextWithoutRepeatingInfo");
			if (tempTextWithoutRepeatingInfo != null) {
				tempDeadline.setTextWithoutRepeatingInfo(tempTextWithoutRepeatingInfo);
			} else {
				tempDeadline.setTextWithoutRepeatingInfo(tempSummary);
			}
		} else {
			tempDeadline.setTextWithoutRepeatingInfo(tempSummary);
		}

		EventDateTime tempStart = anEvent.getStart();
		Date tempWhen;
		if (tempStart.getDateTime() != null) {
			tempWhen = new Date(tempStart.getDateTime().getValue());
			EventDateTime tempEnd = anEvent.getEnd();
			if (tempEnd != null) {
				DateTime tempEndDateTime = tempEnd.getDateTime();
				if (tempEndDateTime != null) {
					tempDeadline.setWhenEndTime(new Date(tempEndDateTime.getValue()));
				}
			}
		} else {
			// Whole day
			tempWhen = midnight(new Date(tempStart.getDate().getValue()));
		}
		tempDeadline.setWhen(tempWhen);
		tempDeadline.setId(anEvent.getId());
		return tempDeadline;
	}

	private String getSummary(Event anEvent) {
		String tempGoogleSummary = anEvent.getSummary();
		String tempOneLineSummary = tempGoogleSummary.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
		String tempTrimmedSummary = tempOneLineSummary.trim();
		return tempTrimmedSummary;
	}

	/**
	 * @return the logListener
	 */
	public final LogListener getLogListener() {
		return logListener;
	}

	/**
	 * @param aLogListener the logListener to set
	 */
	public final void setLogListener(LogListener aLogListener) {
		logListener = aLogListener;
	}

}
