package nortantis.swing;

import nortantis.*;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.ThemeGenerationSettings.BackgroundType;
import nortantis.geom.Dimension;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Varies the open map's theme within rules the user can change, showing the map with each variation. Every variation varies the map's look
 * as it was when the dialog opened, so rolling repeatedly doesn't drift away from it. The rules are kept with the map, and are also how new
 * random maps vary its look when the map is a theme in an art pack.
 */
class RandomizeThemeDialog extends JDialog
{
	/**
	 * A variation of the theme, and its preview once drawn.
	 */
	private static final class Variation
	{
		final MapSettings settings;
		/** The preview, or null if it hasn't been drawn or couldn't be. */
		BufferedImage preview;
		/** The size the preview was last drawn to fit, or null if it hasn't been drawn. */
		Dimension previewSize;
		/** Why the preview couldn't be drawn, or null if it was. */
		String previewError;

		Variation(MapSettings settings)
		{
			this.settings = settings;
		}
	}

	/** The open map's settings, which every variation varies. */
	private final MapSettings base;
	private final ThemeGenerationSettings gen;
	private final Consumer<MapSettings> onApply;
	private final Consumer<MapSettings> onClose;
	/**
	 * The open map as it is, followed by the variations rolled, in the order they were rolled. Only the most recent
	 * {@link Undoer#maxUndoLevels} variations are kept, since each holds a copy of the map's edits.
	 */
	private final List<Variation> variations = new ArrayList<>();
	/**
	 * The most variations whose previews are kept, so that going back and forth between recent variations doesn't redraw them. Older
	 * previews are drawn again if shown again.
	 */
	private static final int maxPreviewsKept = 5;
	/** The variations with previews kept, from least to most recently drawn. */
	private final ArrayDeque<Variation> variationsWithPreviews = new ArrayDeque<>();
	private int shownIndex;
	private UnscaledImagePanel previewPanel;
	private JPanel previewHolder;
	private JProgressBar previewProgressBar;
	private JLabel previewStatusLabel;
	private JButton previousButton;
	private JButton nextButton;
	private boolean isPreviewBeingDrawn;
	private RowHider landColorRows;
	private RowHider regionColorRows;
	private JTextArea landColoringWarningArea;
	private RowHider landColoringWarningHider;

	/**
	 * @param mapSettings
	 *            The open map's settings. Not changed.
	 * @param onApply
	 *            Called with the variation shown when the user applies it. It carries the rules in its
	 *            {@link MapSettings#themeGeneration}, and the region color ranges.
	 * @param onClose
	 *            Called when the user closes the dialog without applying a variation, with the open map's settings carrying the rules, region
	 *            base color, and region color ranges to keep.
	 */
	RandomizeThemeDialog(Window owner, MapSettings mapSettings, Consumer<MapSettings> onApply, Consumer<MapSettings> onClose)
	{
		super(owner, Translation.get("randomizeTheme.title"), ModalityType.APPLICATION_MODAL);
		base = mapSettings.deepCopy();
		this.onApply = onApply;
		this.onClose = onClose;
		if (base.themeGeneration == null)
		{
			throw new IllegalStateException("The map has no rules for randomizing its theme.");
		}
		gen = base.themeGeneration.copy();
		variations.add(new Variation(base));

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(createSettingsPanel(), BorderLayout.WEST);
		content.add(createPreviewPanel(), BorderLayout.CENTER);
		content.add(createBottomPanel(), BorderLayout.SOUTH);
		setContentPane(content);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		updatePreviousAndNextButtons();
		setSize(new java.awt.Dimension(1100, 760));
		setLocationRelativeTo(owner);
	}

