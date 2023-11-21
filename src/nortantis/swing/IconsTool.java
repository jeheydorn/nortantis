package nortantis.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.imgscalr.Scalr.Method;

import nortantis.IconType;
import nortantis.ImageAndMasks;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.MapUpdater;
import nortantis.graph.voronoi.Center;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.Range;
import nortantis.util.Tuple2;
import nortantis.util.Tuple4;

public class IconsTool extends EditorTool
{
	private final boolean showIconPreviewsUsingLandBackground = true;
	private JRadioButton mountainsButton;
	private JRadioButton treesButton;
	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private JRadioButton hillsButton;
	private JRadioButton dunesButton;
	private IconTypeButtons mountainTypes;
	private IconTypeButtons hillTypes;
	private IconTypeButtons duneTypes;
	private IconTypeButtons treeTypes;
	private IconTypeButtons cityButtons;
	private JSlider densitySlider;
	private Random rand;
	private RowHider densityHider;
	private JRadioButton eraseAllButton;
	private JRadioButton citiesButton;
	private RowHider cityTypeHider;
	private JLabel lblCityIconType;
	private final String cityTypeNotSetPlaceholder = "<not set>";
	private RowHider modeHider;
	private JCheckBox onlyUpdateMountainsCheckbox;
	private RowHider onlyUpdateMountainsCheckboxHider;
	private JCheckBox onlyUpdateHillsCheckbox;
	private RowHider onlyUpdateHillsCheckboxHider;
	private JCheckBox onlyUpdateTreesCheckbox;
	private RowHider onlyUpdateTreesCheckboxHider;
	private JCheckBox onlyUpdateDunesCheckbox;
	private RowHider onlyUpdateDunesCheckboxHider;
	private DrawAndEraseModeWidget modeWidget;
	private boolean allowTopsOfIconsToOverlapWater;

