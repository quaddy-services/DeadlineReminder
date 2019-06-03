package de.quaddy_services.deadlinereminder;

import java.util.Date;

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
		} else if (!info.equals(other.info))
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
}
