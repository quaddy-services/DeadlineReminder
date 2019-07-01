package de.quaddy_services.deadlinereminder.extern;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar.Events.Delete;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
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
	private static DateTime lastSyncStarted;

	private Thread t = null;
	private long threadKill;

	private LogListener logListener = null;

	public void pushToGoogle(final List<Deadline> aOpenDeadlines, final DoneSelectionListener aDoneSelectionListener) {
		if (t != null) {
			logWarn("Already active: " + t);
			if (threadKill < System.currentTimeMillis()) {
				logWarn("Interrupt: " + t);
				t.interrupt();
			} else {
				return;
			}
		}
		threadKill = System.currentTimeMillis() + 300 * 1000;
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
				} catch (SocketTimeoutException e) {
					logError("Retry later...", e);
				} catch (Exception e) {
					logError("Error on authenticatino", e);
					String tempUserName = System.getProperty("user.name", "-");
					PersistentCredentialStore tempPersistentCredentialStore = new PersistentCredentialStore();
					tempPersistentCredentialStore.delete(tempUserName);
					// try again
					try {
						logInfo("Again:Start");
						push(aOpenDeadlines, aDoneSelectionListener);
						logInfo("Again:Finished");
					} catch (Exception e2) {
						logError("Again:Error", e2);
					}

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
		new GoogleSync().push(tempDeadlines, new DoneSelectionListener() {

			@Override
			public void deadlineDone(Deadline aDeadline) {
				LOGGER.info("deadlineDone: " + aDeadline);
			}

			@Override
			public void addNewDeadline(Deadline aDeadline) {
				LOGGER.info("addNewDeadline: " + aDeadline);
			}
		});

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
		JsonFactory JSON_FACTORY = new JacksonFactory();
		// authorization
		Credential credential = OAuth2Native.authorize(HTTP_TRANSPORT, JSON_FACTORY, new LocalServerReceiver(), Arrays.asList(CalendarScopes.CALENDAR));
		// set up global Calendar instance
		com.google.api.services.calendar.Calendar client = new com.google.api.services.calendar.Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName("Google-DeadlineReminder/1.0").setHttpRequestInitializer(credential).build();

		return push(client, aOpenDeadlines, aDoneSelectionListener);
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

		CalendarList tempCalendarList = config(client.calendarList().list()).execute();
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

		Calendar tempKeepOld = Calendar.getInstance();
		tempKeepOld.add(Calendar.YEAR, -2);

		Calendar tempTooFarAway = Calendar.getInstance();
		tempTooFarAway.add(Calendar.YEAR, 2);

		Map<Event, Deadline> tempNewEvents = new HashMap<>();
		for (Deadline tempDeadline : aOpenDeadlines) {
			if (tempDeadline.getWhen().after(tempTooFarAway.getTime())) {
				continue;
			}
			Event event = createGoogleEventFromDeadline(tempDeadline);
			tempNewEvents.put(event, tempDeadline);
		}
		logInfo("Matching local events: " + tempNewEvents.size());
		ArrayList<Event> tempCurrentEvents;
		tempCurrentEvents = getCurrentItems(client, tempDeadlineCalendarId);
		logInfo("Already at Google (including history): " + tempCurrentEvents.size());
		long tempNow = System.currentTimeMillis();
		for (Iterator<Event> iCurrent = tempCurrentEvents.iterator(); iCurrent.hasNext();) {
			Event tempEvent = iCurrent.next();
			EventDateTime tempStart = tempEvent.getStart();
			DateTime tempDate = tempStart.getDate();
			String tempSummary = tempEvent.getSummary();
			if (tempSummary.startsWith(OVERDUE_MARKER)) {
				// Overdue events are deleted and recreated next day. The original event is
				// already kept in calendar.
			} else if (isContainedIn(tempNewEvents.keySet(), tempEvent)) {
				// Is still open. Avoid adding past events twice.
			} else {
				if (tempDate != null && tempKeepOld.getTime().getTime() < tempDate.getValue() && tempDate.getValue() < tempNow) {
					// Keep finished event.
					iCurrent.remove();
					continue;
				}
				DateTime tempDateTime = tempStart.getDateTime();
				if (tempDateTime != null && tempKeepOld.getTime().getTime() < tempDateTime.getValue() && tempDateTime.getValue() < tempNow) {
					// Keep finished event.
					iCurrent.remove();
					continue;
				}
			}
		}
		logInfo("Already at Google to be synced: " + tempCurrentEvents.size());
		for (Iterator<Event> iCurrent = tempCurrentEvents.iterator(); iCurrent.hasNext();) {
			Event tempEvent = iCurrent.next();
			boolean tempRemoved = false;
			for (Iterator<Map.Entry<Event, Deadline>> iNew = tempNewEvents.entrySet().iterator(); iNew.hasNext();) {
				Map.Entry<Event, Deadline> tempMapEntry = iNew.next();
				Event tempNewEvent = tempMapEntry.getKey();
				if (isSame(tempEvent, tempNewEvent)) {
					if ("transparent".equals(tempEvent.getTransparency())) {
						Deadline tempDeadline = tempMapEntry.getValue();
						logInfo("Google calendar entry was marked available and so make it done. tempDeadline=" + tempDeadline);
						tempDeadline.setDone(true);
						aDoneSelectionListener.deadlineDone(tempDeadline);
					}
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
			String tempSummary = tempEvent.getSummary();
			DateTime tempLastSyncStarted = getLastSyncStarted();
			if (tempLastSyncStarted == null || tempLastSyncStarted.getValue() < tempEvent.getCreated().getValue()) {
				logInfo("Looks like it is a manual created event in Google=" + tempStart + " " + tempSummary);
				Deadline tempDeadline = createDeadlineFromGoogleEvent(tempEvent);
				// tempNewEvents needs not to be updated as event is already at google.
				// and next time it will be detected as normally added one.
				aDoneSelectionListener.addNewDeadline(tempDeadline);
				iCurrent.remove();
				continue;
			}

			logInfo("No matching current event found for Google=" + tempStart + " " + tempSummary);
		}
		for (Event tempEvent : tempCurrentEvents) {
			String tempSummary = tempEvent.getSummary();
			EventDateTime tempStart = tempEvent.getStart();

			Delete tempDelete = client.events().delete(tempDeadlineCalendarId, tempEvent.getId());
			config(tempDelete).execute();
			logInfo("Deleted " + tempStart + " " + tempSummary + " " + tempEvent);
			slowDown();
		}
		for (Event tempEvent : tempNewEvents.keySet()) {
			EventDateTime tempStart = tempEvent.getStart();
			Event tempResult = config(client.events().insert(tempDeadlineCalendarId, tempEvent)).execute();
			logInfo("Added " + tempStart + " " + tempEvent.getSummary() + " " + tempResult);
			slowDown();
		}
		setLastSyncStarted(new DateTime(tempStartMillis));
		return true;
	}

	/**
	 *
	 */
	private static synchronized void setLastSyncStarted(DateTime aDateTime) {
		lastSyncStarted = aDateTime;
	}

	/**
	 *
	 */
	private static synchronized DateTime getLastSyncStarted() {
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
		String tempOldSummary = aOldEvent.getSummary();
		String tempNewSummary = aNewEvent.getSummary();
		if (tempOldSummary.equals(tempNewSummary)) {
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
		Calendar tempCal = Calendar.getInstance();
		tempCal.set(Calendar.HOUR_OF_DAY, 0);
		tempCal.set(Calendar.MINUTE, 0);
		tempCal.set(Calendar.SECOND, 0);
		tempCal.set(Calendar.MILLISECOND, 0);
		Date tempToday = tempCal.getTime();
		tempCal.set(Calendar.HOUR_OF_DAY, 0);
		Date tempTodayMorning = tempCal.getTime();
		Event event = new Event();
		Date startDate;
		String tempText;
		if (aDeadline.getWhen().before(tempToday)) {
			tempText = OVERDUE_MARKER + aDeadline.getTextWithoutRepeatingInfo() + " !" + DATE_FORMAT.format(aDeadline.getWhen()) + "!";
			startDate = tempTodayMorning;
		} else {
			tempText = aDeadline.getTextWithoutRepeatingInfo();
			startDate = aDeadline.getWhen();
		}
		if (aDeadline.getEndPoint() != null) {
			tempText += " (-" + DeadlineGui.dateFormatWithDay.format(aDeadline.getEndPoint()) + ")";
		} else if (aDeadline.getRepeating() != null) {
			tempText += " (" + DeadlineGui.dateFormatWithDay.format(aDeadline.getRepeating()) + ")";
		}
		event.setSummary(tempText);
		tempCal.setTime(startDate);
		boolean tempIsWholeDayEvent = tempCal.get(Calendar.HOUR_OF_DAY) == 0 && tempCal.get(Calendar.MINUTE) == 0;
		if (tempIsWholeDayEvent) {
			event.setStart(new EventDateTime().setDate(new DateTime(new java.sql.Date(startDate.getTime()).toString())));
			event.setEnd(new EventDateTime().setDate(new DateTime(new java.sql.Date(startDate.getTime() + 24 * 3600000).toString())));
		} else {
			Date endDate = new Date(startDate.getTime() + 3600000);
			DateTime start = new DateTime(startDate, TimeZone.getDefault());
			event.setStart(new EventDateTime().setDateTime(start));
			DateTime end = new DateTime(endDate, TimeZone.getDefault());
			event.setEnd(new EventDateTime().setDateTime(end));
		}
		return event;
	}

	/**
	 * Opposite of {@link #createDeadlineFromGoogleEvent(Event)}
	 */
	private Deadline createDeadlineFromGoogleEvent(Event anEvent) {
		String tempSummary = anEvent.getSummary();
		Deadline tempDeadline = new Deadline();
		tempDeadline.setTextWithoutRepeatingInfo(tempSummary);

		EventDateTime tempStart = anEvent.getStart();
		Date tempWhen;
		if (tempStart.getDateTime() != null) {
			tempWhen = new Date(tempStart.getDateTime().getValue());
		} else {
			// Whole day
			Calendar tempCal = Calendar.getInstance();
			tempCal.setTimeInMillis(tempStart.getDate().getValue());
			tempCal.set(Calendar.HOUR, 0);
			tempCal.set(Calendar.MINUTE, 0);
			tempCal.set(Calendar.SECOND, 0);
			tempCal.set(Calendar.MILLISECOND, 0);
			tempWhen = tempCal.getTime();
		}
		tempDeadline.setWhen(tempWhen);
		return tempDeadline;
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
