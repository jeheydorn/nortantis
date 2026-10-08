package nortantis.swing;

import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.swing.translation.Translation;
import nortantis.util.Logger;
import nortantis.util.OSHelper;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SwingHelper
{

	public static final int borderWidthBetweenComponents = 4;

	public static final int sidePanelMinimumWidth = calcSidePanelMinWidth();
	public static final int colorPickerLeftPadding = 2;
	public static final int sidePanelScrollSpeed = 30;
	private static final int spaceAbovePanelWithLabelAbove = 14;
	/**
	 * The space above a panel with a label above it that belongs with the component above it, which is about what separates two checkboxes
	 * in a list.
	 */
	private static final int spaceAboveAttachedPanelWithLabelAbove = 4;

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

	/**
	 * The width to give a side panel that was last seen at the given width, which is that width unless it is narrower than the narrowest
	 * width a side panel's contents fit in.
	 */
	public static int clampSidePanelWidthToMinimum(int storedWidth)
	{
		return Math.max(storedWidth, sidePanelMinimumWidth);
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
	 * Sets a button's mnemonic (the underlined letter), appends the shortcut to its tooltip, and, on macOS, also binds Option+letter to
	 * activate it. Windows and Linux look-and-feels register the Alt+letter activation for a mnemonic automatically, but the macOS
	 * look-and-feel does not, so Option+letter otherwise does nothing. Binding it explicitly makes the shortcut work on macOS. The binding
	 * is registered at {@link JComponent#WHEN_IN_FOCUSED_WINDOW}, so it only fires while the button is showing (e.g. only the visible tool's
	 * mode buttons in a card layout respond).
	 *
	 * <p>
	 * The tooltip is written here rather than at each call site so that the letter it names is necessarily the letter that is bound. Both
	 * come from {@code keyCode}, so they cannot drift apart, which is what went wrong when translations marked the underlined letter
	 * themselves: the marked letter and the bound key disagreed in 39 of 84 cases. Call sites that set their own tooltip should set it
	 * before calling this, and should not mention the shortcut.
	 */
	public static void bindAltMnemonic(AbstractButton button, int keyCode)
	{
		button.setMnemonic(keyCode);

		// Not KeyEvent.getKeyText, which is localized from the JDK's own bundle and so follows the system locale rather than Nortantis's
		// language setting. Every mnemonic in the app is VK_A through VK_Z, where this cast is exact.
		String shortcut = Translation.get("common.shortcut", getAltKeyName(), String.valueOf((char) keyCode));
		String existing = button.getToolTipText();
		button.setToolTipText(existing == null || existing.isBlank() ? shortcut : existing + " (" + shortcut + ")");

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

	private static final String mixedColorsProperty = "nortantis.mixedColors";
	/**
	 * The look and feel color of the border around a swatch made by {@link #createColorPickerPreviewPanel()}.
	 */
	private static final String colorPickerPreviewBorderColorKey = "controlShadow";
	/**
	 * The most stripes a swatch standing for several colors shows.
	 */
	private static final int maxMixedColorStripes = 8;

	/**
	 * Shows the colors that a swatch made by {@link #createColorPickerPreviewPanel()} stands for. When they differ, the swatch shows a stripe
	 * of each, up to {@link #maxMixedColorStripes}. The swatch's background, which a color picker opened from it starts on, is set to the
	 * first color.
	 */
	public static void showColorsInColorPickerPreview(JPanel colorDisplay, List<Color> colors)
	{
		List<Color> distinctColors = new ArrayList<>(new LinkedHashSet<>(colors));
		colorDisplay.setBackground(colors.get(0));
		colorDisplay.putClientProperty(mixedColorsProperty, distinctColors.size() > 1 ? distinctColors : null);
		colorDisplay.repaint();
	}

	/**
	 * Makes a swatch made by {@link #createColorPickerPreviewPanel()} show only its background color.
	 */
	public static void clearMixedColorsInColorPickerPreview(JPanel colorDisplay)
	{
		colorDisplay.putClientProperty(mixedColorsProperty, null);
		colorDisplay.repaint();
	}

	public static JPanel createColorPickerPreviewPanel()
	{
		JPanel panel = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				@SuppressWarnings("unchecked")
				List<Color> mixedColors = (List<Color>) getClientProperty(mixedColorsProperty);
				if (mixedColors != null)
				{
					// A swatch standing for several colors shows a stripe of each, with a line between stripes so that two similar colors
					// still read as two. The line matches the swatch's border.
					int stripeCount = Math.min(mixedColors.size(), maxMixedColorStripes);
					Color dividerColor = UIManager.getColor(colorPickerPreviewBorderColorKey) != null ? UIManager.getColor(colorPickerPreviewBorderColorKey) : Color.black;
					for (int i = 0; i < stripeCount; i++)
					{
						int left = i * getWidth() / stripeCount;
						int right = (i + 1) * getWidth() / stripeCount;
						g.setColor(isEnabled() ? mixedColors.get(i) : fadeTowardBackground(mixedColors.get(i)));
						g.fillRect(left, 0, right - left, getHeight());
						if (i > 0)
						{
							g.setColor(dividerColor);
							g.fillRect(left, 0, 1, getHeight());
						}
					}
					return;
				}
				// Filled here rather than by an opaque panel, because a color with transparency doesn't cover what was drawn before it, so
				// whatever is behind the swatch has to be painted first.
				g.setColor(isEnabled() ? getBackground() : fadeTowardBackground(getBackground()));
				g.fillRect(0, 0, getWidth(), getHeight());
			}

			/**
			 * Fades the color toward the background so the swatch looks disabled, like the controls beside it. This is done by computing
			 * the faded color rather than by filling over the swatch with translucent background, because at fractional display scales, a
			 * translucent fill doesn't always cover the same pixels as an opaque fill of the same rectangle, which leaves a line of the
			 * unfaded color along an edge.
			 */
			private Color fadeTowardBackground(Color color)
			{
				Color background = getParent() == null ? UIManager.getColor("Panel.background") : getParent().getBackground();
				if (background == null)
				{
					return color;
				}
				// The background, at fadeAlpha, composited over the color.
				final float fadeAlpha = 170f / 255f;
				float colorAlpha = color.getAlpha() / 255f;
				float alpha = fadeAlpha + colorAlpha * (1f - fadeAlpha);
				float colorWeight = colorAlpha * (1f - fadeAlpha);
				return new Color(Math.round((background.getRed() * fadeAlpha + color.getRed() * colorWeight) / alpha),
						Math.round((background.getGreen() * fadeAlpha + color.getGreen() * colorWeight) / alpha),
						Math.round((background.getBlue() * fadeAlpha + color.getBlue() * colorWeight) / alpha), Math.round(alpha * 255f));
			}
		};
		panel.setOpaque(false);
		panel.setPreferredSize(new Dimension(50, 25));
		panel.setBackground(Color.BLACK);
		panel.setBorder(new DynamicLineBorder(colorPickerPreviewBorderColorKey, 1));
		return panel;
	}

	/**
	 * Repaints a component along with what is behind it, reaching a pixel past its edges. At fractional display scales, a component's edges
	 * fall partway through screen pixels, and repainting only the component can leave a stale line of pixels along an edge.
	 */
	public static void repaintIncludingEdges(Component component)
	{
		Container parent = component.getParent();
		if (parent == null)
		{
			component.repaint();
			return;
		}
		parent.repaint(component.getX() - 1, component.getY() - 1, component.getWidth() + 2, component.getHeight() + 2);
	}

	/**
	 * Gives a color swatch a right-click menu that copies its color to the clipboard, and pastes a color from the clipboard into it. The
	 * color is put on the clipboard as hex text, such as #8C6B4A, or #8C6B4A80 when it is partly transparent, so colors can be pasted
	 * between maps and from other programs. Pasting is unavailable while the swatch is disabled.
	 *
	 * @param getColor
	 *            The color to copy.
	 * @param pasteColor
	 *            Applies a pasted color, the same way choosing it in the swatch's color picker does.
	 */
	public static void addColorCopyAndPasteMenu(JComponent swatch, Supplier<Color> getColor, Consumer<Color> pasteColor)
	{
		if (swatch.getToolTipText() == null)
		{
			swatch.setToolTipText(Translation.get("colorSwatch.tooltip"));
		}
		swatch.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				showMenuIfTriggered(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				showMenuIfTriggered(e);
			}

			private void showMenuIfTriggered(MouseEvent e)
			{
				if (!e.isPopupTrigger())
				{
					return;
				}
				JPopupMenu menu = new JPopupMenu();
				JMenuItem copyItem = new JMenuItem(Translation.get("colorSwatch.copy"));
				copyItem.addActionListener(event -> copyColorToClipboard(getColor.get()));
				menu.add(copyItem);
				Color clipboardColor = readColorFromClipboard();
				JMenuItem pasteItem = new JMenuItem(Translation.get("colorSwatch.paste"));
				pasteItem.setEnabled(clipboardColor != null && swatch.isEnabled());
				pasteItem.addActionListener(event -> pasteColor.accept(clipboardColor));
				menu.add(pasteItem);
				menu.show(swatch, e.getX(), e.getY());
			}
		});
	}

	private static void copyColorToClipboard(Color color)
	{
		String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
		if (color.getAlpha() != 255)
		{
			hex += String.format("%02X", color.getAlpha());
		}
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(hex), null);
		}
		catch (IllegalStateException e)
		{
			// The clipboard is briefly unavailable while another program uses it.
			Logger.printError("Unable to copy a color to the clipboard.", e);
		}
	}

	/**
	 * Reads a color written as hex text, with or without a leading #, as #RRGGBB or #RRGGBBAA. Returns null if the clipboard holds anything
	 * else.
	 */
	private static Color readColorFromClipboard()
	{
		String text;
		try
		{
			Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
			text = data == null ? null : data.toString().trim();
		}
		catch (Exception e)
		{
			return null;
		}
		if (text == null)
		{
			return null;
		}
		if (text.startsWith("#"))
		{
			text = text.substring(1);
		}
		if (!text.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
		{
			return null;
		}
		int red = Integer.parseInt(text.substring(0, 2), 16);
		int green = Integer.parseInt(text.substring(2, 4), 16);
		int blue = Integer.parseInt(text.substring(4, 6), 16);
		int alpha = text.length() == 8 ? Integer.parseInt(text.substring(6, 8), 16) : 255;
		return new Color(red, green, blue, alpha);
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
		showModalColorPicker(colorDisplay, title, colorChooser, okHandler, null);
	}

	/**
	 * Shows a color picker for a map setting whose color is shown by a plain swatch. While it is open, each color change is written to the
	 * swatch, and redrawAction runs, throttled, to redraw the map from the GUI. redrawAction must not set an undo point. OK keeps the chosen
	 * color and runs setUndoPointAction. Cancel, Escape, or the window's close button restore the original color. Either way, the map is
	 * redrawn on close only if the last redraw used a different color than the one kept.
	 */
	public static void showColorPickerWithLiveMapPreview(JPanel colorDisplay, String title, Runnable redrawAction, Runnable setUndoPointAction)
	{
		Color originalColor = colorDisplay.getBackground();
		JColorChooser colorChooser = createColorChooserWithOnlyGoodPanels(originalColor);
		Consumer<Color> setColor = color ->
		{
			colorDisplay.setBackground(color);
		};

		Color[] lastColorDrawn = { originalColor };
		Runnable redrawWithCurrentColor = () ->
		{
			lastColorDrawn[0] = colorDisplay.getBackground();
			redrawAction.run();
		};
		// A throttle rather than a debounce: it is only started when it isn't running, so the map keeps redrawing while the user drags.
		Timer redrawTimer = new Timer(colorPickerPreviewIntervalMillis, e -> redrawWithCurrentColor.run());
		redrawTimer.setRepeats(false);
		colorChooser.getSelectionModel().addChangeListener(e ->
		{
			setColor.accept(colorChooser.getColor());
			if (!redrawTimer.isRunning())
			{
				redrawTimer.start();
			}
		});

		ActionListener okHandler = e ->
		{
			redrawTimer.stop();
			setColor.accept(colorChooser.getColor());
			if (!Objects.equals(lastColorDrawn[0], colorDisplay.getBackground()))
			{
				redrawWithCurrentColor.run();
			}
			setUndoPointAction.run();
		};
		ActionListener cancelHandler = e ->
		{
			redrawTimer.stop();
			setColor.accept(originalColor);
			if (!Objects.equals(lastColorDrawn[0], originalColor))
			{
				redrawWithCurrentColor.run();
			}
		};
		showModalColorPicker(colorDisplay, title, colorChooser, okHandler, cancelHandler);
	}

	private static final int colorPickerPreviewIntervalMillis = 100;

	/**
	 * Shows a modal color picker dialog, positioned within the window containing dialogParent, and returns once it is closed. Color pickers
	 * are modal so that nothing else, such as an edit that sets an undo point, can change the map while a picker's color is being previewed.
	 */
	public static void showModalColorPicker(Component dialogParent, String title, JColorChooser colorChooser, ActionListener okHandler, ActionListener cancelHandler)
	{
		Dialog dialog = JColorChooser.createDialog(dialogParent, title, true, colorChooser, okHandler, cancelHandler);
		keepInsideOwnerWindow(dialog, dialogParent);
		dialog.setVisible(true);
	}

	/**
	 * Shifts the dialog so it lies within the window containing {@code component}, along each axis where the window is large enough to hold it.
	 */
	private static void keepInsideOwnerWindow(Window dialog, Component component)
	{
		Window window = component instanceof Window ? (Window) component : SwingUtilities.getWindowAncestor(component);
		if (window == null)
		{
			return;
		}

		Rectangle windowBounds = window.getBounds();
		Rectangle dialogBounds = dialog.getBounds();
		int x = dialogBounds.x;
		int y = dialogBounds.y;
		if (dialogBounds.width <= windowBounds.width)
		{
			x = Math.max(windowBounds.x, Math.min(x, windowBounds.x + windowBounds.width - dialogBounds.width));
		}
		if (dialogBounds.height <= windowBounds.height)
		{
			y = Math.max(windowBounds.y, Math.min(y, windowBounds.y + windowBounds.height - dialogBounds.height));
		}
		dialog.setLocation(x, y);
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
		if (component instanceof JSlider slider && slider.getLabelTable() != null)
		{
			// Some look and feels, such as GTK, make disabled labels wider, but the slider keeps the label sizes it measured before, which
			// cuts their text to "...". Disable the labels now and have the slider measure them again.
			for (Enumeration<?> labels = slider.getLabelTable().elements(); labels.hasMoreElements();)
			{
				if (labels.nextElement() instanceof Component label)
				{
					label.setEnabled(enabled);
				}
			}
			slider.setLabelTable(slider.getLabelTable());
		}
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
	 * Makes a numeric spinner fall back to its model's minimum value when its editor is left blank — for example when the user deletes the
	 * number and tabs away — instead of silently restoring the value that was there before the edit. Intended for spinners whose minimum is
	 * 0, so the blank falls back to 0; callers guard on that themselves.
	 */
	public static void resetSpinnerToMinimumWhenBlank(JSpinner spinner)
	{
		if (!(spinner.getModel() instanceof SpinnerNumberModel model))
		{
			return;
		}
		resetSpinnerToValueWhenBlank(spinner, model.getMinimum());
	}

	/**
	 * Makes a numeric spinner fall back to {@code value} when its editor is left blank — for example when the user deletes the number and
	 * tabs away — instead of silently restoring the value that was there before the edit. {@code value} must be within the model's range.
	 */
	public static void resetSpinnerToValueWhenBlank(JSpinner spinner, Object value)
	{
		if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor editor))
		{
			return;
		}
		JFormattedTextField textField = editor.getTextField();
		// This focus listener runs during the formatted text field's own focus-lost processing, before it reverts an uncommittable
		// (blank) edit back to the previous value, so the field still holds the empty text here. Committing the value clears the
		// field's "edited" state, which suppresses that revert.
		textField.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				if (textField.getText().trim().isEmpty())
				{
					spinner.setValue(value);
				}
			}
		});
	}

	/**
	 * Width, in pixels, that long option-pane messages are wrapped to. See {@link #wrapDialogMessage(Object)}.
	 */
	/** The color of text that warns the user about something that is wrong but not an error. */
	public static final Color warningMessageColor = new Color(160, 90, 0);

	private static final Color linkColor = new Color(26, 113, 228);

	private static final int dialogWrapWidthPixels = 400;

	/**
	 * Messages whose longest line is at most this many characters are shown at their natural width. Longer messages are wrapped. See
	 * {@link #wrapDialogMessage(Object)}.
	 */
	private static final int dialogWrapThresholdChars = 60;

	/**
	 * Escapes text that is about to be concatenated into an HTML Swing label or dialog message. Swing's HTML parser silently drops anything
	 * that looks like a tag it doesn't know, so text with angle brackets in it - a placeholder such as {@code <tree type>}, or a file path
	 * the user typed - disappears from the label without any error. Use this on any run of text that is not itself markup.
	 */
	public static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Creates a label holding a paragraph of HTML that wraps at exactly {@code width} pixels. Without a width an HTML label prefers to lay
	 * out on a single line, which makes {@link Window#pack()} size the window to the width of the whole paragraph.
	 *
	 * <p>
	 * A CSS width (<code>&lt;body style='width:430px'&gt;</code>) would be the obvious way to do this, but Swing's HTML parser treats a CSS
	 * pixel as 1.3 device pixels, so that lays out at 559 px instead. Measuring the wrapped height and fixing the preferred size gives the
	 * width the caller actually asked for, and leaves the label's minimum width at its longest word so it can still shrink if it has to.
	 */
	public static JLabel createWrappedLabel(String html, int width)
	{
		JLabel label = new JLabel(html);
		fitWrappedLabel(label, width);
		return label;
	}

	/**
	 * Sizes an existing HTML label so its preferred size is the size it needs when laid out at {@code width} pixels: no wider than
	 * {@code width}, and tall enough for however many lines the text wraps onto. See {@link #createWrappedLabel(String, int)} for why a CSS
	 * width doesn't do this.
	 */
	public static void fitWrappedLabel(JLabel label, int width)
	{
		if (width <= 0)
		{
			return;
		}
		View view = (View) label.getClientProperty(BasicHTML.propertyKey);
		if (view == null)
		{
			return;
		}
		view.setSize(width, 0);
		int preferredWidth = (int) Math.ceil(view.getPreferredSpan(View.X_AXIS));
		int preferredHeight = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
		label.setPreferredSize(new Dimension(Math.min(preferredWidth, width), preferredHeight));
	}

	private static final double labelTipFontScale = 0.92;
	private static final double labelTipFadeTowardBackground = 0.3;

	/**
	 * Creates a wrapping label with a short tip on a line under it, in a smaller font and a color closer to the background so the tip stands
	 * out less than the label. The tip's size and color follow the look and feel when it changes.
	 */
	public static JLabel createLabelWithTip(String labelText, String tipText, String tooltip)
	{
		JLabel label = new JLabel()
		{
			@Override
			public void updateUI()
			{
				super.updateUI();
				setText(createLabelWithTipHtml(labelText, tipText, getFont()));
			}
		};
		label.setToolTipText(tooltip);
		return label;
	}

	private static String createLabelWithTipHtml(String labelText, String tipText, Font font)
	{
		Color foreground = UIManager.getColor("Label.foreground");
		Color background = UIManager.getColor("Panel.background");
		Color tipColor = foreground != null && background != null ? blend(foreground, background, labelTipFadeTowardBackground) : Color.gray;
		int tipFontSize = font == null ? 10 : (int) Math.round(font.getSize2D() * labelTipFontScale);
		return "<html>" + labelText + "<br><span style='font-size:" + tipFontSize + "pt; color:" + String.format("#%06x", tipColor.getRGB() & 0xffffff) + "'>"
				+ tipText + "</span></html>";
	}

	/**
	 * Mixes two opaque colors, from all of the first at 0 to all of the second at 1.
	 */
	public static Color blend(Color from, Color to, double amountTowardsTo)
	{
		return new Color((int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amountTowardsTo),
				(int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amountTowardsTo),
				(int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amountTowardsTo));
	}

	/**
	 * A label's whole border, sized so that its text sits on a baseline {@code baselineFromBottom} pixels up from the bottom of a label
	 * {@code height} tall, instead of wherever the font's own metrics would put it.
	 *
	 * <p>
	 * A label centers the font's line box and draws the baseline one ascent below the top of it. Both of those are numbers the font
	 * declares, and families disagree about them enough that a column of names each drawn in its own font sits on a different line in every
	 * row - and a family that gives a large share of its line box to the gap between lines, as Gabriola and Nirmala Text do, has the tops of
	 * its glyphs pushed out of sight above a label sized from the ascent and descent. Choosing the baseline puts every row on one line and
	 * leaves the same space under each of them.
	 *
	 * <p>
	 * The border moves the baseline by half of itself, since the line box stays centered in whatever the insets leave.
	 *
	 * <p>
	 * This is for labels only. A text field looks like it should take the same treatment, but its view stops centering and pins the text to
	 * the top once the line box no longer fits, so the border and the baseline part ways.
	 *
	 * @param baselineFromBottom
	 *            How much of the height to keep below the baseline. Anything a font draws below it deeper than this hangs out of the label.
	 * @param leftInset
	 *            Left inset to include, since this is the whole border and it may need to carry an indent.
	 */
	public static Border createBaselineBorder(JLabel label, int height, int baselineFromBottom, int leftInset)
	{
		FontMetrics metrics = label.getFontMetrics(label.getFont());
		double baselineTheFontWouldGive = (height - metrics.getHeight()) / 2.0 + metrics.getAscent();
		int shift = (int) Math.round(((height - baselineFromBottom) - baselineTheFontWouldGive) * 2);
		return BorderFactory.createEmptyBorder(Math.max(0, shift), leftInset, Math.max(0, -shift), 0);
	}

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

		String escaped = escapeHtml(text).replace("\n", "<br>");
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

	/**
	 * Same as {@link #showOptionDialog(Component, Object, String, int, int, Icon, Object[], Object)}, except the dialog can be resized.
	 * JOptionPane's own show* methods always give a dialog that cannot be, so the pane and its dialog are created here instead.
	 *
	 * <p>
	 * The dialog can only be made larger than the size its contents ask for. An option pane lays its contents out at their minimum size when
	 * there isn't room for more, which clips anything that wraps, so there is nothing to gain by allowing it to shrink.
	 *
	 * @return The index in {@code options} of the option the user chose, or {@link JOptionPane#CLOSED_OPTION} if they closed the dialog
	 *         without choosing one.
	 */
	public static int showResizableOptionDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon,
			Object[] options, Object initialValue)
	{
		JOptionPane optionPane = new JOptionPane(wrapDialogMessage(message), messageType, optionType, icon, options, initialValue);
		JDialog dialog = optionPane.createDialog(parent, title);
		dialog.setResizable(true);
		dialog.setMinimumSize(dialog.getSize());
		dialog.setVisible(true);
		dialog.dispose();

		Object chosen = optionPane.getValue();
		if (chosen == null || options == null)
		{
			return JOptionPane.CLOSED_OPTION;
		}
		for (int i = 0; i < options.length; i++)
		{
			if (options[i].equals(chosen))
			{
				return i;
			}
		}
		return JOptionPane.CLOSED_OPTION;
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
		JLabel link = createActionLink(text, () ->
		{
			try
			{
				Desktop.getDesktop().browse(new URI(URL));
			}
			catch (IOException | URISyntaxException ex)
			{
				Logger.printError("Error while trying to open URL: " + URL, ex);
			}
		});
		link.setToolTipText(URL);
		return link;
	}

	/**
	 * A label that looks like a hyperlink but runs the given action instead of opening a URL.
	 */
	public static JLabel createActionLink(String text, Runnable action)
	{
		JLabel link = new JLabel(text);
		link.setForeground(linkColor);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		});
		return link;
	}

	/**
	 * A label holding the look-and-feel's standard warning icon, aligned with the first line of the text it sits beside. The icon is an
	 * image rather than a glyph, so it renders the same across languages, fonts, and operating systems.
	 */
	public static JLabel createWarningIconLabel()
	{
		JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
		iconLabel.setVerticalAlignment(SwingConstants.TOP);
		iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
		return iconLabel;
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

	/**
	 * Like createPanelWithLabelAbove(String, String, List), but optionally with only the usual space between components above the label,
	 * for a panel that belongs with the component above it.
	 */
	public static JPanel createPanelWithLabelAbove(String label, String toolTip, List<? extends Component> components, boolean separateFromAbove)
	{
		JLabel labelComponent = new JLabel(label);
		labelComponent.setToolTipText(toolTip);
		labelComponent.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel componentRow = new JPanel();
		componentRow.setLayout(new BoxLayout(componentRow, BoxLayout.X_AXIS));
		componentRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (Component component : components)
		{
			componentRow.add(component);
		}

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(Box.createVerticalStrut(separateFromAbove ? spaceAbovePanelWithLabelAbove : spaceAboveAttachedPanelWithLabelAbove));
		panel.add(labelComponent);
		panel.add(componentRow);
		return panel;
	}
}
