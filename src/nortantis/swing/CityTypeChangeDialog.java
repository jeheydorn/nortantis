package nortantis.swing;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.MapSettings;

@SuppressWarnings("serial")
public class CityTypeChangeDialog extends JDialog
{
	public CityTypeChangeDialog(MainWindow mainWindow, IconsTool iconsTool, String currentCityIconType, String imagesPath)
	{
		super(mainWindow, "Change City Type", Dialog.ModalityType.APPLICATION_MODAL);

		GridBagOrganizer organizer = new GridBagOrganizer();
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		setSize(new Dimension(450, 318));

		organizer.addLeftAlignedComponent(new JLabel(
				"<html>Maps can use only one city icon type, so changing the city icons type will cause all existing city icons to"
						+ " be changed to icons in the new type. This is because city icon types are designed to only include"
						+ " icons which make sense to place together in a generated map. If the new city icon type doesn't have an image with the"
						+ " same name (ignoring the 'width=...' part), then Nortantis will try to choose a new icon similar to the old one based"
						+ " on the following keywords in the file name: fort, castle, keep, citadel, walled, city, buildings, town, village, houses,"
						+ " farm, homestead, building, house."));

		JComboBox<String> cityIconsSetComboBox = new JComboBox<String>();
		SwingHelper.initializeComboBoxItems(cityIconsSetComboBox, ImageCache.getInstance(imagesPath).getIconGroupNames(IconType.cities),
				currentCityIconType);
		organizer.addLabelAndComponent("City icon type:", "", cityIconsSetComboBox);

		organizer.addVerticalFillerRow();

		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton acceptButton = new JButton("<html><u>O</u>K</html>");
		acceptButton.setMnemonic('o');
		acceptButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				MapSettings settings = mainWindow.getSettingsFromGUI(false);
				iconsTool.setCityIconsType(settings, (String) cityIconsSetComboBox.getSelectedItem());
				dispose();
			}
		});
		buttonsPanel.add(acceptButton);
		JButton cancelButton = new JButton("<html><u>C</u>ancel<html>");
		cancelButton.setMnemonic('c');
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		buttonsPanel.add(cancelButton);
		organizer.addLeftAlignedComponent(buttonsPanel, false);

		getContentPane().add(organizer.panel);
	}
}
