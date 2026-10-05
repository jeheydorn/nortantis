package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import nortantis.FontFinder;
import nortantis.MapFonts.FontProblem;
import nortantis.MapFonts.MissingFontInfo;
import nortantis.TextType;
import nortantis.swing.translation.Translation;

/**
 * A modal dialog shown when a map being opened names fonts this machine does not have. It lets the user pick a replacement for each one, or
 * cancel opening the map.
 */
public class MissingFontDialog
{
	private static final int messageWidth = 460;
	/** How far the lines under a font's name are indented. */
	private static final int rowIndent = 12;
	private static final int previewFontSize = 22;
	private static final int previewVerticalPadding = 6;

	/**
	 * The user's response to the dialog.
	 */
	public static class Result
	{
		/** True if the user chose to cancel opening the map. */
		public final boolean cancelled;
		/** The replacement family for each missing family, keyed by the missing family's name. Empty when {@link #cancelled} is true. */
		public final Map<String, String> replacements;

		private Result(boolean cancelled, Map<String, String> replacements)
		{
			this.cancelled = cancelled;
			this.replacements = replacements;
		}
	}

	/**
	 * Shows the dialog and blocks until the user responds.
	 *
	 * @param parent
	 *            The dialog's parent component.
	 * @param mapName
	 *            The name of the map being opened (without file extension), shown in the message.
	 * @param info
	 *            Which fonts the map names that this machine cannot draw as intended.
	 * @return The user's choice.
	 */
	public static Result show(Component parent, String mapName, MissingFontInfo info)
	{
		Map<String, JComboBox<Object>> comboBoxesByFamily = new HashMap<>();
		JPanel panel = createContentPanel(mapName, info, comboBoxesByFamily);

		String openButtonText = Translation.get("mainWindow.missingFont.openButton");
		String cancelButtonText = Translation.get("mainWindow.missingFont.cancel");
		Object[] options = new Object[] { openButtonText, cancelButtonText };

		// Resizable so that a map naming enough fonts to make the rows scroll can be given a taller dialog and show more of them at once.
		int result = SwingHelper.showResizableOptionDialog(parent, panel, Translation.get("mainWindow.missingFont.title"),
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, openButtonText);

		if (result != 0)
		{
			return new Result(true, new HashMap<>());
		}

		Map<String, String> replacements = new HashMap<>();
		for (Map.Entry<String, JComboBox<Object>> entry : comboBoxesByFamily.entrySet())
		{
			String chosen = getSelectedFamily(entry.getValue());
			if (chosen != null && !chosen.equals(entry.getKey()))
			{
				replacements.put(entry.getKey(), chosen);
			}
		}
		return new Result(false, replacements);
	}

