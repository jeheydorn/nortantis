package nortantis.swing;

import nortantis.GeneratedDimension;
import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.LandShape;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.geom.IntRectangle;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.ThemePanel.LandColoringMethod;
import nortantis.swing.translation.Translation;
import nortantis.util.*;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

@SuppressWarnings("serial")
public class NewSettingsDialog extends JDialog
{
	JSlider worldSizeSlider;
	private JSpinner customWidthSpinner;
	private JSpinner customHeightSpinner;
	private JLabel customDimPreviewLabel;
	private JPanel customSpinnersPanel;
	private RowHider customDimPreviewHider;
	private JComboBox<LandShape> landShapeComboBox;
	private JSlider regionCountSlider;
	private SliderWithDisplayedValue regionCountSliderWithDisplay;
	private JComboBox<GeneratedDimension> dimensionsComboBox;
	BooksWidget booksWidget;
	MapSettings settings;
	private JProgressBar progressBar;
	private MapUpdater updater;
	private MapEditingPanel mapEditingPanel;
	private Dimension defaultSize = new Dimension(900, 750);
	private int amountToSubtractFromLeftAndRightPanels = 40;
	private Timer progressBarTimer;
	public final double cityFrequencySliderScale = 100.0 / SettingsGenerator.maxCityProbability;
	private JSlider cityFrequencySlider;
	private JComboBox<String> cityIconsTypeComboBox;
	private JPanel mapEditingPanelContainer;
	private JComboBox<LandColoringMethod> landColoringMethodComboBox;
	MainWindow mainWindow;
	private JTextField pathDisplay;
	private JComboBox<String> artPackComboBox;

