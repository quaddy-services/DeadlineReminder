package de.quaddy_services.deadlinereminder;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.Timer;

import de.quaddy_services.deadlinereminder.extern.GoogleSync;
import de.quaddy_services.deadlinereminder.file.FileStorage;
import de.quaddy_services.deadlinereminder.gui.DeadlineGui;

public class DeadlineReminder {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		updateLogger();
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				DeadlineReminder tempDeadlineReminder = new DeadlineReminder();
				tempDeadlineReminder.mainEventQueue();
			}
		});
	}

	private static void updateLogger() {
		File tempDir = new File(System.getProperty("user.home", ".") + "/DeadlineReminder");
		tempDir.mkdirs();
		try {
			FileHandler fh = new FileHandler(tempDir.getAbsolutePath() + "/DeadlineReminder.log");
			fh.setFormatter(new SimpleFormatter());
			Logger tempGlobal = Logger.getGlobal();
			Logger tempRootLogger = tempGlobal.getParent();
			tempRootLogger.addHandler(fh);
		} catch (Exception e) {
			e.printStackTrace();
			// ignore
		}
	}

	private Model model;
	private Logger LOGGER = Logger.getLogger("DeadlineReminder");
	private GoogleSync googleSync = new GoogleSync();

	protected void mainEventQueue() {
		try {
			LOGGER.log(Level.INFO, "Start");
			model = createModel();
			LOGGER.log(Level.INFO, new Date() + ": Found " + model.getOpenDeadlines().size() + " deadlines");
			googleSync.pushToGoogle(model.getOpenDeadlines());
			lastSyncSize = model.getOpenDeadlines().size();
			DeadlineGui tempGUI = new DeadlineGui();
			tempGUI.setModel(model);
			final JFrame tempFrame = new JFrame();
			tempFrame.setIconImage(loadIcon());
			tempFrame.setTitle("Deadline Reminder " + new Date());
			Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
			int w = d.width * 70 / 100;
			int h = d.height * 85 / 100;
			tempFrame.setSize(w, h);
			tempFrame.setLocation((d.width - w) / 2, (d.height - h) / 3);
			tempFrame.setAlwaysOnTop(false);
			tempFrame.getContentPane().add(tempGUI);

			tempFrame.setVisible(true);
			tempFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			tempFrame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent aE) {
					super.windowClosing(aE);
					LOGGER.info("Closing");
					exit();
				}
			});
			Timer tempTimer;
			int tempDelay = 600 * 1000;
			// tempDelay = 10 * 1000;
			tempTimer = new Timer(tempDelay, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent aE) {
					every10Minutes(tempFrame);
				}
			});
			tempTimer.setRepeats(true);
			tempTimer.start();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error", e);
		}
	}

	private Image loadIcon() throws IOException {
		InputStream tempIn = getClass().getClassLoader().getResourceAsStream("logo.jpg");
		BufferedImage tempIO = ImageIO.read(tempIn);
		tempIn.close();
		ImageIcon tempImageIcon = new ImageIcon(tempIO);
		return tempImageIcon.getImage();
	}

	private Model createModel() {
		Calendar tempCal = Calendar.getInstance();
		// Date tempFrom = tempCal.getTime();
		tempCal.add(Calendar.DAY_OF_YEAR, 400);
		Date tempTo = tempCal.getTime();
		Storage tempStorage = new FileStorage();
		List<Deadline> tempDeadlines = tempStorage.getOpenDeadlines(tempTo);
		Collections.sort(tempDeadlines, new DeadlineComparator());
		// for (Deadline tempDeadline : tempDeadlines) {
		// System.out.println(tempDeadline);
		// }
		LOGGER.log(Level.FINER, new Date() + ": Found " + tempDeadlines.size() + " deadlines");
		final Model tempModel = new Model();
		tempModel.setSourceInfo(tempStorage.getSourceInfo());
		tempModel.setOpenDeadlines(tempDeadlines);
		return tempModel;
	}

	private void exit() {
		LOGGER.log(Level.INFO, "exit");
		saveModel();
		exitApplicationNow();
	}

	private void exitApplicationNow() {
		LOGGER.log(Level.INFO, "Start exitThread");
		Thread tempExitThread = new Thread() {
			@Override
			public void run() {
				LOGGER.log(Level.INFO, "System.exit(0)");
				System.exit(0);
			}
		};
		tempExitThread.setName(tempExitThread.getName() + "-exitThread");
		tempExitThread.start();
	}

	private synchronized void saveModel() {
		LOGGER.log(Level.INFO, new Date() + ":SaveModel");
		Storage tempFileStorage = new FileStorage();
		tempFileStorage.saveConfirmedTasks(model.getOpenDeadlines());
	}

	private int lastSyncHour = 0;
	private int lastSyncSize = 0;

	private void every10Minutes(JFrame aFrame) {
		try {
			LOGGER.log(Level.FINER, new Date() + ":Check");
			boolean tempDoneAvailable = false;
			for (Deadline tempDeadline : model.getOpenDeadlines()) {
				if (tempDeadline.isDone()) {
					LOGGER.log(Level.INFO, "Done:" + tempDeadline);
					tempDoneAvailable = true;
					break;
				}
			}
			if (tempDoneAvailable) {
				saveModel();
			}
			model = createModel();

			Calendar tempCal = Calendar.getInstance();
			int tempHour = tempCal.get(Calendar.HOUR_OF_DAY);
			if (tempDoneAvailable || tempHour < lastSyncHour || model.getOpenDeadlines().size() != lastSyncSize) {
				// at least once a day.
				googleSync.pushToGoogle(model.getOpenDeadlines());
			}
			lastSyncHour = tempHour;
			lastSyncSize = model.getOpenDeadlines().size();

			DeadlineGui tempGUI = new DeadlineGui();
			tempGUI.setModel(model);
			Container tempContentPane = aFrame.getContentPane();
			tempContentPane.removeAll();
			tempContentPane.invalidate();
			tempContentPane.validate();
			tempContentPane.add(tempGUI);
			tempContentPane.invalidate();
			tempContentPane.validate();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
