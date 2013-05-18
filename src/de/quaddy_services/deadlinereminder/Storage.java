package de.quaddy_services.deadlinereminder;

import java.util.Date;
import java.util.List;

public interface Storage {
	List<Deadline> getOpenDeadlines(Date aTo);

	void saveConfirmedTasks(List<Deadline> aDeadlines);
	
	String getSourceInfo();
}
