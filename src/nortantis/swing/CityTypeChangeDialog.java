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

@SuppressWarnings("serial")
public class CityTypeChangeDialog extends JDialog
{
	public CityTypeChangeDialog(MainWindow mainWindow, IconsTool iconsTool, String currentCityIconType)
	{
		super(mainWindow, "Change City Type", Dialog.ModalityType.APPLICATION_MODAL);
		
		GridBagOrganizer organizer = new GridBagOrganizer();
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		setSize(new Dimension(450, 240));
		
		organizer.addLeftAlignedComponent(new JLabel("<html>Maps can use only one city icon type, so changing the city icons type will cause all existing city icons to"
				+ " be randomly changed to icons in the new type. This is because city icon types are designed to only include"
				+ " icons which make sense to place together in a generated map. Do you wish to continue changing the city icon type for"
				+ " this map?</html>"));

		JComboBox<String> cityIconsSetComboBox = new JComboBox<String>();
		SwingHelper.initializeComboBoxItems(cityIconsSetComboBox, ImageCache.getInstance().getIconGroupNames(IconType.cities), 
				currentCityIconType);
		organizer.addLabelAndComponent("City icon type:", "", cityIconsSetComboBox);
						
		organizer.addVerticalFillerRow();

		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton acceptButton = new JButton("Change City Icon Type and Randomly Shuffle Cities");
		acceptButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				iconsTool.setCityIconsType((String) cityIconsSetComboBox.getSelectedItem());
				dispose();	
			}
		});
		buttonsPanel.add(acceptButton);
		JButton cancelButton = new JButton("Cancel");
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
