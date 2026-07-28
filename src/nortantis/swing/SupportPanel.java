package nortantis.swing;

import nortantis.editor.UserPreferences;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a support ask (buy a book / donate), followed by smaller links to the Nortantis website, blog, and source code. Used both as the
 * bottom overlay on the map canvas at startup (see {@link MapCanvasOverlay}) and inside the About Nortantis dialog.
 */
@SuppressWarnings("serial")
public class SupportPanel extends JPanel
{
	public static final int defaultContentWidth = 480;

	private static final int rowGap = 14;
	private static final int cardPadding = 14;
	private static final int cardArc = 18;
	private static final int checkboxGap = 4;
	private static final int wordGap = 4;

	private static final Color heartColor = new Color(214, 64, 90);

	private static final String websiteUrl = "https://jandjheydorn.com/nortantis";
	private static final String blogUrl = "https://jandjheydorn.com/blog";
	private static final String sourceCodeUrl = "https://github.com/jeheydorn/nortantis";
	private static final String bookUrl = "https://jandjheydorn.com/";
	private static final String donateUrl = "https://jandjheydorn.com/donate";

	private final Dimension fixedPreferredSize;
	private final Font askFont;
	private final Font askFontBold;

	/**
	 * @param contentWidth
	 *            The fixed width, in pixels, to wrap this panel's rows to.
	 * @param showAskCard
	 *            Whether to show the ask (with its "hide this on startup" checkbox) above the website/blog/source links. Pass false when
	 *            {@link UserPreferences#hideStartupSupportPanel} says to hide it - the utility links still show either way, so this only
	 *            ever hides the ask itself, never this whole panel.
	 * @param useCard
	 *            Whether to set the ask off in a highlighted card background/border, rather than as plain text. Ignored if
	 *            {@code showAskCard} is false.
	 */
	public SupportPanel(int contentWidth, boolean showAskCard, boolean useCard)
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

		if (showAskCard)
		{
			// The ask row spaces its words itself rather than through a layout gap, since some of the seams between words and hyperlinks
			// take no space. See buildAskRow.
			JPanel askRow = createFlowRow(0, 4);
			buildAskRow(askRow);

			JCheckBox hideOnStartupCheckbox = new JCheckBox(Translation.get("startup.supportPanel.hideCheckbox"));
			hideOnStartupCheckbox.setOpaque(false);
			hideOnStartupCheckbox.setFont(hideOnStartupCheckbox.getFont().deriveFont(hideOnStartupCheckbox.getFont().getSize2D() - 2f));
			hideOnStartupCheckbox.setSelected(UserPreferences.getInstance().hideStartupSupportPanel);
			hideOnStartupCheckbox.addActionListener(event ->
			{
				UserPreferences.getInstance().hideStartupSupportPanel = hideOnStartupCheckbox.isSelected();
				UserPreferences.getInstance().save();
			});

			JComponent askContainer = wrapAskContent(askRow, hideOnStartupCheckbox, contentWidth, useCard);
			y = addComponent(askContainer, contentWidth, y);
			y += rowGap;
		}

