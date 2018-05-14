package de.quaddy_services.deadlinereminder;

import java.util.Date;

public class Deadline {
	private Date when;
	private String info;
	private boolean done;
	private Date repeating;
	private String textWithoutRepeatingInfo;

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

	@Override
	public String toString() {
		return when + ": " + info + " " + done;
	}

	@Override
	public int hashCode() {
		return when.hashCode() + info.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		Deadline tempOther = (Deadline) obj;
		return when.equals(tempOther.when) && info.equals(tempOther.info);
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
}
