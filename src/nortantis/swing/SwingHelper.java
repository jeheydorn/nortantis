package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
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
import nortantis.util.Logger;

public class SwingHelper
{
	public static final int spaceBetweenRowsOfComponents = 8;
	public static final int borderWidthBetweenComponents = 4;
	public static final int sidePanelPreferredWidth = 300;
	public static final int sidePanelMinimumWidth = 300;
	private static final int sliderWidth = 160;
	public static final int colorPickerLeftPadding = 2;
	public static final int sidePanelScrollSpeed = 20;

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
				colorDisplay.repaint();
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
					if (!((JSlider) component).getValueIsAdjusting())
					{
						action.run();
					}
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
					action.run();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					action.run();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
					action.run();
				}

			});
		}
	}

	public static void handleBackgroundThreadException(Exception ex, Component parent, boolean isExport)
	{
		if (ex instanceof ExecutionException)
		{
			if (ex.getCause() != null)
			{
				ex.getCause().printStackTrace();
				if (isCausedByOutOfMemoryError(ex))
				{
					String message =  isExport ? "Out of memory. Try exporting at a lower resolution." :
						"Out of memory. Try decreasing the Display Quality in the View menu.";
					Logger.printError(message, ex);
					JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
				}
				else
				{
					String message = "Error while creating map:";
					Logger.printError(message, ex.getCause());
					JOptionPane.showMessageDialog(parent, message + " " + ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
			else
			{
				// Should never happen.
				ex.printStackTrace();
				String message = "An ExecutionException error occured with no cause: ";
				Logger.printError(message, ex);
				JOptionPane.showMessageDialog(parent, message + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
		else
		{
			ex.printStackTrace();
			String message = "An unexpected error occured: ";
			Logger.printError(message, ex);
			JOptionPane.showMessageDialog(parent, message + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private static boolean isCausedByOutOfMemoryError(Throwable ex)
	{
		if (ex == null)
		{
			return false;
		}

		if (ex instanceof OutOfMemoryError)
		{
			return true;
		}

		return isCausedByOutOfMemoryError(ex.getCause());
	}
	
	public static java.awt.Point transform(java.awt.Point point, AffineTransform transform)
	{
		java.awt.Point result = new java.awt.Point();
		transform.transform(point, result);
		return result;
	}
}
