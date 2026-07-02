package nortantis.swing;

import nortantis.editor.UserPreferences;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;

/**
 * Shows a support ask (buy a book / donate) in a highlighted card, followed by smaller links to the Nortantis website, blog, and source
 * code. Used both as the bottom overlay on the map canvas at startup (see {@link MapCanvasOverlay}) and inside the About Nortantis dialog.
 * <p>
 * The ask is deliberately the most visually prominent thing here (larger type, a card background, a heart accent) since it's the actual
 * point of this panel. The card is tinted using a translucent overlay of the current look and feel's own colors (rather than an
 * unrelated accent color), so it reads as a gentle highlight consistent with the rest of the UI instead of a garish banner.
 * <p>
 * The book-purchase and donation asks are both shown for every locale (with the same content, only translated) - the book being only sold
 * in English doesn't stop this from linking to it everywhere.
 * <p>
 * Content is laid out once, at construction, wrapped to a fixed width. This avoids the chicken-and-egg problem of a wrapping
 * {@link WrapLayout} row needing a width before it can report a preferred height: since the width never changes after construction, there
 * is no need to relayout later.
 */
@SuppressWarnings("serial")
public class SupportPanel extends JPanel
{
	public static final int defaultContentWidth = 460;

	private static final int rowGap = 14;
	private static final int cardPadding = 14;
	private static final int cardArc = 18;
	private static final int checkboxGap = 10;

	/** A warm rose-red for the heart accent, rather than an alarming pure red, to keep the tone friendly rather than urgent. */
	private static final Color heartColor = new Color(214, 64, 90);

	private static final String websiteUrl = "https://jandjheydorn.com/nortantis";
	private static final String blogUrl = "https://jandjheydorn.com/";
	private static final String sourceCodeUrl = "https://github.com/jeheydorn/nortantis";
	private static final String bookUrl = "https://jandjheydorn.com/";
	private static final String donateUrl = "https://jandjheydorn.com/donate";

	private final Dimension fixedPreferredSize;
	private final Font askFont;
	private final Font askFontBold;

	/**
	 * @param contentWidth
	 *            The fixed width, in pixels, to wrap this panel's rows to.
	 * @param includeHideOnStartupCheckbox
	 *            Whether to include a checkbox, at the bottom of the ask card, for {@link UserPreferences#hideStartupSupportPanel}. This
	 *            should be true only for the About dialog's instance - the startup screen's own instance only exists when that
	 *            preference already says to show it, so a checkbox there would be redundant.
	 */
	public SupportPanel(int contentWidth, boolean includeHideOnStartupCheckbox)
	{
		setOpaque(false);
		setLayout(null);

		Font baseFont = UIManager.getFont("Label.font");
		if (baseFont == null)
		{
			baseFont = new JLabel().getFont();
		}
		askFont = baseFont.deriveFont(baseFont.getSize2D() + 3f);
		askFontBold = askFont.deriveFont(Font.BOLD);

		int y = 0;

		// The ask comes first, in its own highlighted card, since it's the point of this panel; the utility links follow below at the
		// normal text size.
		JPanel askRow = createFlowRow(4, 4);
		buildAskRow(askRow);

		JCheckBox hideOnStartupCheckbox = null;
		if (includeHideOnStartupCheckbox)
		{
			hideOnStartupCheckbox = new JCheckBox(Translation.get("startup.supportPanel.hideCheckbox"));
			hideOnStartupCheckbox.setOpaque(false);
			hideOnStartupCheckbox.setSelected(UserPreferences.getInstance().hideStartupSupportPanel);
			JCheckBox checkboxRef = hideOnStartupCheckbox;
			hideOnStartupCheckbox.addActionListener(event ->
			{
				UserPreferences.getInstance().hideStartupSupportPanel = checkboxRef.isSelected();
				UserPreferences.getInstance().save();
			});
		}

		JComponent askCard = wrapInCard(askRow, hideOnStartupCheckbox, contentWidth);
		y = addComponent(askCard, contentWidth, y);
		y += rowGap;

		JPanel linksRow = createFlowRow(4, 2);
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkWebsite"), websiteUrl));
		linksRow.add(createLinkSeparator());
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkBlog"), blogUrl));
		linksRow.add(createLinkSeparator());
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkSourceCode"), sourceCodeUrl));
		y = addComponent(linksRow, contentWidth, y);

		fixedPreferredSize = new Dimension(contentWidth, y);
	}

