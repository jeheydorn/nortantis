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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import nortantis.MapSettings;
import nortantis.NameCreator;
import nortantis.NotEnoughNamesException;
import nortantis.editor.NameType;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class NameGeneratorDialog extends JDialog
{
	private JTextArea textBox;
	final int numberToGenerate = 50;

	public NameGeneratorDialog(MainWindow mainWindow, MapSettings settings)
	{
		super(mainWindow, "Name Generator", Dialog.ModalityType.APPLICATION_MODAL);
		setSize(new Dimension(500, 810));

		JPanel contents = new JPanel();
		contents.setLayout(new BorderLayout());
		getContentPane().add(contents);

		GridBagOrganizer organizer = new GridBagOrganizer();
		contents.add(organizer.panel, BorderLayout.CENTER);
		ButtonGroup buttonGroup = new ButtonGroup();
		JRadioButton personNameRadioButton = new JRadioButton("Person");
		buttonGroup.add(personNameRadioButton);
		JRadioButton placeNameRadioButton = new JRadioButton("Place");
		buttonGroup.add(placeNameRadioButton);
		organizer.addLabelAndComponentsHorizontal("Name type:", "", Arrays.asList(personNameRadioButton, placeNameRadioButton));

		final String beginsWithLabel = "Begins with:";
		JTextField beginsWith = new JTextField();
		organizer.addLabelAndComponent(beginsWithLabel, "Constrains generated names to start with the given letters.", beginsWith);

		final String endsWithLabel = "Ends with:";
		JTextField endsWith = new JTextField();
		organizer.addLabelAndComponent(endsWithLabel, "Constraints generated names to end with these letters.", endsWith, 0);

		personNameRadioButton.setSelected(true);

		BooksWidget booksWidget = new BooksWidget(true, null);
		booksWidget.checkSelectedBooks(settings.books);
		organizer.addLeftAlignedComponentWithStackedLabel("Books for generating names:", "Selected books will be used to generate names.",
				booksWidget.getContentPanel(), GridBagOrganizer.rowVerticalInset, 2, true, 0.2);

		JPanel generatePanel = new JPanel();
		generatePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		JButton generateButton = new JButton("<html><u>G</u>enerate Names<html>");
		generatePanel.add(generateButton);
		generateButton.setMnemonic(KeyEvent.VK_G);
		organizer.addLeftAlignedComponent(generatePanel, 0, 0, false);
		generateButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!beginsWith.getText().chars().allMatch(Character::isLetter)
						|| !endsWith.getText().chars().allMatch(Character::isLetter))
				{
					String message = beginsWithLabel.replace(":", "") + " and " + endsWithLabel.replace(":", "")
							+ " must contain only letters.";
					JOptionPane.showMessageDialog(NameGeneratorDialog.this, message, "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}

				MapSettings settingsToUse = settings.deepCopy();
				settingsToUse.books = booksWidget.getSelectedBooks();
				settingsToUse.textRandomSeed = System.currentTimeMillis();
				NameCreator nameCreator = new NameCreator(settingsToUse);
				NameType type = personNameRadioButton.isSelected() ? NameType.Person : NameType.Place;
				textBox.setText(generateNamesForType(numberToGenerate, type, beginsWith.getText(), endsWith.getText(), nameCreator));
				textBox.setCaretPosition(0);
			}
		});

		textBox = new JTextArea(numberToGenerate, 30);
		textBox.setEditable(false);
		JScrollPane textBoxScrollPane = new JScrollPane(textBox);
		organizer.addLeftAlignedComponentWithStackedLabel("Generated names:", "", textBoxScrollPane, true, 0.8);

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
				settings.books = booksWidget.getSelectedBooks();
				mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(settings, false, false);
				mainWindow.updater.reprocessBooks();
				dispose();
			}
		});
		bottomButtonsPanel.add(doneButton);
	}

	private String generateNamesForType(int numberToGenerate, NameType type, String requiredPrefix, String requiredSuffix,
			NameCreator nameCreator)
	{
		final int maxAttempts = 100000;
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
						name = nameCreator.generatePersonName("%s", true, requiredPrefix);
					}
					else
					{
						name = nameCreator.generatePlaceName("%s", true, requiredPrefix);
					}
					if (requiredSuffix == null || requiredSuffix.equals("") || name.toLowerCase().endsWith(requiredSuffix.toLowerCase()))
					{
						if (!name.contains(" "))
						{
							break;
						}
					}
				}
				catch (NotEnoughNamesException ex)
				{
					if (requiredSuffix.length() > 0)
					{
						return names + (names.isEmpty() ? "" : "\n")
								+ "Error: Unable to generate enough names with the given books and requested suffix. "
								+ "Try either adding more books or removing or reducing the suffix.";
					}
					else if (requiredPrefix.length() > 0)
					{
						return names + (names.isEmpty() ? "" : "\n")
								+ "Error: Unable to generate enough names with the given books and required prefix. "
								+ "Try including more books or removing or reducing the prefix.";
					}
					return names + (names.isEmpty() ? "" : "\n") + "Error: Unable to generate enough names. Try including more books.";
				}
				catch (Exception ex)
				{
					return names + (names.isEmpty() ? "" : "\n") + "Error: " + ex.getMessage();
				}

				attemptCount++;
				if (attemptCount >= maxAttempts)
				{
					return names + (names.isEmpty() ? "" : "\n") + "Unable to generate enough names with the given contraints. "
							+ "Try using more books or reducing the suffix.";
				}
			}
			names = names + (names.isEmpty() ? "" : "\n") + name;
		}

		return names;
	}
}
