package nortantis.swing;

import nortantis.MapFonts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextBackgroundEffect;
import nortantis.TextDrawer;
import nortantis.TextStyle;
import nortantis.TextStylePart;
import nortantis.TextType;
import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PixelReader;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Tuple2;
import nortantis.geom.IntPoint;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Fonts tab of the Theme panel: the style of each type of text, which text of that type follows except for the parts it has its own
 * values for.
 */
class TextStylesPanel
{
	private final MainWindow mainWindow;
	private final JScrollPane scrollPane;
	private final EnumMap<TextType, TextStyle> styles = new EnumMap<>(TextType.class);
	private final EnumMap<TextType, TextStyleControls> controlsByType = new EnumMap<>(TextType.class);
	private final EnumMap<TextType, CollapsiblePanel> cardsByType = new EnumMap<>(TextType.class);
	private final EnumMap<TextType, SamplePanel> samplesByType = new EnumMap<>(TextType.class);

	/**
	 * The space between the sides of a sample's chip and its text.
	 */
	private static final int sampleChipHorizontalPadding = 6;
	/**
	 * The space between the top or bottom of a sample's chip and its text's tallest or lowest letters.
	 */
	private static final int sampleChipVerticalPadding = 4;
	/**
	 * How tall the tallest and lowest letters of the largest samples are.
	 */
	private static final int largestSampleTextHeight = 39;
	/**
	 * The font size at and above which samples are drawn at the largest size.
	 */
	private static final float fontSizeOfLargestSamples = 24f;

	TextStylesPanel(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;

		GridBagOrganizer organizer = new GridBagOrganizer();

		JButton expandAllButton = new JButton(Translation.get("textStyles.expandAll"));
		expandAllButton.addActionListener(e -> setAllCollapsed(false));
		JButton collapseAllButton = new JButton(Translation.get("textStyles.collapseAll"));
		collapseAllButton.addActionListener(e -> setAllCollapsed(true));
		organizer.addLeftAlignedComponents(Arrays.asList(expandAllButton, collapseAllButton));

		for (TextType type : TextType.values())
		{
			GridBagOrganizer cardOrganizer = new GridBagOrganizer();
			TextStyleControls controls = new TextStyleControls(cardOrganizer, edit -> handleEdit(type, edit), this::getFontFamiliesUsedByThisMap,
					() -> getTextThatFollowsTypeFont(type));
			controls.setVisible(true);
			controlsByType.put(type, controls);

			JButton copyButton = new JButton(Translation.get("textTool.copyStyleTo"));
			copyButton.setToolTipText(Translation.get("textStyles.copyStyleTo.tooltip"));
			copyButton.addActionListener(e -> showCopyDialog(type));
			cardOrganizer.addLeftAlignedComponents(Arrays.asList(copyButton));
			cardOrganizer.addHorizontalSpacerRowToHelpComponentAlignment(0.64);

			CollapsiblePanel card = new CollapsiblePanel("text_styles", type.name(), type.toString(), cardOrganizer.panel, true);
			SamplePanel sample = new SamplePanel();
			card.setTitleAccessory(sample);
			samplesByType.put(type, sample);
			cardsByType.put(type, card);
			organizer.addLeftAlignedComponent(card, GridBagOrganizer.rowVerticalInset, GridBagOrganizer.rowVerticalInset, false);
		}

		organizer.addVerticalFillerRow();
		scrollPane = organizer.createScrollPane();
		scrollPane.addComponentListener(new java.awt.event.ComponentAdapter()
		{
			@Override
			public void componentShown(java.awt.event.ComponentEvent e)
			{
				// The samples sit on the land color, which the Background tab changes.
				updateSamples();
			}
		});
	}

	JScrollPane getScrollPane()
	{
		return scrollPane;
	}

	private void setAllCollapsed(boolean isCollapsed)
	{
		for (CollapsiblePanel card : cardsByType.values())
		{
			card.setCollapsed(isCollapsed);
		}
	}

	private List<String> getFontFamiliesUsedByThisMap()
	{
		MapSettings settings = mainWindow.getSettingsFromGUI(false);
		return settings == null ? new ArrayList<>() : MapFonts.getFamiliesUsed(settings);
	}