	/**
	 * Builds the dialog's contents: the message, a row per font, and the note about when the choice is saved.
	 */
	static JPanel createContentPanel(String mapName, MissingFontInfo info, Map<String, JComboBox<Object>> comboBoxesByFamily)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createFullWidthWrappedLabel(Translation.get("mainWindow.missingFont.message", mapName)));
		panel.add(Box.createVerticalStrut(12));

		// Every missing font gets its own row, however many there are; the rows scroll once there are too many to fit.
		JComponent rows = createRowPerProblem(info, comboBoxesByFamily);
		rows.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(rows);

		return panel;
	}

	/**
	 * A wrapped label that keeps the height it needs.
	 *
	 * <p>
	 * A label in a vertical BoxLayout is given a height between its minimum and its maximum, and a plain label's maximum is unbounded in
	 * both directions, so a taller sibling can leave a wrapped label with less height than its text occupies and the last line is cut off.
	 * Pinning the maximum and the minimum to the height the text needs is what stops the layout from taking it away.
	 */
	private static JLabel createFullWidthWrappedLabel(String text)
	{
		JLabel label = SwingHelper.createWrappedLabel("<html>" + SwingHelper.escapeHtml(text) + "</html>", messageWidth);
		Dimension preferred = label.getPreferredSize();
		label.setMinimumSize(new Dimension(0, preferred.height));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * A label for the lines under a font's name, wrapped to the width left beside the indent those lines are given.
	 */
	private static JLabel createIndentedWrappedLabel(String text)
	{
		return SwingHelper.createWrappedLabel("<html>" + SwingHelper.escapeHtml(text) + "</html>", messageWidth - rowIndent);
	}

	private static JComponent createRowPerProblem(MissingFontInfo info, Map<String, JComboBox<Object>> comboBoxesByFamily)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		int row = 0;
		for (FontProblem problem : info.problems)
		{
			if (row > 0)
			{
				addSpacerRow(panel, row++);
			}

			JLabel familyLabel = new JLabel(problem.family);
			familyLabel.setFont(familyLabel.getFont().deriveFont(Font.BOLD));
			addToRow(panel, row++, familyLabel, 0, 4);

			// Wrapped, since a font can be used by every type of text, both on the map and for new text.
			addToRow(panel, row++, createIndentedWrappedLabel(Translation.get("mainWindow.missingFont.usedBy", describeWhatUsesIt(problem))), rowIndent,
					problem.missingArtPack == null ? 4 : 0);

			if (problem.missingArtPack != null)
			{
				addToRow(panel, row++, createIndentedWrappedLabel(Translation.get("mainWindow.missingFont.fromMissingArtPack", problem.missingArtPack)), rowIndent,
						4);
			}

			JComboBox<Object> comboBox = createFontComboBox(problem);
			comboBoxesByFamily.put(problem.family, comboBox);

			JLabel preview = new JLabel();
			updatePreview(preview, getSelectedFamily(comboBox));
			comboBox.addActionListener(e -> updatePreview(preview, getSelectedFamily(comboBox)));

			JPanel comboRow = new JPanel();
			comboRow.setLayout(new BoxLayout(comboRow, BoxLayout.X_AXIS));
			comboRow.add(new JLabel(Translation.get("mainWindow.missingFont.replaceWith")));
			comboRow.add(Box.createHorizontalStrut(8));
			comboRow.add(comboBox);
			comboRow.add(Box.createHorizontalGlue());
			addToRow(panel, row++, comboRow, rowIndent, 2);
			addToRow(panel, row++, preview, rowIndent, 0);
		}

		return wrapIfTall(panel);
	}

	/**
	 * Names what a missing family draws: how many labels of each type use it, then the types whose style for new text uses it.
	 */
	private static String describeWhatUsesIt(FontProblem problem)
	{
		List<String> parts = new ArrayList<>();
		for (Map.Entry<TextType, Integer> entry : problem.labelCountsByType.entrySet())
		{
			parts.add(Translation.get("mainWindow.missingFont.labels." + entry.getKey().name(), entry.getValue()));
		}
		for (TextType type : problem.newTextTypes)
		{
			parts.add(Translation.get("mainWindow.missingFont.newText." + type.name()));
		}
		return String.join(", ", parts);
	}

	private static JComboBox<Object> createFontComboBox(FontProblem problem)
	{
		// Every family is offered, with the ones that have no glyphs for some of the labels the missing font was drawing greyed out, the same
		// as in the font picker. Leaving those out instead would shorten the list with nothing to say why a font someone came looking for is
		// not in it, and when nothing at all covers the labels it would empty the list entirely. The map's own fonts are not gathered at the
		// top here: the one being replaced is by definition not available, and the rest are a worse answer than what the substitute suggests.
		String suggested = FontFinder.chooseSubstitute(problem.family, problem.textToDrawByStyle);
		if (suggested == null)
		{
			// Nothing on this device draws every character the missing font was drawing, so the characters no font has go undrawn, as they
			// already do for a font that is present and lacks them. What is suggested is then the family a missing font falls back to
			// anyway, rather than whichever family happens to sort first.
			suggested = FontFinder.chooseSubstitute(problem.family, Map.of());
		}

		List<Object> rows = FontFamilySections.buildRows(new ArrayList<>(), "");
		// Memoized because the renderer asks on every repaint, and the text a missing font was drawing is every label it drew.
		Map<String, Boolean> canDrawTheTextByFamily = new HashMap<>();
		return FontFamilySections.createFamilyComboBox(rows, suggested,
				family -> canDrawTheTextByFamily.computeIfAbsent(family, key -> FontFinder.canDisplay(key, problem.textToDrawByStyle)) ? null
						: Translation.get("mainWindow.missingFont.cannotDisplay", family));
	}

	/**
	 * The family chosen in a combo box, or null when nothing in it names a font.
	 */
	private static String getSelectedFamily(JComboBox<Object> comboBox)
	{
		Object selected = comboBox.getSelectedItem();
		return selected instanceof String ? (String) selected : null;
	}

	private static void updatePreview(JLabel preview, String family)
	{
		if (family == null)
		{
			preview.setText("");
			return;
		}

		Font font = new Font(family, Font.PLAIN, previewFontSize);
		preview.setFont(font);
		preview.setText(family);

		FontMetrics metrics = preview.getFontMetrics(font);
		int height = metrics.getMaxAscent() + metrics.getMaxDescent() + previewVerticalPadding;
		preview.setPreferredSize(new Dimension(preview.getPreferredSize().width, height));
		preview.setMinimumSize(new Dimension(0, height));
		preview.revalidate();
		growWindowToFit(preview);
	}

	/**
	 * Stops a panel being stretched taller than its contents, so that height the dialog gains from being resized collects below the rows
	 * instead of being spread through them.
	 */
	private static JComponent pinToPreferredHeight(JPanel panel)
	{
		// A wrapper rather than a fixed maximum, because choosing a taller font makes its preview, and so the rows, taller after the dialog
		// is laid out.
		JPanel pinned = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		pinned.add(panel, BorderLayout.CENTER);
		return pinned;
	}

	/**
	 * Makes the window holding the given component big enough for its contents again, so that a preview grown taller by choosing a different
	 * font pushes the dialog's bottom edge down rather than squeezing the buttons. The window is never shrunk, so it does not jump about as
	 * the user tries different fonts.
	 */
	private static void growWindowToFit(Component component)
	{
		Window window = SwingUtilities.getWindowAncestor(component);
		if (window == null)
		{
			return;
		}

		Dimension preferred = window.getPreferredSize();
		if (preferred.width > window.getWidth() || preferred.height > window.getHeight())
		{
			window.setSize(Math.max(preferred.width, window.getWidth()), Math.max(preferred.height, window.getHeight()));
		}
	}

	/**
	 * Keeps the dialog on screen when several fonts each get their own row.
	 */
	private static JComponent wrapIfTall(JPanel panel)
	{
		final int maxHeightBeforeScrolling = 420;
		if (panel.getPreferredSize().height <= maxHeightBeforeScrolling)
		{
			return pinToPreferredHeight(panel);
		}

		JScrollPane scrollPane = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);

		// The scroll bar takes its width from the scroll pane, not from the space beside it, so a pane only as wide as its contents leaves
		// them narrower than they asked to be. Anything that wraps then needs more height than it reported, and the extra is left below the
		// bottom of the scrollable area, where no amount of scrolling reaches it.
		int scrollBarWidth = scrollPane.getVerticalScrollBar().getPreferredSize().width;
		int contentWidth = Math.max(messageWidth, panel.getPreferredSize().width);
		scrollPane.setPreferredSize(new Dimension(contentWidth + scrollBarWidth, maxHeightBeforeScrolling));
		return scrollPane;
	}

	private static void addToRow(JPanel panel, int row, JComponent component, int leftInset, int bottomInset)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.anchor = GridBagConstraints.LINE_START;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(0, leftInset, bottomInset, 0);
		panel.add(component, constraints);
	}

	private static void addSpacerRow(JPanel panel, int row)
	{
		final int spaceBetweenFonts = 14;
		addToRow(panel, row, (JComponent) Box.createVerticalStrut(spaceBetweenFonts), 0, 0);
	}
}
