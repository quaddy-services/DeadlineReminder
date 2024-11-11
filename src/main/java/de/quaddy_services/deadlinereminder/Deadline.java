package de.quaddy_services.deadlinereminder;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Deadline {
	private static final TimeZone TIME_ZONE = TimeZone.getDefault();
	private static final Logger LOGGER = LoggerFactory.getLogger(Deadline.class);

	public Deadline() {
		super();
	}

	private Date when;
	/**
	 * Same date as {@link #when} but different time.
	 */
	private Date whenEndTime;
	private String info;
	private boolean done;
	private Date repeating;
	private String textWithoutRepeatingInfo;
	private String id;
	private boolean deleted;
	/**
	 * is from termin-added-by-google.txt
	 */
	private boolean addedByGoogle;

	public Date getWhen() {
		return when;
	}

	public void setWhen(Date when) {
		this.when = when;
	}

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info.trim();
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Deadline [");
		if (when != null) {
			builder.append("when=");
			builder.append(when);
			builder.append(", ");
		}
		if (whenEndTime != null) {
			builder.append("whenEndTime=");
			builder.append(whenEndTime);
			builder.append(", ");
		}
		if (info != null) {
			builder.append("info=");
			builder.append(info);
			builder.append(", ");
		}
		builder.append("done=");
		builder.append(done);
		builder.append(", ");
		if (repeating != null) {
			builder.append("repeating=");
			builder.append(repeating);
			builder.append(", ");
		}
		if (textWithoutRepeatingInfo != null) {
			builder.append("textWithoutRepeatingInfo=");
			builder.append(textWithoutRepeatingInfo);
			builder.append(", ");
		}
		if (id != null) {
			builder.append("id=");
			builder.append(id);
			builder.append(", ");
		}
		builder.append("deleted=");
		builder.append(deleted);
		builder.append(", addedByGoogle=");
		builder.append(addedByGoogle);
		builder.append("]");
		return builder.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((info == null) ? 0 : info.hashCode());
		result = prime * result + ((when == null) ? 0 : when.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Deadline other = (Deadline) obj;
		if (info == null) {
			if (other.info != null) {
				return false;
			}
		} else if (other.info != null && !info.equals(other.info)) {
			return false;
		}
		if (when == null) {
			if (other.when != null) {
				return false;
			}
		} else if (!when.equals(other.when)) {
			return false;
		}
		return true;
	}

	public Date getRepeating() {
		return repeating;
	}

	public void setRepeating(Date aRepeating) {
		repeating = aRepeating;
	}

	public void setTextWithoutRepeatingInfo(String aTextWithoutRepeatingInfo) {
		textWithoutRepeatingInfo = aTextWithoutRepeatingInfo;

	}

	/**
	 * @return the textWithoutRepeatingInfo
	 */
	public final String getTextWithoutRepeatingInfo() {
		if (textWithoutRepeatingInfo == null) {
			return getInfo();
		}
		return textWithoutRepeatingInfo;
	}

	/**
	 * ID (e.g. GoogleId)
	 * @return
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param aId the id to set
	 */
	public final void setId(String aId) {
		id = aId;
	}

	public boolean isWholeDayEvent() {
		//TODO replace by boolean
		Calendar tempCal = Calendar.getInstance();
		tempCal.setTime(getWhen());
		boolean tempIsWholeDayEvent = tempCal.get(Calendar.HOUR_OF_DAY) == 0 && tempCal.get(Calendar.MINUTE) == 0;

		return tempIsWholeDayEvent;
	}

	private static DateFormat timeFormat = new SimpleDateFormat("HH:mm");

	/**
	 * Try to add a time. e.g. *1w 17:00 David Nachhilfe
	 *
	 * @param aDate
	 * @param aInfo
	 * @return
	 */
	public void extractTimeFromInfo() {
		//		private Date addTime(Date aDate, String aInfo) {
		String tempInfo = getTextWithoutRepeatingInfo();
		StringTokenizer tempTokens = new StringTokenizer(tempInfo, "* -");
		List<String> tempTimeTokens = new ArrayList<>();
		// consider first token only.
		if (tempTokens.hasMoreTokens()) {
			String tempToken = tempTokens.nextToken();
			if (tempToken.length() > 3 && Character.isDigit(tempToken.charAt(0))) {
				try {
					Date tempTime;
					tempTime = timeFormat.parse(tempToken);
					// found a valid time

					tempTimeTokens.add(tempToken);

					Date tempDateWithoutTime = getWhen();
					Calendar tempCal = Calendar.getInstance();
					tempCal.setTime(tempDateWithoutTime);
					Calendar tempTimeCal = Calendar.getInstance();
					tempTimeCal.setTime(tempTime);
					tempCal.add(Calendar.HOUR_OF_DAY, tempTimeCal.get(Calendar.HOUR_OF_DAY));
					tempCal.add(Calendar.MINUTE, tempTimeCal.get(Calendar.MINUTE));
					Date tempDateWithTime = tempCal.getTime();
					// Correct daylight savings
					int tempAmountTimeOffset = getZoneOffset(tempDateWithoutTime.getTime()) - getZoneOffset(tempDateWithTime.getTime());
					tempCal.add(Calendar.MILLISECOND, tempAmountTimeOffset);
					tempDateWithTime = tempCal.getTime();
					setWhen(tempDateWithTime);

					if (tempTokens.hasMoreTokens()) {
						// Maybe end-time?
						tempToken = tempTokens.nextToken();
						try {
							tempTime = timeFormat.parse(tempToken);

							// found a valid end time
							tempTimeTokens.add(tempToken);

							tempCal = Calendar.getInstance();
							tempCal.setTime(tempDateWithoutTime);
							tempTimeCal = Calendar.getInstance();
							tempTimeCal.setTime(tempTime);
							tempCal.add(Calendar.HOUR_OF_DAY, tempTimeCal.get(Calendar.HOUR_OF_DAY));
							tempCal.add(Calendar.MINUTE, tempTimeCal.get(Calendar.MINUTE));
							Date tempDateWithEndTime = tempCal.getTime();
							setWhenEndTime(tempDateWithEndTime);

						} catch (ParseException e) {
							// ignore
						} catch (RuntimeException e) {
							LOGGER.error("Ok?", e);
						}

					}

					removeTimeTokens(tempTimeTokens);
					return;
				} catch (ParseException e) {
					// ignore
				} catch (RuntimeException e) {
					LOGGER.error("Ok?", e);
				}
			}
		}

	}

	/**
	 *
	 */
	int getZoneOffset(long aTime) {
		return TIME_ZONE.getOffset(aTime);
	}

	private void removeTimeTokens(List<String> aTokens) {
		for (String tempToken : aTokens) {
			String tempInfo = getInfo();
			int tempPos = tempInfo.indexOf(tempToken);
			if (tempPos >= 0) {
				String tempStringUpToPos = tempInfo.substring(0, tempPos);
				if (tempStringUpToPos.endsWith("-")) {
					tempStringUpToPos = tempStringUpToPos.substring(0, tempStringUpToPos.length() - 1);
				} else if (tempStringUpToPos.endsWith("- ")) {
					tempStringUpToPos = tempStringUpToPos.substring(0, tempStringUpToPos.length() - 2);
				}
				String tempNewInfo = tempStringUpToPos + tempInfo.substring(tempPos + tempToken.length()).trim();
				tempNewInfo = tempNewInfo.trim();
				setInfo(tempNewInfo);

			}
		}
		for (String tempToken : aTokens) {
			String tempTextWithoutRepeatingInfo = textWithoutRepeatingInfo;
			if (tempTextWithoutRepeatingInfo != null) {
				int tempPos2 = tempTextWithoutRepeatingInfo.indexOf(tempToken);
				if (tempPos2 >= 0) {
					String tempStringUpToPos = tempTextWithoutRepeatingInfo.substring(0, tempPos2);
					if (tempStringUpToPos.endsWith("-")) {
						tempStringUpToPos = tempStringUpToPos.substring(0, tempStringUpToPos.length() - 1);
					} else if (tempStringUpToPos.endsWith("- ")) {
						tempStringUpToPos = tempStringUpToPos.substring(0, tempStringUpToPos.length() - 2);
					}
					String tempNewTextWithoutRepeatingInfo = tempStringUpToPos + tempTextWithoutRepeatingInfo.substring(tempPos2 + tempToken.length()).trim();
					tempNewTextWithoutRepeatingInfo = tempNewTextWithoutRepeatingInfo.trim();
					setTextWithoutRepeatingInfo(tempNewTextWithoutRepeatingInfo);
				}
			}
		}
	}

	/**
	 * @return the whenEndTime
	 */
	public final Date getWhenEndTime() {
		return whenEndTime;
	}

	/**
	 * @param aWhenEndTime the whenEndTime to set
	 */
	public final void setWhenEndTime(Date aWhenEndTime) {
		whenEndTime = aWhenEndTime;
	}

	public void setDeleted(boolean aDeletedFlag) {
		deleted = aDeletedFlag;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setAddedByGoogle(boolean anAddedByGoogle) {
		addedByGoogle = anAddedByGoogle;
	}

	public boolean isAddedByGoogle() {
		return addedByGoogle;
	}
}