		JPanel linksRow = createFlowRow(4, 2);
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkWebsite"), websiteUrl));
		linksRow.add(createLinkSeparator());
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkBlog"), blogUrl));
		linksRow.add(createLinkSeparator());
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkSourceCode"), sourceCodeUrl));
		linksRow.add(createLinkSeparator());
		linksRow.add(SwingHelper.createHyperlink(Translation.get("startup.linkDonate"), donateUrl));
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
	 * Wraps content, plus a checkbox below it, in a rounded card tinted with the current look and feel's colors, or, if {@code useCard} is
	 * false, in plain text with no card background/border.
	 */
	private JComponent wrapAskContent(JPanel content, JCheckBox checkbox, int width, boolean useCard)
	{
		int padding = useCard ? cardPadding : 0;
		int innerWidth = width - 2 * padding;
		// See addComponent: pin the row's width before measuring its wrapped preferred height.
		content.setSize(innerWidth, 1);
		Dimension contentSize = content.getPreferredSize();
		content.setBounds(padding, padding, innerWidth, contentSize.height);

		JPanel container;
		if (useCard)
		{
			boolean isDarkTheme = UserPreferences.getInstance().lookAndFeel == LookAndFeel.Dark;
			Color cardBackgroundColor = isDarkTheme ? new Color(255, 255, 255, 20) : new Color(0, 0, 0, 16);
			Color cardBorderColor = isDarkTheme ? new Color(255, 255, 255, 45) : new Color(0, 0, 0, 40);

			container = new JPanel(null)
			{
				@Override
				protected void paintComponent(Graphics g)
				{
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					// drawRoundRect's 1px stroke is centered on the path, so at inset 0 (the top/left edges) half the stroke would fall
					// outside this component's paintable area and get clipped. Insetting by 1 on all sides gives the stroke room to
					// render fully everywhere, not just at the bottom/right (which already had margin from the -1 on width/height).
					g2.setColor(cardBackgroundColor);
					g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, cardArc, cardArc);
					g2.setColor(cardBorderColor);
					g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, cardArc, cardArc);
					g2.dispose();
					super.paintComponent(g);
				}
			};
		}
		else
		{
			container = new JPanel(null);
		}
		container.setOpaque(false);
		container.add(content);

		Dimension checkboxSize = measureCheckboxSize(checkbox);
		int checkboxY = padding + contentSize.height + checkboxGap;
		checkbox.setBounds((width - checkboxSize.width) / 2, checkboxY, checkboxSize.width, checkboxSize.height);
		container.add(checkbox);
		int containerHeight = checkboxY + checkboxSize.height + padding;

		container.setPreferredSize(new Dimension(width, containerHeight));
		return container;
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

	private static JLabel createLinkSeparator()
	{
		JLabel separator = new JLabel("|");
		Color disabledColor = UIManager.getColor("Label.disabledForeground");
		separator.setForeground(disabledColor != null ? disabledColor : Color.GRAY);
		return separator;
	}

	private void buildAskRow(JPanel askRow)
	{
		JLabel heart = new JLabel("♥");
		heart.setForeground(heartColor);
		heart.setFont(askFont.deriveFont(askFont.getSize2D() + 2f));
		setSpaceAfter(heart, true);
		askRow.add(heart);

		String betweenLinksText = Translation.get("startup.supportAsk.betweenLinks");
		String afterBookText = Translation.get("startup.supportAsk.afterBookLink");

		JLabel previous = addWords(askRow, null, Translation.get("startup.supportAsk.beforeDonateLink"));
		previous = addLinkWithAttachedPunctuation(askRow, previous,
				createBoldAskLink(Translation.get("startup.supportAsk.donateLinkText"), donateUrl), betweenLinksText);
		previous = addWords(askRow, previous, stripLeadingAttachedPunctuation(betweenLinksText));
		previous = addLinkWithAttachedPunctuation(askRow, previous,
				createBoldAskLink(Translation.get("startup.supportAsk.bookLinkText"), bookUrl), afterBookText);
		addWords(askRow, previous, stripLeadingAttachedPunctuation(afterBookText));
	}

	/**
	 * Adds a hyperlink to a row, together with any punctuation that starts the text following it, grouped into one component so that a line
	 * wrap cannot strand the punctuation at the start of the next line.
	 *
	 * @param followingText
	 *            The text that comes after the link, whose leading punctuation is pulled into the group. Callers must add that text with
	 *            {@link #stripLeadingAttachedPunctuation} applied, or the punctuation appears twice.
	 * @return The label that later pieces should be spaced against: the punctuation when there is any, otherwise the link.
	 */
	private JLabel addLinkWithAttachedPunctuation(JPanel row, JLabel previous, JLabel link, String followingText)
	{
		if (previous != null)
		{
			setSpaceAfter(previous, needsSpaceBetween(previous.getText(), link.getText()));
		}

		String punctuation = leadingAttachedPunctuation(followingText);
		if (punctuation.isEmpty())
		{
			row.add(link);
			return link;
		}

		JLabel punctuationLabel = new JLabel(punctuation);
		punctuationLabel.setFont(askFont);

		JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		group.setOpaque(false);
		group.add(link);
		group.add(punctuationLabel);
		row.add(group);
		return punctuationLabel;
	}

	/**
	 * The run of punctuation at the start of text that belongs to whatever came before it, such as the period that ends the ask sentence.
	 */
	private static String leadingAttachedPunctuation(String text)
	{
		int end = 0;
		while (end < text.length() && attachesToPrecedingText(text.charAt(end)))
		{
			end++;
		}
		return text.substring(0, end);
	}

	private static String stripLeadingAttachedPunctuation(String text)
	{
		return text.substring(leadingAttachedPunctuation(text).length());
	}

	/**
	 * Adds each word of text as its own label so the enclosing WrapLayout row can wrap the sentence naturally around embedded hyperlinks.
	 *
	 * @param previous
	 *            The label the first word follows, or null to leave the spacing before the first word as it is.
	 * @return The label holding the last word, or {@code previous} if the text has no words.
	 */
	private JLabel addWords(JPanel row, JLabel previous, String text)
	{
		JLabel last = previous;
		boolean isFirstWord = true;
		for (String word : text.trim().split("\\s+"))
		{
			if (word.isEmpty())
			{
				continue;
			}

			boolean isFirstPieceOfWord = true;
			for (String piece : splitIntoWrappablePieces(word))
			{
				JLabel label = new JLabel(piece);
				label.setFont(askFont);
				if (isFirstWord && isFirstPieceOfWord)
				{
					addPiece(row, last, label);
				}
				else
				{
					if (isFirstPieceOfWord)
					{
						// Words are separated by whitespace in the text itself, so they always take a space. Pieces within one word are
						// consecutive characters, so they take none.
						setSpaceAfter(last, true);
					}
					row.add(label);
				}
				last = label;
				isFirstPieceOfWord = false;
			}
			isFirstWord = false;
		}
		return last;
	}

	/**
	 * Splits a word into the pieces a line wrap may fall between. Scripts written without spaces between words, such as Chinese, wrap
	 * between characters, so each of their characters becomes its own piece and the row can break anywhere in a run of them; runs of other
	 * characters stay whole so words are never split mid-word. Punctuation that attaches to the text before it stays in the piece it
	 * follows, which keeps a wrap from stranding it at the start of a line.
	 */
	private static List<String> splitIntoWrappablePieces(String word)
	{
		List<String> pieces = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < word.length(); i++)
		{
			char c = word.charAt(i);
			if (attachesToPrecedingText(c) && current.length() > 0)
			{
				current.append(c);
			}
			else if (isWrittenWithoutSpaces(c))
			{
				addPieceIfNotEmpty(pieces, current);
				current.append(c);
			}
			else
			{
				if (current.length() > 0 && isWrittenWithoutSpaces(current.charAt(0)))
				{
					addPieceIfNotEmpty(pieces, current);
				}
				current.append(c);
			}
		}
		addPieceIfNotEmpty(pieces, current);
		return pieces;
	}

	private static void addPieceIfNotEmpty(List<String> pieces, StringBuilder current)
	{
		if (current.length() > 0)
		{
			pieces.add(current.toString());
			current.setLength(0);
		}
	}

	/**
	 * Adds one label of the ask sentence to a row, spacing it from the label it follows.
	 *
	 * @param previous
	 *            The label the added one follows, or null to leave the spacing before it as it is.
	 * @return The added label.
	 */
	private JLabel addPiece(JPanel row, JLabel previous, JLabel piece)
	{
		if (previous != null)
		{
			setSpaceAfter(previous, needsSpaceBetween(previous.getText(), piece.getText()));
		}
		row.add(piece);
		return piece;
	}

	private static void setSpaceAfter(JLabel label, boolean hasSpaceAfter)
	{
		label.setBorder(hasSpaceAfter ? BorderFactory.createEmptyBorder(0, 0, 0, wordGap) : null);
	}

	/**
	 * Whether a space belongs between two adjacent labels of the ask sentence. The translated text has no whitespace where it meets a
	 * hyperlink, so the spacing at those seams is decided from the characters on either side of them: punctuation that attaches to the text
	 * before it, such as the period that ends the sentence, and scripts written without spaces between words, such as Chinese, take no
	 * space.
	 */
	private static boolean needsSpaceBetween(String before, String after)
	{
		if (before.isEmpty() || after.isEmpty())
		{
			return false;
		}

		char endOfBefore = before.charAt(before.length() - 1);
		char startOfAfter = after.charAt(0);
		if (isWrittenWithoutSpaces(endOfBefore) || isWrittenWithoutSpaces(startOfAfter))
		{
			return false;
		}
		return !attachesToPrecedingText(startOfAfter);
	}

	private static boolean attachesToPrecedingText(char c)
	{
		return ".,;:!?%)]}…".indexOf(c) >= 0 || "。，、；：！？）〕】｝」』〉》".indexOf(c) >= 0;
	}

	private static boolean isWrittenWithoutSpaces(char c)
	{
		if (Character.isIdeographic(c))
		{
			return true;
		}

		Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
		return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
				|| block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS || block == Character.UnicodeBlock.HIRAGANA
				|| block == Character.UnicodeBlock.KATAKANA;
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
