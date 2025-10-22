package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.JTextComponent;

import org.apache.commons.io.FilenameUtils;

import nortantis.editor.UserPreferences;
import nortantis.util.Logger;
import nortantis.util.OSHelper;

public class SwingHelper
{
	public static final int spaceBetweenRowsOfComponents = 8;
	public static final int borderWidthBetweenComponents = 4;
	// Fonts in Linux are a little bigger, so make the side panels a little wider.
	public static final int sidePanelPreferredWidth = OSHelper.isLinux() ? 340 : 314;
	public static final int sidePanelMinimumWidth = sidePanelPreferredWidth;
	private static final int sliderWidth = 160;
	public static final int colorPickerLeftPadding = 2;
	public static final int sidePanelScrollSpeed = 30;

	public static void initializeComboBoxItems(JComboBox<String> comboBox, Collection<String> items, String selectedItem,
			boolean forceAddSelectedItem)
	{
		String selectedBefore = (String) comboBox.getSelectedItem();

		// Remove all action listeners
		ActionListener[] listeners = comboBox.getActionListeners();
		for (ActionListener listener : listeners)
		{
			comboBox.removeActionListener(listener);
		}

		comboBox.removeAllItems();
		for (String item : items)
		{
			comboBox.addItem(item);
		}
		if (selectedItem != null && !selectedItem.isEmpty())
		{
			if (!items.contains(selectedItem))
			{
				if (forceAddSelectedItem)
				{
					comboBox.addItem(selectedItem);
				}
				else if (items.size() > 0)
				{
					comboBox.setSelectedIndex(0);
				}
			}
			comboBox.setSelectedItem(selectedItem);
		}
		else if (items.size() > 0)
		{
			comboBox.setSelectedIndex(0);
		}

		// Re-add the action listeners
		for (ActionListener listener : listeners)
		{
			comboBox.addActionListener(listener);
		}

		// If the selection changed, trigger the action listener. I do this here instead of leaving the action listeners when doing
		// the manipulations above to avoid triggering the action listener when adding and removing items.
		String selectedNow = (String) comboBox.getSelectedItem();
		if (selectedBefore != null && !Objects.equals(selectedNow, selectedBefore))
		{
			comboBox.setSelectedItem(comboBox.getSelectedItem());
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> void initializeComboBoxItems(JComboBox<T> comboBox, Collection<T> items, T selectedItem, boolean forceAddSelectedItem)
	{
		T selectedBefore = (T) comboBox.getSelectedItem();

		// Remove all action listeners
		ActionListener[] listeners = comboBox.getActionListeners();
		for (ActionListener listener : listeners)
		{
			comboBox.removeActionListener(listener);
		}

		comboBox.removeAllItems();
		for (T item : items)
		{
			comboBox.addItem(item);
		}
		if (selectedItem != null)
		{
			if (!items.contains(selectedItem))
			{
				if (forceAddSelectedItem)
				{
					comboBox.addItem(selectedItem);
				}
				else if (items.size() > 0)
				{
					comboBox.setSelectedIndex(0);
				}
			}
			comboBox.setSelectedItem(selectedItem);
		}
		else if (items.size() > 0)
		{
			comboBox.setSelectedIndex(0);
		}

		// Re-add the action listeners
		for (ActionListener listener : listeners)
		{
			comboBox.addActionListener(listener);
		}

		// If the selection changed, trigger the action listener. I do this here instead of leaving the action listeners when doing
		// the manipulations above to avoid triggering the action listener when adding and removing items.
		T selectedNow = (T) comboBox.getSelectedItem();
		if (!Objects.equals(selectedNow, selectedBefore))
		{
			comboBox.setSelectedItem(comboBox.getSelectedItem());
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
		panel.setBorder(new DynamicLineBorder("controlShadow", 1));
		return panel;
	}

	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title)
	{
		showColorPicker(parent, colorDisplay, title, () ->
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

		if (OSHelper.isLinux() && UserPreferences.getInstance().lookAndFeel == LookAndFeel.System)
		{
			// Add transparency slider panel because, at least with the VM I use, Linux's System look and feel doesn't have an option for
			// transparency.
			colorChooser.addChooserPanel(new AlphaChooserPanel(initialColor.getAlpha()));
		}

		return colorChooser;
	}


	@SuppressWarnings("serial")
	private static class AlphaChooserPanel extends AbstractColorChooserPanel
	{
		private final JSlider transparencySlider;
		private int transparency;

		public AlphaChooserPanel(int initialAlpha)
		{
			transparency = initialAlpha;
			transparencySlider = new JSlider(0, 255, transparency);
			transparencySlider.setMajorTickSpacing(64);
			transparencySlider.setPaintTicks(true);
			transparencySlider.setPaintLabels(true);
			transparencySlider.addChangeListener(_ ->
			{
				transparency = transparencySlider.getValue();
				ColorSelectionModel model = getColorSelectionModel();
				Color base = model.getSelectedColor();
				if (base != null)
				{
					model.setSelectedColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), transparency));
				}
			});
		}

		@Override
		protected void buildChooser()
		{
			setLayout(new BorderLayout());

			JPanel labelPanel = new JPanel();
			labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
			labelPanel.add(new JLabel("Alpha:"));
			labelPanel.add(Box.createRigidArea(new Dimension(10, 0))); // Adds 10px horizontal space

			JPanel centerPanel = new JPanel(new BorderLayout());
			centerPanel.add(labelPanel, BorderLayout.WEST);
			centerPanel.add(transparencySlider, BorderLayout.CENTER);

			add(centerPanel, BorderLayout.CENTER);
		}


		@Override
		public void updateChooser()
		{
			Color base = getColorFromModel();
			if (base != null)
			{
				transparencySlider.setValue(base.getAlpha());
			}
		}

		@Override
		public String getDisplayName()
		{
			return "Transparency";
		}

		@Override
		public Icon getSmallDisplayIcon()
		{
			return null;
		}

		@Override
		public Icon getLargeDisplayIcon()
		{
			return null;
		}
	}

