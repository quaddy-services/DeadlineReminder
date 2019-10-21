package de.quaddy_services.deadlinereminder.gui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.quaddy_services.deadlinereminder.Deadline;
import de.quaddy_services.deadlinereminder.Model;
import de.quaddy_services.deadlinereminder.ModelLoaderMustBeReleadedListener;
import de.quaddy_services.deadlinereminder.extern.DoneSelectionListener;

public class DeadlineGui extends JPanel {
	/**
	 * Attention, this format is different on JDK8 and JDK11
	 * So. 04.12.2016
	 *  vs
	 * So 04.12.2016 
	 */
	public static DateFormat dateFormatWithDay = new SimpleDateFormat("EE dd.MM.yyyy");
	private static DateFormat timeFormat = new SimpleDateFormat("HH:mm");
	public static DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
	private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineGui.class);

	private JPanel statusPanel = new JPanel();
	private JLabel statusLine = new JLabel();

	private Map<Deadline, JCheckBox> deadlineToCheckBoxMap = new HashMap<>();
	private ModelLoaderMustBeReleadedListener modelLoaderMustBeReleadedListener;
	private Model model;

	public void setModel(Model aModel) {
		model = aModel;
		removeAll();
		deadlineToCheckBoxMap.clear();
		setLayout(new GridBagLayout());
		GridBagConstraints tempGBC1 = new GridBagConstraints();
		tempGBC1.gridx = 0;
		tempGBC1.gridy = 0;
		tempGBC1.weightx = 1.0;
		tempGBC1.weighty = 1.0;
		tempGBC1.fill = GridBagConstraints.BOTH;

		JPanel tempContentPanel = new JPanel();
		JScrollPane tempScroll = new JScrollPane(tempContentPanel);
		// For mouseWheele:
		tempScroll.getVerticalScrollBar().setUnitIncrement(20);
		add(tempScroll, tempGBC1);

		tempGBC1.gridy++;
		tempGBC1.weighty = 0.0;
		add(statusPanel, tempGBC1);
		statusPanel.setLayout(new CardLayout());
		statusPanel.add(statusLine, "all");

		tempContentPanel.setLayout(new GridBagLayout());
		GridBagConstraints tempGBC = new GridBagConstraints();
		tempGBC.fill = GridBagConstraints.HORIZONTAL;
		tempGBC.anchor = GridBagConstraints.WEST;
		tempGBC.gridx = 0;
		tempGBC.gridy = 0;
		tempGBC.insets = new Insets(0, 0, -6, 0);
		tempGBC.weightx = 1.0;
		Calendar tempCal = Calendar.getInstance();
		Date tempToday = tempCal.getTime();
		tempCal.add(Calendar.DAY_OF_YEAR, 2);
		Date tempOverTomorow = tempCal.getTime();
		tempCal.add(Calendar.DAY_OF_YEAR, 5);
		Date tempNextWeek = tempCal.getTime();
		tempCal.add(Calendar.DAY_OF_YEAR, 7);
		Date tempOverNextWeek = tempCal.getTime();
		List<Deadline> tempOpenDeadlines = aModel.getOpenDeadlines();
		if (tempOpenDeadlines.size() == 0) {
			tempContentPanel.add(new JLabel("No open deadlines in " + aModel.getSourceInfo()), tempGBC);
		} else {
			for (final Deadline tempDeadline : tempOpenDeadlines) {
				addDeadlineRow(tempContentPanel, tempGBC, tempToday, tempOverTomorow, tempNextWeek, tempOverNextWeek, tempDeadline);
			}
		}
		invalidate();
		doLayout();
		repaint();
	}

	private void addDeadlineRow(JPanel tempContentPanel, GridBagConstraints tempGBC, Date tempToday, Date tempOverTomorow, Date tempNextWeek,
			Date tempOverNextWeek, final Deadline tempDeadline) {
		String tempText = dateFormatWithDay.format(tempDeadline.getWhen()) + ": ";
		if (!tempDeadline.isWholeDayEvent()) {
			tempText += timeFormat.format(tempDeadline.getWhen()) + " ";
		}
		tempText += tempDeadline.getTextWithoutRepeatingInfo();
		if (tempDeadline.getEndPoint() != null) {
			tempText += " (-" + dateFormat.format(tempDeadline.getEndPoint()) + ")";
		} else if (tempDeadline.getRepeating() != null) {
			tempText += " (" + dateFormat.format(tempDeadline.getRepeating()) + ")";
		}
		JCheckBox tempCheckBox = new JCheckBox(tempText);
		deadlineToCheckBoxMap.put(tempDeadline, tempCheckBox);
		tempCheckBox.setFont(new Font("Monospaced", 0, 14));
		if (tempDeadline.getWhen().before(tempToday)) {
			tempCheckBox.setForeground(Color.RED);
		} else if (tempDeadline.getWhen().before(tempOverTomorow)) {
			tempCheckBox.setForeground(Color.RED.darker());
		} else if (tempDeadline.getWhen().before(tempNextWeek)) {
			tempCheckBox.setForeground(Color.BLUE);
		} else if (tempDeadline.getWhen().before(tempOverNextWeek)) {
			tempCheckBox.setForeground(Color.BLUE.darker());
		} else if (tempDeadline.getRepeating() != null) {
			tempCheckBox.setForeground(Color.GRAY);
		}

		JPanel tempOneLine = new JPanel();
		tempOneLine.setLayout(new GridBagLayout());
		GridBagConstraints tempOneLineGBC = new GridBagConstraints();
		tempOneLineGBC.gridx = 0;
		tempOneLineGBC.weightx = 1.0;
		tempOneLineGBC.fill = GridBagConstraints.BOTH;
		tempOneLine.add(tempCheckBox, tempOneLineGBC);
		if (tempDeadline.getId() != null) {
			tempOneLineGBC.gridx++;
			tempOneLineGBC.weightx = 0.1;
			tempOneLineGBC.anchor = GridBagConstraints.EAST;
			JLabel tempComp = new JLabel("(added via GoogleCalendar)");
			tempComp.setFont(new Font(Font.SERIF, 0, 9));
			tempOneLine.add(tempComp, tempOneLineGBC);
		}

		tempGBC.gridy++;
		tempContentPanel.add(tempOneLine, tempGBC);
		tempCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent aE) {
				if (aE.getStateChange() == ItemEvent.SELECTED) {
					tempDeadline.setDone(true);
				} else {
					tempDeadline.setDone(false);
				}
				LOGGER.info("Selection:" + tempDeadline);
			}
		});
		tempCheckBox.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent aE) {
				super.mousePressed(aE);
				showPopUp(aE);
			}
		});
	}

	protected void showPopUp(MouseEvent aE) {
		final JCheckBox tempCheckBox = (JCheckBox) aE.getSource();
		tempCheckBox.requestFocus();
		if (aE.getButton() == MouseEvent.BUTTON3) {
			LOGGER.info("showPopUp " + aE);
			JPopupMenu tempJPopupMenu = new JPopupMenu();
			tempJPopupMenu.add(new AbstractAction("Copy") {
				@Override
				public void actionPerformed(ActionEvent aE) {
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(tempCheckBox.getText()), null);
				}
			});
			tempJPopupMenu.show(tempCheckBox, aE.getX(), aE.getY());
		}
	}

	public void setStatus(String aText) {
		statusLine.setText(aText);
	}

	/**
	 *
	 */
	public DoneSelectionListener createDoneSelectionListener() {
		return new DoneSelectionListener() {

			@Override
			public void deadlineDone(final Deadline aDeadline) {
				EventQueue.invokeLater(new Runnable() {
					@Override
					public void run() {
						JCheckBox tempCheckBox = deadlineToCheckBoxMap.get(aDeadline);
						if (tempCheckBox == null) {
							LOGGER.warn("No checkbox for " + aDeadline);
						} else {
							tempCheckBox.setSelected(aDeadline.isDone());
						}
					}
				});
			}

			@Override
			public void addNewDeadline(final Deadline aDeadline) {
				EventQueue.invokeLater(new Runnable() {
					@Override
					public void run() {
						model.getAddedFromGoogle().add(aDeadline);
						LOGGER.info("Reload model due to " + aDeadline);
						modelLoaderMustBeReleadedListener.reloadModelNextTime();
					}
				});
			}
		};
	}

	/**
	 *
	 */
	public void setModelLoaderMustBeReleadedListener(ModelLoaderMustBeReleadedListener aModelLoaderMustBeReleadedListener) {
		modelLoaderMustBeReleadedListener = aModelLoaderMustBeReleadedListener;
	}
}
