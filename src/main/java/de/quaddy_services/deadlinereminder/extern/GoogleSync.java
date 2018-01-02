package de.quaddy_services.deadlinereminder.extern;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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
	private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSync.class);
	private static final boolean DEBUG = false;

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yy");

	private Thread t = null;
	private long threadKill;

	public void pushToGoogle(final List<Deadline> aOpenDeadlines) {
		if (t != null) {
			LOGGER.warn("Already active: " + t);
			if (threadKill < System.currentTimeMillis()) {
				LOGGER.warn("Interrupt: " + t);
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
					LOGGER.info("Start");
					push(aOpenDeadlines);
					LOGGER.info("Finished");
				} catch (SocketTimeoutException e) {
					LOGGER.error("Retry later...", e);
				} catch (Exception e) {
					LOGGER.error("Error on authenticatino", e);
					String tempUserName = System.getProperty("user.name", "-");
					PersistentCredentialStore tempPersistentCredentialStore = new PersistentCredentialStore();
					tempPersistentCredentialStore.delete(tempUserName);
					// try again
					try {
						LOGGER.info("Again:Start");
						push(aOpenDeadlines);
						LOGGER.info("Again:Finished");
					} catch (Exception e2) {
						LOGGER.error("Again:Error", e2);
					}

				} finally {
					t = null;
				}

			}
		};
		t.setName("GoogleSync-" + t.getName());
		t.start();
	}

	public static void main(String[] args) throws Exception {
		Calendar tempCal = Calendar.getInstance();
		// Date tempFrom = tempCal.getTime();
		tempCal.add(Calendar.DAY_OF_YEAR, 400);
		Date tempTo = tempCal.getTime();
		Storage tempStorage = new FileStorage();
		List<Deadline> tempDeadlines = tempStorage.getOpenDeadlines(tempTo);
		new GoogleSync().push(tempDeadlines);

	}

	/**
	 * https://developers.google.com/google-apps/calendar/
	 * 
	 * @param aOpenDeadlines
	 * @throws Exception
	 */
	protected void push(List<Deadline> aOpenDeadlines) throws Exception {
		/** Global instance of the HTTP transport. */
		HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

		/** Global instance of the JSON factory. */
		JsonFactory JSON_FACTORY = new JacksonFactory();
		// authorization
		Credential credential = OAuth2Native.authorize(HTTP_TRANSPORT, JSON_FACTORY, new LocalServerReceiver(),
				Arrays.asList(CalendarScopes.CALENDAR));
		// set up global Calendar instance
		com.google.api.services.calendar.Calendar client = new com.google.api.services.calendar.Calendar.Builder(
				HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName("Google-DeadlineReminder/1.0")
				.setHttpRequestInitializer(credential).build();

		push(client, aOpenDeadlines);
	}

	private void push(com.google.api.services.calendar.Calendar client, List<Deadline> aOpenDeadlines)
			throws IOException {

		CalendarList tempCalendarList = config(client.calendarList().list()).execute();
		String tempDeadlineCalendarId = null;
		if (tempCalendarList.getItems() != null) {
			for (CalendarListEntry tempEntry : tempCalendarList.getItems()) {
				if (tempEntry.getSummary().startsWith("Deadline")) {
					tempDeadlineCalendarId = tempEntry.getId();
				}
			}
		}

		Calendar tempTooFarAway = Calendar.getInstance();
		tempTooFarAway.add(Calendar.MONTH, 12);
		List<Event> tempNewEvents = new ArrayList<Event>();
		for (Deadline tempDeadline : aOpenDeadlines) {
			if (tempDeadline.getWhen().after(tempTooFarAway.getTime())) {
				continue;
			}
			Event event = newEvent(tempDeadline);
			tempNewEvents.add(event);
		}
		LOGGER.info("Matching local events: " + tempNewEvents.size());
		ArrayList<Event> tempCurrentEvents;
		tempCurrentEvents = getCurrentItems(client, tempDeadlineCalendarId);
		LOGGER.info("Already at Google: " + tempCurrentEvents.size());
		for (Iterator<Event> iCurrent = tempCurrentEvents.iterator(); iCurrent.hasNext();) {
			Event tempEvent = iCurrent.next();
			for (Iterator<Event> iNew = tempNewEvents.iterator(); iNew.hasNext();) {
				Event tempNewEvent = iNew.next();
				if (isSame(tempEvent, tempNewEvent)) {
					iCurrent.remove();
					iNew.remove();
					break;
				}
			}
		}
		for (Event tempEvent : tempCurrentEvents) {
			Delete tempDelete = client.events().delete(tempDeadlineCalendarId, tempEvent.getId());
			config(tempDelete).execute();
			LOGGER.info("Deleted " + tempEvent.getSummary() + " " + tempEvent);
			slowDown();
		}
		for (Event tempEvent : tempNewEvents) {
			Event tempResult = config(client.events().insert(tempDeadlineCalendarId, tempEvent)).execute();
			LOGGER.info("Added " + tempEvent.getSummary() + " " + tempResult);
			slowDown();
		}
	}

	/**
	 * Avoid
	 * 
	 * com.google.api.client.googleapis.json.GoogleJsonResponseException: 403 Forbidden
	{
	"code" : 403,
	"errors" : [ {
	"domain" : "usageLimits",
	"message" : "Rate Limit Exceeded",
	"reason" : "rateLimitExceeded"
	} ],
	"message" : "Rate Limit Exceeded"

	 */
	private void slowDown() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			LOGGER.warn("Error", e);
		}
	}

	private boolean isSame(Event aEvent, Event aNewEvent) {
		if (aEvent.getSummary().equals(aNewEvent.getSummary())) {
			EventDateTime tempOldStart = aEvent.getStart();
			DateTime tempDT1 = tempOldStart.getDateTime();
			EventDateTime tempNewStart = aNewEvent.getStart();
			DateTime tempDT2 = tempNewStart.getDateTime();
			if (tempDT1 != null && tempDT2 != null && tempDT1.equals(tempDT2)) {
				return true;
			}
			DateTime tempOldStartDate = tempOldStart.getDate();
			DateTime tempNewStartDate = tempNewStart.getDate();
			if (tempOldStartDate != null && tempNewStartDate != null) {
				String tempD1 = tempOldStartDate.toString();
				String tempD2 = tempNewStartDate.toString();
				if (tempD1 != null && tempD2 != null && tempD1.equals(tempD2)) {
					return true;
				}
			}
		}
		return false;
	}

	private ArrayList<Event> getCurrentItems(com.google.api.services.calendar.Calendar client,
			String tempDeadlineCalendarId) throws IOException {
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

	private Event newEvent(Deadline aDeadline) {
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
			tempText = DATE_FORMAT.format(aDeadline.getWhen()) + "!" + aDeadline.getInfo();
			startDate = tempTodayMorning;
		} else {
			tempText = aDeadline.getInfo();
			startDate = aDeadline.getWhen();
		}
		if (aDeadline.getRepeating() != null) {
			tempText += " (" + DeadlineGui.dateFormat.format(aDeadline.getRepeating()) + ")";
		}
		event.setSummary(tempText);
		tempCal.setTime(startDate);
		boolean tempIsWholeDayEvent = tempCal.get(Calendar.HOUR_OF_DAY) == 0 && tempCal.get(Calendar.MINUTE) == 0;
		if (tempIsWholeDayEvent) {
			event.setStart(new EventDateTime().setDate(new DateTime(new java.sql.Date(startDate.getTime()).toString())));
			event.setEnd(new EventDateTime().setDate(new DateTime(new java.sql.Date(startDate.getTime() + 24 * 3600000)
					.toString())));
		} else {
			Date endDate = new Date(startDate.getTime() + 3600000);
			DateTime start = new DateTime(startDate, TimeZone.getDefault());
			event.setStart(new EventDateTime().setDateTime(start));
			DateTime end = new DateTime(endDate, TimeZone.getDefault());
			event.setEnd(new EventDateTime().setDateTime(end));
		}
		return event;
	}

}
