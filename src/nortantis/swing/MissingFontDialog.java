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
import nortantis.FontFinder.AvailableFont;
import nortantis.MapSettings.FontProblem;
import nortantis.MapSettings.MissingFontInfo;
import nortantis.swing.translation.Translation;

/**
 * A modal dialog shown when a map being opened names fonts this machine cannot draw as the map's author intended, either because they are
 * not installed or because they have no glyphs for the map's text. It lets the user pick a replacement for each one, or cancel opening the
 * map.
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
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JLabel messageLabel = SwingHelper.createWrappedLabel(
				"<html>" + SwingHelper.escapeHtml(Translation.get("mainWindow.missingFont.message", mapName)) + "</html>", messageWidth);
		messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(messageLabel);
		panel.add(Box.createVerticalStrut(12));

		Map<String, JComboBox<String>> comboBoxesByFamily = new HashMap<>();
		JComponent rows = info.problems.size() > maxRowsBeforeCollapsing ? createCollapsedRow(info, comboBoxesByFamily)
				: createRowPerProblem(info, comboBoxesByFamily);
		rows.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(rows);

		panel.add(Box.createVerticalStrut(12));
		JLabel savedLabel = SwingHelper.createWrappedLabel(
				"<html>" + SwingHelper.escapeHtml(Translation.get("mainWindow.missingFont.savedOnNextSave")) + "</html>", messageWidth);
		savedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(savedLabel);

		String openButtonText = Translation.get("mainWindow.missingFont.openButton");
		String cancelButtonText = Translation.get("mainWindow.missingFont.cancel");
		Object[] options = new Object[] { openButtonText, cancelButtonText };

		int result = SwingHelper.showOptionDialog(parent, panel, Translation.get("mainWindow.missingFont.title"), JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, openButtonText);

		if (result != 0)
		{
			return new Result(true, new HashMap<>());
		}

		Map<String, String> replacements = new HashMap<>();
		for (Map.Entry<String, JComboBox<String>> entry : comboBoxesByFamily.entrySet())
		{
			String chosen = (String) entry.getValue().getSelectedItem();
			if (chosen != null && !chosen.equals(entry.getKey()))
			{
				replacements.put(entry.getKey(), chosen);
			}
		}
		return new Result(false, replacements);
	}

	private static JComponent createRowPerProblem(MissingFontInfo info, Map<String, JComboBox<String>> comboBoxesByFamily)
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

			addToRow(panel, row++, new JLabel(describe(problem)), 12, 2);
			addToRow(panel, row++, new JLabel(Translation.get("mainWindow.missingFont.usedBy", String.join(", ", problem.usedBy))), 12, 4);

			JComboBox<String> comboBox = createFontComboBox(problem);
			comboBoxesByFamily.put(problem.family, comboBox);

			JLabel preview = new JLabel();
			updatePreview(preview, (String) comboBox.getSelectedItem());
			comboBox.addActionListener(e -> updatePreview(preview, (String) comboBox.getSelectedItem()));

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
	private static JComponent createCollapsedRow(MissingFontInfo info, Map<String, JComboBox<String>> comboBoxesByFamily)
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

		// Every font gets the same replacement, so the choices must be drawable for all of the text, and each family maps to the same combo.
		FontProblem combined = combine(info);
		JComboBox<String> comboBox = createFontComboBox(combined);
		for (FontProblem problem : info.problems)
		{
			comboBoxesByFamily.put(problem.family, comboBox);
		}

		JLabel preview = new JLabel();
		updatePreview(preview, (String) comboBox.getSelectedItem());
		comboBox.addActionListener(e -> updatePreview(preview, (String) comboBox.getSelectedItem()));

		JPanel comboRow = new JPanel();
		comboRow.setLayout(new BoxLayout(comboRow, BoxLayout.X_AXIS));
		comboRow.add(new JLabel(Translation.get("mainWindow.missingFont.replaceAllWith")));
		comboRow.add(Box.createHorizontalStrut(8));
		comboRow.add(comboBox);
		comboRow.add(Box.createHorizontalGlue());
		addToRow(panel, row++, comboRow, 0, 2);
		addToRow(panel, row++, preview, 0, 0);

		return panel;
	}

	private static FontProblem combine(MissingFontInfo info)
	{
		StringBuilder undrawable = new StringBuilder();
		Set<String> usedBy = new LinkedHashSet<>();
		FontProblem.Kind kind = FontProblem.Kind.NotInstalled;
		for (FontProblem problem : info.problems)
		{
			undrawable.append(problem.sampleOfUndrawableText);
			usedBy.addAll(problem.usedBy);
			if (problem.kind == FontProblem.Kind.MissingCharacters)
			{
				kind = FontProblem.Kind.MissingCharacters;
			}
		}
		return new FontProblem(info.problems.get(0).family, kind, info.problems.get(0).category, new ArrayList<>(usedBy), undrawable.toString());
	}

	private static String describe(FontProblem problem)
	{
		if (problem.kind == FontProblem.Kind.MissingCharacters)
		{
			return Translation.get("mainWindow.missingFont.messageMissingCharacters", problem.sampleOfUndrawableText);
		}
		return Translation.get("mainWindow.missingFont.messageNotInstalled");
	}

	private static JComboBox<String> createFontComboBox(FontProblem problem)
	{
		// For a font that lacks glyphs, offering families that lack them too would just move the problem, so the list is filtered to
		// families that can draw the text. Bundled fonts come first because listAvailableFonts orders them that way.
		List<String> families = new ArrayList<>();
		for (AvailableFont font : FontFinder.listAvailableFonts())
		{
			if (FontFinder.canDisplay(font.family, problem.sampleOfUndrawableText))
			{
				families.add(font.family);
			}
		}

		String suggested = FontFinder.chooseSubstitute(problem.family, problem.category, problem.sampleOfUndrawableText);

		JComboBox<String> comboBox = new JComboBox<>();
		SwingHelper.initializeComboBoxItems(comboBox, families, suggested, true);
		return comboBox;
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
	 * Keeps the dialog on screen when several fonts each get their own row.
	 */
	private static JComponent wrapIfTall(JPanel panel)
	{
		final int maxHeightBeforeScrolling = 420;
		if (panel.getPreferredSize().height <= maxHeightBeforeScrolling)
		{
			return panel;
		}

		JScrollPane scrollPane = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setPreferredSize(new Dimension(Math.max(messageWidth, panel.getPreferredSize().width), maxHeightBeforeScrolling));
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
