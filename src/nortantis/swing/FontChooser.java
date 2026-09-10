package nortantis.swing;

import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FontChooser
{
	/**
	 * Multiplier applied to a preview's maximum point size to get a preview height tall enough for any font drawn at that size. A font's
	 * maximum ascent plus maximum descent rarely reaches twice its point size, even for script faces with tall swashes.
	 */
	private static final double previewHeightToPointSizeThatFitsAnyFont = 3.0;

	/** Space left above and below the preview text so glyphs don't sit against the edge of the holder. */
	private static final int previewVerticalPadding = 6;

	private final JLabel fontDisplay = new JLabel("");
	private final JPanel displayHolder = new JPanel();
	private final JPanel statusPanel = new JPanel();
	private final JLabel statusLabel = new JLabel();
	private final JLabel fixLink;
	private Runnable fixAction;
	private String textThatMustBeDrawable = "";
	private List<String> familiesUsedByThisMap = new ArrayList<>();
	final JButton chooseButton;
	private Font font;
	private final int maxFontDisplaySize;
	private final int minPreviewHeight;
	private final int maxPreviewHeight;
	private final Runnable okAction;
	private final String labelText;

	/**
	 * Creates a font chooser whose preview grows to fit any font drawn at maxFontSize.
	 */
	public FontChooser(String labelText, int minPreviewHeight, int maxFontSize, Runnable okAction)
	{
		this(labelText, minPreviewHeight, heightThatFitsAnyFontAtSize(maxFontSize), maxFontSize, okAction);
	}

	public FontChooser(String labelText, int minPreviewHeight, int maxPreviewHeight, int maxFontSize, Runnable okAction)
	{
		this.minPreviewHeight = minPreviewHeight;
		this.maxPreviewHeight = Math.max(minPreviewHeight, maxPreviewHeight);
		this.maxFontDisplaySize = maxFontSize;
		this.okAction = okAction;
		this.labelText = labelText;
		chooseButton = new JButton(Translation.get("fontChooser.choose"));

		statusLabel.setForeground(SwingHelper.warningMessageColor);
		fixLink = SwingHelper.createActionLink(Translation.get("theme.font.fix"), () ->
		{
			if (fixAction != null)
			{
				fixAction.run();
			}
		});

		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		statusPanel.add(SwingHelper.createWarningIconLabel());
		statusPanel.add(statusLabel);
		statusPanel.add(Box.createHorizontalStrut(8));
		statusPanel.add(fixLink);
		statusPanel.add(Box.createHorizontalGlue());
		statusPanel.setVisible(false);
	}

	/**
	 * Shows a warning under the preview saying that this machine will not draw the chosen font the way the map says it should.
	 *
	 * @param message
	 *            The warning to show, or null to show nothing. Silence is the normal case and must stay visually quiet.
	 * @param fixTooltip
	 *            Tooltip for the fix link, naming the font it would switch to, or null to hide the link.
	 * @param fixAction
	 *            What the fix link does, or null to hide the link.
	 */
	public void setStatus(String message, String fixTooltip, Runnable fixAction)
	{
		this.fixAction = fixAction;
		statusLabel.setText(message == null ? "" : message);
		fixLink.setToolTipText(fixTooltip);
		fixLink.setVisible(fixAction != null);
		statusPanel.setVisible(message != null);
	}

	/**
	 * A preview height that any font drawn at the given point size will fit within.
	 */
	public static int heightThatFitsAnyFontAtSize(int maxFontSize)
	{
		return (int) (maxFontSize * previewHeightToPointSizeThatFitsAnyFont) + previewVerticalPadding;
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer)
	{
		final int spaceUnderFontDisplays = 4;
		displayHolder.setLayout(new BorderLayout());
		displayHolder.add(fontDisplay);
		applyPreviewHeight(minPreviewHeight);

		chooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				runFontChooser(organizer.panel, okAction);
			}
		});
		JPanel chooseButtonHolder = new JPanel();
		chooseButtonHolder.setLayout(new BoxLayout(chooseButtonHolder, BoxLayout.X_AXIS));
		chooseButtonHolder.add(chooseButton);
		chooseButtonHolder.add(Box.createHorizontalGlue());
		RowHider hider = organizer.addLabelAndComponentsVertical(labelText, "",
				Arrays.asList(displayHolder, statusPanel, Box.createVerticalStrut(spaceUnderFontDisplays), chooseButtonHolder));
		return hider;
	}

	private void runFontChooser(JComponent parent, Runnable okAction)
	{
		JFontChooser fontChooser = new JFontChooser();
		fontChooser.setTextThatMustBeDrawable(textThatMustBeDrawable);
		fontChooser.setFamiliesUsedByThisMap(familiesUsedByThisMap);
		fontChooser.setSelectedFont(font);
		int status = fontChooser.showDialog(parent);
		if (status == JFontChooser.OK_OPTION)
		{
			font = fontChooser.getSelectedFont();
			updatePreview();
			okAction.run();
		}
	}

	/**
	 * The text this font has to be able to draw, so the picker can mark families that have no glyphs for it.
	 */
	public void setTextThatMustBeDrawable(String text)
	{
		textThatMustBeDrawable = text == null ? "" : text;
	}

	/**
	 * The families the map already draws with, which the picker lists first so that matching one piece of text to another does not mean
	 * hunting through every font on the machine for the one already in use.
	 */
	public void setFamiliesUsedByThisMap(List<String> families)
	{
		familiesUsedByThisMap = families == null ? new ArrayList<>() : families;
	}

	public Font getFont()
	{
		return font;
	}

	public void setFont(Font font)
	{
		this.font = font;
		updatePreview();
	}

	private void updatePreview()
	{
		if (font == null)
		{
			return;
		}

		Font displayFont = font.deriveFont(font.getStyle(), (float) (Math.min(font.getSize(), maxFontDisplaySize)));
		fontDisplay.setFont(displayFont);
		// getName, not getFontName: the preview names the family the map records, not the particular face the family resolves to. Those
		// differ whenever a family ships a single weight that isn't Regular, and for a family this machine doesn't have at all.
		fontDisplay.setText(font.getName());

		// Measure the font rather than assuming a height. getMaxAscent is used rather than getAscent because script faces routinely draw
		// swashes and ascenders above the typical ascent, and those are the fonts this app is full of.
		FontMetrics metrics = fontDisplay.getFontMetrics(displayFont);
		int neededHeight = metrics.getMaxAscent() + metrics.getMaxDescent() + previewVerticalPadding;
		applyPreviewHeight(Math.max(minPreviewHeight, Math.min(neededHeight, maxPreviewHeight)));
		displayHolder.revalidate();
	}

	private void applyPreviewHeight(int height)
	{
		displayHolder.setPreferredSize(new Dimension(displayHolder.getPreferredSize().width, height));
		// A long font name rendered at the preview point size otherwise reports a very large minimum width, which makes GridBagLayout starve
		// the label column until the left-side labels collapse to zero width and disappear. Letting the preview shrink keeps the labels
		// visible; the name is simply clipped when it doesn't fit.
		displayHolder.setMinimumSize(new Dimension(0, height));
	}

}
