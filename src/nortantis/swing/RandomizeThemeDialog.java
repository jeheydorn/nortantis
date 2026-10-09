package nortantis.swing;

import nortantis.*;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.ThemeGenerationSettings.BackgroundType;
import nortantis.editor.FreeIcon;
import nortantis.geom.Dimension;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

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
		/** Whether the backgrounds of title and region text were varied, including on the text already on the map. */
		final boolean areTextBackgroundsVaried;
		/** The preview, or null if it hasn't been drawn or couldn't be. */
		BufferedImage preview;
		/** The size the preview was last drawn to fit, or null if it hasn't been drawn. */
		Dimension previewSize;

		Variation(MapSettings settings, boolean areTextBackgroundsVaried)
		{
			this.settings = settings;
			this.areTextBackgroundsVaried = areTextBackgroundsVaried;
		}
	}

	/** The open map's settings, which every variation varies. */
	private final MapSettings base;
	private final ThemeGenerationSettings gen;
	private final BiConsumer<MapSettings, Boolean> onApply;
	private final Consumer<MapSettings> onClose;
	/** The open map as it is, followed by each variation rolled, in the order they were rolled. */
	private final List<Variation> variations = new ArrayList<>();
	private int shownIndex;
	private JComboBox<String> artPackComboBox;
	private final List<Runnable> resourceListRebuilders = new ArrayList<>();
	private UnscaledImagePanel previewPanel;
	private JPanel previewHolder;
	private JProgressBar previewProgressBar;
	private JLabel previewStatusLabel;
	private JButton previousButton;
	private JButton nextButton;
	private boolean isPreviewBeingDrawn;

	/**
	 * @param mapSettings
	 *            The open map's settings. Not changed.
	 * @param onApply
	 *            Called with the variation shown when the user applies it, and whether the backgrounds of its title and region text were
	 *            varied. It carries the rules in its {@link MapSettings#themeGeneration}, and the region color ranges.
	 * @param onClose
	 *            Called when the user closes the dialog without applying a variation, with the open map's settings carrying the rules, region
	 *            base color, and region color ranges to keep.
	 */
	RandomizeThemeDialog(Window owner, MapSettings mapSettings, BiConsumer<MapSettings, Boolean> onApply, Consumer<MapSettings> onClose)
	{
		super(owner, Translation.get("randomizeTheme.title"), ModalityType.APPLICATION_MODAL);
		base = mapSettings.deepCopy();
		this.onApply = onApply;
		this.onClose = onClose;
		gen = base.themeGeneration != null ? base.themeGeneration.copy() : ThemeGenerationSettings.createDefault();
		if (gen.artPack == null)
		{
			gen.artPack = chooseDefaultArtPack(base);
		}
		variations.add(new Variation(base, false));

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

	/**
	 * The art pack most of the map's icons come from, which is the art pack whose art is really on the map. Falls back to the art pack the
	 * Icons tool shows, and then the installed one.
	 */
	private static String chooseDefaultArtPack(MapSettings settings)
	{
		Map<String, Integer> counts = new HashMap<>();
		if (settings.edits != null && settings.edits.freeIcons != null)
		{
			settings.edits.freeIcons.doWithLock(() ->
			{
				for (FreeIcon icon : settings.edits.freeIcons)
				{
					if (icon.artPack != null)
					{
						counts.merge(icon.artPack, 1, Integer::sum);
					}
				}
			});
		}
		Optional<Map.Entry<String, Integer>> mostCommon = counts.entrySet().stream().max(Map.Entry.comparingByValue());
		if (mostCommon.isPresent())
		{
			return mostCommon.get().getKey();
		}
		return settings.artPack != null ? settings.artPack : Assets.installedArtPack;
	}

	private JComponent createSettingsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		artPackComboBox = new JComboBoxFixed<>();
		for (String artPack : Assets.listArtPacks(base.customImagesPath != null && !base.customImagesPath.isEmpty()))
		{
			artPackComboBox.addItem(artPack);
		}
		artPackComboBox.setSelectedItem(gen.artPack);
		artPackComboBox.addActionListener(e ->
		{
			gen.artPack = (String) artPackComboBox.getSelectedItem();
			resourceListRebuilders.forEach(Runnable::run);
		});
		organizer.addLabelAndComponent(Translation.get("randomizeTheme.artPack.label"), Translation.get("randomizeTheme.artPack.help"), artPackComboBox);

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.oceanColorVariation"));
		String oceanHelp = "randomizeTheme.oceanColorVariation.help";
		addColorVariationSliders(organizer, oceanHelp, gen.oceanHueVariation, value -> gen.oceanHueVariation = value, gen.oceanSaturationVariation,
				value -> gen.oceanSaturationVariation = value, gen.oceanBrightnessVariation, value -> gen.oceanBrightnessVariation = value);

		if (base.drawRegionColors)
		{
			// Region colors are generated around the region base color with the map's own region color ranges, which the Land and Water
			// tool also edits.
			organizer.addSectionHeading(Translation.get("randomizeTheme.section.regionBaseColorVariation"));
			addRegionBaseColorChooser(organizer);
			addColorVariationSliders(organizer, "randomizeTheme.regionBaseColorVariation.help", gen.regionBaseHueVariation, value -> gen.regionBaseHueVariation = value,
					gen.regionBaseSaturationVariation, value -> gen.regionBaseSaturationVariation = value, gen.regionBaseBrightnessVariation,
					value -> gen.regionBaseBrightnessVariation = value);

			organizer.addSectionHeading(Translation.get("randomizeTheme.section.regionColorVariation"));
			addRangeSlider(organizer, "landWaterTool.hueRange", LandWaterTool.maxHueRange, base.hueRange, value -> base.hueRange = value);
			addRangeSlider(organizer, "landWaterTool.saturationRange", LandWaterTool.maxSaturationRange, base.saturationRange, value -> base.saturationRange = value);
			addRangeSlider(organizer, "landWaterTool.brightnessRange", LandWaterTool.maxBrightnessRange, base.brightnessRange, value -> base.brightnessRange = value);
		}
		else
		{
			organizer.addSectionHeading(Translation.get("randomizeTheme.section.landColorVariation"));
			addColorVariationSliders(organizer, "randomizeTheme.landColorVariation.help", gen.landHueVariation, value -> gen.landHueVariation = value,
					gen.landSaturationVariation, value -> gen.landSaturationVariation = value, gen.landBrightnessVariation, value -> gen.landBrightnessVariation = value);
		}

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.ocean"));
		addProbabilitySlider(organizer, "randomizeTheme.drawOceanWavesProbability", gen.drawOceanWavesProbability, value -> gen.drawOceanWavesProbability = value);
		addEnumCheckboxes(organizer, "randomizeTheme.allowedOceanWaveTypes",
				Arrays.asList(OceanWaves.ConcentricWaves, OceanWaves.WavyLines, OceanWaves.Hatching, OceanWaves.Ripples, OceanWaves.SincWaves), gen.allowedOceanWaveTypes,
				ThemePanel::getWaveTypeName);
		addVariationSlider(organizer, "randomizeTheme.oceanShadingLevelVariation", 0, 50, gen.oceanShadingLevelVariation, value -> gen.oceanShadingLevelVariation = value);
		addProbabilitySlider(organizer, "randomizeTheme.oceanShadingWithWavesProbability", gen.oceanShadingWithWavesProbability,
				value -> gen.oceanShadingWithWavesProbability = value);

		organizer.addSectionHeading(Translation.get("randomizeTheme.section.landAndBorder"));
		addVariationSlider(organizer, "randomizeTheme.coastShadingLevelVariation", 0, 50, gen.coastShadingLevelVariation, value -> gen.coastShadingLevelVariation = value);
		addVariationSlider(organizer, "randomizeTheme.grungeWidthVariation", 0, 1000, gen.grungeWidthVariation, value -> gen.grungeWidthVariation = value);
		addProbabilitySlider(organizer, "randomizeTheme.drawBorderProbability", gen.drawBorderProbability, value -> gen.drawBorderProbability = value);
		addResourceCheckboxes(organizer, "randomizeTheme.allowedBorders", () -> SettingsGenerator.listBorderChoices(getArtPackToChooseFrom(), base.customImagesPath),
				gen.allowedBorderNames, name -> name);
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
				RandomizeThemeDialog::getBackgroundTypeName);

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

	/**
	 * The art pack borders and background textures are chosen from: the one chosen in the dialog, or the installed one if the chosen one
	 * isn't installed.
	 */
	private String getArtPackToChooseFrom()
	{
		return gen.artPack != null && Assets.artPackExists(gen.artPack, base.customImagesPath) ? gen.artPack : Assets.installedArtPack;
	}

	private String createTooltipFromHelpKey(String helpKey)
	{
		return "<html>" + Translation.get(helpKey) + "<br>" + Translation.get("randomizeTheme.zeroNeverChanges") + "</html>";
	}

	private void addVariationSlider(GridBagOrganizer organizer, String key, int min, int max, int value, IntConsumer setValue)
	{
		addVariationSlider(organizer, key + ".label", null, key + ".help", min, max, value, setValue);
	}

	/**
	 * Adds a variation slider whose label has the tip from the key's ".tip" translation under it.
	 */
	private void addVariationSliderWithTip(GridBagOrganizer organizer, String key, int min, int max, int value, IntConsumer setValue)
	{
		addVariationSlider(organizer, key + ".label", key + ".tip", key + ".help", min, max, value, setValue);
	}

	private void addVariationSlider(GridBagOrganizer organizer, String labelKey, String helpKey, int min, int max, int value, IntConsumer setValue)
	{
		addVariationSlider(organizer, labelKey, null, helpKey, min, max, value, setValue);
	}

	private void addVariationSlider(GridBagOrganizer organizer, String labelKey, String tipKey, String helpKey, int min, int max, int value, IntConsumer setValue)
	{
		JSlider slider = new JSlider(min, max, Math.max(min, Math.min(max, value)));
		slider.setPaintLabels(false);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, null, () -> setValue.accept(slider.getValue()), 44);
		addSliderToOrganizer(organizer, sliderWithDisplay, labelKey, tipKey, createTooltipFromHelpKey(helpKey));
	}

	private static void addSliderToOrganizer(GridBagOrganizer organizer, SliderWithDisplayedValue sliderWithDisplay, String labelKey, String tipKey, String tooltip)
	{
		if (tipKey == null)
		{
			sliderWithDisplay.addToOrganizer(organizer, Translation.get(labelKey), tooltip);
		}
		else
		{
			sliderWithDisplay.addToOrganizer(organizer, SwingHelper.createLabelWithTip(Translation.get(labelKey), Translation.get(tipKey), tooltip));
		}
	}

	/**
	 * Adds hue, saturation, and brightness variation sliders, which go as high as the Land and Water tool's color generator ranges.
	 */
	private void addColorVariationSliders(GridBagOrganizer organizer, String helpKey, int hueVariation, IntConsumer setHueVariation, int saturationVariation,
			IntConsumer setSaturationVariation, int brightnessVariation, IntConsumer setBrightnessVariation)
	{
		addVariationSlider(organizer, "landWaterTool.hueRange.label", helpKey, 0, LandWaterTool.maxHueRange, hueVariation, setHueVariation);
		addVariationSlider(organizer, "landWaterTool.saturationRange.label", helpKey, 0, LandWaterTool.maxSaturationRange, saturationVariation, setSaturationVariation);
		addVariationSlider(organizer, "landWaterTool.brightnessRange.label", helpKey, 0, LandWaterTool.maxBrightnessRange, brightnessVariation, setBrightnessVariation);
	}

	/**
	 * Adds a chooser for the map's region base color, which is a setting of the map rather than a rule for varying it.
	 */
	private void addRegionBaseColorChooser(GridBagOrganizer organizer)
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
		organizer.addLabelAndComponentsHorizontal(Translation.get("landWaterTool.baseColor.label"), Translation.get("landWaterTool.baseColor.help"),
				Arrays.asList(colorDisplay, chooseButton), SwingHelper.borderWidthBetweenComponents);
	}

	/**
	 * Adds a slider for one of the map's region color ranges, which are settings of the map rather than rules for varying it.
	 */
	private void addRangeSlider(GridBagOrganizer organizer, String key, int max, int value, IntConsumer setValue)
	{
		JSlider slider = new JSlider(0, max, Math.max(0, Math.min(max, value)));
		slider.setPaintLabels(false);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, null, () -> setValue.accept(slider.getValue()), 44);
		sliderWithDisplay.addToOrganizer(organizer, Translation.get(key + ".label"), Translation.get(key + ".help"));
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
	 * Adds a checkbox for each choice. Checking every choice is stored as an empty set, which means every choice, including ones added in
	 * later versions.
	 */
	private <E> RowHider addEnumCheckboxes(GridBagOrganizer organizer, String key, List<E> choices, Set<E> allowed, Function<E, String> getName)
	{
		JPanel panel = new JPanel(new WrapLayout(WrapLayout.LEFT, 4, 0));
		Map<E, JCheckBox> checkboxes = new LinkedHashMap<>();
		Runnable update = () ->
		{
			allowed.clear();
			boolean allChecked = checkboxes.values().stream().allMatch(JCheckBox::isSelected);
			if (!allChecked)
			{
				checkboxes.forEach((choice, checkbox) ->
				{
					if (checkbox.isSelected())
					{
						allowed.add(choice);
					}
				});
			}
		};
		for (E choice : choices)
		{
			JCheckBox checkbox = new JCheckBox(getName.apply(choice));
			checkbox.setSelected(allowed.isEmpty() || allowed.contains(choice));
			checkbox.addActionListener(e -> update.run());
			checkboxes.put(choice, checkbox);
			panel.add(checkbox);
		}
		return organizer.addLeftAlignedComponentWithStackedLabel(Translation.get(key + ".label"), "<html>" + Translation.get(key + ".help") + "</html>", panel);
	}

	/**
	 * Adds a checkbox for each resource to choose among, rebuilt when the art pack changes. Checking every one is stored as an empty set.
	 */
	private void addResourceCheckboxes(GridBagOrganizer organizer, String key, Supplier<List<NamedResource>> listResources, Set<String> allowed,
			Function<String, String> getDisplayName)
	{
		JPanel panel = new JPanel(new WrapLayout(WrapLayout.LEFT, 4, 0));
		Runnable rebuild = () ->
		{
			panel.removeAll();
			// Resources from several art packs can share a name, and the rules allow them by name.
			Set<String> names = new LinkedHashSet<>();
			for (NamedResource resource : listResources.get())
			{
				names.add(resource.name);
			}
			// A name the art pack doesn't have, such as from rules for another art pack, isn't kept.
			allowed.retainAll(names);
			Map<JCheckBox, String> checkboxes = new LinkedHashMap<>();
			for (String name : names)
			{
				JCheckBox checkbox = new JCheckBox(getDisplayName.apply(name));
				checkbox.setSelected(allowed.isEmpty() || allowed.contains(name));
				checkbox.addActionListener(e ->
				{
					allowed.clear();
					if (!checkboxes.keySet().stream().allMatch(JCheckBox::isSelected))
					{
						checkboxes.forEach((box, boxName) ->
						{
							if (box.isSelected())
							{
								allowed.add(boxName);
							}
						});
					}
				});
				checkboxes.put(checkbox, name);
				panel.add(checkbox);
			}
			panel.revalidate();
			panel.repaint();
		};
		rebuild.run();
		resourceListRebuilders.add(rebuild);
		organizer.addLeftAlignedComponentWithStackedLabel(Translation.get(key + ".label"), "<html>" + Translation.get(key + ".help") + "</html>", panel);
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
		SettingsGenerator.randomizeTheme(variation, base, getArtPackToChooseFrom(), new Random());
		variations.add(new Variation(variation, gen.shuffleTextBackgrounds));
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
				}
				catch (Exception e)
				{
					Logger.printError("Unable to draw the preview of the theme's variation.", e);
					variation.preview = null;
				}
				showPreview();
			}
		};
		worker.execute();
	}

	private void apply()
	{
		Variation variation = variations.get(shownIndex);
		keepRulesAndRanges(variation.settings);
		onApply.accept(variation.settings, variation.areTextBackgroundsVaried);
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
