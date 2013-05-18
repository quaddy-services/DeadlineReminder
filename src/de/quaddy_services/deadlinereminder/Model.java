package de.quaddy_services.deadlinereminder;

import java.util.List;

public class Model {
	private List<Deadline> openDeadlines;
	private String sourceInfo;

	public List<Deadline> getOpenDeadlines() {
		return openDeadlines;
	}

	public void setOpenDeadlines(List<Deadline> aOpenDeadlines) {
		openDeadlines = aOpenDeadlines;
	}

	public String getSourceInfo() {
		return sourceInfo;
	}

	public void setSourceInfo(String aSourceInfo) {
		sourceInfo = aSourceInfo;
	}
	
	
}
