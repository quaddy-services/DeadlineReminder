package de.quaddy_services.deadlinereminder.extern;

import de.quaddy_services.deadlinereminder.Deadline;

/**
 *
 */
public interface DoneSelectionListener {

	/**
	 * The deadline is done.
	 */
	void deadlineDone(Deadline aDeadline);

	/**
	 * a new calendar entry needs to be added to termin.txt
	 */
	void addNewDeadline(Deadline aDeadline);
}