	/**
	 * The distinct characters of the text of the given type that is drawn with the type's font.
	 */
	private String getTextThatFollowsTypeFont(TextType type)
	{
		if (mainWindow.edits == null || mainWindow.edits.text == null)
		{
			return "";
		}
		Set<Integer> codePoints = new LinkedHashSet<>();
		for (MapText text : mainWindow.edits.text)
		{
			if (text.type == type && text.styleOverrides.fontFamily == null && text.value != null)
			{
				text.value.codePoints().forEach(codePoints::add);
			}
		}
		StringBuilder result = new StringBuilder();
		for (int codePoint : codePoints)
		{
			result.appendCodePoint(codePoint);
		}
		return result.toString();
	}

	private void handleEdit(TextType type, TextStyleControls.StyleEdit edit)
	{
		TextStyle style = styles.get(type);
		if (style == null)
		{
			return;
		}
		edit.apply(style);
		controlsByType.get(type).showStyles(Arrays.asList(style));
		updateSample(type);
		handleStylesChange();
	}

	private void handleStylesChange()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.Text, null);
		mainWindow.handleThemeChange(false);
		mainWindow.updater.createAndShowMapTextChange();
	}

	private void showCopyDialog(TextType sourceType)
	{
		TextStyle source = styles.get(sourceType);
		if (source == null)
		{
			return;
		}
		TextStyle sourceCopy = source.copy();
		new CopyTextStyleDialog(mainWindow, sourceType, choice -> copyStyle(sourceCopy, choice)).setVisible(true);
	}

	/**
	 * Copies the chosen parts of a style to the styles of the chosen types, and when chosen, resets those parts of the text of those types
	 * that has its own values for them. Sets an undo point and redraws the text.
	 */
	void copyStyle(TextStyle source, CopyTextStyleDialog.Choice choice)
	{
		for (TextType type : choice.types())
		{
			TextStyle style = styles.get(type);
			if (style != null)
			{
				style.copyPartsFrom(source, choice.parts());
				controlsByType.get(type).showStyles(Arrays.asList(style));
				updateSample(type);
			}
		}
		if (choice.resetsOwnStyles() && mainWindow.edits != null)
		{
			for (MapText text : mainWindow.edits.text)
			{
				if (choice.types().contains(text.type))
				{
					for (TextStylePart part : choice.parts())
					{
						text.styleOverrides.clear(part);
					}
				}
			}
		}
		handleStylesChange();
	}

	/**
	 * The style of the given type of text. It must not be modified.
	 */
	TextStyle getStyle(TextType type)
	{
		return styles.get(type);
	}

	/**
	 * A copy of the style of each type of text.
	 */
	EnumMap<TextType, TextStyle> copyStyles()
	{
		EnumMap<TextType, TextStyle> result = new EnumMap<>(TextType.class);
		for (Map.Entry<TextType, TextStyle> entry : styles.entrySet())
		{
			result.put(entry.getKey(), entry.getValue().copy());
		}
		return result;
	}

	void loadSettingsIntoGUI(MapSettings settings)
	{
		styles.clear();
		styles.putAll(settings.copyTextStyleDefaults());
		for (Map.Entry<TextType, TextStyle> entry : styles.entrySet())
		{
			controlsByType.get(entry.getKey()).showStyles(Arrays.asList(entry.getValue()));
		}
		// Drawn later because the land color may not be loaded yet.
		SwingUtilities.invokeLater(this::updateSamples);
	}

	void getSettingsFromGUI(MapSettings settings)
	{
		if (!styles.isEmpty())
		{
			settings.textStyleDefaults = copyStyles();
		}
	}

	private void updateSamples()
	{
		for (TextType type : TextType.values())
		{
			updateSample(type);
		}
	}

	private void updateSample(TextType type)
	{
		SamplePanel samplePanel = samplesByType.get(type);
		TextStyle style = styles.get(type);
		Image sample = style == null ? null : createSample(type, style);
		// A shape is its own backdrop.
		samplePanel.chipColor = style != null && style.background.effect.isShape() ? null : mainWindow.themePanel.getLandColor();
		samplePanel.setImage(sample == null ? null : AwtBridge.toBufferedImage(sample));
	}

	/**
	 * Draws the name of the type of text in its style. Text with a font size of {@link #fontSizeOfLargestSamples} or more is drawn at the
	 * largest size, and smaller text is drawn smaller in proportion to its font size. The size of the text depends on nothing else, such as
	 * its background or how long the name is. A shape behind the text, such as a scroll, is drawn whole and cropped to its edges.
	 * Other text is centered on a chip that fits the text's tallest and lowest letters, or taller if a glow or outline needs it.
	 */
	private static Image createSample(TextType type, TextStyle style)
	{
		double osScale = SwingHelper.getOSScale();
		TextStyle styleWithoutBackground = style.copy();
		styleWithoutBackground.background.effect = TextBackgroundEffect.None;

		// The tallest and lowest letters, so that the size doesn't depend on which letters the name has.
		int referenceHeight;
		try (Image reference = drawSampleText("Hgy", type, styleWithoutBackground, osScale))
		{
			if (reference == null)
			{
				return null;
			}
			referenceHeight = findVisibleBounds(reference).height;
		}
		double scale = largestSampleTextHeight * osScale / referenceHeight;
		scale *= Math.min(1.0, style.font.getSize() / fontSizeOfLargestSamples);

		try (Image textImage = drawSampleText(type.toString(), type, style, osScale * scale))
		{
			if (textImage == null)
			{
				return null;
			}
			IntRectangle visibleBounds = findVisibleBounds(textImage);
			if (style.background.effect.isShape())
			{
				return textImage.copySubImage(visibleBounds);
			}

			int horizontalPadding = (int) Math.round(sampleChipHorizontalPadding * osScale);
			int verticalPadding = (int) Math.round(sampleChipVerticalPadding * osScale);
			int height = Math.max((int) Math.round(referenceHeight * scale), visibleBounds.height) + 2 * verticalPadding;
			Image result = Image.create(visibleBounds.width + 2 * horizontalPadding, height, ImageType.ARGB);
			try (Painter p = result.createPainter(DrawQuality.High))
			{
				p.drawImage(textImage, horizontalPadding - visibleBounds.x, (height - visibleBounds.height) / 2 - visibleBounds.y);
			}
			return result;
		}
	}

	private static Image drawSampleText(String value, TextType type, TextStyle style, double resolution)
	{
		MapText text = TextDrawer.createMapText(value, new nortantis.geom.Point(0, 0), 0.0, type, 1.0, 0);
		Tuple2<Image, IntPoint> drawn = createSampleTextDrawer(type, style, resolution).drawTextOntoNewImage(text, null);
		return drawn == null ? null : drawn.getFirst();
	}

	/**
	 * The bounds of the part of an image that isn't fully transparent. The drawn text has a transparent margin that isn't the same on every
	 * side.
	 */
	private static IntRectangle findVisibleBounds(Image image)
	{
		int left = image.getWidth();
		int right = -1;
		int top = image.getHeight();
		int bottom = -1;
		try (PixelReader pixels = image.createPixelReader())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					if (pixels.getAlpha(x, y) > 0)
					{
						left = Math.min(left, x);
						right = Math.max(right, x);
						top = Math.min(top, y);
						bottom = Math.max(bottom, y);
					}
				}
			}
		}
		if (right < 0)
		{
			return new IntRectangle(0, 0, image.getWidth(), image.getHeight());
		}
		return new IntRectangle(left, top, right - left + 1, bottom - top + 1);
	}

	private static TextDrawer createSampleTextDrawer(TextType type, TextStyle style, double resolution)
	{
		MapSettings sampleSettings = new MapSettings();
		sampleSettings.resolution = resolution;
		sampleSettings.textStyleDefaults.put(type, style);
		return new TextDrawer(sampleSettings);
	}

	/**
	 * Shows a sample, on a rounded chip of the map's land color if it has a chip color, without the OS's display scaling so that the text is
	 * drawn crisply. A sample wider than the space left for it is cut off on the right.
	 */
	@SuppressWarnings("serial")
	private static class SamplePanel extends UnscaledImagePanel
	{
		Color chipColor;

		@Override
		protected void drawImage(Graphics2D g2, BufferedImage image)
		{
			if (chipColor != null)
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(chipColor);
				int arc = image.getHeight() / 2;
				g2.fillRoundRect(0, 0, image.getWidth(), image.getHeight(), arc, arc);
			}
			g2.drawImage(image, 0, 0, null);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return new Dimension(0, getPreferredSize().height);
		}
	}
}
