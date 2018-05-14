package de.quaddy_services.deadlinereminder;

import java.awt.EventQueue;
import java.util.Date;

import de.quaddy_services.deadlinereminder.extern.LogListener;
import de.quaddy_services.deadlinereminder.gui.DeadlineGui;

public class StatusLogListener implements LogListener {

	private DeadlineGui gui;

	public StatusLogListener(DeadlineGui aGUI) {
		gui = aGUI;
	}

	@Override
	public void warn(String aString) {
		updateText(aString);

	}

	private void updateText(String aString) {
		String tempText = new Date() + " GoogleSync: " + aString;
		EventQueue.invokeLater(() -> {
			gui.setStatus(tempText);
		});
	}

	@Override
	public void info(String aString) {
		updateText(aString);

	}

	@Override
	public void error(String aString) {
		updateText(aString);

	}

}
