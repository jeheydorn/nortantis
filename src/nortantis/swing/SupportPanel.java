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
	private static final int checkboxGap = 4;

	private static final Color heartColor = new Color(214, 64, 90);

	private static final String websiteUrl = "https://jandjheydorn.com/nortantis";
	private static final String blogUrl = "https://jandjheydorn.com/blog";
	private static final String sourceCodeUrl = "https://github.com/jeheydorn/nortantis";
	private static final String bookUrl = "https://jandjheydorn.com/";
	private static final String donateUrl = "https://jandjheydorn.com/donate";

	// Google Analytics campaign (UTM) tag for links that point at our own site. utm_content is filled in per link (see withCampaign) so
	// individual links in this panel can be compared in Analytics.
	private static final String campaignParameters = "utm_source=nortantis&utm_medium=app&utm_campaign=support_panel&utm_content=";

	private final Dimension fixedPreferredSize;
	private final Font askFont;
	private final Font askFontBold;

	/**
	 * @param contentWidth
	 *            The fixed width, in pixels, to wrap this panel's rows to.
	 * @param showAskCard
	 *            Whether to show the ask card (with its "hide this on startup" checkbox) above the website/blog/source links. Pass false
	 *            when {@link UserPreferences#hideStartupSupportPanel} says to hide it - the utility links still show either way, so this
	 *            only ever hides the ask itself, never this whole panel.
	 */
	public SupportPanel(int contentWidth, boolean showAskCard)
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

		// The ask comes first, in its own highlighted card
		if (showAskCard)
		{
			JPanel askRow = createFlowRow(4, 4);
			buildAskRow(askRow);

			JCheckBox hideOnStartupCheckbox = new JCheckBox(Translation.get("startup.supportPanel.hideCheckbox"));
			hideOnStartupCheckbox.setOpaque(false);
			// Kept small so it doesn't compete with the ask for attention.
			hideOnStartupCheckbox.setFont(hideOnStartupCheckbox.getFont().deriveFont(hideOnStartupCheckbox.getFont().getSize2D() - 2f));
			hideOnStartupCheckbox.setSelected(UserPreferences.getInstance().hideStartupSupportPanel);
			hideOnStartupCheckbox.addActionListener(event ->
			{
				UserPreferences.getInstance().hideStartupSupportPanel = hideOnStartupCheckbox.isSelected();
				UserPreferences.getInstance().save();
			});

			JComponent askCard = wrapInCard(askRow, hideOnStartupCheckbox, contentWidth);
			y = addComponent(askCard, contentWidth, y);
			y += rowGap;
		}

		JPanel linksRow = createFlowRow(4, 2);
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkWebsite"), withCampaign(websiteUrl, "footer_website")));
		linksRow.add(createLinkSeparator());
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkBlog"), withCampaign(blogUrl, "footer_blog")));
		linksRow.add(createLinkSeparator());
		// The source code link goes to GitHub, whose analytics we can't see, so it is left untagged.
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkSourceCode"), sourceCodeUrl));
		linksRow.add(createLinkSeparator());
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkDonate"), withCampaign(donateUrl, "footer_donate")));
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
	 * Wraps content, plus a checkbox below it, in a softly rounded card tinted with the current look and feel's own colors, so the ask
	 * reads as a deliberate callout without introducing an unrelated accent color.
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
				// drawRoundRect's 1px stroke is centered on the path, so at inset 0 (the top/left edges) half the stroke would fall
				// outside this component's paintable area and get clipped. Insetting by 1 on all sides gives the stroke room to render
				// fully everywhere, not just at the bottom/right (which already had margin from the -1 on width/height).
				g2.setColor(cardBackgroundColor);
				g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, cardArc, cardArc);
				g2.setColor(cardBorderColor);
				g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, cardArc, cardArc);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		card.setOpaque(false);
		card.add(content);

		Dimension checkboxSize = measureCheckboxSize(checkbox);
		int checkboxY = cardPadding + contentSize.height + checkboxGap;
		checkbox.setBounds((width - checkboxSize.width) / 2, checkboxY, checkboxSize.width, checkboxSize.height);
		card.add(checkbox);
		int cardHeight = checkboxY + checkboxSize.height + cardPadding;

		card.setPreferredSize(new Dimension(width, cardHeight));
		return card;
	}

	/**
	 * {@link JCheckBox#getPreferredSize()} under-measures the text width here (likely a stale metric cached from before this checkbox's
	 * font was shrunk after construction), which clipped the checkbox's text. Measuring directly from font metrics, with a generous
	 * allowance for the checkbox icon and its gap from the text, avoids that.
	 */
	private static Dimension measureCheckboxSize(JCheckBox checkbox)
	{
		FontMetrics metrics = checkbox.getFontMetrics(checkbox.getFont());
		int textWidth = metrics.stringWidth(checkbox.getText());
		int iconSize = 16;
		// The icon allowance is deliberately more generous than iconSize alone (28 vs. 16): FlatLaf's checkbox icon reserves extra
		// horizontal space beyond the visible glyph (e.g. for its hover/focus highlight), and under-allowing here clips the text.
		int width = textWidth + 28 + checkbox.getIconTextGap() + 12;
		int height = Math.max(metrics.getHeight(), iconSize) + 4;
		return new Dimension(width, height);
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

		addWords(askRow, Translation.get("startup.supportAsk.beforeDonateLink"));
		askRow.add(createBoldAskLink(Translation.get("startup.supportAsk.donateLinkText"), withCampaign(donateUrl, "ask_donate")));
		addWords(askRow, Translation.get("startup.supportAsk.betweenLinks"));
		askRow.add(createBoldAskLink(Translation.get("startup.supportAsk.bookLinkText"), withCampaign(bookUrl, "ask_book")));
		addWords(askRow, Translation.get("startup.supportAsk.afterBookLink"));
	}

	/**
	 * Adds each word of text as its own label so the enclosing WrapLayout row can wrap the sentence naturally around embedded hyperlinks.
	 */
	/**
	 * Appends Google Analytics campaign (UTM) parameters to a link that points at our own site (jandjheydorn.com), so clicks originating
	 * from this in-app panel can be attributed in Analytics. These go to pages we control that carry the Analytics tag, so the parameters
	 * are read there; it's user-initiated navigation only, with no personal data. External links (e.g. the GitHub source) are left
	 * untagged since we can't see their analytics anyway.
	 *
	 * @param content
	 *            Identifies which link within this panel was clicked (utm_content), so individual links can be compared.
	 */
	private static String withCampaign(String url, String content)
	{
		String separator = url.contains("?") ? "&" : "?";
		return url + separator + campaignParameters + content;
	}

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
