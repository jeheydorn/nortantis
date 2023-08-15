package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import nortantis.SettingsGenerator;

public class SwingHelper
{
	public static final int spaceBetweenRowsOfComponents = 8;
	public static final int borderWidthBetweenComponents = 4;
	public static final int sidePanelPreferredWidth = 300;
	public static final int sidePanelMinimumWidth = 300;
	private static final int sliderWidth = 160;
	public static final int colorPickerLeftPadding = 2;
	public static final int sidePanelScrollSpeed = 11;

	public static void initializeComboBoxItems(JComboBox<String> comboBox, Collection<String> items, String selectedItem)
	{
		comboBox.removeAllItems();
		for (String item : items)
		{
			comboBox.addItem(item);
		}
		if (selectedItem != null && !selectedItem.isEmpty())
		{
			if (!items.contains(selectedItem))
			{
				comboBox.addItem(selectedItem);
			}
			comboBox.setSelectedItem(selectedItem);
		}
	}

	public static void setSliderWidthForSidePanel(JSlider slider)
	{
		slider.setPreferredSize(new Dimension(sliderWidth, slider.getPreferredSize().height));
	}

	public static JPanel createColorPickerPreviewPanel()
	{
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(50, 25));
		panel.setBackground(Color.BLACK);
		return panel;
	}

	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title)
	{
		showColorPickerWithPreviewPanel(parent, colorDisplay, title, () ->
		{
		});
	}

	public static JColorChooser createColorChooserWithOnlyGoodPanels(Color initialColor)
	{
		JColorChooser colorChooser = new JColorChooser(initialColor);

		AbstractColorChooserPanel[] panels = colorChooser.getChooserPanels();
		for (int i = panels.length - 1; i >= 0; i--)
		{
			if (panels[i].getDisplayName().equalsIgnoreCase("Swatches") || panels[i].getDisplayName().equalsIgnoreCase("CMYK"))
			{
				colorChooser.removeChooserPanel(panels[i]);
			}
		}

		return colorChooser;
	}

	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title, Runnable okAction)
	{
		JColorChooser colorChooser = createColorChooserWithOnlyGoodPanels(colorDisplay.getBackground());

		ActionListener okHandler = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorDisplay.setBackground(colorChooser.getColor());
				okAction.run();
			}

		};

		Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false, colorChooser, okHandler, null);
		dialog.setVisible(true);
	}

	public static void showColorPicker(JComponent parent, final JPanel colorDisplay, String title, Runnable okAction)
	{
		final JColorChooser colorChooser = createColorChooserWithOnlyGoodPanels(colorDisplay.getBackground());
		colorChooser.setPreviewPanel(new JPanel());

		ActionListener okHandler = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorDisplay.setBackground(colorChooser.getColor());
				okAction.run();
			}

		};
		Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false, colorChooser, okHandler, null);
		dialog.setVisible(true);

	}

	public static JPanel createBooksPanel(Runnable actionToRunWhenSelectionChanges)
	{
		JPanel booksPanel = new JPanel();
		booksPanel.setLayout(new BoxLayout(booksPanel, BoxLayout.Y_AXIS));

		createBooksCheckboxes(booksPanel, actionToRunWhenSelectionChanges);

		return booksPanel;
	}

	private static void createBooksCheckboxes(JPanel booksPanel, Runnable actionToRunWhenSelectionChanges)
	{
		for (String book : SettingsGenerator.getAllBooks())
		{
			final JCheckBox checkBox = new JCheckBox(book);
			booksPanel.add(checkBox);
			if (actionToRunWhenSelectionChanges != null)
			{
				addListener(checkBox, actionToRunWhenSelectionChanges);
			}
		}
	}

	// TODO remove
	// public static List<JCheckBox> createBooksCheckboxes()
	// {
	// return SettingsGenerator.getAllBooks().stream().map(bookName -> new
	// JCheckBox(bookName)).collect(Collectors.toList());
	// }

	public static void checkSelectedBooks(JPanel booksPanel, Set<String> selectedBooks)
	{
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				checkBox.setSelected(selectedBooks.contains(checkBox.getText()));
			}
		}
	}

	public static Set<String> getSelectedBooksFromGUI(JPanel booksPanel)
	{
		Set<String> books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				if (checkBox.isSelected())
				{
					books.add(checkBox.getText());
				}
			}
		}

		return books;
	}

	public static void setEnabled(Component component, boolean enabled)
	{
		component.setEnabled(enabled);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				setEnabled(child, enabled);
			}
		}
	}

	@SuppressWarnings("rawtypes")
	public static void addListener(Component component, Runnable action)
	{
		if (component instanceof AbstractButton)
		{
			((AbstractButton) component).addActionListener(new ActionListener()
			{

				@Override
				public void actionPerformed(ActionEvent e)
				{
					action.run();
				}
			});
		}
		else if (component instanceof JComboBox)
		{
			((JComboBox) component).addActionListener(new ActionListener()
			{

				@Override
				public void actionPerformed(ActionEvent e)
				{
					action.run();
				}
			});
		}
		else if (component instanceof JSlider)
		{
			((JSlider) component).addChangeListener(new ChangeListener()
			{

				@Override
				public void stateChanged(ChangeEvent e)
				{
					action.run();
				}
			});
		}
		else if (component instanceof JTextComponent)
		{
			((JTextComponent) component).getDocument().addDocumentListener(new DocumentListener()
			{

				@Override
				public void insertUpdate(DocumentEvent e)
				{
					// TODO Decide if this needs to run action.

				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					// TODO Decide if this needs to run action.

				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					action.run();
				}

			});
		}
	}

	// TODO remove it not used
	public static void addListenerToThisAndAllChildren(Component component, Runnable action)
	{
		addListener(component, action);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				addListenerToThisAndAllChildren(child, action);
			}
		}
	}

	public static void handleBackgroundThreadException(Exception ex)
	{
		if (ex instanceof ExecutionException)
		{
			if (ex.getCause() != null)
			{
				ex.getCause().printStackTrace();
				if (ex.getCause() instanceof OutOfMemoryError)
				{
					JOptionPane.showMessageDialog(null,
							"Out of memory. Try allocating more memory to the Java heap space, or decrease the resolution in the Background tab.",
							"Error", JOptionPane.ERROR_MESSAGE);
				}
				else
				{
					JOptionPane.showMessageDialog(null, ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
			else
			{
				// Should never happen.
				ex.printStackTrace();
				JOptionPane.showMessageDialog(null, "An ExecutionException error occured with no cause: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
		else
		{
			ex.printStackTrace();
			JOptionPane.showMessageDialog(null, "An unexpected error occured: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}