	public static void showColorPicker(JComponent parent, final JPanel colorDisplay, String title, Runnable okAction)
	{
		final JColorChooser colorChooser = createColorChooserWithOnlyGoodPanels(colorDisplay.getBackground());

		ActionListener okHandler = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorDisplay.setBackground(colorChooser.getColor());
				colorDisplay.repaint();
				parent.repaint();
				okAction.run();
			}

		};
		Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false, colorChooser, okHandler, null);
		dialog.setVisible(true);

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
					String message = isExport
							? "Out of memory. Try exporting at a lower resolution, or decreasing the Display Quality before exporting."
							: "Out of memory. Try decreasing the Display Quality.";
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
				JOptionPane.showMessageDialog(parent, message + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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

	/**
	 * Shows a message with the option to hide it in the future.
	 * 
	 * @return True if the message should be hidden in the future. False if not.
	 */
	public static boolean showDismissibleMessage(String title, String message, Dimension popupSize, Component parentComponent)
	{
		JCheckBox checkBox = new JCheckBox("Don't show this message again.");
		Object[] options = { "OK" };
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JLabel label = new JLabel("<html>" + message + "</html>");
		panel.add(label);
		panel.add(Box.createVerticalStrut(5));
		panel.add(Box.createVerticalGlue());
		panel.add(checkBox);
		panel.setPreferredSize(popupSize);
		int result = JOptionPane.showOptionDialog(parentComponent, panel, title, JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
				options, options[0]);
		if (result == JOptionPane.YES_OPTION)
		{
			if (checkBox.isSelected())
			{
				return true;
			}
		}
		return false;
	}

	public static JPanel stackLabelAndComponent(JLabel label, Component component)
	{
		JPanel stackPanel = new JPanel();
		stackPanel.setLayout(new BoxLayout(stackPanel, BoxLayout.Y_AXIS));
		JPanel labelPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
		labelPanel.add(Box.createRigidArea(new Dimension(1, 2)));
		labelPanel.add(label);
		labelPanel.add(Box.createHorizontalGlue());
		stackPanel.add(labelPanel);
		stackPanel.add(Box.createRigidArea(new Dimension(5, 2)));
		stackPanel.add(component);

		return stackPanel;
	}

	public static JPanel placeLabelToLeftOfComponents(JLabel label, Component... components)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(label);
		panel.add(Box.createRigidArea(new Dimension(5, 2)));
		panel.add(Box.createHorizontalGlue());
		panel.add(Box.createRigidArea(new Dimension(5, 2)));
		for (int i = 0; i < components.length; i++)
		{
			panel.add(components[i]);
			if (i < components.length - 1)
			{
				panel.add(Box.createRigidArea(new Dimension(5, 2)));
			}
		}

		return panel;
	}

	public static JLabel createHyperlink(String text, String URL)
	{
		JLabel link = new JLabel(text);
		link.setForeground(new Color(26, 113, 228));
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				try
				{
					Desktop.getDesktop().browse(new URI(URL));
				}
				catch (IOException | URISyntaxException ex)
				{
					Logger.printError("Error while trying to open URL: " + URL, ex);
				}
			}
		});
		return link;
	}

	public static String chooseImageFile(Component parent, String curFolder)
	{
		File currentFolder = new File(curFolder);
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(currentFolder);
		fileChooser.setFileFilter(new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return null;
			}

			@Override
			public boolean accept(File f)
			{
				String extension = FilenameUtils.getExtension(f.getName()).toLowerCase();
				return f.isDirectory() || extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg");
			}
		});
		int status = fileChooser.showOpenDialog(parent);
		if (status == JFileChooser.APPROVE_OPTION)
		{
			return fileChooser.getSelectedFile().toString();
		}
		return null;
	}

	/**
	 * Finds the amount apps are being scaled by the operating system.
	 * 
	 * @return The scale. 1.0 means unscaled.
	 */
	public static double getOSScale()
	{
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		GraphicsConfiguration gc = gd.getDefaultConfiguration();
		AffineTransform transform = gc.getDefaultTransform();

		double scaleX = transform.getScaleX();
		return scaleX;
	}

	public static Color getTextColorForPlaceholderImages()
	{
		int grayLevel = UserPreferences.getInstance().lookAndFeel == LookAndFeel.Dark ? 168 : 128;
		return new Color(grayLevel, grayLevel, grayLevel);
	}
}
