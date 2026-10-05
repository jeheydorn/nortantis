package nortantis.swing;

import nortantis.*;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.editor.FreeIcon;
import nortantis.geom.Dimension;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.Logger;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Exports the open map's theme to a .nortTheme file, and edits the rules for how new random maps vary it. A sample map drawn from the
 * rules shows what they do. The open map is never changed, except that the rules are kept with it so the next export remembers them.
 */
class ThemeExportDialog extends JDialog
{
	private static final Object destinationChooseLocation = new Object();
	private static final long sampleMapLandSeed = 72;
	private static final int sampleMapWorldSize = 6000;
	private static final int sampleMapRegionCount = 6;

	private final MapSettings mapSettings;
	private final ThemeGenerationSettings gen;
	private final BiConsumer<ThemeGenerationSettings, String> onExported;
	private JComboBox<String> artPackComboBox;
	private final List<Runnable> resourceListRebuilders = new ArrayList<>();
	private JComboBox<Object> destinationComboBox;
	private JTextField fileNameField;
	private UnscaledImagePanel previewPanel;
	private JLabel previewStatusLabel;
	private long rollSeed = new Random().nextLong();
	/**
	 * Increases with each sample map requested, so that a sample map that finishes after a newer one was requested is not shown.
	 */
	private boolean isSampleBeingDrawn;
	/** Whether the sample map should be drawn again when the draw in progress finishes, because something it depends on changed. */
	private boolean isAnotherSampleRequested;

