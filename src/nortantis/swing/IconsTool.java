package nortantis.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
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
import javax.swing.DefaultFocusManager;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.imgscalr.Scalr.Method;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.EdgeEdit;
import nortantis.editor.MapUpdater;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.Range;
import nortantis.util.Tuple2;
import nortantis.util.Tuple3;
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
	private JRadioButton riversButton;
	private JRadioButton citiesButton;
	private RowHider riverOptionHider;
	private JSlider riverWidthSlider;
	private Corner riverStart;
	private RowHider cityTypeHider;
	private JLabel lblCityIconType;
	private final String cityTypeNotSetPlaceholder = "<not set>";
	private JToggleButton drawModeButton;
	private JToggleButton eraseModeButton;
	private RowHider modeHider;
	private JCheckBox onlyUpdateMountainsCheckbox;
	private RowHider onlyUpdateMountainsCheckboxHider;
	private JCheckBox onlyUpdateHillsCheckbox;
	private RowHider onlyUpdateHillsCheckboxHider;
	private JCheckBox onlyUpdateTreesCheckbox;
	private RowHider onlyUpdateTreesCheckboxHider;
	private JCheckBox onlyUpdateDunesCheckbox;
	private RowHider onlyUpdateDunesCheckboxHider;

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
		mapEditingPanel.setHighlightRivers(false);
	}

	@Override
	protected JPanel createToolsOptionsPanel()
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

			riversButton = new JRadioButton("Rivers");
			group.add(riversButton);
			radioButtons.add(riversButton);
			riversButton.addActionListener(new ActionListener()
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

		ActionListener modeListener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (e != null && e.getSource() == eraseModeButton)
				{
					drawModeButton.setSelected(false);
					if (!eraseModeButton.isSelected())
					{
						eraseModeButton.setSelected(true);
					}
					eraseModeButton.grabFocus();
				}
				else
				{
					// Draw button
					eraseModeButton.setSelected(false);
					if (!drawModeButton.isSelected())
					{
						drawModeButton.setSelected(true);
					}
					drawModeButton.grabFocus();
				}
				updateTypePanels();
			}
		};
		drawModeButton = new JToggleButton("<html><u>D</u>raw</html>");
		drawModeButton.setToolTipText("Draw using the selected brush (Alt+D)");
		drawModeButton.setSelected(true);
		drawModeButton.addActionListener(modeListener);
		eraseModeButton = new JToggleButton("<html><u>E</u>rase</html>");
		eraseModeButton.setToolTipText("Erase using the selected brush (Alt+E)");
		eraseModeButton.addActionListener(modeListener);
		modeHider = organizer.addLabelAndComponentsHorizontal(
				"Mode:", "Whether to draw or erase using the selected brush type.", Arrays.asList(drawModeButton, eraseModeButton), 0, 5
		);

		mountainTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.mountains, mountainTypes);
		hillTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.hills, hillTypes);
		duneTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.sand, duneTypes);
		treeTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.trees, treeTypes);
		selectDefaultTreesButtion();

		lblCityIconType = new JLabel("<not set>");
		JButton changeButton = new JButton("Change");
		IconsTool thisTool = this;
		changeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				CityTypeChangeDialog dialog = new CityTypeChangeDialog(mainWindow, thisTool, lblCityIconType.getText());
				dialog.setLocationRelativeTo(toolsPanel);
				dialog.setVisible(true);
			}
		});
		cityTypeHider = organizer.addLabelAndComponentsVertical(
				"City icons type:", "", Arrays.asList(lblCityIconType, Box.createVerticalStrut(4), changeButton)
		);

		createOrUpdateRadioButtonsForCities(organizer);

		// River options
		{
			riverWidthSlider = new JSlider(1, 15);
			final int initialValue = 1;
			riverWidthSlider.setValue(initialValue);
			SwingHelper.setSliderWidthForSidePanel(riverWidthSlider);
			JLabel riverWidthDisplay = new JLabel(initialValue + "");
			riverWidthDisplay.setPreferredSize(new Dimension(13, riverWidthDisplay.getPreferredSize().height));
			riverWidthSlider.addChangeListener(new ChangeListener()
			{
				@Override
				public void stateChanged(ChangeEvent e)
				{
					riverWidthDisplay.setText(riverWidthSlider.getValue() + "");
				}
			});
			riverOptionHider = organizer.addLabelAndComponentsHorizontal("Width:", "River width to draw. Note that different widths might look the same depending on the resolution the map is drawn at.", Arrays.asList(riverWidthSlider, riverWidthDisplay));
		}

		densitySlider = new JSlider(1, 50);
		densitySlider.setValue(10);
		SwingHelper.setSliderWidthForSidePanel(densitySlider);
		densityHider = organizer.addLabelAndComponent("Density:", "", densitySlider);

		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		onlyUpdateMountainsCheckbox = new JCheckBox("Only update mountains");
		onlyUpdateMountainsCheckbox.setToolTipText("When checked, mountains will only be drawn over existing mountains, making it easier to change the images used by a group of mountains.");
		onlyUpdateMountainsCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateMountainsCheckbox);

		onlyUpdateHillsCheckbox = new JCheckBox("Only update hills");
		onlyUpdateHillsCheckbox.setToolTipText("When checked, hills will only be drawn over existing hills, making it easier to change the images used by a group of hills.");
		onlyUpdateHillsCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateHillsCheckbox);

		onlyUpdateTreesCheckbox = new JCheckBox("Only update trees");
		onlyUpdateTreesCheckbox.setToolTipText("When checked, trees will only be drawn over existing trees, making it easier to change the images used by a group of trees.");
		onlyUpdateTreesCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateTreesCheckbox);

		onlyUpdateDunesCheckbox = new JCheckBox("Only update dunes");
		onlyUpdateDunesCheckbox.setToolTipText("When checked, dunes will only be drawn over existing dunes, making it easier to change the images used by a group of dunes.");
		onlyUpdateDunesCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateDunesCheckbox);

		mountainsButton.doClick();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.666);
		organizer.addVerticalFillerRow();

		setupKeyboardShortcuts();

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

	private void setupKeyboardShortcuts()
	{
		// Using KeyEventDispatcher instead of KeyListener makes the keys work
		// when any component is focused.
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_E) && e.isAltDown())
				{
					eraseModeButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_D) && e.isAltDown())
				{
					drawModeButton.doClick();
				}

				return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
	}

	private void updateTypePanels()
	{
		modeHider.setVisible(
				mountainsButton.isSelected() || hillsButton.isSelected() || dunesButton.isSelected() || treesButton.isSelected()
						|| riversButton.isSelected() || citiesButton.isSelected()
		);

		mountainTypes.hider.setVisible(mountainsButton.isSelected() && drawModeButton.isSelected());
		hillTypes.hider.setVisible(hillsButton.isSelected() && drawModeButton.isSelected());
		duneTypes.hider.setVisible(dunesButton.isSelected() && drawModeButton.isSelected());
		treeTypes.hider.setVisible(treesButton.isSelected() && drawModeButton.isSelected());
		cityButtons.hider.setVisible(citiesButton.isSelected() && drawModeButton.isSelected());
		cityTypeHider.setVisible(citiesButton.isSelected() && drawModeButton.isSelected());
		densityHider.setVisible(treesButton.isSelected() && drawModeButton.isSelected());
		riverOptionHider.setVisible(riversButton.isSelected() && drawModeButton.isSelected());
		brushSizeHider.setVisible(
				!(riversButton.isSelected() && drawModeButton.isSelected()) && !(citiesButton.isSelected() && drawModeButton.isSelected())
		);

		onlyUpdateMountainsCheckboxHider.setVisible(mountainsButton.isSelected() && drawModeButton.isSelected());
		onlyUpdateHillsCheckboxHider.setVisible(hillsButton.isSelected() && drawModeButton.isSelected());
		onlyUpdateDunesCheckboxHider.setVisible(dunesButton.isSelected() && drawModeButton.isSelected());
		onlyUpdateTreesCheckboxHider.setVisible(treesButton.isSelected() && drawModeButton.isSelected());
	}

	private IconTypeButtons createOrUpdateRadioButtonsForIconType(GridBagOrganizer organizer, IconType iconType, IconTypeButtons existing)
	{
		ButtonGroup group = new ButtonGroup();
		List<RadioButtonWithImage> radioButtons = new ArrayList<>();
		for (String groupName : ImageCache.getInstance().getIconGroupNames(iconType))
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
			return new IconTypeButtons(
					organizer.addLabelAndComponentsVerticalWithComponentPanel("Type:", "", radioButtons, buttonsPanel), radioButtons,
					buttonsPanel
			);
		}
		else
		{
			existing.buttons = radioButtons;
			GridBagOrganizer.updateComponentsPanelVertical(radioButtons, existing.buttonsPanel);
			return existing;
		}

	}

	@Override
	public void handleImagesRefresh()
	{
		mountainTypes = createOrUpdateRadioButtonsForIconType(null, IconType.mountains, mountainTypes);
		hillTypes = createOrUpdateRadioButtonsForIconType(null, IconType.hills, hillTypes);
		duneTypes = createOrUpdateRadioButtonsForIconType(null, IconType.sand, duneTypes);
		treeTypes = createOrUpdateRadioButtonsForIconType(null, IconType.trees, treeTypes);
		selectDefaultTreesButtion();

		createOrUpdateRadioButtonsForCities(null);
	}

	private void updateIconTypeButtonPreviewImages(MapSettings settings)
	{
		updateOneIconTypeButtonPreviewImages(settings, IconType.mountains, mountainTypes);
		updateOneIconTypeButtonPreviewImages(settings, IconType.hills, hillTypes);
		updateOneIconTypeButtonPreviewImages(settings, IconType.sand, duneTypes);
		updateOneIconTypeButtonPreviewImages(settings, IconType.trees, treeTypes);

		
		updateCityButtonPreviewImages(settings);
	}

	private void updateCityButtonPreviewImages(MapSettings settings)
	{
		String cityType = lblCityIconType.getText();
		SwingWorker<List<BufferedImage>, Void> worker = new SwingWorker<>()
		{
			@Override
			protected List<BufferedImage> doInBackground() throws Exception
			{
				List<BufferedImage> previewImages = new ArrayList<>();
				Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> cityIcons = ImageCache.getInstance()
						.getIconsWithWidths(IconType.cities, cityType);

				for (RadioButtonWithImage button : cityButtons.buttons)
				{
					String cityIconNameWithoutWidthOrExtension = button.getText();
					if (!cityIcons.containsKey(cityIconNameWithoutWidthOrExtension))
					{
						throw new IllegalArgumentException(
								"No city icon exists for the button '" + cityIconNameWithoutWidthOrExtension + "'"
						);
					}
					BufferedImage icon = cityIcons.get(cityIconNameWithoutWidthOrExtension).getFirst();
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

				for (int i : new Range(previewImages.size()))
				{
					cityButtons.buttons.get(i).setImage(previewImages.get(i));
				}
			}
		};

		worker.execute();
	}

	private void updateOneIconTypeButtonPreviewImages(MapSettings settings, IconType iconType, IconTypeButtons buttons)
	{
		for (RadioButtonWithImage button : buttons.buttons)
		{
			SwingWorker<BufferedImage, Void> worker = new SwingWorker<>()
			{
				@Override
				protected BufferedImage doInBackground() throws Exception
				{
					return createIconPreviewForGroup(settings, iconType, button.getText());
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

	private BufferedImage createIconPreviewForGroup(MapSettings settings, IconType iconType, String groupName)
	{
		return createIconPreview(settings, ImageCache.getInstance().loadIconGroup(iconType, groupName));
	}

	private BufferedImage createIconPreview(MapSettings settings, List<BufferedImage> images)
	{
		final int maxRowWidth = 154;
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
					.createBackgroundImageDisplaysImages(
							size, settings.backgroundRandomSeed, settings.colorizeOcean, settings.colorizeLand, settings.generateBackground,
							settings.generateBackgroundFromTexture, settings.backgroundTextureImage
					);
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

	private void createOrUpdateRadioButtonsForCities(GridBagOrganizer organizer)
	{
		Set<String> cityTypes = ImageCache.getInstance().getIconGroupNames(IconType.cities);

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
			for (String fileNameWithoutWidthOrExtension : ImageCache.getInstance()
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
					buttonsPanel
			);
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
		if (riversButton.isSelected())
		{
			if (drawModeButton.isSelected())
			{
				return;
			}
			else
			{
				// When deleting rivers with the single-point brush size,
				// highlight the closest edge instead of a polygon.
				Set<Edge> possibleRivers = getSelectedEdges(e.getPoint(), brushSizes.get(brushSizeComboBox.getSelectedIndex()));
				for (Edge edge : possibleRivers)
				{
					EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
					eEdit.riverLevel = 0;
				}
				mapEditingPanel.clearHighlightedEdges();
			}
		}

		Set<Center> selected = getSelectedLandCenters(e.getPoint());

		if (mountainsButton.isSelected())
		{
			if (drawModeButton.isSelected())
			{
				String rangeId = mountainTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateMountainsCheckbox.isSelected() && (cEdit.icon == null || cEdit.icon.iconType != CenterIconType.Mountain))
					{
						continue;
					}
					cEdit.icon = new CenterIcon(CenterIconType.Mountain, rangeId, Math.abs(rand.nextInt()));
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Mountain)
					{
						cEdit.icon = null;
					}
				}
			}
		}
		else if (hillsButton.isSelected())
		{
			if (drawModeButton.isSelected())
			{
				String rangeId = hillTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateHillsCheckbox.isSelected() && (cEdit.icon == null || cEdit.icon.iconType != CenterIconType.Hill))
					{
						continue;
					}
					cEdit.icon = new CenterIcon(CenterIconType.Hill, rangeId, Math.abs(rand.nextInt()));
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Hill)
					{
						cEdit.icon = null;
					}
				}
			}
		}
		else if (dunesButton.isSelected())
		{
			if (drawModeButton.isSelected())
			{
				String rangeId = duneTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateDunesCheckbox.isSelected() && (cEdit.icon == null || cEdit.icon.iconType != CenterIconType.Dune))
					{
						continue;
					}
					cEdit.icon = new CenterIcon(CenterIconType.Dune, rangeId, Math.abs(rand.nextInt()));
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Dune)
					{
						cEdit.icon = null;
					}
				}
			}
		}
		else if (treesButton.isSelected())
		{
			if (drawModeButton.isSelected())
			{
				String treeType = treeTypes.getSelectedOption();
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateTreesCheckbox.isSelected() && cEdit.trees == null)
					{
						continue;
					}
					cEdit.trees = new CenterTrees(treeType, densitySlider.getValue() / 10.0, Math.abs(rand.nextLong()));
				}
			}
			else
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					cEdit.trees = null;
				}
			}
		}
		else if (citiesButton.isSelected())
		{
			if (drawModeButton.isSelected())
			{
				if (cityButtons.buttons.size() == 0)
				{
					return;
				}

				String cityName = cityButtons.getSelectedOption();
				for (Center center : selected)
				{
					CenterIcon cityIcon = new CenterIcon(CenterIconType.City, cityName);
					// Only add the city if it will be drawn. That way, if
					// somebody
					// later shrinks the city image or swaps out
					// the image files, previously hidden cities don't start
					// popping
					// up along coastlines and lakes.
					// Note that all icons can fail to draw because they would
					// overlap an ocean or lake, but I don't think it's
					// a big deal for other icon types.
					if (updater.mapParts.iconDrawer.doesCityFitOnLand(center, new CenterIcon(CenterIconType.City, cityName)))
					{
						mainWindow.edits.centerEdits.get(center.index).icon = cityIcon;
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
						cEdit.icon = null;
					}
				}
			}
		}
		else if (eraseAllButton.isSelected())
		{
			for (Center center : selected)
			{
				eraseIconAndTreeEdits(center, mainWindow.edits);
			}
		}

		handleMapChange(selected);
	}

	static void eraseIconAndTreeEdits(Center center, MapEdits edits)
	{
		edits.centerEdits.get(center.index).trees = null;
		edits.centerEdits.get(center.index).icon = null;
		for (Edge edge : center.borders)
		{
			EdgeEdit eEdit = edits.edgeEdits.get(edge.index);
			if (eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
			{
				eEdit.riverLevel = 0;
			}
		}
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

		if (riversButton.isSelected() && drawModeButton.isSelected())
		{
			riverStart = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
		}
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (riversButton.isSelected() && drawModeButton.isSelected())
		{
			Corner end = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
			Set<Edge> river = filterOutOceanAndCoastEdges(updater.mapParts.graph.findPathGreedy(riverStart, end));
			for (Edge edge : river)
			{
				int base = (riverWidthSlider.getValue() - 1);
				int riverLevel = (base * base * 2) + VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn + 1;
				mainWindow.edits.edgeEdits.get(edge.index).riverLevel = riverLevel;
			}
			riverStart = null;
			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.repaint();

			if (river.size() > 0)
			{
				updater.createAndShowMapIncrementalUsingEdges(river);
			}
		}

		undoer.setUndoPoint(UpdateType.Incremental, this);
	}

	private Set<Edge> filterOutOceanAndCoastEdges(Set<Edge> edges)
	{
		return edges.stream().filter(e -> (e.d0 == null || !e.d0.isWater) && (e.d1 == null || !e.d1.isWater)).collect(Collectors.toSet());
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		if (!(riversButton.isSelected() && drawModeButton.isSelected()))
		{
			highlightHoverCentersOrEdgesAndBrush(e);
			mapEditingPanel.repaint();
		}
	}

	private void highlightHoverCentersOrEdgesAndBrush(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.hideBrush();
		if (riversButton.isSelected() && eraseModeButton.isSelected())
		{
			int brushDiameter = brushSizes.get(brushSizeComboBox.getSelectedIndex());
			if (brushDiameter > 1)
			{
				mapEditingPanel.showBrush(e.getPoint(), brushDiameter);
			}
			Set<Edge> candidates = getSelectedEdges(e.getPoint(), brushDiameter);
			for (Edge edge : candidates)
			{
				EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
				if (eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
				{
					mapEditingPanel.addHighlightedEdge(edge);
				}
			}
		}
		else
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			mapEditingPanel.addHighlightedCenters(selected);
			mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
		}
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (riversButton.isSelected() && drawModeButton.isSelected())
		{
			if (riverStart != null)
			{
				mapEditingPanel.clearHighlightedEdges();
				Corner end = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
				Set<Edge> river = filterOutOceanAndCoastEdges(updater.mapParts.graph.findPathGreedy(riverStart, end));
				mapEditingPanel.addHighlightedEdges(river);
				mapEditingPanel.repaint();
			}
		}
		else
		{
			highlightHoverCentersOrEdgesAndBrush(e);
			handleMousePressOrDrag(e);
		}
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.hideBrush();
		if (riversButton.isSelected() && eraseModeButton.isSelected())
		{
			mapEditingPanel.clearHighlightedEdges();
		}
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
		if (citiesButton.isSelected() && drawModeButton.isSelected())
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
		boolean isCityTypeChange = !settings.cityIconTypeName.equals(lblCityIconType.getText());
		lblCityIconType.setText(settings.cityIconTypeName);
		if (isCityTypeChange)
		{
			createOrUpdateRadioButtonsForCities(null);
		}
		updateTypePanels();
		if (showIconPreviewsUsingLandBackground && changeEffectsBackgroundImages)
		{
			updateIconTypeButtonPreviewImages(settings);
		}
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.cityIconTypeName = lblCityIconType.getText();
	}

	@Override
	public boolean shouldShowTextWhenTextIsEnabled()
	{
		return false;
	}

	public void setCityIconsType(MapSettings settings, String cityIconType)
	{
		if (cityIconType.equals(lblCityIconType.getText()))
		{
			return;
		}

		lblCityIconType.setText(cityIconType == null ? cityTypeNotSetPlaceholder : cityIconType);
		createOrUpdateRadioButtonsForCities(null);
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
