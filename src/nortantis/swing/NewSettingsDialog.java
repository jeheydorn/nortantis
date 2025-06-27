package nortantis.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.Timer;

import org.apache.commons.lang3.StringUtils;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.geom.Rectangle;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.ThemePanel.LandColoringMethod;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class NewSettingsDialog extends JDialog
{
	JSlider worldSizeSlider;
	JSlider edgeLandToWaterProbSlider;
	JSlider centerLandToWaterProbSlider;
	private JComboBox<String> dimensionsComboBox;
	BooksWidget booksWidget;
	MapSettings settings;
	private JProgressBar progressBar;
	private MapUpdater updater;
	private MapEditingPanel mapEditingPanel;
	private Dimension defaultSize = new Dimension(900, 750);
	private int amountToSubtractFromLeftAndRightPanels = 40;
	private Timer progressBarTimer;
	public final double cityFrequencySliderScale = 100.0 * 1.0 / SettingsGenerator.maxCityProbabillity;
	private JSlider cityFrequencySlider;
	private JComboBox<String> cityIconsTypeComboBox;
	private JPanel mapEditingPanelContainer;
	private JComboBox<LandColoringMethod> landColoringMethodComboBox;
	MainWindow mainWindow;
	private JTextField pathDisplay;
	private JComboBox<String> artPackComboBox;

	public NewSettingsDialog(MainWindow mainWindow, MapSettings settingsToKeepThemeFrom)
	{
		super(mainWindow, "Create New Map", Dialog.ModalityType.APPLICATION_MODAL);
		this.mainWindow = mainWindow;

		createGUI(mainWindow);

		if (settingsToKeepThemeFrom == null)
		{
			settings = SettingsGenerator.generate(UserPreferences.getInstance().defaultCustomImagesPath);
		}
		else
		{
			settings = settingsToKeepThemeFrom.deepCopy();
			// Clear out export paths so that creating a new map was the same theme doesn't overwrite exported files from the previous map.
			settings.imageExportPath = null;
			settings.heightmapExportPath = null;
			randomizeLand();
			settings.textRandomSeed = Math.abs(new Random().nextInt());
			List<String> cityIconTypes = ImageCache.getInstance(settings.artPack, settings.customImagesPath)
					.getIconGroupNames(IconType.cities);
			if (cityIconTypes.size() > 0)
			{
				settings.cityIconTypeName = ProbabilityHelper.sampleUniform(new Random(), new ArrayList<>(cityIconTypes));
			}
		}
		loadSettingsIntoGUI(settings);

		updater.setEnabled(true);
	}

	private void createGUI(MainWindow mainWindow)
	{
		setSize(defaultSize);
		setMinimumSize(defaultSize);

		GridBagOrganizer organizer = new GridBagOrganizer();
		JPanel container = organizer.panel;
		add(container);
		container.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		container.setPreferredSize(defaultSize);

		JPanel generatorSettingsPanel = new JPanel();
		generatorSettingsPanel.setLayout(new BoxLayout(generatorSettingsPanel, BoxLayout.X_AXIS));
		organizer.addLeftAlignedComponent(generatorSettingsPanel, 0, 0, false);

		createLeftPanel(generatorSettingsPanel);
		generatorSettingsPanel.add(Box.createHorizontalStrut(20));
		createRightPanel(generatorSettingsPanel);

		createMapEditingPanel();
		createMapUpdater();
		organizer.addLeftAlignedComponent(mapEditingPanelContainer, 0, 0, true);

		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				progressBar.setVisible(updater.isMapBeingDrawn());
			}
		};
		progressBarTimer = new Timer(50, listener);
		progressBarTimer.setInitialDelay(500);

		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

		{
			JButton randomizeThemeButton = new JButton("Randomize Theme");
			randomizeThemeButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					randomizeTheme();
				}
			});
			bottomPanel.add(randomizeThemeButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton randomizeLandButton = new JButton("Randomize Land");
			randomizeLandButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					randomizeLand();
				}
			});
			bottomPanel.add(randomizeLandButton);
			bottomPanel.add(Box.createHorizontalStrut(40));
		}

		{
			JButton flipHorizontallyButton = new JButton("↔ Flip");
			flipHorizontallyButton.setToolTipText("Flip the land shape horizontally.");
			flipHorizontallyButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					// If the map is rotated on it's side, then flip vertically instead of horizontally.
					if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
					{
						settings.flipVertically = !settings.flipVertically;
					}
					else
					{
						settings.flipHorizontally = !settings.flipHorizontally;
					}
					handleMapChange();
				}
			});
			bottomPanel.add(flipHorizontallyButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton flipVerticallyButton = new JButton("↕ Flip");
			flipVerticallyButton.setToolTipText("Flip the land shape vertically.");
			flipVerticallyButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					// If the map is rotated on it's side, then flip horizontally instead of vertically.
					if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
					{
						settings.flipHorizontally = !settings.flipHorizontally;
					}
					else
					{
						settings.flipVertically = !settings.flipVertically;
					}
					handleMapChange();
				}
			});
			bottomPanel.add(flipVerticallyButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton rotateButton = new JButton("↺ Left");
			rotateButton.setToolTipText("Rotate the land shape counterclockwise by 90°.");

			rotateButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					if (settings.rightRotationCount == 0)
					{
						settings.rightRotationCount = 3;
					}
					else
					{
						settings.rightRotationCount--;
					}
					handleMapChange();
				}
			});
			bottomPanel.add(rotateButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton rotateButton = new JButton("↻ Right");
			rotateButton.setToolTipText("Rotate the land shape clockwise by 90°.");
			rotateButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					settings.rightRotationCount = (settings.rightRotationCount + 1) % 4;
					handleMapChange();
				}
			});
			bottomPanel.add(rotateButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}


		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Drawing...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		bottomPanel.add(progressBar);
		bottomPanel.add(Box.createHorizontalGlue());

		JPanel bottomButtonsPanel = new JPanel();
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton createMapButton = new JButton("<html>C<u>r</u>eate Map</html>");
		createMapButton.setMnemonic(KeyEvent.VK_R);
		bottomButtonsPanel.add(createMapButton);
		createMapButton.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				onCreateMap(mainWindow);
			}
		});

		bottomPanel.add(bottomButtonsPanel);
		organizer.addLeftAlignedComponent(bottomPanel, 0, 0, false);

		addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent componentEvent)
			{
				handleMapChange();
			}
		});

		pack();
	}

	private void onCreateMap(MainWindow mainWindow)
	{
		mainWindow.updater.cancel();

		// Disable the updater so that it ignores anything triggered by the window resizing as it closes.
		// I'm not if that's even possible but I think I saw it happen a few times and try to draw a tiny
		// map.
		mainWindow.updater.setEnabled(false);

		mainWindow.updater.dowWhenMapIsNotDrawing(() ->
		{
			mainWindow.clearOpenSettingsFilePath();
			MapSettings settings = getSettingsFromGUI();
			mainWindow.loadSettingsIntoGUI(settings);

			dispose();
		});
	}

	private void createLeftPanel(JPanel generatorSettingsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel leftPanel = organizer.panel;
		generatorSettingsPanel.add(leftPanel);

		dimensionsComboBox = new JComboBox<>();
		for (String dimension : SettingsGenerator.getAllowedDimmensions())
		{
			dimensionsComboBox.addItem(dimension);
		}
		createMapChangeListener(dimensionsComboBox);
		organizer.addLabelAndComponent("Dimensions: <br>(cannot be changed in editor)",
				"Dimensions of the map when exported at 100% resolution, although the resolution can be scaled up or down while"
						+ " exporting. This doesn't include the border, if you add one.",
				dimensionsComboBox);

		worldSizeSlider = new JSlider();
		worldSizeSlider.setSnapToTicks(true);
		worldSizeSlider.setMajorTickSpacing(8000);
		worldSizeSlider.setMinorTickSpacing(SettingsGenerator.worldSizePrecision);
		worldSizeSlider.setPaintLabels(true);
		worldSizeSlider.setPaintTicks(true);
		worldSizeSlider.setMinimum(SettingsGenerator.minWorldSize);
		worldSizeSlider.setMaximum(SettingsGenerator.maxWorldSize);
		createMapChangeListener(worldSizeSlider);
		organizer.addLabelAndComponent("World size: <br>(cannot be changed in editor)",
				"The number of polygons in the randomly generated world.", worldSizeSlider);

		edgeLandToWaterProbSlider = new JSlider();
		edgeLandToWaterProbSlider.setValue(70);
		edgeLandToWaterProbSlider.setPaintTicks(true);
		edgeLandToWaterProbSlider.setPaintLabels(true);
		edgeLandToWaterProbSlider.setMinorTickSpacing(25);
		edgeLandToWaterProbSlider.setMajorTickSpacing(25);
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = edgeLandToWaterProbSlider.getMinimum(); i < edgeLandToWaterProbSlider.getMaximum()
					+ 1; i += edgeLandToWaterProbSlider.getMajorTickSpacing())
			{
				labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
			}
			edgeLandToWaterProbSlider.setLabelTable(labelTable);
		}
		createMapChangeListener(edgeLandToWaterProbSlider);
		organizer.addLabelAndComponent("Edge land probability:",
				"The probability that a tectonic plate touching the edge of the map will be land rather than ocean.",
				edgeLandToWaterProbSlider);

		centerLandToWaterProbSlider = new JSlider();
		centerLandToWaterProbSlider.setValue(70);
		centerLandToWaterProbSlider.setPaintTicks(true);
		centerLandToWaterProbSlider.setPaintLabels(true);
		centerLandToWaterProbSlider.setMinorTickSpacing(25);
		centerLandToWaterProbSlider.setMajorTickSpacing(25);
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = centerLandToWaterProbSlider.getMinimum(); i < centerLandToWaterProbSlider.getMaximum()
					+ 1; i += centerLandToWaterProbSlider.getMajorTickSpacing())
			{
				labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
			}
			centerLandToWaterProbSlider.setLabelTable(labelTable);
		}
		createMapChangeListener(centerLandToWaterProbSlider);
		organizer.addLabelAndComponent("Center land probability:",
				"The probability that a tectonic plate not touching the edge of the map will be land rather than ocean.",
				centerLandToWaterProbSlider);

		landColoringMethodComboBox = new JComboBox<LandColoringMethod>();
		for (LandColoringMethod method : LandColoringMethod.values())
		{
			landColoringMethodComboBox.addItem(method);
		}

		createMapChangeListener(landColoringMethodComboBox);
		organizer.addLabelAndComponent("Land coloring method:", "How to color the land.", landColoringMethodComboBox);

		JButton changeButton = new JButton("Change");
		pathDisplay = new JTextField();
		pathDisplay.setText(FileHelper.replaceHomeFolderPlaceholder(UserPreferences.getInstance().defaultCustomImagesPath));
		pathDisplay.setEditable(false);
		organizer.addLabelAndComponentsHorizontal("Custom Images Folder:", "Configure custom images to use when generating this map.",
				Arrays.asList(pathDisplay, changeButton));

		changeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				CustomImagesDialog dialog = new CustomImagesDialog(mainWindow, settings.customImagesPath, (value) ->
				{
					settings.customImagesPath = value;
					updatePathDisplay();
					settings.artPack = Assets.customArtPack;
					initializeArtPackOptionsAndCityTypeOptions();

					enableOrDisableProgressBar(true);
					updater.createAndShowMapFull(() ->
					{
						ImageCache.clear();
					});
				});
				dialog.setLocationRelativeTo(NewSettingsDialog.this);
				dialog.setVisible(true);
			}
		});

		organizer.addLeftAlignedComponent(
				Box.createRigidArea(new Dimension((defaultSize.width / 2) - amountToSubtractFromLeftAndRightPanels, 0)));

		organizer.addVerticalFillerRow();
	}

	private void initializeCityTypeOptions()
	{
		SwingHelper.initializeComboBoxItems(cityIconsTypeComboBox,
				ImageCache.getInstance((String) artPackComboBox.getSelectedItem(), (String) settings.customImagesPath)
						.getIconGroupNames(IconType.cities),
				(String) cityIconsTypeComboBox.getSelectedItem(), false);
	}

	private void initializeArtPackOptionsAndCityTypeOptions()
	{
		SwingHelper.initializeComboBoxItems(artPackComboBox, Assets.listArtPacks(!StringUtils.isEmpty(settings.customImagesPath)),
				settings.artPack, false);
		initializeCityTypeOptions();
	}

	private void updatePathDisplay()
	{
		if (settings != null && settings.customImagesPath != null && !settings.customImagesPath.isEmpty())
		{
			pathDisplay.setText(FileHelper.replaceHomeFolderPlaceholder(settings.customImagesPath));
		}
		else
		{
			pathDisplay.setText("");
		}
	}

	private void createRightPanel(JPanel generatorSettingsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel rightPanel = organizer.panel;
		generatorSettingsPanel.add(rightPanel);

		artPackComboBox = new JComboBox<String>();
		JLabel artPackLabel = new JLabel("Art pack:");
		artPackLabel.setToolTipText(
				"The set of images and icons to use. '" + Assets.installedArtPack + "' is the images that come installed with Nortantis. '"
						+ Assets.customArtPack + "' means use the custom images folder, if one is selected.");
		artPackComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				updateBackgroundTextureAndBorderToUseArtPackIfNeeded();
				initializeCityTypeOptions();
				handleMapChange();
			}
		});

		cityIconsTypeComboBox = new JComboBox<String>();
		createMapChangeListener(cityIconsTypeComboBox);
		JLabel cityIconTypeLabel = new JLabel("City icon type:");
		cityIconTypeLabel.setToolTipText("The set of city images to use.");

		JPanel rowPanel = new JPanel();
		rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
		organizer.addLeftAlignedComponent(rowPanel);
		rowPanel.add(artPackLabel);
		rowPanel.add(Box.createHorizontalStrut(5));
		rowPanel.add(artPackComboBox);
		rowPanel.add(Box.createHorizontalGlue());
		rowPanel.add(cityIconTypeLabel);
		rowPanel.add(Box.createHorizontalStrut(5));
		rowPanel.add(cityIconsTypeComboBox);

		cityFrequencySlider = new JSlider();
		cityFrequencySlider.setPaintLabels(true);
		cityFrequencySlider.setSnapToTicks(false);
		cityFrequencySlider.setPaintTicks(true);
		cityFrequencySlider.setMinorTickSpacing(10);
		cityFrequencySlider.setMinimum(0);
		cityFrequencySlider.setMaximum(100);
		cityFrequencySlider.setMajorTickSpacing(25);
		createMapChangeListener(cityFrequencySlider);
		organizer.addLabelAndComponent("City frequency:",
				"Higher values create more cities. Lower values create less cities. Zero means no cities.", cityFrequencySlider);

		booksWidget = new BooksWidget(true, () -> handleMapChange());
		booksWidget.getContentPanel().setPreferredSize(new Dimension(360, 180));
		organizer.addLeftAlignedComponentWithStackedLabel("Books for generating text:",
				"Selected books will be used to generate new names.", booksWidget.getContentPanel());

		organizer.addLeftAlignedComponent(
				Box.createRigidArea(new Dimension((defaultSize.width / 2) - amountToSubtractFromLeftAndRightPanels, 0)));

		organizer.addVerticalFillerRow();
	}

	private void updateBackgroundTextureAndBorderToUseArtPackIfNeeded()
	{
		String artPack = (String) artPackComboBox.getSelectedItem();
		boolean backgroundTextureNeedsUpdate = settings.backgroundTextureResource != null
				&& !settings.backgroundTextureResource.artPack.equals(artPack);
		boolean borderNeedsUpdate = settings.borderResource != null && !settings.borderResource.artPack.equals(artPack);

		if (backgroundTextureNeedsUpdate || borderNeedsUpdate)
		{
			MapSettings randomSettings = SettingsGenerator.generate(new Random(), artPack, settings.customImagesPath);

			if (backgroundTextureNeedsUpdate)
			{
				settings.backgroundTextureResource = randomSettings.backgroundTextureResource;
			}
			if (borderNeedsUpdate)
			{
				settings.borderResource = randomSettings.borderResource;
			}
		}

	}

	private void randomizeTheme()
	{
		MapSettings randomSettings = SettingsGenerator.generate(new Random(), (String) artPackComboBox.getSelectedItem(),
				settings.customImagesPath);
		settings.oceanShadingLevel = randomSettings.oceanShadingLevel;
		settings.oceanWavesLevel = randomSettings.oceanWavesLevel;
		settings.concentricWaveCount = randomSettings.concentricWaveCount;
		settings.oceanWavesType = randomSettings.oceanWavesType;
		settings.riverColor = randomSettings.riverColor;
		settings.roadColor = randomSettings.roadColor;
		settings.coastShadingLevel = randomSettings.coastShadingLevel;
		settings.coastShadingColor = randomSettings.coastShadingColor;
		settings.oceanWavesColor = randomSettings.oceanWavesColor;
		settings.coastlineColor = randomSettings.coastlineColor;
		settings.frayedBorder = randomSettings.frayedBorder;
		settings.frayedBorderSize = randomSettings.frayedBorderSize;
		settings.frayedBorderColor = randomSettings.frayedBorderColor;
		settings.frayedBorderBlurLevel = randomSettings.frayedBorderBlurLevel;
		settings.frayedBorderSeed = randomSettings.frayedBorderSeed;
		settings.grungeWidth = randomSettings.grungeWidth;
		settings.generateBackground = randomSettings.generateBackground;
		settings.generateBackgroundFromTexture = randomSettings.generateBackgroundFromTexture;
		settings.solidColorBackground = randomSettings.solidColorBackground;
		settings.colorizeOcean = randomSettings.colorizeOcean;
		settings.colorizeLand = randomSettings.colorizeLand;
		settings.backgroundTextureResource = randomSettings.backgroundTextureResource;
		settings.backgroundTextureImage = randomSettings.backgroundTextureImage;
		settings.backgroundRandomSeed = randomSettings.backgroundRandomSeed;
		settings.oceanColor = randomSettings.oceanColor;
		settings.borderColorOption = randomSettings.borderColorOption;
		settings.borderColor = randomSettings.borderColor;
		settings.landColor = randomSettings.landColor;
		settings.regionBaseColor = randomSettings.regionBaseColor;
		settings.hueRange = randomSettings.hueRange;
		settings.saturationRange = randomSettings.saturationRange;
		settings.brightnessRange = randomSettings.brightnessRange;
		settings.titleFont = randomSettings.titleFont;
		settings.regionFont = randomSettings.regionFont;
		settings.mountainRangeFont = randomSettings.mountainRangeFont;
		settings.otherMountainsFont = randomSettings.otherMountainsFont;
		settings.riverFont = randomSettings.riverFont;
		settings.boldBackgroundColor = randomSettings.boldBackgroundColor;
		settings.textColor = randomSettings.textColor;
		settings.drawBoldBackground = randomSettings.drawBoldBackground;
		settings.drawRegionBoundaries = randomSettings.drawRegionBoundaries;
		settings.regionBoundaryStyle = randomSettings.regionBoundaryStyle;
		settings.drawRegionColors = randomSettings.drawRegionColors;
		settings.regionsRandomSeed = randomSettings.regionsRandomSeed;
		settings.drawBorder = randomSettings.drawBorder;
		settings.borderResource = randomSettings.borderResource;
		settings.borderWidth = randomSettings.borderWidth;
		settings.lineStyle = randomSettings.lineStyle;

		handleMapChange();
	}

	private void randomizeLand()
	{
		MapSettings randomSettings = SettingsGenerator.generate(null);
		settings.randomSeed = randomSettings.randomSeed;
		handleMapChange();
	}

	private void createMapEditingPanel()
	{
		BufferedImage placeHolder = AwtFactory.unwrap(ImageHelper.createPlaceholderImage(new String[] { "Drawing..." },
				AwtFactory.wrap(SwingHelper.getTextColorForPlaceholderImages())));
		mapEditingPanel = new MapEditingPanel(placeHolder);

		mapEditingPanelContainer = new JPanel();
		mapEditingPanelContainer.setLayout(new FlowLayout(FlowLayout.CENTER));
		mapEditingPanelContainer.add(mapEditingPanel);
	}

	private void createMapUpdater()
	{
		updater = new MapUpdater(false)
		{

			@Override
			protected void onBeginDraw()
			{
			}

			@Override
			public MapSettings getSettingsFromGUI()
			{
				MapSettings settings = NewSettingsDialog.this.getSettingsFromGUI();

				// This is only the maximum size because I'm passing in
				// maxDimensions to MapCreator.create.
				settings.resolution = 1.0;

				return settings;
			}

			@Override
			protected void onFinishedDrawing(Image map, boolean anotherDrawIsQueued, int borderWidthAsDrawn,
					Rectangle incrementalChangeArea, List<String> warningMessages)
			{
				mapEditingPanel.setImage(AwtFactory.unwrap(map));

				if (!anotherDrawIsQueued)
				{
					enableOrDisableProgressBar(false);
				}

				NewSettingsDialog.this.revalidate();
				NewSettingsDialog.this.repaint();
			}

			@Override
			protected void onFailedToDraw()
			{
				enableOrDisableProgressBar(false);
			}

			@Override
			protected MapEdits getEdits()
			{
				return settings.edits;
			}

			@Override
			protected Image getCurrentMapForIncrementalUpdate()
			{
				return AwtFactory.wrap(mapEditingPanel.mapFromMapCreator);
			}

		};
		updater.setEnabled(false);
	}

	private nortantis.geom.Dimension getMapDrawingAreaSize()
	{
		final int additionalWidthToRemoveIDontKnowWhereItsCommingFrom = 4;
		return new nortantis.geom.Dimension(
				(mapEditingPanelContainer.getSize().width - additionalWidthToRemoveIDontKnowWhereItsCommingFrom) * mapEditingPanel.osScale,
				(mapEditingPanelContainer.getSize().height - additionalWidthToRemoveIDontKnowWhereItsCommingFrom)
						* mapEditingPanel.osScale);

	}

	private void loadSettingsIntoGUI(MapSettings settings)
	{
		dimensionsComboBox.setSelectedIndex(getDimensionIndexFromDimensions(settings.generatedWidth, settings.generatedHeight));
		worldSizeSlider.setValue(settings.worldSize);
		edgeLandToWaterProbSlider.setValue((int) (settings.edgeLandToWaterProbability * 100));
		centerLandToWaterProbSlider.setValue((int) (settings.centerLandToWaterProbability * 100));
		if (settings.drawRegionColors)
		{
			landColoringMethodComboBox.setSelectedItem(LandColoringMethod.ColorPoliticalRegions);
		}
		else
		{
			landColoringMethodComboBox.setSelectedItem(LandColoringMethod.SingleColor);
		}

		cityFrequencySlider.setValue((int) (settings.cityProbability * cityFrequencySliderScale));
		initializeArtPackOptionsAndCityTypeOptions();
		cityIconsTypeComboBox.setSelectedItem(settings.cityIconTypeName);

		booksWidget.checkSelectedBooks(settings.books);

		updatePathDisplay();
	}

	private MapSettings getSettingsFromGUI()
	{
		MapSettings resultSettings = settings.deepCopy();
		resultSettings.worldSize = worldSizeSlider.getValue();
		resultSettings.edgeLandToWaterProbability = edgeLandToWaterProbSlider.getValue() / 100.0;
		resultSettings.centerLandToWaterProbability = centerLandToWaterProbSlider.getValue() / 100.0;

		Dimension generatedDimensions = getGeneratedBackgroundDimensionsFromGUI();
		resultSettings.generatedWidth = (int) generatedDimensions.getWidth();
		resultSettings.generatedHeight = (int) generatedDimensions.getHeight();

		resultSettings.drawRegionColors = landColoringMethodComboBox.getSelectedItem().equals(LandColoringMethod.ColorPoliticalRegions);

		resultSettings.books = booksWidget.getSelectedBooks();

		resultSettings.cityProbability = cityFrequencySlider.getValue() / cityFrequencySliderScale;
		resultSettings.cityIconTypeName = (String) cityIconsTypeComboBox.getSelectedItem();
		resultSettings.artPack = (String) artPackComboBox.getSelectedItem();

		return resultSettings;
	}

	private Dimension getGeneratedBackgroundDimensionsFromGUI()
	{
		String selected = (String) dimensionsComboBox.getSelectedItem();
		return parseGenerateBackgroundDimensionsFromDropdown(selected);
	}

	public static Dimension parseGenerateBackgroundDimensionsFromDropdown(String selected)
	{
		selected = selected.substring(0, selected.indexOf("("));
		String[] parts = selected.split("x");
		return new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
	}

	private int getDimensionIndexFromDimensions(int generatedWidth, int generatedHeight)
	{
		for (int i : new Range(dimensionsComboBox.getItemCount()))
		{
			Dimension dim = parseGenerateBackgroundDimensionsFromDropdown(dimensionsComboBox.getItemAt(i));
			if (dim.getWidth() == generatedWidth && dim.getHeight() == generatedHeight)
			{
				return i;
			}
		}
		throw new IllegalArgumentException("No dropdown menu option with dimentions " + generatedWidth + " x " + generatedHeight);
	}

	private void enableOrDisableProgressBar(boolean enable)
	{
		if (enable)
		{
			progressBarTimer.start();
		}
		else
		{
			progressBarTimer.stop();
			progressBar.setVisible(false);
		}

	}

	public void createMapChangeListener(Component component)
	{
		SwingHelper.addListener(component, () -> handleMapChange());
	}

	public void handleMapChange()
	{
		nortantis.geom.Dimension size = getMapDrawingAreaSize();
		if (size != null && size.width > 0.0 && size.height > 0.0)
		{
			updater.setMaxMapSize(getMapDrawingAreaSize());
			enableOrDisableProgressBar(true);
			updater.createAndShowMapFull();
		}
	}
}