	private JComponent createSettingsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.landColoring"));
		addEnumCheckboxes(organizer, "randomizeTheme.allowedLandColoringMethods", Arrays.asList(LandColoringMethod.values()), gen.allowedLandColoringMethods,
				LandColoringMethod::toString, this::updateLandColoringRows);
		addLandColoringWarning(organizer);

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.oceanColorVariation"));
		String oceanHelp = "randomizeTheme.oceanColorVariation.help";
		addColorVariationSliders(organizer, oceanHelp, gen.oceanHueVariation, value -> gen.oceanHueVariation = value, gen.oceanSaturationVariation,
				value -> gen.oceanSaturationVariation = value, gen.oceanBrightnessVariation, value -> gen.oceanBrightnessVariation = value);

		// Only the colors of the land coloring methods variations can have are shown.
		landColorRows = organizer.addSectionHeading(Translation.get("randomizeTheme.section.landColorVariation"));
		landColorRows.add(addColorVariationSliders(organizer, "randomizeTheme.landColorVariation.help", gen.landHueVariation, value -> gen.landHueVariation = value,
				gen.landSaturationVariation, value -> gen.landSaturationVariation = value, gen.landBrightnessVariation, value -> gen.landBrightnessVariation = value));

		// Region colors are generated around the region base color with the map's own region color ranges, which the Land and Water tool
		// also edits.
		regionColorRows = organizer.addSectionHeading(Translation.get("randomizeTheme.section.regionBaseColorVariation"));
		regionColorRows.add(addRegionBaseColorChooser(organizer));
		regionColorRows.add(addColorVariationSliders(organizer, "randomizeTheme.regionBaseColorVariation.help", gen.regionBaseHueVariation,
				value -> gen.regionBaseHueVariation = value, gen.regionBaseSaturationVariation, value -> gen.regionBaseSaturationVariation = value,
				gen.regionBaseBrightnessVariation, value -> gen.regionBaseBrightnessVariation = value));
		regionColorRows.add(organizer.addSectionHeading(Translation.get("randomizeTheme.section.regionColorVariation")));
		regionColorRows.add(addRangeSlider(organizer, "landWaterTool.hueRange", LandWaterTool.maxHueRange, base.hueRange, value -> base.hueRange = value));
		regionColorRows.add(addRangeSlider(organizer, "landWaterTool.saturationRange", LandWaterTool.maxSaturationRange, base.saturationRange,
				value -> base.saturationRange = value));
		regionColorRows.add(addRangeSlider(organizer, "landWaterTool.brightnessRange", LandWaterTool.maxBrightnessRange, base.brightnessRange,
				value -> base.brightnessRange = value));

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.ocean"));
		addProbabilitySlider(organizer, "randomizeTheme.drawOceanWavesProbability", gen.drawOceanWavesProbability, value -> gen.drawOceanWavesProbability = value);
		addEnumCheckboxes(organizer, "randomizeTheme.allowedOceanWaveTypes",
				ThemeGenerationSettings.oceanWaveTypesToChooseFrom, gen.allowedOceanWaveTypes,
				ThemePanel::getWaveTypeName);
		addVariationSlider(organizer, "randomizeTheme.oceanShadingLevelVariation", 0, 50, gen.oceanShadingLevelVariation, value -> gen.oceanShadingLevelVariation = value);
		addProbabilitySlider(organizer, "randomizeTheme.oceanShadingWithWavesProbability", gen.oceanShadingWithWavesProbability,
				value -> gen.oceanShadingWithWavesProbability = value);

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.landAndBorder"));
		addVariationSlider(organizer, "randomizeTheme.coastShadingLevelVariation", 0, 50, gen.coastShadingLevelVariation, value -> gen.coastShadingLevelVariation = value);
		addVariationSlider(organizer, "randomizeTheme.grungeWidthVariation", 0, 1000, gen.grungeWidthVariation, value -> gen.grungeWidthVariation = value);
		addProbabilitySlider(organizer, "randomizeTheme.drawBorderProbability", gen.drawBorderProbability, value -> gen.drawBorderProbability = value);
		addVariationSliderWithTip(organizer, "randomizeTheme.borderWidthVariation", 0, 300, gen.borderWidthVariation, value -> gen.borderWidthVariation = value);
		addProbabilitySliderWithTip(organizer, "randomizeTheme.frayedBorderProbability", gen.frayedBorderProbability, value -> gen.frayedBorderProbability = value);
		addVariationSlider(organizer, "randomizeTheme.frayedBorderBlurLevelVariation", 0, 150, gen.frayedBorderBlurLevelVariation, value -> gen.frayedBorderBlurLevelVariation = value);
		addVariationSlider(organizer, "randomizeTheme.frayedBorderSizeVariation", 0, 7, gen.frayedBorderSizeVariation, value -> gen.frayedBorderSizeVariation = value);

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.regionsRoadsAndBackground"));
		addProbabilitySlider(organizer, "randomizeTheme.drawRegionBoundariesProbability", gen.drawRegionBoundariesProbability, value -> gen.drawRegionBoundariesProbability = value);
		addEnumCheckboxes(organizer, "randomizeTheme.allowedRegionBoundaryStrokeTypes", Arrays.asList(StrokeType.values()), gen.allowedRegionBoundaryStrokeTypes, Object::toString);
		addEnumCheckboxes(organizer, "randomizeTheme.allowedRoadStrokeTypes", Arrays.asList(StrokeType.values()), gen.allowedRoadStrokeTypes, Object::toString);
		addEnumCheckboxes(organizer, "randomizeTheme.allowedLineStyles", Arrays.asList(LineStyle.values()), gen.allowedLineStyles, RandomizeThemeDialog::getLineStyleName);
		addEnumCheckboxes(organizer, "randomizeTheme.allowedBackgroundTypes", Arrays.asList(BackgroundType.values()), gen.allowedBackgroundTypes,
				RandomizeThemeDialog::getBackgroundTypeName, this::updateLandColoringRows);

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.text"));
		JCheckBox shuffleTextBackgroundsCheckBox = new JCheckBox(Translation.get("randomizeTheme.shuffleTextBackgrounds"));
		shuffleTextBackgroundsCheckBox.setToolTipText("<html>" + Translation.get("randomizeTheme.shuffleTextBackgrounds.help") + "</html>");
		shuffleTextBackgroundsCheckBox.setSelected(gen.shuffleTextBackgrounds);
		organizer.addLeftAlignedComponent(shuffleTextBackgroundsCheckBox);
		RowHider textBackgroundRows = addEnumCheckboxes(organizer, "randomizeTheme.allowedTitleBackgroundEffects", Arrays.asList(TextBackgroundEffect.values()),
				gen.allowedTitleBackgroundEffects, TextBackgroundEffect::toString);
		textBackgroundRows.add(addEnumCheckboxes(organizer, "randomizeTheme.allowedRegionBackgroundEffects", Arrays.asList(TextBackgroundEffect.values()),
				gen.allowedRegionBackgroundEffects, TextBackgroundEffect::toString));
		textBackgroundRows.setEnabled(gen.shuffleTextBackgrounds);
		shuffleTextBackgroundsCheckBox.addActionListener(e ->
		{
			gen.shuffleTextBackgrounds = shuffleTextBackgroundsCheckBox.isSelected();
			textBackgroundRows.setEnabled(gen.shuffleTextBackgrounds);
		});

