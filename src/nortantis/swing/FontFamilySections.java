package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.border.Border;

import nortantis.FontFinder;
import nortantis.FontFinder.AvailableFont;
import nortantis.swing.translation.Translation;

/**
 * Builds and draws the grouped rows that every list of font families shares: the families a map already uses, then the families each art
 * pack supplies, then the ones this computer supplies.
 *
 * <p>
 * The groups are headings rather than a filter the user switches between. A filter has to be noticed to be understood, and someone who does
 * not notice it sees fonts missing with no explanation. A heading someone does not notice costs them nothing, because the list under it is
 * still whole.
 */
class FontFamilySections
{
	/** A row naming the group of families beneath it. It is a heading rather than a font, so it cannot be chosen. */
	record SectionHeading(String title)
	{
	}

	/** How far a heading's color is moved from the list's text color towards its background, so headings read as quieter than fonts. */
	private static final double headingFadeTowardsBackground = 0.42;
	/** How far a heading's rule is moved towards the background. A rule as bright as the text draws more attention than what it separates. */
	private static final double ruleFadeTowardsBackground = 0.78;
	private static final int headingTopPadding = 5;

	/**
	 * The rows of a family list: every family this installation can draw with, under a heading naming where it came from.
	 *
	 * <p>
	 * A family a map uses appears twice, once under each heading that describes it. That is what lets a map's own fonts be seen together
	 * without any of them going missing from where someone browsing would look for them.
	 *
	 * @param familiesUsedByThisMap
	 *            Families to gather into the first group, or empty for none.
	 * @param searchText
	 *            Narrows the rows to families whose names contain it, or empty to include them all.
	 */
	static List<Object> buildRows(List<String> familiesUsedByThisMap, String searchText)
	{
		return buildRows(familiesUsedByThisMap, searchText, family -> true);
	}

	/**
	 * @param isOffered
	 *            Which families to include at all, for a list that can only offer some of them.
	 */
	static List<Object> buildRows(List<String> familiesUsedByThisMap, String searchText, Predicate<String> isOffered)
	{
		List<Object> rows = new ArrayList<>();
		addSection(rows, Translation.get("fontChooser.section.usedByThisMap"), familiesUsedByThisMap, searchText, isOffered);

		// Registered fonts are grouped by the pack that supplied them, in the order listAvailableFonts gives, which puts the bundled ones
		// first. Anything the machine itself supplies came from no pack and goes last.
		Map<String, List<String>> familiesByArtPack = new LinkedHashMap<>();
		List<String> systemFamilies = new ArrayList<>();
		for (AvailableFont font : FontFinder.listAvailableFonts())
		{
			if (font.artPack == null)
			{
				systemFamilies.add(font.family);
			}
			else
			{
				familiesByArtPack.computeIfAbsent(font.artPack, key -> new ArrayList<>()).add(font.family);
			}
		}

		for (Map.Entry<String, List<String>> entry : familiesByArtPack.entrySet())
		{
			addSection(rows, entry.getKey(), entry.getValue(), searchText, isOffered);
		}
		addSection(rows, Translation.get("fontChooser.section.thisDevice"), systemFamilies, searchText, isOffered);

		if (rows.isEmpty())
		{
			// An empty list looks like a list that failed to load. Saying nothing matched is what tells the user the search is why.
			rows.add(new SectionHeading(Translation.get("fontChooser.noMatches", searchText)));
		}
		return rows;
	}

	/**
	 * Adds a heading and the families under it that match what has been typed, or nothing at all when none of them do. A heading with
	 * nothing under it would say a group is empty when what is really true is that nothing in it matched.
	 */
	private static void addSection(List<Object> rows, String title, List<String> families, String searchText, Predicate<String> isOffered)
	{
		List<String> matching = new ArrayList<>();
		for (String family : families)
		{
			if (matches(family, searchText) && isOffered.test(family))
			{
				matching.add(family);
			}
		}

		if (matching.isEmpty())
		{
			return;
		}

		rows.add(new SectionHeading(title));
		rows.addAll(matching);
	}

	/**
	 * Whether a family name matches what has been typed. Any part of the name matches, not only its start, since a family is as often
	 * looked for by its typeface as by its foundry - "garamond" has to find EB Garamond.
	 */
	static boolean matches(String family, String searchText)
	{
		return searchText.isEmpty() || family.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
	}

