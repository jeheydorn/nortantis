package nortantis.swing;

import nortantis.MapSettings;
import nortantis.NameCreator;
import nortantis.NotEnoughNamesException;
import nortantis.editor.NameType;
import nortantis.swing.translation.Translation;
import nortantis.util.Range;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;

@SuppressWarnings("serial")
public class NameGeneratorDialog extends JDialog
{
	private JTextArea textBox;
	final int numberToGenerate = 50;

	public NameGeneratorDialog(MainWindow mainWindow, MapSettings settings)
	{
		super(mainWindow, Translation.get("nameGenerator.title"), Dialog.ModalityType.APPLICATION_MODAL);
		setSize(new Dimension(500, 810));

		JPanel contents = new JPanel();
		contents.setLayout(new BorderLayout());
		getContentPane().add(contents);

		GridBagOrganizer organizer = new GridBagOrganizer();
		contents.add(organizer.panel, BorderLayout.CENTER);
		ButtonGroup buttonGroup = new ButtonGroup();
		JRadioButton personNameRadioButton = new JRadioButton(Translation.get("nameGenerator.person"));
		buttonGroup.add(personNameRadioButton);
		JRadioButton placeNameRadioButton = new JRadioButton(Translation.get("nameGenerator.place"));
		buttonGroup.add(placeNameRadioButton);
		organizer.addLabelAndComponentsHorizontal(Translation.get("nameGenerator.nameType.label"), "", Arrays.asList(personNameRadioButton, placeNameRadioButton));

		final String beginsWithLabel = Translation.get("nameGenerator.beginsWith.label");
		JTextField beginsWith = new JTextField();
		organizer.addLabelAndComponent(beginsWithLabel, Translation.get("nameGenerator.beginsWith.help"), beginsWith);

		final String endsWithLabel = Translation.get("nameGenerator.endsWith.label");
		JTextField endsWith = new JTextField();
		organizer.addLabelAndComponent(endsWithLabel, Translation.get("nameGenerator.endsWith.help"), endsWith, 0);

		personNameRadioButton.setSelected(true);

		BooksWidget booksWidget = new BooksWidget(true, null);
		booksWidget.checkSelectedBooks(settings.books);
		organizer.addLeftAlignedComponentWithStackedLabel(Translation.get("nameGenerator.booksForNames.label"), Translation.get("nameGenerator.booksForNames.help"), booksWidget.getContentPanel(),
				GridBagOrganizer.rowVerticalInset, 2, true, 0.2);

		JPanel generatePanel = new JPanel();
		generatePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		JButton generateButton = new JButton(Translation.get("nameGenerator.generateNames"));
		generatePanel.add(generateButton);
		generateButton.setMnemonic(KeyEvent.VK_G);
		organizer.addLeftAlignedComponent(generatePanel, 0, 0, false);
		generateButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!beginsWith.getText().chars().allMatch(Character::isLetter) || !endsWith.getText().chars().allMatch(Character::isLetter))
				{
					String message = Translation.get("nameGenerator.lettersOnly", beginsWithLabel.replace(":", ""), endsWithLabel.replace(":", ""));
					JOptionPane.showMessageDialog(NameGeneratorDialog.this, message, Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
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
		organizer.addLeftAlignedComponentWithStackedLabel(Translation.get("nameGenerator.generatedNames.label"), "", textBoxScrollPane, true, 0.8);

		JPanel bottomButtonsPanel = new JPanel();
		contents.add(bottomButtonsPanel, BorderLayout.SOUTH);
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

		JButton doneButton = new JButton(Translation.get("nameGenerator.done"));
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

	private String generateNamesForType(int numberToGenerate, NameType type, String requiredPrefix, String requiredSuffix, NameCreator nameCreator)
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
					if (requiredSuffix == null || requiredSuffix.isEmpty() || name.toLowerCase().endsWith(requiredSuffix.toLowerCase()))
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
						return names + (names.isEmpty() ? "" : "\n") + Translation.get("nameGenerator.errorSuffix");
					}
					else if (requiredPrefix.length() > 0)
					{
						return names + (names.isEmpty() ? "" : "\n") + Translation.get("nameGenerator.errorPrefix");
					}
					return names + (names.isEmpty() ? "" : "\n") + Translation.get("nameGenerator.errorGeneral");
				}
				catch (Exception ex)
				{
					return names + (names.isEmpty() ? "" : "\n") + "Error: " + ex.getMessage();
				}

				attemptCount++;
				if (attemptCount >= maxAttempts)
				{
					return names + (names.isEmpty() ? "" : "\n") + Translation.get("nameGenerator.errorConstraints");
				}
			}
			names = names + (names.isEmpty() ? "" : "\n") + name;
		}

		return names;
	}
}
