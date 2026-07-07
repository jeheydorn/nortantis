package nortantis.swing;

import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.swing.translation.Translation;
import nortantis.util.Logger;
import nortantis.util.OSHelper;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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

public class SwingHelper
{

	public static final int borderWidthBetweenComponents = 4;

	public static final int sidePanelMinimumWidth = calcSidePanelMinWidth();
	public static final int colorPickerLeftPadding = 2;
	public static final int sidePanelScrollSpeed = 30;

	private static int calcSidePanelMinWidth()
	{
		int base = 314;
		// Fonts in Linux are a little bigger, so make the side panels a little wider.
		int osAddition = OSHelper.isLinux() ? 40 : 0;
		LookAndFeel lookAndFeel = UserPreferences.getInstance().lookAndFeel;
		int uiThemeAddition = OSHelper.isLinux() && lookAndFeel.equals(LookAndFeel.System) ? 20 : OSHelper.isMac() && lookAndFeel.equals(LookAndFeel.System) ? 40 : 0;
		String language = Translation.getEffectiveLocale().getLanguage();
		int languageAddition = switch (language)
		{
			case "de" -> 30;
			case "es" -> 10;
			case "fr" -> 0;
			case "pt" -> 10;
			case "ru" -> 50;
			default -> 0;
		};
		int total = base + osAddition + uiThemeAddition + languageAddition;
		return total;
	}

	public static int getMenuShortcutKeyMask()
	{
		return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
	}

	public static boolean isCommandKeyDown(InputEvent e)
	{
		return OSHelper.isMac() ? e.isMetaDown() : e.isControlDown();
	}

	public static boolean isCommandModifierKeyCode(int keyCode)
	{
		return OSHelper.isMac() ? keyCode == KeyEvent.VK_META : keyCode == KeyEvent.VK_CONTROL;
	}

	public static String getCommandKeyName()
	{
		return OSHelper.isMac() ? "\u2318" : Translation.get("key.ctrl");
	}

	/**
	 * The display name of the key that triggers mnemonics (the underlined letters). This is the Alt key on Windows and Linux, but the Option
	 * key (\u2325) on macOS, since {@link #bindAltMnemonic} binds Option+letter there.
	 */
	public static String getAltKeyName()
	{
		return OSHelper.isMac() ? "\u2325" : Translation.get("key.alt");
	}

	/**
	 * Binds a keyboard shortcut to {@code button} so that pressing the shortcut anywhere in the editor window invokes the button's
	 * {@code ActionListener} (via {@code doClick()}). When focus is on an editable {@link JTextComponent} the shortcut is suppressed so the
	 * text component's built-in handler runs instead \u2014 this matters for {@code DELETE} in particular (which would otherwise delete a
	 * selected map object instead of a character) and for any future shortcuts that overlap with text-editing keys.
	 *
	 * <p>
	 * Replaces the boilerplate of building an {@link AbstractAction} and wiring {@link InputMap}/{@link ActionMap} by hand at each call
	 * site. Bindings are registered at {@link JComponent#WHEN_IN_FOCUSED_WINDOW} so the user doesn't have to focus the button first.
	 *
	 * @param button
	 *            The button to fire when the shortcut is pressed. The shortcut runs the button's existing {@code ActionListener}s.
	 * @param keyStroke
	 *            The shortcut, e.g. {@code KeyStroke.getKeyStroke("DELETE")} or
	 *            {@code KeyStroke.getKeyStroke(KeyEvent.VK_C, getMenuShortcutKeyMask())}.
	 * @param actionName
	 *            An InputMap/ActionMap key. Must be unique per button; conventionally something like {@code "deleteAction"} or
	 *            {@code "copyAction"}.
	 */
	public static void bindButtonShortcut(JButton button, KeyStroke keyStroke, String actionName)
	{
		bindButtonShortcut(button, actionName, keyStroke);
	}