	/**
	 * Dresses a label as a section heading: quieter than the font names it introduces, so it reads as something that organizes the list
	 * rather than something in it.
	 */
	static void applyHeadingStyle(JLabel label, SectionHeading heading, Component list, java.awt.Font baseFont)
	{
		label.setText(heading.title());
		label.setFont(baseFont.deriveFont(java.awt.Font.BOLD));
		// Set explicitly rather than by disabling the label, which asks the look and feel for a grey that is meant to say "you cannot use
		// this" - true of a heading, but not what it should be saying.
		label.setForeground(blend(list.getForeground(), list.getBackground(), headingFadeTowardsBackground));
		label.setEnabled(true);
	}

	/**
	 * The border that separates a heading from the group above it, faint enough to divide without competing with what it separates.
	 *
	 * @param isFirstRow
	 *            True when nothing precedes the heading, in which case there is nothing to separate it from.
	 */
	static Border createHeadingBorder(Component list, boolean isFirstRow)
	{
		Border padding = BorderFactory.createEmptyBorder(headingTopPadding, 0, 0, 0);
		if (isFirstRow)
		{
			return padding;
		}
		Border rule = BorderFactory.createMatteBorder(1, 0, 0, 0, blend(list.getForeground(), list.getBackground(), ruleFadeTowardsBackground));
		return BorderFactory.createCompoundBorder(rule, padding);
	}

	/**
	 * A combo box of font families grouped under the same headings as the font picker's list, so that where a font comes from is shown the
	 * same way wherever a font is chosen.
	 */
	static JComboBox<Object> createFamilyComboBox(List<Object> rows, String selectedFamily)
	{
		JComboBox<Object> comboBox = new SectionedFamilyComboBox(rows);
		comboBox.setRenderer(new ComboSectionRenderer(comboBox));
		if (selectedFamily != null)
		{
			comboBox.setSelectedItem(selectedFamily);
		}
		return comboBox;
	}

	/**
	 * A combo box whose headings cannot be landed on. Arrowing through a list steps onto every row in turn, so a heading has to hand the
	 * selection on in the direction it was already moving rather than simply refusing it, which would make it a wall.
	 */
	@SuppressWarnings("serial")
	private static class SectionedFamilyComboBox extends JComboBox<Object>
	{
		SectionedFamilyComboBox(List<Object> rows)
		{
			super(rows.toArray());
		}

		@Override
		public void setSelectedItem(Object item)
		{
			if (!(item instanceof SectionHeading))
			{
				super.setSelectedItem(item);
				return;
			}

			int heading = indexOf(item);
			int step = heading >= getSelectedIndex() ? 1 : -1;
			for (int i = heading + step; i >= 0 && i < getItemCount(); i += step)
			{
				if (!(getItemAt(i) instanceof SectionHeading))
				{
					super.setSelectedItem(getItemAt(i));
					return;
				}
			}
		}

		private int indexOf(Object item)
		{
			for (int i = 0; i < getItemCount(); i++)
			{
				if (getItemAt(i) == item)
				{
					return i;
				}
			}
			return -1;
		}
	}

	@SuppressWarnings("serial")
	private static class ComboSectionRenderer extends DefaultListCellRenderer
	{
		private final JComboBox<Object> comboBox;

		ComboSectionRenderer(JComboBox<Object> comboBox)
		{
			this.comboBox = comboBox;
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			if (!(value instanceof SectionHeading))
			{
				return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			}

			JLabel label = (JLabel) super.getListCellRendererComponent(list, "", index, false, false);
			applyHeadingStyle(label, (SectionHeading) value, list, comboBox.getFont());
			label.setBorder(createHeadingBorder(list, index == 0));
			return label;
		}
	}

	private static Color blend(Color from, Color to, double amountTowardsTo)
	{
		return new Color((int) Math.round(from.getRed() + ((to.getRed() - from.getRed()) * amountTowardsTo)),
				(int) Math.round(from.getGreen() + ((to.getGreen() - from.getGreen()) * amountTowardsTo)),
				(int) Math.round(from.getBlue() + ((to.getBlue() - from.getBlue()) * amountTowardsTo)));
	}
}
