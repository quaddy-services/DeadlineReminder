package de.quaddy_services.deadlinereminder.gui;

import java.awt.CardLayout;
import java.awt.Color;
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
import java.util.List;

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

public class DeadlineGui extends JPanel {
	public static DateFormat dateFormat = new SimpleDateFormat("EE dd.MM.yyyy");
	private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineGui.class);

	public void setModel(Model aModel) {
		removeAll();
		setLayout(new CardLayout());
		JPanel tempPanel = new JPanel();
		JScrollPane tempScroll = new JScrollPane(tempPanel);
		add(tempScroll, "all");
		tempPanel.setLayout(new GridBagLayout());
		GridBagConstraints tempGBC = new GridBagConstraints();
		tempGBC.fill = GridBagConstraints.NONE;
		tempGBC.anchor = GridBagConstraints.WEST;
		tempGBC.gridx = 0;
		tempGBC.gridy = 0;
		tempGBC.insets = new Insets(0, 0, -6, 0);
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
			tempPanel.add(new JLabel("No open deadlines in " + aModel.getSourceInfo()), tempGBC);
		} else {
			for (final Deadline tempDeadline : tempOpenDeadlines) {
				String tempText = dateFormat.format(tempDeadline.getWhen()) + ": " + tempDeadline.getInfo();
				if (tempDeadline.getRepeating() != null) {
					tempText += " (" + dateFormat.format(tempDeadline.getRepeating()) + ")";
				}
				JCheckBox tempCheckBox = new JCheckBox(tempText);
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
				tempGBC.gridy++;
				tempPanel.add(tempCheckBox, tempGBC);
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
		}
		invalidate();
		doLayout();
		repaint();
	}

	protected void showPopUp(MouseEvent aE) {
		LOGGER.info("showPopUp " + aE);
		final JCheckBox tempCheckBox = (JCheckBox) aE.getSource();
		tempCheckBox.requestFocus();
		if (aE.getButton() == MouseEvent.BUTTON3) {
			JPopupMenu tempJPopupMenu = new JPopupMenu();
			tempJPopupMenu.add(new AbstractAction("Copy") {
				@Override
				public void actionPerformed(ActionEvent aE) {
					Toolkit.getDefaultToolkit().getSystemClipboard()
							.setContents(new StringSelection(tempCheckBox.getText()), null);
				}
			});
			tempJPopupMenu.show(tempCheckBox, aE.getX(), aE.getY());
		}
	}
}
