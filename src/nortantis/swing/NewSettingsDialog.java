package nortantis.swing;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.geom.IntRectangle;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.ThemePanel.LandColoringMethod;
import nortantis.swing.translation.TranslatedEnumRenderer;
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
	JSlider edgeLandToWaterProbSlider;
	JSlider centerLandToWaterProbSlider;
	private JComboBox<String> dimensionsComboBox;
	BooksWidget booksWidget;
	MapSettings settings;
	private JProgressBar progressBar;
	private MapUpdater updater;
	private MapEditingPanel mapEditingPanel;
	private Dimension defaultSize = new Dimension(975, 750);
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

		dimensionsComboBox = new JComboBox<>();
		for (String dimension : SettingsGenerator.getAllowedDimensions())
		{
			dimensionsComboBox.addItem(dimension);
		}
		createMapChangeListener(dimensionsComboBox);
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.dimensions.label"), Translation.get("newSettingsDialog.dimensions.help"), dimensionsComboBox);

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

		edgeLandToWaterProbSlider = new JSlider();
		edgeLandToWaterProbSlider.setValue(70);
		edgeLandToWaterProbSlider.setPaintTicks(true);
		edgeLandToWaterProbSlider.setPaintLabels(true);
		edgeLandToWaterProbSlider.setMinorTickSpacing(25);
		edgeLandToWaterProbSlider.setMajorTickSpacing(25);
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = edgeLandToWaterProbSlider.getMinimum(); i < edgeLandToWaterProbSlider.getMaximum() + 1; i += edgeLandToWaterProbSlider.getMajorTickSpacing())
			{
				labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
			}
			edgeLandToWaterProbSlider.setLabelTable(labelTable);
		}
		createMapChangeListener(edgeLandToWaterProbSlider);
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.edgeLandProbability.label"), Translation.get("newSettingsDialog.edgeLandProbability.help"), edgeLandToWaterProbSlider);

		centerLandToWaterProbSlider = new JSlider();
		centerLandToWaterProbSlider.setValue(70);
		centerLandToWaterProbSlider.setPaintTicks(true);
		centerLandToWaterProbSlider.setPaintLabels(true);
		centerLandToWaterProbSlider.setMinorTickSpacing(25);
		centerLandToWaterProbSlider.setMajorTickSpacing(25);
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = centerLandToWaterProbSlider.getMinimum(); i < centerLandToWaterProbSlider.getMaximum() + 1; i += centerLandToWaterProbSlider.getMajorTickSpacing())
			{
				labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
			}
			centerLandToWaterProbSlider.setLabelTable(labelTable);
		}
		createMapChangeListener(centerLandToWaterProbSlider);
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.centerLandProbability.label"), Translation.get("newSettingsDialog.centerLandProbability.help"), centerLandToWaterProbSlider);

		landColoringMethodComboBox = new JComboBox<LandColoringMethod>();
		for (LandColoringMethod method : LandColoringMethod.values())
		{
			landColoringMethodComboBox.addItem(method);
		}

		landColoringMethodComboBox.setRenderer(new TranslatedEnumRenderer());
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
		final int additionalWidthToRemoveIDontKnowWhereItsComingFrom = 4;
		return new nortantis.geom.Dimension((mapEditingPanelContainer.getSize().width - additionalWidthToRemoveIDontKnowWhereItsComingFrom) * mapEditingPanel.osScale,
				(mapEditingPanelContainer.getSize().height - additionalWidthToRemoveIDontKnowWhereItsComingFrom) * mapEditingPanel.osScale);

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
		selected = selected.substring(0, selected.indexOf('('));
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
		throw new IllegalArgumentException("No dropdown menu option with dimensions " + generatedWidth + " x " + generatedHeight);
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
