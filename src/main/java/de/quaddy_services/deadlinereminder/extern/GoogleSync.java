package de.quaddy_services.deadlinereminder.extern;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.api.client.auth.oauth2.Credential;
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

/**
 * https://code.google.com/apis/console/
 * 
 * @author User
 * 
 */
public class GoogleSync {
	private static final Logger LOGGER = Logger.getLogger("GoogleSync");
	private static final boolean DEBUG = false;

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yy");

	private Thread t = null;
	private long threadKill;

	public void pushToGoogle(final List<Deadline> aOpenDeadlines) {
		if (t != null) {
			LOGGER.warning("Already active: " + t);
			if (threadKill < System.currentTimeMillis()) {
				LOGGER.warning("Interrupt: " + t);
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
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, "Error", e);
					String tempUserName = System.getProperty("user.name", "-");
					if (new PersistentCredentialStore().delete(tempUserName)) {
						// try again
						try {
							LOGGER.log(Level.INFO, "Again:Start");
							push(aOpenDeadlines);
							LOGGER.log(Level.INFO, "Again:Finished");
						} catch (Exception e2) {
							LOGGER.log(Level.SEVERE, "Again:Error", e2);
						}
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
		com.google.api.services.calendar.Calendar client = com.google.api.services.calendar.Calendar
				.builder(HTTP_TRANSPORT, JSON_FACTORY).setApplicationName("Google-DeadlineReminder/1.0")
				.setHttpRequestInitializer(credential).build();

		push(client, aOpenDeadlines);
	}

	private void push(com.google.api.services.calendar.Calendar client, List<Deadline> aOpenDeadlines)
			throws IOException {
		CalendarList tempCalendarList = client.calendarList().list().execute();
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
		ArrayList<Event> tempCurrentEvents;
		tempCurrentEvents = getCurrentItems(client, tempDeadlineCalendarId);
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
			tempDelete.execute();
			LOGGER.log(Level.INFO, "Deleted " + tempEvent.getSummary() + " " + tempEvent);
		}
		for (Event tempEvent : tempNewEvents) {
			Event tempResult = client.events().insert(tempDeadlineCalendarId, tempEvent).execute();
			LOGGER.log(Level.INFO, "Added " + tempEvent.getSummary() + " " + tempResult);
		}
	}

	private boolean isSame(Event aEvent, Event aNewEvent) {
		if (aEvent.getSummary().equals(aNewEvent.getSummary())) {
			DateTime tempDT1 = aEvent.getStart().getDateTime();
			DateTime tempDT2 = aNewEvent.getStart().getDateTime();
			if (tempDT1 != null && tempDT2 != null && tempDT1.equals(tempDT2)) {
				return true;
			}
			String tempD1 = aEvent.getStart().getDate();
			String tempD2 = aNewEvent.getStart().getDate();
			if (tempD1 != null && tempD2 != null && tempD1.equals(tempD2)) {
				return true;
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
			Events tempExecute = tempList.execute();
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
		if (aDeadline.getWhen().before(tempToday)) {
			event.setSummary(DATE_FORMAT.format(aDeadline.getWhen()) + "!" + aDeadline.getInfo());
			startDate = tempTodayMorning;
		} else {
			event.setSummary(aDeadline.getInfo());
			startDate = aDeadline.getWhen();
		}
		tempCal.setTime(startDate);
		boolean tempIsWholeDayEvent = tempCal.get(Calendar.HOUR_OF_DAY) == 0 && tempCal.get(Calendar.MINUTE) == 0;
		if (tempIsWholeDayEvent) {
			event.setStart(new EventDateTime().setDate(new java.sql.Date(startDate.getTime()).toString()));
			event.setEnd(new EventDateTime().setDate(new java.sql.Date(startDate.getTime() + 24 * 3600000).toString()));
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