	public NewSettingsDialog(MainWindow mainWindow, MapSettings settingsToKeepThemeFrom)
	{
		super(mainWindow, Translation.get("newSettingsDialog.title"), Dialog.ModalityType.APPLICATION_MODAL);
		this.mainWindow = mainWindow;

		createGUI(mainWindow);

		if (settingsToKeepThemeFrom == null)
		{
			settings = SettingsGenerator.generate(UserPreferences.getInstance().defaultCustomImagesPath);
		}
		else
		{
			settings = SettingsGenerator.newMapWithSameTheme(settingsToKeepThemeFrom);
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
			JButton randomizeThemeButton = new JButton(Translation.get("newSettingsDialog.randomizeTheme"));
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
			JButton randomizeLandButton = new JButton(Translation.get("newSettingsDialog.randomizeLand"));
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
			JButton flipHorizontallyButton = new JButton(Translation.get("newSettingsDialog.flipHorizontal"));
			flipHorizontallyButton.setToolTipText(Translation.get("newSettingsDialog.flipHorizontal.tooltip"));
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
			JButton flipVerticallyButton = new JButton(Translation.get("newSettingsDialog.flipVertical"));
			flipVerticallyButton.setToolTipText(Translation.get("newSettingsDialog.flipVertical.tooltip"));
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
			JButton rotateButton = new JButton(Translation.get("newSettingsDialog.rotateLeft"));
			rotateButton.setToolTipText(Translation.get("newSettingsDialog.rotateLeft.tooltip"));

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
			JButton rotateButton = new JButton(Translation.get("newSettingsDialog.rotateRight"));
			rotateButton.setToolTipText(Translation.get("newSettingsDialog.rotateRight.tooltip"));
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
		progressBar.setString(Translation.get("newSettingsDialog.drawing"));
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		bottomPanel.add(progressBar);
		bottomPanel.add(Box.createHorizontalGlue());

		JPanel bottomButtonsPanel = new JPanel();
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton createMapButton = new JButton(Translation.get("newSettingsDialog.create"));
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
		// Cancel and disable the dialog's own updater to stop it from submitting new GPU jobs
		updater.cancel();
		updater.setEnabled(false);

		// Get settings before disposing the dialog
		MapSettings settings = getSettingsFromGUI();

		// Dispose the dialog immediately
		dispose();

		// Cancel any current drawing in main window
		mainWindow.updater.cancel();

		// Load settings into main window - this will start a new draw and show the progress bar immediately.
		// GPU operations will be serialized through GPUExecutor.
		mainWindow.clearOpenSettingsFilePath();
		mainWindow.loadSettingsIntoGUI(settings);
	}

	private void createLeftPanel(JPanel generatorSettingsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel leftPanel = organizer.panel;
		generatorSettingsPanel.add(leftPanel);

		dimensionsComboBox = new JComboBox<GeneratedDimension>();
		for (GeneratedDimension dimension : GeneratedDimension.values())
		{
			dimensionsComboBox.addItem(dimension);
		}
		// Show just the short name (e.g. "16 by 9") rather than "4096 × 2304 (16 by 9)" so the
		// combo box stays narrow and leaves room for the custom-ratio spinners without squashing
		// the rest of the left panel.
		dimensionsComboBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof GeneratedDimension)
				{
					setText(((GeneratedDimension) value).displayName());
				}
				return this;
			}
		});
		dimensionsComboBox.setMaximumSize(new Dimension(dimensionsComboBox.getPreferredSize().width, dimensionsComboBox.getPreferredSize().height));

		customWidthSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 32768, 1));
		customHeightSpinner = new JSpinner(new SpinnerNumberModel(9, 1, 32768, 1));
		Dimension spinnerDim = new Dimension(60, customWidthSpinner.getPreferredSize().height);
		customWidthSpinner.setPreferredSize(spinnerDim);
		customWidthSpinner.setMaximumSize(spinnerDim);
		customHeightSpinner.setPreferredSize(spinnerDim);
		customHeightSpinner.setMaximumSize(spinnerDim);

		customDimPreviewLabel = new JLabel();
		customWidthSpinner.addChangeListener(e -> clearMapPreview());
		customHeightSpinner.addChangeListener(e -> clearMapPreview());
		createMapChangeListener(customWidthSpinner);
		createMapChangeListener(customHeightSpinner);
		customWidthSpinner.addChangeListener(e -> updateCustomDimPreview());
		customHeightSpinner.addChangeListener(e -> updateCustomDimPreview());
		updateCustomDimPreview();

		customSpinnersPanel = new JPanel();
		customSpinnersPanel.setLayout(new BoxLayout(customSpinnersPanel, BoxLayout.X_AXIS));
		customSpinnersPanel.add(Box.createHorizontalStrut(5));
		customSpinnersPanel.add(customWidthSpinner);
		customSpinnersPanel.add(new JLabel(" \u00d7 "));
		customSpinnersPanel.add(customHeightSpinner);
		customSpinnersPanel.setVisible(false);

		JPanel dimensionsRowPanel = new JPanel();
		dimensionsRowPanel.setLayout(new BoxLayout(dimensionsRowPanel, BoxLayout.X_AXIS));
		dimensionsRowPanel.add(dimensionsComboBox);
		dimensionsRowPanel.add(customSpinnersPanel);
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.dimensions.label"), Translation.get("newSettingsDialog.dimensions.help"), dimensionsRowPanel);

		// Preview label showing the normalized pixel dimensions — lives in a compact row
		// directly below the dimensions row so it doesn't add width to the inline spinner row.
		customDimPreviewHider = organizer.addLabelAndComponent("", "", customDimPreviewLabel, 2);
		customDimPreviewHider.setVisible(false);

		dimensionsComboBox.addActionListener(e ->
		{
			boolean isCustom = dimensionsComboBox.getSelectedItem() == GeneratedDimension.Any;
			customSpinnersPanel.setVisible(isCustom);
			customDimPreviewHider.setVisible(isCustom);
			clearMapPreview();
			handleMapChange();
		});

		worldSizeSlider = new JSlider();
		worldSizeSlider.setSnapToTicks(true);
		worldSizeSlider.setMajorTickSpacing(8000);
		worldSizeSlider.setMinorTickSpacing(SettingsGenerator.worldSizePrecision);
		worldSizeSlider.setPaintLabels(true);
		worldSizeSlider.setPaintTicks(true);
		worldSizeSlider.setMinimum(SettingsGenerator.minWorldSize);
		worldSizeSlider.setMaximum(SettingsGenerator.maxWorldSize);
		createMapChangeListener(worldSizeSlider);
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.worldSize.label"), Translation.get("newSettingsDialog.worldSize.help"), worldSizeSlider);

		landShapeComboBox = new JComboBox<LandShape>();
		for (LandShape shape : LandShape.values())
		{
			landShapeComboBox.addItem(shape);
		}
		createMapChangeListener(landShapeComboBox);
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.landShape.label"), Translation.get("newSettingsDialog.landShape.help"), landShapeComboBox);

		regionCountSlider = new JSlider();
		regionCountSlider.setMinimum(SettingsGenerator.minRegionCount);
		regionCountSlider.setMaximum(SettingsGenerator.maxRegionCount(SettingsGenerator.maxWorldSize));
		regionCountSlider.setValue(3);
		regionCountSlider.setSnapToTicks(true);
		regionCountSlider.setMajorTickSpacing(1);
		createMapChangeListener(regionCountSlider);
		regionCountSliderWithDisplay = new SliderWithDisplayedValue(regionCountSlider);
		regionCountSliderWithDisplay.addToOrganizer(organizer, Translation.get("newSettingsDialog.regionCount.label"), Translation.get("newSettingsDialog.regionCount.help"));

		// Update region count slider max when world size changes.
		worldSizeSlider.addChangeListener(e ->
		{
			int maxRegions = SettingsGenerator.maxRegionCount(worldSizeSlider.getValue());
			regionCountSlider.setMaximum(maxRegions);
			if (regionCountSlider.getValue() > maxRegions)
			{
				regionCountSlider.setValue(maxRegions);
			}
		});

		landColoringMethodComboBox = new JComboBox<LandColoringMethod>();
		for (LandColoringMethod method : LandColoringMethod.values())
		{
			landColoringMethodComboBox.addItem(method);
		}

		createMapChangeListener(landColoringMethodComboBox);
		organizer.addLabelAndComponent(Translation.get("theme.landColoringMethod.label"), Translation.get("theme.landColoringMethod.help"), landColoringMethodComboBox);

		JButton changeButton = new JButton(Translation.get("newSettingsDialog.change"));
		pathDisplay = new JTextField();
		pathDisplay.setText(FileHelper.replaceHomeFolderPlaceholder(UserPreferences.getInstance().defaultCustomImagesPath));
		pathDisplay.setEditable(false);
		organizer.addLabelAndComponentsHorizontal(Translation.get("newSettingsDialog.customImagesFolder.label"), Translation.get("newSettingsDialog.customImagesFolder.help"),
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

		organizer.addLeftAlignedComponent(Box.createRigidArea(new Dimension((defaultSize.width / 2) - amountToSubtractFromLeftAndRightPanels, 0)));

		organizer.addVerticalFillerRow();
	}

	private void initializeCityTypeOptions()
	{
		SwingHelper.initializeComboBoxItems(cityIconsTypeComboBox,
				ImageCache.getInstance((String) artPackComboBox.getSelectedItem(), (String) settings.customImagesPath).getIconGroupNames(IconType.cities),
				(String) cityIconsTypeComboBox.getSelectedItem(), false);
	}

	private void initializeArtPackOptionsAndCityTypeOptions()
	{
		SwingHelper.initializeComboBoxItems(artPackComboBox, Assets.listArtPacks(!StringUtils.isEmpty(settings.customImagesPath)), settings.artPack, false);
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
		JLabel artPackLabel = new JLabel(Translation.get("newSettingsDialog.artPack.label"));
		artPackLabel.setToolTipText(Translation.get("newSettingsDialog.artPack.help"));
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
		JLabel cityIconTypeLabel = new JLabel(Translation.get("newSettingsDialog.cityIconType.label"));
		cityIconTypeLabel.setToolTipText(Translation.get("newSettingsDialog.cityIconType.help"));

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
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.cityFrequency.label"), Translation.get("newSettingsDialog.cityFrequency.help"), cityFrequencySlider);

		booksWidget = new BooksWidget(true, () -> handleMapChange());
		booksWidget.getContentPanel().setPreferredSize(new Dimension(360, 180));
		organizer.addLeftAlignedComponentWithStackedLabel(Translation.get("newSettingsDialog.booksForText.label"), Translation.get("newSettingsDialog.booksForText.help"),
				booksWidget.getContentPanel());

		organizer.addLeftAlignedComponent(Box.createRigidArea(new Dimension((defaultSize.width / 2) - amountToSubtractFromLeftAndRightPanels, 0)));

		organizer.addVerticalFillerRow();
	}

	private void updateBackgroundTextureAndBorderToUseArtPackIfNeeded()
	{
		String artPack = (String) artPackComboBox.getSelectedItem();
		boolean backgroundTextureNeedsUpdate = settings.backgroundTextureResource != null && !settings.backgroundTextureResource.artPack.equals(artPack);
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
		SettingsGenerator.randomizeTheme(settings, (String) artPackComboBox.getSelectedItem(), settings.customImagesPath);
		handleMapChange();
	}

	private void randomizeLand()
	{
		SettingsGenerator.randomizeLand(settings);
		handleMapChange();
	}

	private void createMapEditingPanel()
	{
		BufferedImage placeHolder = AwtBridge.toBufferedImage(ImageHelper.getInstance().createPlaceholderImage(new String[] { Translation.get("newSettingsDialog.drawing") },
				AwtBridge.fromAwtColor(SwingHelper.getTextColorForPlaceholderImages())));
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
			protected void onFinishedDrawingFull(Image map, boolean anotherDrawIsQueued, int borderWidthAsDrawn, List<String> warningMessages)
			{
				if (mapEditingPanel.mapFromMapCreator != null && mapEditingPanel.mapFromMapCreator != map)
				{
					mapEditingPanel.mapFromMapCreator.close();
				}
				mapEditingPanel.mapFromMapCreator = map;
				onFinishedDrawingCommon(anotherDrawIsQueued);
			}

			@Override
			protected void onFinishedDrawingIncremental(boolean anotherDrawIsQueued, int borderWidthAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
				onFinishedDrawingCommon(anotherDrawIsQueued);
			}

			private void onFinishedDrawingCommon(boolean anotherDrawIsQueued)
			{
				mapEditingPanel.setImage(AwtBridge.toBufferedImage(mapEditingPanel.mapFromMapCreator));

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
				return mapEditingPanel.mapFromMapCreator;
			}

		};
		updater.setEnabled(false);
	}

	private nortantis.geom.Dimension getMapDrawingAreaSize()
	{
		final int additionalWidthToRemoveIDontKnowWhereItsComingFrom = 10;
		return new nortantis.geom.Dimension((mapEditingPanelContainer.getSize().width - additionalWidthToRemoveIDontKnowWhereItsComingFrom) * mapEditingPanel.osScale,
				(mapEditingPanelContainer.getSize().height - additionalWidthToRemoveIDontKnowWhereItsComingFrom) * mapEditingPanel.osScale);

	}

	private void loadSettingsIntoGUI(MapSettings settings)
	{
		GeneratedDimension dim = GeneratedDimension.fromDimensions(settings.generatedWidth, settings.generatedHeight);
		dimensionsComboBox.setSelectedItem(dim);
		if (dim == GeneratedDimension.Any)
		{
			customWidthSpinner.setValue(settings.generatedWidth);
			customHeightSpinner.setValue(settings.generatedHeight);
			updateCustomDimPreview();
		}
		customSpinnersPanel.setVisible(dim == GeneratedDimension.Any);
		customDimPreviewHider.setVisible(dim == GeneratedDimension.Any);
		worldSizeSlider.setValue(settings.worldSize);
		if (settings.landShape != null)
		{
			landShapeComboBox.setSelectedItem(settings.landShape);
		}
		else
		{
			landShapeComboBox.setSelectedItem(LandShape.Continents);
		}
		regionCountSlider.setMaximum(SettingsGenerator.maxRegionCount(settings.worldSize));
		regionCountSlider.setValue(Math.min(settings.regionCount > 0 ? settings.regionCount : 3, regionCountSlider.getMaximum()));
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
		resultSettings.landShape = (LandShape) landShapeComboBox.getSelectedItem();
		resultSettings.regionCount = regionCountSlider.getValue();
		// Derive old probability fields for backwards compatibility.
		switch (resultSettings.landShape)
		{
			case Continents:
				resultSettings.edgeLandToWaterProbability = 0.1;
				resultSettings.centerLandToWaterProbability = 0.75;
				break;
			case Inland_Sea:
				resultSettings.edgeLandToWaterProbability = 0.75;
				resultSettings.centerLandToWaterProbability = 0.1;
				break;
			case Scattered:
				resultSettings.edgeLandToWaterProbability = 0.5;
				resultSettings.centerLandToWaterProbability = 0.5;
				break;
		}

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
		GeneratedDimension selected = (GeneratedDimension) dimensionsComboBox.getSelectedItem();
		if (selected == GeneratedDimension.Any)
		{
			return normalizeCustomDimensions(((Number) customWidthSpinner.getValue()).intValue(),
					((Number) customHeightSpinner.getValue()).intValue());
		}
		return new Dimension(selected.width, selected.height);
	}

	private void clearMapPreview()
	{
		updater.cancel();
		mapEditingPanel.setImage(null);
	}

	private static Dimension normalizeCustomDimensions(int w, int h)
	{
		if (w >= h)
		{
			return new Dimension(4096, Math.max(1, (int) Math.round(4096.0 * h / w)));
		}
		else
		{
			return new Dimension(Math.max(1, (int) Math.round(4096.0 * w / h)), 4096);
		}
	}

	private void updateCustomDimPreview()
	{
		int w = ((Number) customWidthSpinner.getValue()).intValue();
		int h = ((Number) customHeightSpinner.getValue()).intValue();
		Dimension norm = normalizeCustomDimensions(w, h);
		customDimPreviewLabel.setText("(" + (int) norm.getWidth() + " \u00d7 " + (int) norm.getHeight() + ")");
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
