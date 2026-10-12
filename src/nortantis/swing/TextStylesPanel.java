package nortantis.swing;

import nortantis.MapFonts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextDrawer;
import nortantis.TextStyle;
import nortantis.TextStylePart;
import nortantis.TextType;
import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.Painter;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Tuple2;
import nortantis.util.Tuple4;
import nortantis.geom.IntPoint;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
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
	private final EnumMap<TextType, JLabel> samplesByType = new EnumMap<>(TextType.class);
	/**
	 * True when the samples may be out of date with the styles, and so must be redrawn the next time the panel is shown.
	 */
	private boolean areSamplesStale = true;

	private static final int sampleWidth = 130;
	private static final int sampleHeight = 22;
	private static final int sampleFadeWidth = 4;

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
			JLabel sample = new JLabel();
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
				// The samples are drawn over the map's background, which other tabs change.
				areSamplesStale = true;
				updateSamplesIfStale();
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
		// Drawn later because the samples read the map's background from the GUI, which may not be done loading.
		markSamplesStale();
	}

	void getSettingsFromGUI(MapSettings settings)
	{
		if (!styles.isEmpty())
		{
			settings.textStyleDefaults = copyStyles();
		}
	}

	/**
	 * Redraws the samples if they may be out of date and the panel is showing.
	 */
	void updateSamplesIfStale()
	{
		if (!areSamplesStale || !scrollPane.isShowing())
		{
			return;
		}
		areSamplesStale = false;
		MapSettings settings = mainWindow.getSettingsFromGUI(false);
		for (TextType type : TextType.values())
		{
			updateSample(type, settings);
		}
	}

	/**
	 * Marks the samples to be redrawn the next time the panel is shown.
	 */
	void markSamplesStale()
	{
		areSamplesStale = true;
		SwingUtilities.invokeLater(this::updateSamplesIfStale);
	}

	private void updateSample(TextType type)
	{
		updateSample(type, mainWindow.getSettingsFromGUI(false));
	}

	private void updateSample(TextType type, MapSettings settings)
	{
		JLabel sampleLabel = samplesByType.get(type);
		TextStyle style = styles.get(type);
		if (style == null || settings == null)
		{
			sampleLabel.setIcon(null);
			return;
		}
		Image sample = createSample(type, style, settings);
		sampleLabel.setIcon(sample == null ? null : new ImageIcon(AwtBridge.toBufferedImage(sample)));
	}

	/**
	 * Draws the name of the type of text in its style over the map's land background, scaled down to fit the sample.
	 */
	private static Image createSample(TextType type, TextStyle style, MapSettings settings)
	{
		double osScale = SwingHelper.getOSScale();
		IntDimension size = new IntDimension((int) (sampleWidth * osScale), (int) (sampleHeight * osScale));
		Path backgroundImagePath = settings.getBackgroundImagePath().getFirst();
		Tuple4<Image, ImageHelper.ColorizeAlgorithm, Image, ImageHelper.ColorizeAlgorithm> backgrounds = ThemePanel.createBackgroundImageDisplayImages(size,
				settings.backgroundRandomSeed, settings.colorizeOcean, settings.colorizeLand, settings.generateBackground, settings.generateBackgroundFromTexture,
				settings.solidColorBackground, backgroundImagePath == null ? null : backgroundImagePath.toString());
		if (backgrounds == null)
		{
			return null;
		}

		MapText text = TextDrawer.createMapText(type.toString(), new nortantis.geom.Point(0, 0), 0.0, type, 1.0, 0);
		Tuple2<Image, IntPoint> drawn = createSampleTextDrawer(type, style, osScale).drawTextOntoNewImage(text, null);
		if (drawn != null && (drawn.getFirst().getHeight() > size.height || drawn.getFirst().getWidth() > size.width))
		{
			double scale = Math.min(size.height / (double) drawn.getFirst().getHeight(), size.width / (double) drawn.getFirst().getWidth());
			drawn.getFirst().close();
			drawn = createSampleTextDrawer(type, style, osScale * scale).drawTextOntoNewImage(text, null);
		}

		Image result;
		try (Image land = ImageHelper.getInstance().colorize(backgrounds.getThird(), settings.landColor, backgrounds.getFourth()))
		{
			int width = drawn == null ? size.width : Math.min(size.width, drawn.getFirst().getWidth());
			try (Image cropped = land.copySubImage(new IntRectangle(0, 0, width, size.height)))
			{
				result = IconsTool.fadeEdges(cropped, (int) (sampleFadeWidth * osScale));
			}
		}
		if (drawn != null)
		{
			try (Image textImage = drawn.getFirst(); Painter p = result.createPainter(DrawQuality.High))
			{
				p.drawImage(textImage, (result.getWidth() - textImage.getWidth()) / 2, (result.getHeight() - textImage.getHeight()) / 2);
			}
		}
		return result;
	}

	private static TextDrawer createSampleTextDrawer(TextType type, TextStyle style, double resolution)
	{
		MapSettings sampleSettings = new MapSettings();
		sampleSettings.resolution = resolution;
		sampleSettings.textStyleDefaults.put(type, style);
		return new TextDrawer(sampleSettings);
	}
}
