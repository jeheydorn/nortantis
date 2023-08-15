package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;

import nortantis.SettingsGenerator;
import nortantis.util.JFontChooser;
import nortantis.util.Tuple2;

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

	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title)
	{
		Color c = JColorChooser.showDialog(parent, "", colorDisplay.getBackground());
		if (c != null)
			colorDisplay.setBackground(c);
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

	public static void showColorPicker(JComponent parent, final JPanel colorDisplay, String title)
	{
		final JColorChooser colorChooser = new JColorChooser(colorDisplay.getBackground());
		colorChooser.setPreviewPanel(new JPanel());

		ActionListener okHandler = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorDisplay.setBackground(colorChooser.getColor());
			}

		};
		Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false, colorChooser, okHandler, null);
		dialog.setVisible(true);

	}


	public static JPanel createBooksPanel()
	{
		JPanel booksPanel = new JPanel();
		booksPanel.setLayout(new BoxLayout(booksPanel, BoxLayout.Y_AXIS));

		createBooksCheckboxes(booksPanel);

		return booksPanel;
	}

	public static void createBooksCheckboxes(JPanel booksPanel)
	{
		for (String book : SettingsGenerator.getAllBooks())
		{
			final JCheckBox checkBox = new JCheckBox(book);
			booksPanel.add(checkBox);
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

	static void handleBackgroundThreadException(Exception ex)
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
