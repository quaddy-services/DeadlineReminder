package de.quaddy_services.deadlinereminder;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	}

	private Model model;
	private Logger LOGGER = LoggerFactory.getLogger(DeadlineReminder.class);
	private GoogleSync googleSync;
	private DeadlineGui gui;

	protected void mainEventQueue() {
		try {
			LOGGER.info("Start");
			model = createModel();
			LOGGER.info(new Date() + ": Found " + model.getOpenDeadlines().size() + " deadlines");
			googleSync = new GoogleSync();
			gui = new DeadlineGui();

			googleSync.setLogListener(new StatusLogListener(gui));

			googleSync.pushToGoogle(model.getOpenDeadlines(), gui.createDoneSelectionListener());

			gui.setModel(model);
			gui.setModelLoaderMustBeReleadedListener(new ModelLoaderMustBeReleadedListener() {

				@Override
				public void reloadModelNextTime() {
					reloadModel();
				}

			});
			final JFrame tempFrame = new JFrame();
			tempFrame.setIconImage(loadIcon());
			tempFrame.setTitle("Deadline Reminder " + new Date());
			Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
			int w = d.width * 70 / 100;
			int h = d.height * 85 / 100;
			tempFrame.setSize(w, h);
			tempFrame.setLocation((d.width - w) / 2, (d.height - h) / 3);
			tempFrame.setAlwaysOnTop(false);
			tempFrame.getContentPane().add(gui);

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
					every10Minutes();
				}
			});
			tempTimer.setRepeats(true);
			tempTimer.start();
		} catch (Exception e) {
			LOGGER.error("Error", e);
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
		LOGGER.debug(new Date() + ": Found " + tempDeadlines.size() + " deadlines");
		final Model tempModel = new Model();
		tempModel.setSourceInfo(tempStorage.getSourceInfo());
		tempModel.setOpenDeadlines(tempDeadlines);
		return tempModel;
	}

	private void exit() {
		LOGGER.info("exit");
		saveModel();
		exitApplicationNow();
	}

	private void exitApplicationNow() {
		LOGGER.info("Start exitThread");
		Thread tempExitThread = new Thread() {
			@Override
			public void run() {
				LOGGER.info("System.exit(0)");
				System.exit(0);
			}
		};
		tempExitThread.setName(tempExitThread.getName() + "-exitThread");
		tempExitThread.start();
	}

	private synchronized void saveModel() {
		LOGGER.info(new Date() + ":SaveModel");
		Storage tempFileStorage = new FileStorage();
		tempFileStorage.saveConfirmedTasks(model.getOpenDeadlines());
		try {
			List<Deadline> tempAddedFromGoogle = new ArrayList<>(model.getAddedFromGoogle());
			model.getAddedFromGoogle().clear();
			tempFileStorage.addFromGroogle(tempAddedFromGoogle);
		} catch (IOException e) {
			throw new RuntimeException("Error", e);
		}
		try {
			List<Deadline> tempRemovedFromGoogle = new ArrayList<>(model.getRemovedFromGoogle());
			model.getRemovedFromGoogle().clear();
			tempFileStorage.removeFromGroogle(tempRemovedFromGoogle);
		} catch (IOException e) {
			throw new RuntimeException("Error", e);
		}
	}

	private void every10Minutes() {
		reloadModel();

		googleSync.pushToGoogle(model.getOpenDeadlines(), gui.createDoneSelectionListener());

	}

	/**
	 *
	 */
	private void reloadModel() {
		try {
			LOGGER.debug(new Date() + ":Check");
			boolean tempDoneAvailable = false;
			for (Deadline tempDeadline : model.getOpenDeadlines()) {
				if (tempDeadline.isDone()) {
					LOGGER.info("Done:" + tempDeadline);
					tempDoneAvailable = true;
					break;
				}
			}
			if (tempDoneAvailable || !model.getAddedFromGoogle().isEmpty() || !model.getRemovedFromGoogle().isEmpty()) {
				saveModel();
			}
			model = createModel();

			gui.setModel(model);
			gui.invalidate();
			gui.validate();
			gui.invalidate();
			gui.validate();
		} catch (Exception e) {
			LOGGER.error("Error", e);
		}
	}
}
