package de.quaddy_services.deadlinereminder;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;

public class Deadline {
	public Deadline() {
		super();
	}

	private Date when;
	private String info;
	private boolean done;
	private Date repeating;
	private String textWithoutRepeatingInfo;
	private Date endPoint;
	private String id;

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
		this.info = info;
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Deadline [when=" + when + ", info=" + info + ", done=" + done + ", repeating=" + repeating + ", textWithoutRepeatingInfo="
				+ textWithoutRepeatingInfo + ", endPoint=" + endPoint + "]";
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
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Deadline other = (Deadline) obj;
		if (info == null) {
			if (other.info != null)
				return false;
		} else if (!info.trim().equals(other.info.trim()))
			return false;
		if (when == null) {
			if (other.when != null)
				return false;
		} else if (!when.equals(other.when))
			return false;
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

	public void setEndPoint(Date aEndPoint) {
		endPoint = aEndPoint;
	}

	/**
	 * @return the endPoint
	 */
	public final Date getEndPoint() {
		return endPoint;
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
		String tempInfo = getInfo();
		StringTokenizer tempTokens = new StringTokenizer(tempInfo, "* ");
		while (tempTokens.hasMoreTokens()) {
			String tempToken = tempTokens.nextToken();
			if (tempToken.length() > 3 && Character.isDigit(tempToken.charAt(0))) {
				try {
					Date tempTime;
					tempTime = timeFormat.parse(tempToken);
					// found a valid time

					Date tempDateWithoutTime = getWhen();
					Calendar tempCal = Calendar.getInstance();
					tempCal.setTime(tempDateWithoutTime);
					Calendar tempTimeCal = Calendar.getInstance();
					tempTimeCal.setTime(tempTime);
					tempCal.add(Calendar.HOUR_OF_DAY, tempTimeCal.get(Calendar.HOUR_OF_DAY));
					tempCal.add(Calendar.MINUTE, tempTimeCal.get(Calendar.MINUTE));
					Date tempDateWithTime = tempCal.getTime();
					setWhen(tempDateWithTime);

					int tempPos = tempInfo.indexOf(tempToken);
					String tempNewInfo = tempInfo.substring(0, tempPos) + tempInfo.substring(tempPos + tempToken.length()).trim();
					setInfo(tempNewInfo.trim());

					String tempTextWithoutRepeatingInfo = textWithoutRepeatingInfo;
					if (tempTextWithoutRepeatingInfo != null) {
						int tempPos2 = tempTextWithoutRepeatingInfo.indexOf(tempToken);
						String tempNewTextWithoutRepeatingInfo = tempTextWithoutRepeatingInfo.substring(0, tempPos2)
								+ tempTextWithoutRepeatingInfo.substring(tempPos2 + tempToken.length()).trim();
						setTextWithoutRepeatingInfo(tempNewTextWithoutRepeatingInfo);
					}
					return;
				} catch (ParseException e) {
					// ignore
				}
			}
		}

	}
}
