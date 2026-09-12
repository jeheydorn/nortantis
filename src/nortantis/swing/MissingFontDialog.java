package nortantis.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import nortantis.FontFinder;
import nortantis.MapFonts.FontProblem;
import nortantis.MapFonts.MissingFontInfo;
import nortantis.MapSettings.ThemeFontType;
import nortantis.swing.translation.Translation;

/**
 * A modal dialog shown when a map being opened names fonts this machine does not have. It lets the user pick a replacement for each one, or
 * cancel opening the map.
 */
public class MissingFontDialog
{
	/**
	 * Beyond this many distinct problem families, the dialog collapses to a single "replace all with" row rather than asking the user to
	 * make one decision per font.
	 */
	private static final int maxRowsBeforeCollapsing = 5;

	private static final int messageWidth = 460;
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

		JComponent rows = info.problems.size() > maxRowsBeforeCollapsing ? createCollapsedRow(info, comboBoxesByFamily)
				: createRowPerProblem(info, comboBoxesByFamily);
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

			addToRow(panel, row++, new JLabel(Translation.get("mainWindow.missingFont.usedBy", describeWhatUsesIt(problem))), 12,
					problem.missingArtPack == null ? 4 : 0);

			if (problem.missingArtPack != null)
			{
				addToRow(panel, row++, new JLabel(Translation.get("mainWindow.missingFont.fromMissingArtPack", problem.missingArtPack)), 12,
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
			addToRow(panel, row++, comboRow, 12, 2);
			addToRow(panel, row++, preview, 12, 0);
		}

		return wrapIfTall(panel);
	}

	/**
	 * A single "replace all with" row. A map naming this many missing fonts does not want one decision per font, and the fonts it names are
	 * in practice all the same kind of thing.
	 */
	private static JComponent createCollapsedRow(MissingFontInfo info, Map<String, JComboBox<Object>> comboBoxesByFamily)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		int row = 0;

		List<String> familyNames = new ArrayList<>();
		for (FontProblem problem : info.problems)
		{
			familyNames.add(problem.family);
		}
		JLabel familiesLabel = SwingHelper.createWrappedLabel("<html>" + SwingHelper.escapeHtml(String.join(", ", familyNames)) + "</html>",
				messageWidth);
		addToRow(panel, row++, familiesLabel, 0, 8);

		// Collapsing hides the per-font rows, so the art packs to install are named once here instead of being lost.
		Set<String> missingArtPacks = new LinkedHashSet<>();
		for (FontProblem problem : info.problems)
		{
			if (problem.missingArtPack != null)
			{
				missingArtPacks.add(problem.missingArtPack);
			}
		}
		if (!missingArtPacks.isEmpty())
		{
			addToRow(panel, row++, SwingHelper.createWrappedLabel("<html>" + SwingHelper.escapeHtml(
					Translation.get("mainWindow.missingFont.fromMissingArtPacks", String.join(", ", missingArtPacks))) + "</html>",
					messageWidth), 0, 8);
		}

		// Every font gets the same replacement, so the choices must be drawable for all of the text, and each family maps to the same combo.
		FontProblem combined = combine(info);
		JComboBox<Object> comboBox = createFontComboBox(combined);
		for (FontProblem problem : info.problems)
		{
			comboBoxesByFamily.put(problem.family, comboBox);
		}

		JLabel preview = new JLabel();
		updatePreview(preview, getSelectedFamily(comboBox));
		comboBox.addActionListener(e -> updatePreview(preview, getSelectedFamily(comboBox)));

		JPanel comboRow = new JPanel();
		comboRow.setLayout(new BoxLayout(comboRow, BoxLayout.X_AXIS));
		comboRow.add(new JLabel(Translation.get("mainWindow.missingFont.replaceAllWith")));
		comboRow.add(Box.createHorizontalStrut(8));
		comboRow.add(comboBox);
		comboRow.add(Box.createHorizontalGlue());
		addToRow(panel, row++, comboRow, 0, 2);
		addToRow(panel, row++, preview, 0, 0);

		return pinToPreferredHeight(panel);
	}

	private static FontProblem combine(MissingFontInfo info)
	{
		StringBuilder textToDraw = new StringBuilder();
		Set<ThemeFontType> usedBy = new LinkedHashSet<>();
		int individualLabelCount = 0;
		for (FontProblem problem : info.problems)
		{
			textToDraw.append(problem.textToDraw);
			usedBy.addAll(problem.usedByThemeFontTypes);
			individualLabelCount += problem.individualLabelCount;
		}
		// The combined row offers one replacement for every font, so it carries no single art pack; the art packs are named per font above.
		return new FontProblem(info.problems.get(0).family, new ArrayList<>(usedBy), individualLabelCount, textToDraw.toString(), null);
	}

	/**
	 * Names what a missing family draws: the kinds of text whose theme font it is, and the labels that chose it for themselves.
	 */
	private static String describeWhatUsesIt(FontProblem problem)
	{
		List<String> parts = new ArrayList<>();
		for (ThemeFontType type : problem.usedByThemeFontTypes)
		{
			parts.add(Translation.get("themeFontType." + type.name()));
		}

		if (problem.individualLabelCount > 0)
		{
			parts.add(Translation.get(
					problem.individualLabelCount == 1 ? "mainWindow.missingFont.oneIndividualLabel" : "mainWindow.missingFont.individualLabels",
					problem.individualLabelCount));
		}
		return String.join(", ", parts);
	}

	private static JComboBox<Object> createFontComboBox(FontProblem problem)
	{
		// Offering a replacement with no glyphs for the labels the missing font was drawing would trade one problem for another, so the
		// families offered are only those that can draw them. The map's own fonts are not gathered at the top here: the one being replaced
		// is by definition not available, and the rest are a worse answer than what the substitute suggests.
		String suggested = FontFinder.chooseSubstitute(problem.family, problem.textToDraw);
		List<Object> rows = FontFamilySections.buildRows(new ArrayList<>(), "", family -> FontFinder.canDisplay(family, problem.textToDraw));
		if (!namesAFont(rows))
		{
			// Nothing on this device draws every character the missing font was drawing, so no replacement avoids the problem and offering
			// none would leave nothing to choose. The whole list is offered instead, and the characters no font has go undrawn, as they
			// already do for a font that is present and lacks them. What is suggested is then the family a missing font falls back to
			// anyway, rather than whichever family happens to sort first.
			rows = FontFamilySections.buildRows(new ArrayList<>(), "");
			suggested = FontFinder.chooseSubstitute(problem.family, null);
		}
		return FontFamilySections.createFamilyComboBox(rows, suggested);
	}

	/**
	 * Whether any of the given rows names a font rather than heading a group of them.
	 */
	private static boolean namesAFont(List<Object> rows)
	{
		for (Object row : rows)
		{
			if (row instanceof String)
			{
				return true;
			}
		}
		return false;
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
	}

	/**
	 * Stops a panel being stretched taller than its contents, so that height the dialog gains from being resized collects below the rows
	 * instead of being spread through them.
	 */
	private static JComponent pinToPreferredHeight(JPanel panel)
	{
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
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