	/**
	 * Sets a button's mnemonic (the underlined letter) and, on macOS, also binds Option+letter to activate it. Windows and Linux
	 * look-and-feels register the Alt+letter activation for a mnemonic automatically, but the macOS look-and-feel does not, so Option+letter
	 * otherwise does nothing. Binding it explicitly makes the shortcut work on macOS. The binding is registered at
	 * {@link JComponent#WHEN_IN_FOCUSED_WINDOW}, so it only fires while the button is showing (e.g. only the visible tool's mode buttons in a
	 * card layout respond).
	 */
	public static void bindAltMnemonic(AbstractButton button, int keyCode)
	{
		button.setMnemonic(keyCode);

		if (!OSHelper.isMac())
		{
			return;
		}

		String actionName = "altMnemonic";
		button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, InputEvent.ALT_DOWN_MASK), actionName);
		button.getActionMap().put(actionName, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (button.isEnabled() && button.isShowing())
				{
					button.doClick();
				}
			}
		});
	}

	/**
	 * Binds one or more keystrokes to a button, all firing the button's action. Use several keystrokes when a single logical shortcut has
	 * different key codes across platforms - for example the key labeled "delete" sends {@code VK_DELETE} on Windows but {@code VK_BACK_SPACE}
	 * on most Mac keyboards, so binding both makes the shortcut work everywhere.
	 */
	public static void bindButtonShortcut(JButton button, String actionName, KeyStroke... keyStrokes)
	{
		Action action = new AbstractAction(actionName)
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
				if (focused instanceof JTextComponent && ((JTextComponent) focused).isEditable())
				{
					// Let the text component's own handler take this shortcut (e.g. so DELETE inside a text
					// field deletes a character, not a selected map object).
					return;
				}
				button.doClick();
			}
		};
		for (KeyStroke keyStroke : keyStrokes)
		{
			button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionName);
		}
		button.getActionMap().put(actionName, action);
	}


	public static void initializeComboBoxItems(JComboBox<String> comboBox, Collection<String> items, String selectedItem, boolean forceAddSelectedItem)
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

	public static void reduceHorizontalMargin(AbstractButton button)
	{
		Insets m = button.getMargin();
		final int amountToReduce = 3;
		button.setMargin(new Insets(m.top, m.left - amountToReduce, m.bottom, m.right - amountToReduce));
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
			transparencySlider.addChangeListener(ignored ->
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
			labelPanel.add(new JLabel(Translation.get("colorChooser.alpha")));
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
			return Translation.get("colorChooser.transparency");
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

	/**
	 * True when running on macOS with the System theme (the native Aqua look-and-feel) active, as opposed to the Dark/Light FlatLaf themes.
	 */
	public static boolean isMacSystemLookAndFeel()
	{
		return OSHelper.isMac() && UIManager.getLookAndFeel().getClass().getName().equals(UIManager.getSystemLookAndFeelClassName());
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

	public static void addListener(Component component, Runnable action)
	{
		addListener(component, action, false);
	}

	public static void addListener(Component component, Runnable action, boolean runActionWhenValueIsAdjusting)
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
					if (runActionWhenValueIsAdjusting || !((JSlider) component).getValueIsAdjusting())
					{
						action.run();
					}
				}
			});
		}
		else if (component instanceof JSpinner)
		{
			((JSpinner) component).addChangeListener(new ChangeListener()
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

	/**
	 * Width, in pixels, that long option-pane messages are wrapped to. See {@link #wrapDialogMessage(Object)}.
	 */
	private static final int dialogWrapWidthPixels = 400;

	/**
	 * Messages whose longest line is at most this many characters are shown at their natural width. Longer messages are wrapped. See
	 * {@link #wrapDialogMessage(Object)}.
	 */
	private static final int dialogWrapThresholdChars = 60;

	/**
	 * Prepares an option-pane message so that long text wraps to a reasonable width. Some look-and-feels (notably the native macOS one) do
	 * not word-wrap long plain-text option-pane messages, so a long message can render wider than the screen. This wraps a long plain
	 * String in width-constrained HTML, which every look-and-feel wraps consistently. Non-String messages (e.g. a JPanel), messages the
	 * caller already marked up as HTML, and short strings are returned unchanged.
	 */
	static Object wrapDialogMessage(Object message)
	{
		if (!(message instanceof String))
		{
			return message;
		}

		String text = (String) message;
		if (text.toLowerCase().contains("<html"))
		{
			return message;
		}

		int longestLineLength = 0;
		for (String line : text.split("\n", -1))
		{
			longestLineLength = Math.max(longestLineLength, line.length());
		}
		if (longestLineLength <= dialogWrapThresholdChars)
		{
			return message;
		}

		String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
		return "<html><body><div style='width:" + dialogWrapWidthPixels + "px'>" + escaped + "</div></body></html>";
	}

	/**
	 * Drop-in replacement for {@link JOptionPane#showMessageDialog(Component, Object, String, int)} that wraps long messages via
	 * {@link #wrapDialogMessage(Object)}.
	 */
	public static void showMessageDialog(Component parent, Object message, String title, int messageType)
	{
		JOptionPane.showMessageDialog(parent, wrapDialogMessage(message), title, messageType);
	}

	/**
	 * Updates a map-drawing progress bar from the given updater's current draw state, and shows or hides it based on whether a draw is
	 * running. Full draws report determinate progress; incremental draws (and the idle state) leave the bar indeterminate. Determinate is
	 * preferred because an indeterminate bar does not animate under the macOS System look and feel. Call from the EDT.
	 */
	public static void updateMapDrawingProgressBar(JProgressBar progressBar, MapUpdater updater)
	{
		double progress = updater.getDrawProgress();
		if (progress >= 0)
		{
			progressBar.setIndeterminate(false);
			progressBar.setValue((int) Math.round(progress * 100));
		}
		else
		{
			progressBar.setIndeterminate(true);
		}
		progressBar.setVisible(updater.isMapBeingDrawn());
	}

	/**
	 * Drop-in replacement for {@link JOptionPane#showConfirmDialog(Component, Object, String, int)} that wraps long messages via
	 * {@link #wrapDialogMessage(Object)}.
	 */
	public static int showConfirmDialog(Component parent, Object message, String title, int optionType)
	{
		return JOptionPane.showConfirmDialog(parent, wrapDialogMessage(message), title, optionType);
	}

	/**
	 * Drop-in replacement for {@link JOptionPane#showOptionDialog(Component, Object, String, int, int, Icon, Object[], Object)} that wraps
	 * long messages via {@link #wrapDialogMessage(Object)}.
	 */
	public static int showOptionDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon, Object[] options,
			Object initialValue)
	{
		return JOptionPane.showOptionDialog(parent, wrapDialogMessage(message), title, optionType, messageType, icon, options, initialValue);
	}

	public static void handleException(Exception ex, Component parent, boolean isExport)
	{
		if (ex instanceof ExecutionException)
		{
			if (ex.getCause() != null)
			{
				ex.getCause().printStackTrace();
				if (isCausedByOutOfMemoryError(ex))
				{
					String message = isExport ? Translation.get("common.outOfMemoryExport") : Translation.get("common.outOfMemory");
					Logger.printError(message, ex);
					showMessageDialog(parent, message, Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
				}
				else
				{
					String message = Translation.get("common.errorCreatingMap");
					Logger.printError(message, ex.getCause());
					showMessageDialog(parent, message + " " + ex.getCause().getMessage(), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
				}
			}
			else
			{
				// Should never happen.
				ex.printStackTrace();
				String message = Translation.get("common.executionError");
				Logger.printError(message, ex);
				showMessageDialog(parent, message + ex.getMessage(), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
			}
		}
		else
		{
			ex.printStackTrace();
			String message = Translation.get("common.unexpectedError");
			Logger.printError(message, ex);
			showMessageDialog(parent, message + " " + ex.getMessage(), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
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

	/**
	 * Shows a message with the option to hide it in the future.
	 * 
	 * @return True if the message should be hidden in the future. False if not.
	 */
	public static boolean showDismissibleMessage(String title, String message, Dimension popupSize, int JOptionPaneMessageType, Component parentComponent)
	{
		JCheckBox checkBox = new JCheckBox(Translation.get("common.dontShowAgain"));
		Object[] options = { Translation.get("common.ok") };
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JLabel label = new JLabel("<html>" + message + "</html>");
		panel.add(label);
		panel.add(Box.createVerticalStrut(5));
		panel.add(Box.createVerticalGlue());
		panel.add(checkBox);
		panel.setPreferredSize(popupSize);
		int result = JOptionPane.showOptionDialog(parentComponent, panel, title, JOptionPane.YES_NO_OPTION, JOptionPaneMessageType, null, options, options[0]);
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