	public IconsTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);
		rand = new Random();
	}

	@Override
	public String getToolbarName()
	{
		return "Icons";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get(AssetsPath.getInstallPath(), "internal/Icon tool.png").toString();
	}

	@Override
	public void onBeforeSaving()
	{
	}

	@Override
	public void onSwitchingAway()
	{
	}

	@Override
	protected JPanel createToolOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		// Tools
		{
			ButtonGroup group = new ButtonGroup();
			List<JComponent> radioButtons = new ArrayList<>();

			mountainsButton = new JRadioButton("Mountains");
			group.add(mountainsButton);
			radioButtons.add(mountainsButton);
			mountainsButton.setSelected(true);
			mountainsButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			hillsButton = new JRadioButton("Hills");
			group.add(hillsButton);
			radioButtons.add(hillsButton);
			hillsButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			dunesButton = new JRadioButton("Dunes");
			group.add(dunesButton);
			radioButtons.add(dunesButton);
			dunesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			treesButton = new JRadioButton("Trees");
			group.add(treesButton);
			radioButtons.add(treesButton);
			treesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			citiesButton = new JRadioButton("Cities");
			group.add(citiesButton);
			radioButtons.add(citiesButton);
			citiesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			eraseAllButton = new JRadioButton("Erase All");
			group.add(eraseAllButton);
			radioButtons.add(eraseAllButton);
			eraseAllButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

			organizer.addLabelAndComponentsVertical("Brush:", "", radioButtons);
		}

		modeWidget = new DrawAndEraseModeWidget("Draw using the selected brush", "Erase using the selected brush",
				() -> updateTypePanels());
		modeHider = modeWidget.addToOrganizer(organizer, "Whether to draw or erase using the selected brush type");

		mountainTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.mountains, mountainTypes, null);
		hillTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.hills, hillTypes, null);
		duneTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.sand, duneTypes, null);
		treeTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.trees, treeTypes, null);
		selectDefaultTreesButtion();

		lblCityIconType = new JLabel("<not set>");
		JButton changeButton = new JButton("Change");
		IconsTool thisTool = this;
		changeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				CityTypeChangeDialog dialog = new CityTypeChangeDialog(mainWindow, thisTool, lblCityIconType.getText(),
						mainWindow.customImagesPath);
				dialog.setLocationRelativeTo(toolsPanel);
				dialog.setVisible(true);
			}
		});
		cityTypeHider = organizer.addLabelAndComponentsVertical("City icons type:", "",
				Arrays.asList(lblCityIconType, Box.createVerticalStrut(4), changeButton));

		createOrUpdateRadioButtonsForCities(organizer, null);

		{
			densitySlider = new JSlider(1, 50);
			final int initialValue = 18;
			densitySlider.setValue(initialValue);
			SwingHelper.setSliderWidthForSidePanel(densitySlider);
			JLabel densityDisplay = new JLabel(initialValue + "");
			densityDisplay.setPreferredSize(new Dimension(13, densityDisplay.getPreferredSize().height));
			densitySlider.addChangeListener(new ChangeListener()
			{
				@Override
				public void stateChanged(ChangeEvent e)
				{
					densityDisplay.setText(densitySlider.getValue() + "");
				}
			});
			densityHider = organizer.addLabelAndComponentsHorizontal("Density:", "", Arrays.asList(densitySlider, densityDisplay));
		}

		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		onlyUpdateMountainsCheckbox = new JCheckBox("Only update mountains");
		onlyUpdateMountainsCheckbox.setToolTipText(
				"When checked, mountains will only be drawn over existing mountains, making it easier to change the images used by a group of mountains.");
		onlyUpdateMountainsCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateMountainsCheckbox);

		onlyUpdateHillsCheckbox = new JCheckBox("Only update hills");
		onlyUpdateHillsCheckbox.setToolTipText(
				"When checked, hills will only be drawn over existing hills, making it easier to change the images used by a group of hills.");
		onlyUpdateHillsCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateHillsCheckbox);

		onlyUpdateTreesCheckbox = new JCheckBox("Only update trees");
		onlyUpdateTreesCheckbox.setToolTipText(
				"When checked, trees will only be drawn over existing trees, making it easier to change the images used by a group of trees.");
		onlyUpdateTreesCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateTreesCheckbox);

		onlyUpdateDunesCheckbox = new JCheckBox("Only update dunes");
		onlyUpdateDunesCheckbox.setToolTipText(
				"When checked, dunes will only be drawn over existing dunes, making it easier to change the images used by a group of dunes.");
		onlyUpdateDunesCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateDunesCheckbox);

		mountainsButton.doClick();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.666);
		organizer.addVerticalFillerRow();

		if (!showIconPreviewsUsingLandBackground)
		{
			updateIconTypeButtonPreviewImages(null);
		}

		return toolOptionsPanel;
	}

	/**
	 * Prevents cacti from being the default tree brush
	 */
	private void selectDefaultTreesButtion()
	{
		if (treeTypes.buttons.size() > 1 && treeTypes.buttons.get(0).getText().equals("cacti"))
		{
			treeTypes.buttons.get(1).getRadioButton().setSelected(true);
		}
	}

	private void updateTypePanels()
	{
		modeHider.setVisible(mountainsButton.isSelected() || hillsButton.isSelected() || dunesButton.isSelected()
				|| treesButton.isSelected() || citiesButton.isSelected());

		mountainTypes.hider.setVisible(mountainsButton.isSelected() && modeWidget.isDrawMode());
		hillTypes.hider.setVisible(hillsButton.isSelected() && modeWidget.isDrawMode());
		duneTypes.hider.setVisible(dunesButton.isSelected() && modeWidget.isDrawMode());
		treeTypes.hider.setVisible(treesButton.isSelected() && modeWidget.isDrawMode());
		cityButtons.hider.setVisible(citiesButton.isSelected() && modeWidget.isDrawMode());
		cityTypeHider.setVisible(citiesButton.isSelected() && modeWidget.isDrawMode());
		densityHider.setVisible(treesButton.isSelected() && modeWidget.isDrawMode());
		brushSizeHider.setVisible(!(citiesButton.isSelected() && modeWidget.isDrawMode()));

		onlyUpdateMountainsCheckboxHider.setVisible(mountainsButton.isSelected() && modeWidget.isDrawMode());
		onlyUpdateHillsCheckboxHider.setVisible(hillsButton.isSelected() && modeWidget.isDrawMode());
		onlyUpdateDunesCheckboxHider.setVisible(dunesButton.isSelected() && modeWidget.isDrawMode());
		onlyUpdateTreesCheckboxHider.setVisible(treesButton.isSelected() && modeWidget.isDrawMode());
	}

	private IconTypeButtons createOrUpdateRadioButtonsForIconType(GridBagOrganizer organizer, IconType iconType, IconTypeButtons existing,
			String customImagesPath)
	{
		ButtonGroup group = new ButtonGroup();
		List<RadioButtonWithImage> radioButtons = new ArrayList<>();
		List<String> groupNames = new ArrayList<>(ImageCache.getInstance(customImagesPath).getIconGroupNames(iconType));
		if (iconType == IconType.trees && groupNames.contains("deciduous") && groupNames.contains("pine") && groupNames.contains("cacti"))
		{
			// Force trees used by the generator to sort first
			List<String> temp = new ArrayList<>();
			temp.add("cacti");
			temp.add("deciduous");
			temp.add("pine");
			groupNames.remove("cacti");
			groupNames.remove("deciduous");
			groupNames.remove("pine");
			temp.addAll(groupNames);
			groupNames = temp;
		}
		for (String groupName : groupNames)
		{
			RadioButtonWithImage button = new RadioButtonWithImage(groupName, null);
			group.add(button.getRadioButton());
			radioButtons.add(button);
		}
		if (radioButtons.size() > 0)
		{
			radioButtons.get(0).getRadioButton().setSelected(true);
		}

		if (existing == null)
		{
			JPanel buttonsPanel = new JPanel();
			return new IconTypeButtons(organizer.addLabelAndComponentsVerticalWithComponentPanel("Type:", "", radioButtons, buttonsPanel),
					radioButtons, buttonsPanel);
		}
		else
		{
			existing.buttons = radioButtons;
			GridBagOrganizer.updateComponentsPanelVertical(radioButtons, existing.buttonsPanel);
			return existing;
		}

	}

	@Override
	public void handleImagesRefresh(String customImagesPath)
	{
		mountainTypes = createOrUpdateRadioButtonsForIconType(null, IconType.mountains, mountainTypes, customImagesPath);
		hillTypes = createOrUpdateRadioButtonsForIconType(null, IconType.hills, hillTypes, customImagesPath);
		duneTypes = createOrUpdateRadioButtonsForIconType(null, IconType.sand, duneTypes, customImagesPath);
		treeTypes = createOrUpdateRadioButtonsForIconType(null, IconType.trees, treeTypes, customImagesPath);
		selectDefaultTreesButtion();

		createOrUpdateRadioButtonsForCities(null, customImagesPath);
	}

	private void updateIconTypeButtonPreviewImages(MapSettings settings)
	{
		String customImagesPath = settings == null ? null : settings.customImagesPath;
		updateOneIconTypeButtonPreviewImages(settings, IconType.mountains, mountainTypes, customImagesPath);
		updateOneIconTypeButtonPreviewImages(settings, IconType.hills, hillTypes, customImagesPath);
		updateOneIconTypeButtonPreviewImages(settings, IconType.sand, duneTypes, customImagesPath);
		updateOneIconTypeButtonPreviewImages(settings, IconType.trees, treeTypes, customImagesPath);


		updateCityButtonPreviewImages(settings);
	}

	private void updateCityButtonPreviewImages(MapSettings settings)
	{
		final String cityType = lblCityIconType.getText();
		final List<String> iconNamesWithoutWidthOrExtension = cityButtons.buttons.stream().map((button) -> button.getText())
				.collect(Collectors.toList());
		SwingWorker<List<BufferedImage>, Void> worker = new SwingWorker<>()
		{
			@Override
			protected List<BufferedImage> doInBackground() throws Exception
			{
				List<BufferedImage> previewImages = new ArrayList<>();
				Map<String, Tuple2<ImageAndMasks, Integer>> cityIcons = ImageCache.getInstance(settings.customImagesPath)
						.getIconsWithWidths(IconType.cities, cityType);

				for (String cityIconNameWithoutWidthOrExtension : iconNamesWithoutWidthOrExtension)
				{
					if (!cityIcons.containsKey(cityIconNameWithoutWidthOrExtension))
					{
						throw new IllegalArgumentException(
								"No city icon exists for the button '" + cityIconNameWithoutWidthOrExtension + "'");
					}
					BufferedImage icon = cityIcons.get(cityIconNameWithoutWidthOrExtension).getFirst().image;
					BufferedImage preview = createIconPreview(settings, Collections.singletonList(icon));
					previewImages.add(preview);
				}

				return previewImages;
			}

			@Override
			public void done()
			{
				List<BufferedImage> previewImages;
				try
				{
					previewImages = get();
				}
				catch (InterruptedException | ExecutionException e)
				{
					throw new RuntimeException(e);
				}

				if (!cityType.equals(lblCityIconType.getText()))
				{
					// The user changed the city type while we were updating the city icons from a previous change. Do nothing.
					// The next update will set the icons on the buttons.
					return;
				}

				for (int i : new Range(previewImages.size()))
				{
					cityButtons.buttons.get(i).setImage(previewImages.get(i));
				}
			}
		};

		worker.execute();
	}

	private void updateOneIconTypeButtonPreviewImages(MapSettings settings, IconType iconType, IconTypeButtons buttons,
			String customImagesPath)
	{
		for (RadioButtonWithImage button : buttons.buttons)
		{
			final String buttonText = button.getText();
			SwingWorker<BufferedImage, Void> worker = new SwingWorker<>()
			{
				@Override
				protected BufferedImage doInBackground() throws Exception
				{
					return createIconPreviewForGroup(settings, iconType, buttonText, customImagesPath);
				}

				@Override
				public void done()
				{
					BufferedImage previewImage;
					try
					{
						previewImage = get();
					}
					catch (InterruptedException | ExecutionException e)
					{
						throw new RuntimeException(e);
					}

					button.setImage(previewImage);
				}
			};

			worker.execute();
		}
	}

	private BufferedImage createIconPreviewForGroup(MapSettings settings, IconType iconType, String groupName, String customImagesPath)
	{
		return createIconPreview(settings, ImageCache.getInstance(customImagesPath).loadIconGroup(iconType, groupName));
	}

	private BufferedImage createIconPreview(MapSettings settings, List<BufferedImage> images)
	{
		final int maxRowWidth = 168;
		final int scaledHeight = 30;

		// Find the size needed for the preview
		int rowCount = 1;
		int largestRowWidth = 0;
		{
			int rowWidth = 0;
			for (BufferedImage image : images)
			{
				int scaledWidth = ImageHelper.getWidthWhenScaledByHeight(image, scaledHeight);
				if (rowWidth + scaledWidth > maxRowWidth)
				{
					rowCount++;
					rowWidth = scaledWidth;
				}
				else
				{
					rowWidth += scaledWidth;
				}

				largestRowWidth = Math.max(largestRowWidth, rowWidth);
			}
		}

		// Create the background image for the preview
		final int padding = 9;
		Dimension size = new Dimension(largestRowWidth + (padding * 2), (rowCount * scaledHeight) + (padding * 2));

		BufferedImage previewImage;
		if (showIconPreviewsUsingLandBackground)
		{
			Tuple4<BufferedImage, ImageHelper.ColorifyAlgorithm, BufferedImage, ImageHelper.ColorifyAlgorithm> tuple = ThemePanel
					.createBackgroundImageDisplaysImages(size, settings.backgroundRandomSeed, settings.colorizeOcean, settings.colorizeLand,
							settings.generateBackground, settings.generateBackgroundFromTexture, settings.backgroundTextureImage);
			previewImage = tuple.getThird();
			previewImage = ImageHelper.colorify(previewImage, settings.landColor, tuple.getFourth());
		}
		else
		{
			previewImage = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		}

		previewImage = fadeEdges(previewImage, padding - 2);

		Graphics2D g = previewImage.createGraphics();

		if (!showIconPreviewsUsingLandBackground)
		{
			g.setColor(new Color(152, 152, 152));
			g.fillRect(0, 0, size.width, size.height);
		}

		int x = padding;
		int y = padding;
		for (BufferedImage image : images)
		{
			BufferedImage scaled = ImageHelper.scaleByHeight(image, scaledHeight, Method.ULTRA_QUALITY);
			if (x - padding + scaled.getWidth() > maxRowWidth)
			{
				x = padding;
				y += scaledHeight;
			}

			g.drawImage(scaled, x, y, null);

			x += scaled.getWidth();
		}

		return previewImage;
	}

	private BufferedImage fadeEdges(BufferedImage image, int fadeWidth)
	{
		BufferedImage box = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = box.createGraphics();
		g.setColor(Color.white);
		g.fillRect(fadeWidth, fadeWidth, image.getWidth() - fadeWidth * 2, image.getHeight() - fadeWidth * 2);
		g.dispose();

		// Use convolution to make a hazy background for the text.
		BufferedImage hazyBox = ImageHelper.convolveGrayscale(box, ImageHelper.createGaussianKernel(fadeWidth), true, false);

		return ImageHelper.setAlphaFromMask(image, hazyBox, false);
	}

	private void createOrUpdateRadioButtonsForCities(GridBagOrganizer organizer, String customImagesPath)
	{
		Set<String> cityTypes = ImageCache.getInstance(customImagesPath).getIconGroupNames(IconType.cities);

		String cityType = lblCityIconType.getText();
		List<RadioButtonWithImage> radioButtons = new ArrayList<>();
		ButtonGroup group = new ButtonGroup();

		if (cityType.equals(cityTypeNotSetPlaceholder))
		{

		}
		else if (!cityTypes.contains(cityType))
		{
			// The city type selected for this map does not exist in the icons/cities folder.
		}
		else
		{
			for (String fileNameWithoutWidthOrExtension : ImageCache.getInstance(customImagesPath)
					.getIconGroupFileNamesWithoutWidthOrExtension(IconType.cities, cityType))
			{
				RadioButtonWithImage button = new RadioButtonWithImage(fileNameWithoutWidthOrExtension, null);
				group.add(button.getRadioButton());
				radioButtons.add(button);
			}

			if (radioButtons.size() > 0)
			{
				radioButtons.get(0).getRadioButton().setSelected(true);
			}
		}

		if (cityButtons == null)
		{
			// This is the first time to create the city buttons.
			JPanel buttonsPanel = new JPanel();
			cityButtons = new IconTypeButtons(
					organizer.addLabelAndComponentsVerticalWithComponentPanel("Cities:", "", radioButtons, buttonsPanel), radioButtons,
					buttonsPanel);
		}
		else
		{
			// The city buttons have been created before, so update them.
			cityButtons.buttons = radioButtons;
			GridBagOrganizer.updateComponentsPanelVertical(radioButtons, cityButtons.buttonsPanel);
		}

	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}

	private void handleMousePressOrDrag(MouseEvent e)
	{
		Set<Center> selected = getSelectedLandCenters(e.getPoint());

		if (mountainsButton.isSelected())
		{
			if (modeWidget.isDrawMode())
			{
				String rangeId = mountainTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateMountainsCheckbox.isSelected() && (cEdit.icon == null || cEdit.icon.iconType != CenterIconType.Mountain))
					{
						continue;
					}
					CenterIcon newIcon = new CenterIcon(CenterIconType.Mountain, rangeId, Math.abs(rand.nextInt()));
					cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, newIcon, cEdit.trees);
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Mountain)
					{
						cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, null, cEdit.trees);
					}
				}
			}
		}
		else if (hillsButton.isSelected())
		{
			if (modeWidget.isDrawMode())
			{
				String rangeId = hillTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateHillsCheckbox.isSelected() && (cEdit.icon == null || cEdit.icon.iconType != CenterIconType.Hill))
					{
						continue;
					}
					CenterIcon newIcon = new CenterIcon(CenterIconType.Hill, rangeId, Math.abs(rand.nextInt()));
					cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, newIcon, cEdit.trees);
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Hill)
					{
						cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, null, cEdit.trees);
					}
				}
			}
		}
		else if (dunesButton.isSelected())
		{
			if (modeWidget.isDrawMode())
			{
				String rangeId = duneTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateDunesCheckbox.isSelected() && (cEdit.icon == null || cEdit.icon.iconType != CenterIconType.Dune))
					{
						continue;
					}
					CenterIcon newIcon = new CenterIcon(CenterIconType.Dune, rangeId, Math.abs(rand.nextInt()));
					cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, newIcon, cEdit.trees);
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Dune)
					{
						cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, null, cEdit.trees);
					}
				}
			}
		}
		else if (treesButton.isSelected())
		{
			if (modeWidget.isDrawMode())
			{
				String treeType = treeTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateTreesCheckbox.isSelected() && cEdit.trees == null)
					{
						continue;
					}
					CenterTrees newTrees = new CenterTrees(treeType, densitySlider.getValue() / 10.0, Math.abs(rand.nextLong()));
					cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, cEdit.icon, newTrees);
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, cEdit.icon, null);
				}
			}
		}
		else if (citiesButton.isSelected())
		{
			if (modeWidget.isDrawMode())
			{
				if (cityButtons.buttons.size() == 0)
				{
					return;
				}

				String cityName = cityButtons.getSelectedOption();
				for (Center center : selected)
				{
					CenterIcon cityIcon = new CenterIcon(CenterIconType.City, cityName);
					// Only add the city if it will be drawn. That way, we don't set an undo point for a city that won't draw.
					// Note that other icons types can have this problem, but IconDrawer.removeIconEditsThatFailedToDraw will remove the
					// icon
					// from the edits after the draw. I originally added this fix for cities before creating that method, but I'm leaving it
					// in place to save creating an extra undo point here, although it might not be necessary.
					if (updater.mapParts.iconDrawer.doesCityFitOnLand(center, new CenterIcon(CenterIconType.City, cityName),
							allowTopsOfIconsToOverlapWater))
					{
						CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
						cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, cityIcon, cEdit.trees);
					}
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.City)
					{
						cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, null, cEdit.trees);
					}
				}
			}
		}
		else if (eraseAllButton.isSelected())
		{
			for (Center center : selected)
			{
				eraseIconEdits(center, mainWindow.edits);
			}
		}

		handleMapChange(selected);
	}

	static void eraseIconEdits(Center center, MapEdits edits)
	{
		CenterEdit cEdit = edits.centerEdits.get(center.index);
		cEdit.setValuesWithLock(cEdit.isWater, cEdit.isLake, cEdit.regionId, null, null);
	}

	private Set<Center> getSelectedLandCenters(java.awt.Point point)
	{
		Set<Center> selected = getSelectedCenters(point);
		return selected.stream().filter(c -> !c.isWater).collect(Collectors.toSet());
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		handleMousePressOrDrag(e);
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		undoer.setUndoPoint(UpdateType.Incremental, this);
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		highlightHoverCenters(e);
		mapEditingPanel.repaint();
	}

	private void highlightHoverCenters(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();

		Set<Center> selected = getSelectedCenters(e.getPoint());
		mapEditingPanel.addHighlightedCenters(selected);
		mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		highlightHoverCenters(e);
		handleMousePressOrDrag(e);
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.repaint();
	}

	@Override
	public void onActivate()
	{
	}

	@Override
	protected void onBeforeShowMap()
	{
	}

	@Override
	protected void onAfterUndoRedo()
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.repaint();
	}

	private Set<Center> getSelectedCenters(java.awt.Point pointFromMouse)
	{
		if (citiesButton.isSelected() && modeWidget.isDrawMode())
		{
			// It doesn't make sense to allow drawing cities with a large brush.
			return getSelectedCenters(pointFromMouse, 1);
		}
		return getSelectedCenters(pointFromMouse, brushSizes.get(brushSizeComboBox.getSelectedIndex()));
	}

	private void handleMapChange(Set<Center> centers)
	{
		updater.createAndShowMapIncrementalUsingCenters(centers);
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages)
	{
		boolean isCityTypeChange = !(settings.cityIconTypeName == null ? "" : settings.cityIconTypeName).equals(lblCityIconType.getText());
		lblCityIconType.setText((settings.cityIconTypeName == null ? "" : settings.cityIconTypeName));
		if (isCityTypeChange)
		{
			createOrUpdateRadioButtonsForCities(null, settings.customImagesPath);
		}
		updateTypePanels();
		if (showIconPreviewsUsingLandBackground && (changeEffectsBackgroundImages || isCityTypeChange))
		{
			updateIconTypeButtonPreviewImages(settings);
		}
		allowTopsOfIconsToOverlapWater = settings.allowTopsOfIconsToOverlapWater;
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.cityIconTypeName = lblCityIconType.getText();
	}

	@Override
	public boolean shouldShowTextWhenTextIsEnabled()
	{
		return true;
	}

	public void setCityIconsType(MapSettings settings, String cityIconType)
	{
		if (cityIconType.equals(lblCityIconType.getText()))
		{
			return;
		}

		lblCityIconType.setText(cityIconType == null ? cityTypeNotSetPlaceholder : cityIconType);
		createOrUpdateRadioButtonsForCities(null, settings.customImagesPath);
		updateCityButtonPreviewImages(settings);
		undoer.setUndoPoint(UpdateType.Full, this);
		updater.createAndShowMapFull();
	}

	@Override
	public void handleEnablingAndDisabling(MapSettings settings)
	{
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
	}
}