	private JPanel createFlowRow(int hgap, int vgap)
	{
		JPanel row = new JPanel(new WrapLayout(FlowLayout.CENTER, hgap, vgap));
		row.setOpaque(false);
		return row;
	}

	/**
	 * Wraps content (and, if given, a checkbox below it) in a softly rounded card tinted with the current look and feel's own colors, so
	 * the ask reads as a deliberate callout without introducing an unrelated accent color.
	 */
	private JComponent wrapInCard(JPanel content, JCheckBox checkbox, int width)
	{
		int innerWidth = width - 2 * cardPadding;
		// See addComponent: pin the row's width before measuring its wrapped preferred height.
		content.setSize(innerWidth, 1);
		Dimension contentSize = content.getPreferredSize();
		content.setBounds(cardPadding, cardPadding, innerWidth, contentSize.height);

		boolean isDarkTheme = UserPreferences.getInstance().lookAndFeel == LookAndFeel.Dark;
		Color cardBackgroundColor = isDarkTheme ? new Color(255, 255, 255, 20) : new Color(0, 0, 0, 16);
		Color cardBorderColor = isDarkTheme ? new Color(255, 255, 255, 45) : new Color(0, 0, 0, 40);

		JPanel card = new JPanel(null)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(cardBackgroundColor);
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cardArc, cardArc);
				g2.setColor(cardBorderColor);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cardArc, cardArc);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		card.setOpaque(false);
		card.add(content);

		int cardHeight = cardPadding + contentSize.height;
		if (checkbox != null)
		{
			Dimension checkboxSize = checkbox.getPreferredSize();
			int checkboxY = cardPadding + contentSize.height + checkboxGap;
			checkbox.setBounds((width - checkboxSize.width) / 2, checkboxY, checkboxSize.width, checkboxSize.height);
			card.add(checkbox);
			cardHeight = checkboxY + checkboxSize.height;
		}
		cardHeight += cardPadding;

		card.setPreferredSize(new Dimension(width, cardHeight));
		return card;
	}

	private int addComponent(JComponent component, int width, int y)
	{
		component.setSize(width, Math.max(1, component.getHeight()));
		Dimension size = component.getPreferredSize();
		component.setBounds(0, y, width, size.height);
		add(component);
		return y + size.height;
	}

	private JLabel createBoldAskLink(String text, String url)
	{
		JLabel link = SwingHelper.createHyperlink(text, url);
		link.setFont(askFontBold);
		return link;
	}

	/**
	 * A small "|" separator between the header links, so adjacent hyperlinks (which are otherwise the same color with only a small gap
	 * between them) read as distinct clickable items rather than running together.
	 */
	private static JLabel createLinkSeparator()
	{
		JLabel separator = new JLabel("|");
		Color disabledColor = UIManager.getColor("Label.disabledForeground");
		separator.setForeground(disabledColor != null ? disabledColor : Color.GRAY);
		return separator;
	}

	private void buildAskRow(JPanel askRow)
	{
		// The book and donation asks are shown identically in every locale (content-wise; only the translated wording differs) - the
		// book being English-only, and the donation system's country support, don't change what's offered here.
		JLabel heart = new JLabel("♥");
		heart.setForeground(heartColor);
		heart.setFont(askFont.deriveFont(askFont.getSize2D() + 2f));
		askRow.add(heart);

		addWords(askRow, Translation.get("startup.supportAsk.full"));
		askRow.add(createBoldAskLink(Translation.get("startup.supportAsk.bookLinkText"), bookUrl));
		addWords(askRow, Translation.get("startup.supportAsk.fullMiddle"));
		askRow.add(createBoldAskLink(Translation.get("startup.supportAsk.donationLinkTextWithPeriod"), donateUrl));
	}

	/**
	 * Adds each word of text as its own label so the enclosing WrapLayout row can wrap the sentence naturally around embedded hyperlinks.
	 */
	private void addWords(JPanel row, String text)
	{
		for (String word : text.trim().split("\\s+"))
		{
			if (!word.isEmpty())
			{
				JLabel label = new JLabel(word);
				label.setFont(askFont);
				row.add(label);
			}
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		return fixedPreferredSize;
	}

	@Override
	public Dimension getMinimumSize()
	{
		return fixedPreferredSize;
	}

	@Override
	public Dimension getMaximumSize()
	{
		// Without this, a null-layout JPanel's default maximum size is (Integer.MAX_VALUE, Integer.MAX_VALUE), which breaks the size
		// calculations of any BoxLayout ancestor (e.g. the About dialog stacks this below the bug-report section).
		return fixedPreferredSize;
	}
}