	/**
	 * @param mapSettings
	 *            The open map's settings. Not changed.
	 * @param mapName
	 *            The open map's name, used as the default theme name, or null.
	 * @param onExported
	 *            Called after the theme is written, with the rules to keep with the map and where the theme was written.
	 */
	ThemeExportDialog(Window owner, MapSettings mapSettings, String mapName, BiConsumer<ThemeGenerationSettings, String> onExported)
	{
		super(owner, Translation.get("exportTheme.title"), ModalityType.APPLICATION_MODAL);
		this.mapSettings = mapSettings.deepCopyExceptEdits();
		this.mapSettings.edits = mapSettings.edits;
		this.onExported = onExported;
		gen = mapSettings.themeGeneration != null ? mapSettings.themeGeneration.copy() : ThemeGenerationSettings.createDefault();
		if (gen.artPack == null)
		{
			gen.artPack = chooseDefaultArtPack(mapSettings);
		}

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(createSettingsPanel(), BorderLayout.WEST);
		content.add(createPreviewPanel(), BorderLayout.CENTER);
		content.add(createBottomPanel(mapName), BorderLayout.SOUTH);
		setContentPane(content);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		setSize(new java.awt.Dimension(1100, 760));
		setLocationRelativeTo(owner);
		drawSampleMap();
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
		for (String artPack : Assets.listArtPacks(mapSettings.customImagesPath != null && !mapSettings.customImagesPath.isEmpty()))
		{
			artPackComboBox.addItem(artPack);
		}
		artPackComboBox.setSelectedItem(gen.artPack);
		artPackComboBox.addActionListener(e ->
		{
			gen.artPack = (String) artPackComboBox.getSelectedItem();
			resourceListRebuilders.forEach(Runnable::run);
			drawSampleMap();
		});
		organizer.addLabelAndComponent(Translation.get("exportTheme.artPack.label"), Translation.get("exportTheme.artPack.help"), artPackComboBox);

		organizer.addSectionHeading(Translation.get("exportTheme.section.colors"));
		addVariationSlider(organizer, "exportTheme.hueVariation", 0, 90, gen.hueVariation, value -> gen.hueVariation = value);
		addVariationSlider(organizer, "exportTheme.saturationVariation", 0, 60, gen.saturationVariation, value -> gen.saturationVariation = value);
		addVariationSlider(organizer, "exportTheme.brightnessVariation", 0, 60, gen.brightnessVariation, value -> gen.brightnessVariation = value);

		organizer.addSectionHeading(Translation.get("exportTheme.section.ocean"));
		addProbabilitySlider(organizer, "exportTheme.drawOceanWavesProbability", gen.drawOceanWavesProbability, value -> gen.drawOceanWavesProbability = value);
		addEnumCheckboxes(organizer, "exportTheme.allowedOceanWaveTypes",
				Arrays.asList(OceanWaves.ConcentricWaves, OceanWaves.WavyLines, OceanWaves.Hatching, OceanWaves.Ripples, OceanWaves.SincWaves), gen.allowedOceanWaveTypes,
				ThemePanel::getWaveTypeName);
		addVariationSlider(organizer, "exportTheme.concentricWaveCountVariation", 0, 2, gen.concentricWaveCountVariation, value -> gen.concentricWaveCountVariation = value);
		addVariationSlider(organizer, "exportTheme.oceanWavesLevelVariation", 0, 50, gen.oceanWavesLevelVariation, value -> gen.oceanWavesLevelVariation = value);
		addProbabilitySlider(organizer, "exportTheme.fadeConcentricWavesProbability", gen.fadeConcentricWavesProbability, value -> gen.fadeConcentricWavesProbability = value);
		addProbabilitySlider(organizer, "exportTheme.jitterToConcentricWavesProbability", gen.jitterToConcentricWavesProbability,
				value -> gen.jitterToConcentricWavesProbability = value);
		addProbabilitySlider(organizer, "exportTheme.brokenLinesForConcentricWavesProbability", gen.brokenLinesForConcentricWavesProbability,
				value -> gen.brokenLinesForConcentricWavesProbability = value);
		addVariationSlider(organizer, "exportTheme.oceanShadingLevelVariation", 0, 50, gen.oceanShadingLevelVariation, value -> gen.oceanShadingLevelVariation = value);
		addProbabilitySlider(organizer, "exportTheme.oceanShadingWithWavesProbability", gen.oceanShadingWithWavesProbability,
				value -> gen.oceanShadingWithWavesProbability = value);

		organizer.addSectionHeading(Translation.get("exportTheme.section.landAndBorder"));
		addVariationSlider(organizer, "exportTheme.coastShadingLevelVariation", 0, 50, gen.coastShadingLevelVariation, value -> gen.coastShadingLevelVariation = value);
		addVariationSlider(organizer, "exportTheme.grungeWidthVariation", 0, 1000, gen.grungeWidthVariation, value -> gen.grungeWidthVariation = value);
		addProbabilitySlider(organizer, "exportTheme.drawBorderProbability", gen.drawBorderProbability, value -> gen.drawBorderProbability = value);
		addResourceCheckboxes(organizer, "exportTheme.allowedBorders",
				() -> Assets.listBorderTypesForArtPack(gen.artPack, mapSettings.customImagesPath).stream().map(border -> border.name).toList(), gen.allowedBorderNames,
				name -> name);
		addProbabilitySlider(organizer, "exportTheme.frayedBorderProbability", gen.frayedBorderProbability, value -> gen.frayedBorderProbability = value);
		addVariationSlider(organizer, "exportTheme.frayedBorderBlurLevelVariation", 0, 150, gen.frayedBorderBlurLevelVariation, value -> gen.frayedBorderBlurLevelVariation = value);
		addVariationSlider(organizer, "exportTheme.frayedBorderSizeVariation", 0, 7, gen.frayedBorderSizeVariation, value -> gen.frayedBorderSizeVariation = value);

		organizer.addSectionHeading(Translation.get("exportTheme.section.regionsRoadsAndBackground"));
		addEnumCheckboxes(organizer, "exportTheme.allowedLandColoringMethods", Arrays.asList(LandColoringMethod.values()), gen.allowedLandColoringMethods, Object::toString);
		addProbabilitySlider(organizer, "exportTheme.drawRegionBoundariesProbability", gen.drawRegionBoundariesProbability, value -> gen.drawRegionBoundariesProbability = value);
		addEnumCheckboxes(organizer, "exportTheme.allowedRegionBoundaryStrokeTypes", Arrays.asList(StrokeType.values()), gen.allowedRegionBoundaryStrokeTypes, Object::toString);
		addEnumCheckboxes(organizer, "exportTheme.allowedRoadStrokeTypes", Arrays.asList(StrokeType.values()), gen.allowedRoadStrokeTypes, Object::toString);
		addEnumCheckboxes(organizer, "exportTheme.allowedLineStyles", Arrays.asList(LineStyle.values()), gen.allowedLineStyles, ThemeExportDialog::getLineStyleName);
		addProbabilitySlider(organizer, "exportTheme.fractalBackgroundProbability", gen.fractalBackgroundProbability, value -> gen.fractalBackgroundProbability = value);
		addResourceCheckboxes(organizer, "exportTheme.allowedBackgroundTextures",
				() -> Assets.listBackgroundTexturesForArtPack(gen.artPack, mapSettings.customImagesPath).stream().map(texture -> texture.name).toList(),
				gen.allowedBackgroundTextureNames, FilenameUtils::getBaseName);
		addResourceCheckboxes(organizer, "exportTheme.allowedCityIconTypes",
				() -> new ArrayList<>(ImageCache.getInstance(gen.artPack, mapSettings.customImagesPath).getIconGroupNames(IconType.cities)), gen.allowedCityIconTypeNames,
				name -> name);

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.55);
		organizer.addVerticalFillerRow();
		JScrollPane scrollPane = organizer.createScrollPane();
		scrollPane.setPreferredSize(new java.awt.Dimension(470, 600));
		return scrollPane;
	}

	private String createTooltip(String key)
	{
		return "<html>" + Translation.get(key + ".help") + "<br>" + Translation.get("exportTheme.zeroNeverChanges") + "</html>";
	}

	private void addVariationSlider(GridBagOrganizer organizer, String key, int min, int max, int value, IntConsumer setValue)
	{
		JSlider slider = new JSlider(min, max, Math.max(min, Math.min(max, value)));
		slider.setPaintLabels(false);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, null, () -> setValue.accept(slider.getValue()), 44);
		sliderWithDisplay.addToOrganizer(organizer, Translation.get(key + ".label"), createTooltip(key));
	}

	private void addProbabilitySlider(GridBagOrganizer organizer, String key, double value, Consumer<Double> setValue)
	{
		JSlider slider = new JSlider(0, 100, (int) Math.round(value * 100));
		slider.setPaintLabels(false);
		SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(slider, (sliderValue) -> sliderValue + "%", () -> setValue.accept(slider.getValue() / 100.0), 44);
		sliderWithDisplay.addToOrganizer(organizer, Translation.get(key + ".label"), "<html>" + Translation.get(key + ".help") + "</html>");
	}

	/**
	 * Adds a checkbox for each choice. Checking every choice is stored as an empty set, which means every choice, including ones added in
	 * later versions.
	 */
	private static String getLineStyleName(LineStyle lineStyle)
	{
		return switch (lineStyle)
		{
			case Jagged -> Translation.get("theme.lineStyle.jagged");
			case Splines -> Translation.get("theme.lineStyle.splines");
			case SplinesWithSmoothedCoastlines -> Translation.get("theme.lineStyle.splinesSmoothed");
		};
	}

	private <E> void addEnumCheckboxes(GridBagOrganizer organizer, String key, List<E> choices, Set<E> allowed, Function<E, String> getName)
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
		organizer.addLeftAlignedComponentWithStackedLabel(Translation.get(key + ".label"), "<html>" + Translation.get(key + ".help") + "</html>", panel);
	}

	/**
	 * Adds a checkbox for each resource in the theme's art pack, rebuilt when the art pack changes. Checking every one is stored as an empty
	 * set.
	 */
	private void addResourceCheckboxes(GridBagOrganizer organizer, String key, Supplier<List<String>> listResources, Set<String> allowed, Function<String, String> getDisplayName)
	{
		JPanel panel = new JPanel(new WrapLayout(WrapLayout.LEFT, 4, 0));
		Runnable rebuild = () ->
		{
			panel.removeAll();
			List<String> names = listResources.get();
			// A name the art pack doesn't have, such as from a theme for another art pack, isn't kept.
			allowed.retainAll(names);
			List<JCheckBox> checkboxes = new ArrayList<>();
			for (String name : names)
			{
				JCheckBox checkbox = new JCheckBox(getDisplayName.apply(name));
				checkbox.setSelected(allowed.isEmpty() || allowed.contains(name));
				checkbox.addActionListener(e ->
				{
					allowed.clear();
					if (!checkboxes.stream().allMatch(JCheckBox::isSelected))
					{
						for (JCheckBox box : checkboxes)
						{
							if (box.isSelected())
							{
								allowed.add(box.getText());
							}
						}
					}
				});
				checkboxes.add(checkbox);
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
		JLabel explanation = new JLabel("<html>" + Translation.get("exportTheme.sampleExplanation") + "</html>");
		panel.add(explanation, BorderLayout.NORTH);
		previewPanel = new UnscaledImagePanel();
		JPanel previewHolder = new JPanel(new GridBagLayout());
		previewHolder.add(previewPanel);
		panel.add(previewHolder, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		JButton rollButton = new JButton(Translation.get("exportTheme.rollTheme"));
		rollButton.setToolTipText(Translation.get("exportTheme.rollTheme.tooltip"));
		rollButton.addActionListener(e ->
		{
			rollSeed = new Random().nextLong();
			drawSampleMap();
		});
		buttons.add(rollButton);
		previewStatusLabel = new JLabel();
		buttons.add(previewStatusLabel);
		panel.add(buttons, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createBottomPanel(String mapName)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 6);
		c.anchor = GridBagConstraints.LINE_START;

		c.gridx = 0;
		c.gridy = 0;
		panel.add(new JLabel(Translation.get("exportTheme.name.label")), c);
		fileNameField = new JTextField(mapName != null && !mapName.isEmpty() ? mapName : Translation.get("exportTheme.defaultName"), 24);
		c.gridx = 1;
		panel.add(fileNameField, c);

		c.gridx = 2;
		panel.add(new JLabel(Translation.get("exportTheme.destination.label")), c);
		destinationComboBox = new JComboBoxFixed<>();
		destinationComboBox.addItem(Translation.get("exportTheme.destination.userThemes"));
		for (String artPack : Assets.listArtPacks(false))
		{
			if (!artPack.equals(Assets.installedArtPack))
			{
				destinationComboBox.addItem(artPack);
			}
		}
		destinationComboBox.addItem(destinationChooseLocation);
		destinationComboBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value == destinationChooseLocation)
				{
					setText(Translation.get("exportTheme.destination.chooseLocation"));
				}
				else if (value instanceof String artPack && index != 0 && destinationComboBox.getItemAt(0) != value)
				{
					setText(Translation.get("exportTheme.destination.artPack", artPack));
				}
				return this;
			}
		});
		if (gen.artPack != null && !gen.artPack.equals(Assets.installedArtPack) && !gen.artPack.equals(Assets.customArtPack))
		{
			destinationComboBox.setSelectedItem(gen.artPack);
		}
		destinationComboBox.setToolTipText(Translation.get("exportTheme.destination.help"));
		c.gridx = 3;
		panel.add(destinationComboBox, c);

		c.gridx = 4;
		c.weightx = 1;
		panel.add(Box.createHorizontalGlue(), c);
		c.weightx = 0;

		JButton exportButton = new JButton(Translation.get("exportTheme.export"));
		exportButton.addActionListener(e -> export());
		c.gridx = 5;
		panel.add(exportButton, c);
		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> dispose());
		c.gridx = 6;
		panel.add(cancelButton, c);
		getRootPane().setDefaultButton(exportButton);
		return panel;
	}

	/**
	 * Draws the sample map: always the same small world, so that only the theme differs between rolls, and so that themes can be compared.
	 */
	private void drawSampleMap()
	{
		if (isSampleBeingDrawn)
		{
			isAnotherSampleRequested = true;
			return;
		}
		isSampleBeingDrawn = true;
		previewStatusLabel.setText(Translation.get("exportTheme.drawingSample"));
		MapSettings theme = createThemeSettings();
		String artPack = gen.artPack != null && Assets.artPackExists(gen.artPack, mapSettings.customImagesPath) ? gen.artPack : Assets.installedArtPack;
		long seed = rollSeed;
		SwingWorker<Image, Void> worker = new SwingWorker<>()
		{
			@Override
			protected Image doInBackground() throws Exception
			{
				MapSettings sample = SettingsGenerator.generateFromTheme(new Random(seed), artPack, theme, false, mapSettings.customImagesPath);
				sample.randomSeed = sampleMapLandSeed;
				sample.regionsRandomSeed = sampleMapLandSeed;
				sample.textRandomSeed = sampleMapLandSeed;
				sample.worldSize = sampleMapWorldSize;
				sample.landShape = LandShape.Continents;
				sample.regionCount = sampleMapRegionCount;
				sample.generatedWidth = GeneratedDimension.Sixteen_by_9.width;
				sample.generatedHeight = GeneratedDimension.Sixteen_by_9.height;
				double osScale = SwingHelper.getOSScale();
				return new MapCreator().createMap(sample, new Dimension(560 * osScale, 315 * osScale), null);
			}

			@Override
			protected void done()
			{
				isSampleBeingDrawn = false;
				if (!isDisplayable())
				{
					return;
				}
				if (isAnotherSampleRequested)
				{
					isAnotherSampleRequested = false;
					drawSampleMap();
					return;
				}
				try
				{
					Image map = get();
					previewPanel.setImage(AwtBridge.toBufferedImage(map));
					previewStatusLabel.setText(Translation.get("exportTheme.sampleNotExported"));
				}
				catch (Exception e)
				{
					Logger.printError("Unable to draw the sample map for the theme.", e);
					previewStatusLabel.setText(Translation.get("exportTheme.sampleFailed"));
				}
			}
		};
		worker.execute();
	}

	/**
	 * The open map's theme with the rules in this dialog, and the open map's current look as the rules' base values.
	 */
	private MapSettings createThemeSettings()
	{
		MapSettings theme = mapSettings.deepCopyExceptEdits();
		theme.edits = new nortantis.swing.MapEdits();
		ThemeGenerationSettings themeGeneration = gen.copy();
		themeGeneration.setBaseValuesFrom(mapSettings);
		theme.themeGeneration = themeGeneration;
		return theme;
	}

	private void export()
	{
		String name = fileNameField.getText().trim();
		if (name.isEmpty())
		{
			SwingHelper.showMessageDialog(this, Translation.get("exportTheme.nameRequired"), Translation.get("exportTheme.title"), JOptionPane.WARNING_MESSAGE);
			return;
		}

		Path path;
		Object destination = destinationComboBox.getSelectedItem();
		if (destination == destinationChooseLocation)
		{
			JFileChooser fileChooser = new JFileChooser();
			if (mapSettings.themeExportPath != null && new File(mapSettings.themeExportPath).getParentFile() != null)
			{
				fileChooser.setCurrentDirectory(new File(mapSettings.themeExportPath).getParentFile());
			}
			fileChooser.setSelectedFile(new File(name + MapSettings.themeFileExtensionWithDot));
			fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter()
			{
				@Override
				public boolean accept(File f)
				{
					return f.isDirectory() || ThemeCatalog.isThemeFile(f.toPath());
				}

				@Override
				public String getDescription()
				{
					return Translation.get("exportTheme.fileType");
				}
			});
			if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			{
				return;
			}
			path = fileChooser.getSelectedFile().toPath();
		}
		else
		{
			Path folder = destinationComboBox.getSelectedIndex() == 0 ? Assets.getUserThemesFolder() : Assets.getThemesFolderForArtPack((String) destination, mapSettings.customImagesPath);
			path = Paths.get(folder.toString(), name);
		}
		if (!ThemeCatalog.isThemeFile(path))
		{
			path = Paths.get(path.toString() + MapSettings.themeFileExtensionWithDot);
		}
		if (Files.exists(path) && destination != destinationChooseLocation)
		{
			int replace = SwingHelper.showConfirmDialog(this, Translation.get("exportTheme.replace", FilenameUtils.getBaseName(path.toString())), Translation.get("exportTheme.title"),
					JOptionPane.YES_NO_OPTION);
			if (replace != JOptionPane.YES_OPTION)
			{
				return;
			}
		}

		MapSettings theme = createThemeSettings();
		try
		{
			Files.createDirectories(path.getParent());
			theme.writeThemeToFile(path.toString());
		}
		catch (Exception e)
		{
			Logger.printError("Unable to export the theme to " + path, e);
			SwingHelper.showMessageDialog(this, Translation.get("exportTheme.failed", e.getMessage()), Translation.get("exportTheme.title"), JOptionPane.ERROR_MESSAGE);
			return;
		}

		showSharingWarningsIfNeeded(theme);
		onExported.accept(theme.themeGeneration, path.toString());
		dispose();
	}

	/**
	 * Tells the author what someone the theme is shared with might not have: fonts from their device, and art packs.
	 */
	private void showSharingWarningsIfNeeded(MapSettings theme)
	{
		List<String> deviceFonts = new ArrayList<>();
		for (String family : MapFonts.getFamiliesUsed(theme))
		{
			if (FontFinder.getArtPack(family) == null)
			{
				deviceFonts.add(family);
			}
		}
		Set<String> artPacks = new TreeSet<>();
		if (theme.drawBorder && theme.borderResource != null)
		{
			artPacks.add(theme.borderResource.artPack);
		}
		if (theme.backgroundTextureResource != null && theme.generateBackgroundFromTexture)
		{
			artPacks.add(theme.backgroundTextureResource.artPack);
		}
		if (theme.themeGeneration.artPack != null)
		{
			artPacks.add(theme.themeGeneration.artPack);
		}
		artPacks.remove(Assets.installedArtPack);

		List<String> messages = new ArrayList<>();
		if (!deviceFonts.isEmpty())
		{
			messages.add(Translation.get("exportTheme.deviceFontsWarning", String.join(", ", deviceFonts)));
		}
		if (!artPacks.isEmpty())
		{
			messages.add(Translation.get("exportTheme.artPacksWarning", String.join(", ", artPacks)));
		}
		if (!messages.isEmpty())
		{
			SwingHelper.showMessageDialog(this, String.join("\n\n", messages), Translation.get("exportTheme.title"), JOptionPane.INFORMATION_MESSAGE);
		}
	}
}
