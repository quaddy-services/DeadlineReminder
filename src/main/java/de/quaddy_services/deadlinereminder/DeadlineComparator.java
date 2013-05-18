package de.quaddy_services.deadlinereminder;

import java.util.Comparator;

public class DeadlineComparator implements Comparator<Deadline> {


	@Override
	public int compare(Deadline o1, Deadline o2) {
		int tempCompareTo = o1.getWhen().compareTo(o2.getWhen());
		if (tempCompareTo == 0) {
			return o1.getInfo().compareTo(o2.getInfo());
		}
		return tempCompareTo;
	}

}
