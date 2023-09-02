package nortantis.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultFocusManager;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.Timer;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.editor.MapUpdater;
import nortantis.graph.geom.Rectangle;
import nortantis.swing.ThemePanel.LandColoringMethod;
import nortantis.util.ImageHelper;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class NewSettingsDialog extends JDialog
{
	JSlider worldSizeSlider;
	JSlider edgeLandToWaterProbSlider;
	JSlider centerLandToWaterProbSlider;
	private JComboBox<String> dimensionsComboBox;
	JPanel booksPanel;
	MapSettings settings;
	private JProgressBar progressBar;
	private MapUpdater updater;
	private MapEditingPanel mapEditingPanel;
	private Dimension defaultSize = new Dimension(800, 700);
	private Timer progressBarTimer;
	public final double cityFrequencySliderScale = 100.0 * 1.0 / SettingsGenerator.maxCityProbabillity;
	private JSlider cityFrequencySlider;
	private JComboBox<String> cityIconsSetComboBox;
	private JPanel mapEditingPanelContainer;
	private JComboBox<LandColoringMethod> landColoringMethodComboBox;

	public NewSettingsDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Create New Map", Dialog.ModalityType.APPLICATION_MODAL);

		createGUI(mainWindow);

		settings = SettingsGenerator.generate();
		loadSettingsIntoGUI(settings);

		updater.setEnabled(true);
		updater.createAndShowMapFull();
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

		JPanel randomizePanel = new JPanel();
		randomizePanel.setLayout(new BoxLayout(randomizePanel, BoxLayout.X_AXIS));
		randomizePanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		JButton randomizeThemeButton = new JButton("Randomize Theme");
		randomizeThemeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				randomizeTheme();
			}
		});
		randomizePanel.add(randomizeThemeButton);
		randomizePanel.add(Box.createHorizontalStrut(5));

		JButton randomizeLandButton = new JButton("Randomize Land");
		randomizeLandButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				randomizeLand();
			}
		});
		randomizePanel.add(randomizeLandButton);
		organizer.addLeftAlignedComponent(randomizePanel, 0, 0, false);

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
				updater.setMaxMapSize(getMapDrawingAreaSize());
				handleMapChange();
			}
		});

		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_R) && e.isAltDown())
				{
					createMapButton.doClick();
				}
				return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);

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
		organizer.addLabelAndComponentToPanel("Dimensions: <br>(cannot be changed in editor)",
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
		organizer.addLabelAndComponentToPanel("World size: <br>(cannot be changed in editor)",
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
		organizer.addLabelAndComponentToPanel("Edge land probability:",
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
		organizer.addLabelAndComponentToPanel("Center land probability:",
				"The probability that a tectonic plate not touching the edge of the map will be land rather than ocean.",
				centerLandToWaterProbSlider);

		landColoringMethodComboBox = new JComboBox<LandColoringMethod>();
		for (LandColoringMethod method : LandColoringMethod.values())
		{
			landColoringMethodComboBox.addItem(method);
		}

		createMapChangeListener(landColoringMethodComboBox);
		organizer.addLabelAndComponentToPanel("Land coloring method:", "How to color the land.", landColoringMethodComboBox);

		organizer.addVerticalFillerRow();
	}

	private void createRightPanel(JPanel generatorSettingsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel rightPanel = organizer.panel;
		generatorSettingsPanel.add(rightPanel);

		cityFrequencySlider = new JSlider();
		cityFrequencySlider.setPaintLabels(true);
		cityFrequencySlider.setSnapToTicks(false);
		cityFrequencySlider.setPaintTicks(true);
		cityFrequencySlider.setMinorTickSpacing(10);
		cityFrequencySlider.setMinimum(0);
		cityFrequencySlider.setMaximum(100);
		cityFrequencySlider.setMajorTickSpacing(25);
		createMapChangeListener(cityFrequencySlider);
		organizer.addLabelAndComponentToPanel("City frequency:",
				"Higher values create more cities. Lower values create less cities. Zero means no cities.", cityFrequencySlider);

		cityIconsSetComboBox = new JComboBox<String>();
		createMapChangeListener(cityIconsSetComboBox);
		organizer.addLabelAndComponentToPanel("City icon type:",
				"Higher values create more cities. Lower values create less cities. Zero means no cities.", cityIconsSetComboBox);

		booksPanel = SwingHelper.createBooksPanel(() -> handleMapChange());
		JScrollPane booksScrollPane = new JScrollPane(booksPanel);
		booksScrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		Dimension size = new Dimension(360, 130);
		booksScrollPane.setPreferredSize(size);
		organizer.addLeftAlignedComponentWithStackedLabel("Books for generating text:",
				"Selected books will be used to generate new names.", booksScrollPane);

		organizer.addVerticalFillerRow();
	}

	private void randomizeTheme()
	{
		MapSettings randomSettings = SettingsGenerator.generate();
		settings.coastShadingLevel = randomSettings.coastShadingLevel;
		settings.oceanEffectsLevel = randomSettings.oceanEffectsLevel;
		settings.concentricWaveCount = randomSettings.concentricWaveCount;
		settings.oceanEffect = randomSettings.oceanEffect;
		settings.riverColor = randomSettings.riverColor;
		settings.roadColor = randomSettings.roadColor;
		settings.coastShadingColor = randomSettings.coastShadingColor;
		settings.oceanEffectsColor = randomSettings.oceanEffectsColor;
		settings.coastlineColor = randomSettings.coastlineColor;
		settings.frayedBorder = randomSettings.frayedBorder;
		settings.frayedBorderSize = randomSettings.frayedBorderSize;
		settings.frayedBorderColor = randomSettings.frayedBorderColor;
		settings.frayedBorderBlurLevel = randomSettings.frayedBorderBlurLevel;
		settings.grungeWidth = randomSettings.grungeWidth;
		settings.generateBackground = randomSettings.generateBackground;
		settings.generateBackgroundFromTexture = randomSettings.generateBackgroundFromTexture;
		settings.colorizeOcean = randomSettings.colorizeOcean;
		settings.colorizeLand = randomSettings.colorizeLand;
		settings.backgroundTextureImage = randomSettings.backgroundTextureImage;
		settings.backgroundRandomSeed = randomSettings.backgroundRandomSeed;
		settings.oceanColor = randomSettings.oceanColor;
		settings.landColor = randomSettings.landColor;
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
		settings.drawRegionColors = randomSettings.drawRegionColors;
		settings.regionsRandomSeed = randomSettings.regionsRandomSeed;
		settings.drawBorder = randomSettings.drawBorder;
		settings.borderType = randomSettings.borderType;
		settings.borderWidth = randomSettings.borderWidth;
		settings.lineStyle = randomSettings.lineStyle;

		handleMapChange();
	}

	private void randomizeLand()
	{
		MapSettings randomSettings = SettingsGenerator.generate();
		settings.randomSeed = randomSettings.randomSeed;
		handleMapChange();
	}

	private void createMapEditingPanel()
	{
		BufferedImage placeHolder = ImageHelper.createPlaceholderImage(new String[] { "Drawing..." });
		mapEditingPanel = new MapEditingPanel(placeHolder);

		mapEditingPanelContainer = new JPanel();
		mapEditingPanelContainer.setLayout(new FlowLayout(FlowLayout.CENTER));
		mapEditingPanelContainer.add(mapEditingPanel);
	}

	private void createMapUpdater()
	{
		final NewSettingsDialog thisDialog = this;
		updater = new MapUpdater(false)
		{

			@Override
			protected void onBeginDraw()
			{
			}

			@Override
			protected MapSettings getSettingsFromGUI()
			{
				MapSettings settings = thisDialog.getSettingsFromGUI();

				// This is only the maximum size because I'm passing in
				// maxDimensions to MapCreator.create.
				settings.resolution = 1.0;

				return settings;
			}

			@Override
			protected void onFinishedDrawing(BufferedImage map, boolean anotherDrawIsQueued, int borderWidthAsDrawn,
					Rectangle incrementalChangeArea)
			{
				mapEditingPanel.image = map;

				if (!anotherDrawIsQueued)
				{
					enableOrDisableProgressBar(false);
				}

				mapEditingPanel.revalidate();
				mapEditingPanel.repaint();
				thisDialog.revalidate();
				thisDialog.repaint();
			}

			@Override
			protected void onFailedToDraw()
			{
				enableOrDisableProgressBar(false);
				mapEditingPanel.clearSelectedCenters();
			}

			@Override
			protected MapEdits getEdits()
			{
				assert settings.edits.isEmpty();
				return settings.edits;
			}

			@Override
			protected BufferedImage getCurrentMapForIncrementalUpdate()
			{
				return mapEditingPanel.mapFromMapCreator;
			}

		};
		updater.setMaxMapSize(getMapDrawingAreaSize());
		updater.setEnabled(false);
	}

	private Dimension getMapDrawingAreaSize()
	{
		final int additionalWidthToRemoveIDontKnowWhereItsCommingFrom = 4;
		return new Dimension(mapEditingPanelContainer.getSize().width - additionalWidthToRemoveIDontKnowWhereItsCommingFrom,
				mapEditingPanelContainer.getSize().height - additionalWidthToRemoveIDontKnowWhereItsCommingFrom);

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
		SwingHelper.initializeComboBoxItems(cityIconsSetComboBox, ImageCache.getInstance().getIconSets(IconType.cities),
				settings.cityIconSetName);

		SwingHelper.checkSelectedBooks(booksPanel, settings.books);
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

		resultSettings.books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				if (checkBox.isSelected())
					resultSettings.books.add(checkBox.getText());
			}
		}

		resultSettings.cityProbability = cityFrequencySlider.getValue() / cityFrequencySliderScale;
		resultSettings.cityIconSetName = (String) cityIconsSetComboBox.getSelectedItem();

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
		enableOrDisableProgressBar(true);
		updater.createAndShowMapFull();
	}

}
