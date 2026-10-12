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

	private static final int sampleWidth = 180;
	private static final int sampleHeight = 22;
	/**
	 * The font size at and above which samples fill their height. Smaller text is drawn smaller.
	 */
	private static final float fontSizeThatFillsSample = 12f;
	/**
	 * The space between the top or bottom of a sample and its visible text.
	 */
	private static final int sampleVerticalInset = 2;

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
	 * Draws the name of the type of text in its style, centered vertically in an image the sample's height. Text with a font size of
	 * {@link #fontSizeThatFillsSample} or more is scaled so that its tallest and lowest letters fill the sample's height, and smaller text is
	 * scaled down from that in proportion to its font size. A shape behind the text, such as a scroll, is scaled with the text so that it
	 * fits whole in the sample. Other background effects can reach past the sample's height, and are cropped.
	 */
	private static Image createSample(TextType type, TextStyle style)
	{
		double osScale = SwingHelper.getOSScale();
		IntDimension size = new IntDimension((int) (sampleWidth * osScale), (int) (sampleHeight * osScale));
		boolean isShape = style.background.effect.isShape();
		int availableHeight = isShape ? size.height : size.height - 2 * (int) Math.ceil(sampleVerticalInset * osScale);

		double scale;
		TextStyle referenceStyle = style.copy();
		if (!isShape)
		{
			referenceStyle.background.effect = TextBackgroundEffect.None;
		}
		try (Image reference = drawSampleText("Hgy", type, referenceStyle, osScale))
		{
			if (reference == null)
			{
				return null;
			}
			scale = availableHeight / (double) findVisibleBounds(reference).height;
		}
		scale *= Math.min(1.0, style.font.getSize() / fontSizeThatFillsSample);

		Image textImage = drawSampleText(type.toString(), type, style, osScale * scale);
		if (textImage == null)
		{
			return null;
		}
		IntRectangle visibleBounds = findVisibleBounds(textImage);
		double fitScale;
		if (isShape)
		{
			// The shape around the name can be a little taller than the one around the reference.
			fitScale = Math.min(size.width / (double) visibleBounds.width, size.height / (double) visibleBounds.height);
		}
		else
		{
			fitScale = size.width / (double) textImage.getWidth();
		}
		if (fitScale < 1.0)
		{
			scale *= fitScale;
			textImage.close();
			textImage = drawSampleText(type.toString(), type, style, osScale * scale);
			if (textImage == null)
			{
				return null;
			}
			visibleBounds = findVisibleBounds(textImage);
		}

		try (Image toClose = textImage)
		{
			// A shape is cropped to its edges. Other text keeps its transparent margin on the sides, which pads it from the edges of the chip.
			int left = isShape ? visibleBounds.x : 0;
			int width = isShape ? visibleBounds.width : textImage.getWidth();
			Image result = Image.create(Math.min(width, size.width), size.height, ImageType.ARGB);
			try (Painter p = result.createPainter(DrawQuality.High))
			{
				p.drawImage(textImage, -left, (size.height - visibleBounds.height) / 2 - visibleBounds.y);
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
	 * Shows a sample on a rounded chip of the map's land color, without the OS's display scaling, so that the text is drawn crisply.
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
	}
}