		updateLandColoringRows();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.55);
		organizer.addVerticalFillerRow();
		JScrollPane scrollPane = organizer.createScrollPane();
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		JPanel titledPanel = new JPanel(new BorderLayout());
		titledPanel.setBorder(BorderFactory.createTitledBorder(new DynamicLineBorder("controlShadow", 1), Translation.get("randomizeTheme.settingsTitle")));
		titledPanel.add(scrollPane, BorderLayout.CENTER);
		titledPanel.setPreferredSize(new java.awt.Dimension(470, 600));
		return titledPanel;
	}

	private String createTooltipFromHelpKey(String helpKey)
	{
		return "<html>" + Translation.get(helpKey) + "<br>" + Translation.get("randomizeTheme.zeroNeverChanges") + "</html>";
	}

	private RowHider addVariationSlider(GridBagOrganizer organizer, String key, int min, int max, int value, IntConsumer setValue)
	{
		return addVariationSlider(organizer, key + ".label", null, key + ".help", min, max, value, setValue);
	}

	/**
	 * Adds a variation slider whose label has the tip from the key's ".tip" translation under it.
	 */
	private RowHider addVariationSliderWithTip(GridBagOrganizer organizer, String key, int min, int max, int value, IntConsumer setValue)
	{
		return addVariationSlider(organizer, key + ".label", key + ".tip", key + ".help", min, max, value, setValue);
	}

	private RowHider addVariationSlider(GridBagOrganizer organizer, String labelKey, String helpKey, int min, int max, int value, IntConsumer setValue)
	{
		return addVariationSlider(organizer, labelKey, null, helpKey, min, max, value, setValue);
	}

	private RowHider addVariationSlider(GridBagOrganizer organizer, String labelKey, String tipKey, String helpKey, int min, int max, int value,
			IntConsumer setValue)
	{
		JSlider slider = new JSlider(min, max, Math.max(min, Math.min(max, value)));
		slider.setPaintLabels(false);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, null, () -> setValue.accept(slider.getValue()), 44);
		return addSliderToOrganizer(organizer, sliderWithDisplay, labelKey, tipKey, createTooltipFromHelpKey(helpKey));
	}

	private static RowHider addSliderToOrganizer(GridBagOrganizer organizer, SliderWithDisplayedValue sliderWithDisplay, String labelKey, String tipKey,
			String tooltip)
	{
		if (tipKey == null)
		{
			return sliderWithDisplay.addToOrganizer(organizer, Translation.get(labelKey), tooltip);
		}
		else
		{
			return sliderWithDisplay.addToOrganizer(organizer, SwingHelper.createLabelWithTip(Translation.get(labelKey), Translation.get(tipKey), tooltip));
		}
	}

	/**
	 * Adds hue, saturation, and brightness variation sliders, which go as high as the Land and Water tool's color generator ranges.
	 */
	private RowHider addColorVariationSliders(GridBagOrganizer organizer, String helpKey, int hueVariation, IntConsumer setHueVariation, int saturationVariation,
			IntConsumer setSaturationVariation, int brightnessVariation, IntConsumer setBrightnessVariation)
	{
		RowHider rows = addVariationSlider(organizer, "landWaterTool.hueRange.label", helpKey, 0, LandWaterTool.maxHueRange, hueVariation, setHueVariation);
		rows.add(addVariationSlider(organizer, "landWaterTool.saturationRange.label", helpKey, 0, LandWaterTool.maxSaturationRange, saturationVariation,
				setSaturationVariation));
		rows.add(addVariationSlider(organizer, "landWaterTool.brightnessRange.label", helpKey, 0, LandWaterTool.maxBrightnessRange, brightnessVariation,
				setBrightnessVariation));
		return rows;
	}

	/**
	 * Adds a chooser for the map's region base color, which is a setting of the map rather than a rule for varying it.
	 */
	private RowHider addRegionBaseColorChooser(GridBagOrganizer organizer)
	{
		JPanel colorDisplay = SwingHelper.createColorPickerPreviewPanel();
		colorDisplay.setBackground(AwtBridge.toAwtColor(base.regionBaseColor));
		JButton chooseButton = new JButton(Translation.get("common.choose"));
		chooseButton.addActionListener(e -> SwingHelper.showColorPicker(organizer.panel, colorDisplay, Translation.get("landWaterTool.baseColor.title"),
				() -> base.regionBaseColor = AwtBridge.fromAwtColor(colorDisplay.getBackground())));
		SwingHelper.addColorCopyAndPasteMenu(colorDisplay, colorDisplay::getBackground, color ->
		{
			colorDisplay.setBackground(color);
			base.regionBaseColor = AwtBridge.fromAwtColor(color);
		});
		return organizer.addLabelAndComponentsHorizontal(Translation.get("landWaterTool.baseColor.label"), Translation.get("landWaterTool.baseColor.help"),
				Arrays.asList(colorDisplay, chooseButton), SwingHelper.borderWidthBetweenComponents);
	}

	/**
	 * Adds a slider for one of the map's region color ranges, which are settings of the map rather than rules for varying it.
	 */
	private RowHider addRangeSlider(GridBagOrganizer organizer, String key, int max, int value, IntConsumer setValue)
	{
		JSlider slider = new JSlider(0, max, Math.max(0, Math.min(max, value)));
		slider.setPaintLabels(false);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, null, () -> setValue.accept(slider.getValue()), 44);
		return sliderWithDisplay.addToOrganizer(organizer, Translation.get(key + ".label"), Translation.get(key + ".help"));
	}

	private void addProbabilitySlider(GridBagOrganizer organizer, String key, double value, Consumer<Double> setValue)
	{
		addProbabilitySlider(organizer, key, null, value, setValue);
	}

	/**
	 * Adds a probability slider whose label has the tip from the key's ".tip" translation under it.
	 */
	private void addProbabilitySliderWithTip(GridBagOrganizer organizer, String key, double value, Consumer<Double> setValue)
	{
		addProbabilitySlider(organizer, key, key + ".tip", value, setValue);
	}

	private void addProbabilitySlider(GridBagOrganizer organizer, String key, String tipKey, double value, Consumer<Double> setValue)
	{
		JSlider slider = new JSlider(0, 100, (int) Math.round(value * 100));
		slider.setPaintLabels(false);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, (sliderValue) -> sliderValue + "%", () -> setValue.accept(slider.getValue() / 100.0), 44);
		addSliderToOrganizer(organizer, sliderWithDisplay, key + ".label", tipKey, "<html>" + Translation.get(key + ".help") + "</html>");
	}

	/**
	 * Adds the warning, shown by {@link #updateLandColoringRows()}, that political regions can't be colored on a background generated from a
	 * texture when the map doesn't colorize land.
	 */
	private void addLandColoringWarning(GridBagOrganizer organizer)
	{
		landColoringWarningArea = new JTextArea();
		landColoringWarningArea.setEditable(false);
		landColoringWarningArea.setFocusable(false);
		landColoringWarningArea.setLineWrap(true);
		landColoringWarningArea.setWrapStyleWord(true);
		landColoringWarningArea.setOpaque(false);
		landColoringWarningArea.setForeground(SwingHelper.warningMessageColor);
		landColoringWarningArea.setFont(UIManager.getFont("Label.font"));

		JPanel warningPanel = new JPanel(new BorderLayout());
		warningPanel.add(SwingHelper.createWarningIconLabel(), BorderLayout.WEST);
		warningPanel.add(landColoringWarningArea, BorderLayout.CENTER);
		landColoringWarningHider = organizer.addLeftAlignedComponent(warningPanel);
	}

	/**
	 * Shows the colors of the land coloring methods variations can have, and the warning when political regions can't always be colored.
	 */
	private void updateLandColoringRows()
	{
		boolean allowsRegionColors = isLandColoringMethodAllowed(LandColoringMethod.ColorPoliticalRegions);
		boolean allowsTextures = gen.allowedBackgroundTypes.isEmpty() || gen.allowedBackgroundTypes.contains(BackgroundType.GeneratedFromTexture);
		// A background generated from a texture can only be given region colors when the land is colorized, which isn't varied.
		boolean regionColorsCanBeDropped = allowsRegionColors && allowsTextures && !base.colorizeLand;
		boolean onlyTextures = gen.allowedBackgroundTypes.equals(EnumSet.of(BackgroundType.GeneratedFromTexture));
		String methodName = LandColoringMethod.ColorPoliticalRegions.toString();
		landColoringWarningArea.setText(onlyTextures ? Translation.get("randomizeTheme.regionColorsNeverDrawn", methodName, Translation.get("theme.colorLand"))
				: Translation.get("randomizeTheme.regionColorsNotDrawnOnTextures", methodName, Translation.get("theme.colorLand"),
						Translation.get("theme.background.generatedFromTexture")));
		landColoringWarningHider.setVisible(regionColorsCanBeDropped);
		regionColorRows.setVisible(allowsRegionColors && !(regionColorsCanBeDropped && onlyTextures));
		landColorRows.setVisible(isLandColoringMethodAllowed(LandColoringMethod.SingleColor) || regionColorsCanBeDropped);
	}

	private boolean isLandColoringMethodAllowed(LandColoringMethod method)
	{
		return gen.allowedLandColoringMethods.isEmpty() || gen.allowedLandColoringMethods.contains(method);
	}

	private static String getLineStyleName(LineStyle lineStyle)
	{
		return switch (lineStyle)
		{
			case Jagged -> Translation.get("theme.lineStyle.jagged");
			case Splines -> Translation.get("theme.lineStyle.splines");
			case SplinesWithSmoothedCoastlines -> Translation.get("theme.lineStyle.splinesSmoothed");
		};
	}

	private static String getBackgroundTypeName(BackgroundType backgroundType)
	{
		return switch (backgroundType)
		{
			case Fractal -> Translation.get("theme.background.fractalNoise");
			case GeneratedFromTexture -> Translation.get("theme.background.generatedFromTexture");
			case SolidColor -> Translation.get("theme.background.solidColor");
		};
	}

	/**
	 * Adds a checkbox for each choice. The checked choices are stored by name, so that checking every choice doesn't opt into choices added
	 * in later versions. Checking none is stored as an empty set, which means every choice, including ones added in later versions.
	 */
	private <E> RowHider addEnumCheckboxes(GridBagOrganizer organizer, String key, List<E> choices, Set<E> allowed, Function<E, String> getName)
	{
		return addEnumCheckboxes(organizer, key, choices, allowed, getName, () ->
		{
		});
	}

	/**
	 * Like {@link #addEnumCheckboxes(GridBagOrganizer, String, List, Set, Function)}, calling onChange after the allowed choices change.
	 */
	private <E> RowHider addEnumCheckboxes(GridBagOrganizer organizer, String key, List<E> choices, Set<E> allowed, Function<E, String> getName, Runnable onChange)
	{
		JPanel panel = new JPanel(new WrapLayout(WrapLayout.LEFT, 4, 0));
		Map<E, JCheckBox> checkboxes = new LinkedHashMap<>();
		Runnable update = () ->
		{
			allowed.clear();
			checkboxes.forEach((choice, checkbox) ->
			{
				if (checkbox.isSelected())
				{
					allowed.add(choice);
				}
			});
			onChange.run();
		};
		for (E choice : choices)
		{
			JCheckBox checkbox = new JCheckBox(getName.apply(choice));
			checkbox.setSelected(allowed.contains(choice));
			checkbox.addActionListener(e -> update.run());
			checkboxes.put(choice, checkbox);
			panel.add(checkbox);
		}
		return organizer.addLeftAlignedComponentWithStackedLabel(Translation.get(key + ".label"),
				"<html>" + Translation.get(key + ".help") + "<br>" + Translation.get("randomizeTheme.noneCheckedAllowsAll") + "</html>", panel);
	}

	private JComponent createPreviewPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		JLabel explanation = new JLabel("<html>" + Translation.get("randomizeTheme.explanation", Translation.get("newSettingsDialog.randomizeTheme"),
				Translation.get("newSettingsDialog.title")) + "</html>");
		panel.add(explanation, BorderLayout.NORTH);
		previewPanel = new UnscaledImagePanel();
		previewHolder = new JPanel(new GridBagLayout());
		previewHolder.add(previewPanel);
		// The preview is drawn to fit the space it's shown in, so it's drawn again when that space changes size, which includes when the
		// dialog is first shown.
		previewHolder.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				showPreview();
			}
		});
		panel.add(previewHolder, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new BorderLayout(5, 0));
		JPanel rollButtons = new JPanel();
		rollButtons.setLayout(new BoxLayout(rollButtons, BoxLayout.X_AXIS));
		JButton rollButton = new JButton(Translation.get("randomizeTheme.rollTheme"));
		rollButton.setToolTipText(Translation.get("randomizeTheme.rollTheme.tooltip"));
		SwingHelper.bindAltMnemonic(rollButton, KeyEvent.VK_R);
		rollButton.addActionListener(e -> roll());
		rollButtons.add(rollButton);
		rollButtons.add(Box.createHorizontalStrut(5));
		previousButton = new JButton("↩");
		previousButton.setToolTipText(Translation.get("randomizeTheme.previous.tooltip"));
		previousButton.addActionListener(e -> showVariation(shownIndex - 1));
		rollButtons.add(previousButton);
		rollButtons.add(Box.createHorizontalStrut(5));
		nextButton = new JButton("↪");
		nextButton.setToolTipText(Translation.get("randomizeTheme.next.tooltip"));
		nextButton.addActionListener(e -> showVariation(shownIndex + 1));
		rollButtons.add(nextButton);
		buttons.add(rollButtons, BorderLayout.WEST);
		// Only one of the progress bar and the status label shows at a time, in the rest of the row, at its own height and centered
		// vertically. The row keeps the same height whichever shows, so that showing one doesn't resize the preview and start another draw.
		JPanel status = new JPanel(new GridBagLayout())
		{
			@Override
			public java.awt.Dimension getPreferredSize()
			{
				java.awt.Dimension size = super.getPreferredSize();
				return new java.awt.Dimension(size.width, Math.max(previewProgressBar.getPreferredSize().height, previewStatusLabel.getPreferredSize().height));
			}
		};
		previewProgressBar = new JProgressBar();
		previewProgressBar.setIndeterminate(true);
		previewProgressBar.setStringPainted(true);
		previewProgressBar.setString(Translation.get("randomizeTheme.drawingPreview"));
		previewProgressBar.setVisible(false);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		final int progressBarSidePadding = 20;
		c.insets = new Insets(0, progressBarSidePadding, 0, progressBarSidePadding);
		status.add(previewProgressBar, c);
		c.insets = new Insets(0, 0, 0, 0);
		previewStatusLabel = new JLabel(Translation.get("randomizeTheme.previewFailed"));
		previewStatusLabel.setVisible(false);
		c.gridx = 1;
		status.add(previewStatusLabel, c);
		buttons.add(status, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.SOUTH);
		return panel;
	}

	/**
	 * The most room the preview can take up, in pixels of the map image, or null if the preview hasn't been laid out yet.
	 */
	private Dimension getPreviewSize()
	{
		int width = previewHolder.getWidth();
		int height = previewHolder.getHeight();
		if (width <= 0 || height <= 0)
		{
			return null;
		}
		double osScale = SwingHelper.getOSScale();
		return new Dimension(width * osScale, height * osScale);
	}

	private JComponent createBottomPanel()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		JButton applyButton = new JButton(Translation.get("randomizeTheme.applyAndClose"));
		applyButton.setToolTipText(Translation.get("randomizeTheme.applyAndClose.tooltip"));
		applyButton.addActionListener(e -> apply());
		panel.add(applyButton);
		JButton closeButton = new JButton(Translation.get("randomizeTheme.close"));
		closeButton.setToolTipText(Translation.get("randomizeTheme.close.tooltip"));
		closeButton.addActionListener(e -> close());
		panel.add(closeButton);
		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> dispose());
		panel.add(cancelButton);
		getRootPane().setDefaultButton(applyButton);
		return panel;
	}

	/**
	 * Adds a new variation of the open map's look, and shows it.
	 */
	private void roll()
	{
		MapSettings variation = base.deepCopy();
		variation.themeGeneration = gen.copy();
		// The border and background texture are chosen from the art packs the map's own come from.
		SettingsGenerator.randomizeTheme(variation, base, SettingsGenerator.getArtPackOfBorder(base), SettingsGenerator.getArtPackOfBackgroundTexture(base), new Random());
		variations.add(new Variation(variation));
		// The open map stays first, so the oldest variation rolled is the one dropped.
		while (variations.size() > Undoer.maxUndoLevels + 1)
		{
			forgetPreview(variations.remove(1));
		}
		showVariation(variations.size() - 1);
	}

	private void showVariation(int index)
	{
		if (index < 0 || index >= variations.size())
		{
			return;
		}
		shownIndex = index;
		updatePreviousAndNextButtons();
		showPreview();
	}

	private void updatePreviousAndNextButtons()
	{
		previousButton.setEnabled(shownIndex > 0);
		nextButton.setEnabled(shownIndex < variations.size() - 1);
	}

	/**
	 * Shows the preview of the variation shown, drawing it first if it hasn't been drawn at the size the preview has room for.
	 */
	private void showPreview()
	{
		Dimension size = getPreviewSize();
		if (size == null)
		{
			return;
		}
		Variation variation = variations.get(shownIndex);
		if (size.equals(variation.previewSize))
		{
			previewPanel.setImage(variation.preview);
			previewStatusLabel.setText(variation.previewError == null ? Translation.get("randomizeTheme.previewFailed")
					: Translation.get("randomizeTheme.previewFailed") + " " + variation.previewError);
			previewStatusLabel.setVisible(variation.preview == null && !isPreviewBeingDrawn);
			return;
		}

		previewPanel.setImage(null);
		previewStatusLabel.setVisible(false);
		if (isPreviewBeingDrawn)
		{
			// The draw in progress shows this variation when it finishes.
			return;
		}
		isPreviewBeingDrawn = true;
		previewProgressBar.setVisible(true);
		// Drawing can change the settings it's given, so it gets its own copy.
		MapSettings settingsToDraw = variation.settings.deepCopy();
		SwingWorker<Image, Void> worker = new SwingWorker<>()
		{
			@Override
			protected Image doInBackground() throws Exception
			{
				return new MapCreator().createMap(settingsToDraw, size, null);
			}

			@Override
			protected void done()
			{
				isPreviewBeingDrawn = false;
				previewProgressBar.setVisible(false);
				if (!isDisplayable())
				{
					return;
				}
				variation.previewSize = size;
				try
				{
					variation.preview = AwtBridge.toBufferedImage(get());
					variation.previewError = null;
				}
				catch (Exception e)
				{
					Logger.printError("Unable to draw the preview of the theme's variation.", e);
					variation.preview = null;
					variation.previewError = SwingHelper.getBriefErrorMessage(e);
				}
				keepPreview(variation);
				showPreview();
			}
		};
		worker.execute();
	}

	private void keepPreview(Variation variation)
	{
		variationsWithPreviews.remove(variation);
		variationsWithPreviews.addLast(variation);
		while (variationsWithPreviews.size() > maxPreviewsKept)
		{
			forgetPreview(variationsWithPreviews.peekFirst());
		}
	}

	private void forgetPreview(Variation variation)
	{
		variationsWithPreviews.remove(variation);
		variation.preview = null;
		variation.previewSize = null;
	}

	private void apply()
	{
		Variation variation = variations.get(shownIndex);
		keepRulesAndRanges(variation.settings);
		onApply.accept(variation.settings);
		dispose();
	}

	private void close()
	{
		keepRulesAndRanges(base);
		onClose.accept(base);
		dispose();
	}

	/**
	 * Gives the settings the rules and region color ranges as they are now, which may have changed since a variation was rolled.
	 */
	private void keepRulesAndRanges(MapSettings settings)
	{
		settings.themeGeneration = gen.copy();
		settings.hueRange = base.hueRange;
		settings.saturationRange = base.saturationRange;
		settings.brightnessRange = base.brightnessRange;
	}
}
