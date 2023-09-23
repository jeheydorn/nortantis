package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import nortantis.MapSettings;
import nortantis.TextDrawer;
import nortantis.editor.NameType;
import nortantis.editor.UserPreferences;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class NameGeneratorDialog extends JDialog
{
	private JTextArea textBox;

	public NameGeneratorDialog(MainWindow mainWindow, MapSettings settings)
	{
		super(mainWindow, "Name Generator", Dialog.ModalityType.APPLICATION_MODAL);
		setSize(new Dimension(800, 700));
		
		JPanel contents = new JPanel();
		contents.setLayout(new BorderLayout());
		getContentPane().add(contents);

		GridBagOrganizer organizer = new GridBagOrganizer();
		contents.add(organizer.panel, BorderLayout.CENTER);
		ButtonGroup buttonGroup = new ButtonGroup();
		JRadioButton personNameRadioButton = new JRadioButton("Person");
		buttonGroup.add(personNameRadioButton);
		JRadioButton placeNameRadioButton = new JRadioButton("Place");
		buttonGroup.add(personNameRadioButton);
		organizer.addLabelAndComponentsVertical("Name type:", "", Arrays.asList(personNameRadioButton, placeNameRadioButton));

		if (UserPreferences.getInstance().nameGeneratorDefaultType == NameType.Person)
		{
			personNameRadioButton.setSelected(true);
		}
		else
		{
			placeNameRadioButton.setSelected(true);
		}

		JPanel booksPanel = SwingHelper.createBooksPanel(() ->
		{
		});
		JScrollPane booksScrollPane = new JScrollPane(booksPanel);
		booksScrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		Dimension size = new Dimension(360, 130);
		booksScrollPane.setPreferredSize(size);
		organizer.addLeftAlignedComponentWithStackedLabel(
				"Books for generating text:", "Selected books will be used to generate names.", booksScrollPane
		);
		
		JPanel generatePanel = new JPanel();
		generatePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		JButton generateButton = new JButton("<html><u>G</u>enerate Names<html>");
		generatePanel.add(generateButton);
		generateButton.setMnemonic(KeyEvent.VK_G);
		organizer.addLeftAlignedComponent(generatePanel, false);
		generateButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				TextDrawer textDrawer = new TextDrawer(settings, 1.0);
				NameType type = personNameRadioButton.isSelected() ? NameType.Person : NameType.Place; 
				// TODO create fields for prefix and suffix and hook them up here.
				textBox.setText(generateNamesForType(50, type, "", "", textDrawer));
			}
		});

		
		textBox = new JTextArea(50, 50);
		JScrollPane textBoxScrollPane = new JScrollPane(textBox);
		organizer.addLeftAlignedComponentWithStackedLabel("Generated names:", "", textBoxScrollPane);


		JPanel bottomButtonsPanel = new JPanel();
		contents.add(bottomButtonsPanel, BorderLayout.SOUTH);
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

		JButton doneButton = new JButton("<html><u>D</u>one</html>");
		doneButton.setMnemonic(KeyEvent.VK_D);
		doneButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				settings.books = SwingHelper.getSelectedBooksFromGUI(booksPanel);
				mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(settings, false);
				dispose();
			}
		});
		bottomButtonsPanel.add(doneButton);
	}
	
	private String generateNamesForType(int numberToGenerate, NameType type, String requiredPrefix, String requiredSuffix, TextDrawer textDrawer)
	{
		final int maxAttempts = 10000;
		String names = "";

		for (@SuppressWarnings("unused")
		int i : new Range(numberToGenerate))
		{
			String name = "";
			int attemptCount = 0;
			while (true)
			{
				try
				{
					if (type == NameType.Person)
					{
						name = textDrawer.generatePersonName("%s", true, requiredPrefix);
					}
					else
					{
						name = textDrawer.generatePlaceName("%s", true, requiredPrefix);
					}
					if (requiredSuffix == null || requiredSuffix.equals("") || name.toLowerCase().endsWith(requiredSuffix.toLowerCase()))
					{
						if (!name.contains(" "))
						{
							break;
						}
					}
				}
				catch(Exception ex)
				{
					return names + (names.isEmpty() ? "" : "\n") + "Error: " + ex.getMessage();
				}
				
				attemptCount++;
				if (attemptCount >= maxAttempts)
				{
					return names + (names.isEmpty() ? "" : "\n") + "Unable to generate enough names with the given contraints. "
							+ "Try using more books or reducing the required suffix.";
				}
			}
			names = names + "\n" + name;
		}
		
		return names;
	}
}
