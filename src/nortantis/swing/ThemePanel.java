package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import nortantis.BackgroundGenerator;
import nortantis.BorderColorOption;
import nortantis.BorderPosition;
import nortantis.FractalBGGenerator;
import nortantis.FreeIconCollection;
import nortantis.IconDrawer;
import nortantis.IconType;
import nortantis.ImageAndMasks;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.NamedResource;
import nortantis.SettingsGenerator;
import nortantis.Stroke;
import nortantis.StrokeType;
import nortantis.TextureSource;
import nortantis.WorldGraph;
import nortantis.editor.CenterEdit;
import nortantis.editor.CenterTrees;
import nortantis.editor.FreeIcon;
import nortantis.editor.UserPreferences;
import nortantis.geom.IntDimension;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.util.ComparableCounter;
import nortantis.util.Counter;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.ListMap;
import nortantis.util.Tuple2;
import nortantis.util.Tuple2Comp;
import nortantis.util.Tuple4;

@SuppressWarnings("serial")
public class ThemePanel extends JTabbedPane
{
	private MainWindow mainWindow;
	private JSlider coastShadingSlider;
	private JSlider rippleWavesLevelSlider;
	private JSlider concentricWavesLevelSlider;
	private JRadioButton ripplesRadioButton;
	private JRadioButton noneRadioButton;
	private JRadioButton concentricWavesButton;
	private JPanel coastShadingColorDisplay;
	private JPanel coastlineColorDisplay;
	private JSlider coastShadingTransparencySlider;
	private RowHider coastShadingTransparencyHider;
	private JPanel oceanWavesColorDisplay;
	private JPanel riverColorDisplay;
	private JCheckBox enableTextCheckBox;
	private JPanel grungeColorDisplay;
	private JTextField backgroundSeedTextField;
	private JRadioButton rdbtnGeneratedFromTexture;
	private JRadioButton rdbtnFractal;
	private BGColorPreviewPanel oceanDisplayPanel;
	private BGColorPreviewPanel landDisplayPanel;
	private ActionListener backgroundImageButtonGroupListener;
	private Dimension backgroundDisplaySize = new Dimension(150, 110);
	private JComboBox<LandColoringMethod> landColoringMethodComboBox;
	private JSlider grungeSlider;
	private JTextField textureImageFilename;
	private JCheckBox colorizeLandCheckbox;
	private JCheckBox colorizeOceanCheckbox;
	private JButton btnChooseLandColor;
	private JButton btnChooseOceanColor;
	private JButton btnNewBackgroundSeed;
	private ItemListener colorizeCheckboxListener;
	private JComboBox<NamedResource> borderTypeComboBox;
	private JSlider borderWidthSlider;
	private JCheckBox drawBorderCheckbox;
	private JSlider frayedEdgeSizeSlider;
	private JSlider frayedEdgeShadingSlider;
	private JCheckBox frayedEdgeCheckbox;
	private JButton btnChooseCoastShadingColor;
	private JRadioButton jaggedLinesButton;
	private JRadioButton splinesLinesButton;
	private JRadioButton splinesWithSmoothedCoastlinesButton;
	private ActionListener oceanEffectsListener;
	private JLabel titleFontDisplay;
	private JLabel regionFontDisplay;
	private JLabel mountainRangeFontDisplay;
	private JLabel otherMountainsFontDisplay;
	private JLabel riverFontDisplay;
	private JPanel textColorDisplay;
	private JPanel boldBackgroundColorDisplay;
	private JCheckBox drawBoldBackgroundCheckbox;
	private RowHider textureImageHider;
	private RowHider colorizeOceanCheckboxHider;
	private RowHider colorizeLandCheckboxHider;
	private RowHider landColorHider;
	private JButton btnChooseBoldBackgroundColor;
	private JButton btnTitleFont;
	private JButton btnRegionFont;
	private JButton btnMountainRangeFont;
	private JButton btnOtherMountainsFont;
	private JButton btnRiverFont;
	private JButton btnChooseTextColor;
	private ActionListener enableTextCheckboxActionListener;
	private ActionListener frayedEdgeCheckboxActionListener;
	private RowHider coastShadingColorHider;
	private RowHider coastShadingColorDisabledMessageHider;
	private JCheckBox drawGrungeCheckbox;
	private ActionListener drawGrungeCheckboxActionListener;
	private JButton grungeColorChooseButton;
	private JCheckBox drawOceanEffectsInLakesCheckbox;
	private JSlider treeHeightSlider;
	private boolean enableSizeSliderListeners;
	private JSlider mountainScaleSlider;
	private JSlider hillScaleSlider;
	private JSlider duneScaleSlider;
	private JSlider cityScaleSlider;
	private JCheckBox drawRegionBoundariesCheckbox;
	private JComboBox<StrokeType> regionBoundaryTypeComboBox;
	private JSlider regionBoundaryWidthSlider;
	private RowHider regionBoundaryTypeComboBoxHider;
	private RowHider regionBoundaryWidthSliderHider;
	private JSlider oceanShadingSlider;
	private JPanel oceanShadingColorDisplay;
	private JButton btnChooseOceanShadingColor;
	private JPanel borderColorDisplay;
	private JButton borderColorChooseButton;
	private JComboBox<BorderColorOption> borderColorOptionComboBox;
	private RowHider borderColorHider;
	private JSlider coastlineWidthSlider;
	private JPanel regionBoundaryColorDisplay;
	private RowHider regionBoundaryColorHider;
	private JRadioButton assetsRadioButton;
	private JRadioButton fileRadioButton;
	private JComboBox<NamedResource> textureImageComboBox;
	private RowHider textureSourceButtonsHider;
	private RowHider textureImageComboBoxHider;
	private RowHider rippleWavesLevelSliderHider;
	private RowHider oceanWavesColorHider;
	private JCheckBox drawRoadsCheckbox;
	private JComboBox<StrokeType> roadStyleComboBox;
	private RowHider roadStyleComboBoxHider;
	private JSlider roadWidthSlider;
	private RowHider roadWidthSliderHider;
	private JPanel roadColorDisplay;
	private RowHider roadColorHider;
	private JCheckBox fadeWavesCheckbox;
	private JCheckBox jitterWavesCheckbox;
	private JCheckBox brokenLinesCheckbox;
	private RowHider concentricWavesOptionsHider;
	private RowHider concentricWavesLevelSliderHider;
	private JTextField frayedEdgesSeedTextField;
	private JButton newFrayedEdgesSeedButton;
	private JRadioButton solidColorButton;
	private JComboBox<BorderPosition> borderPositionComboBox;

	public ThemePanel(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;

		int width = UserPreferences.getInstance().themePanelWidth > SwingHelper.sidePanelMinimumWidth
				? UserPreferences.getInstance().themePanelWidth
				: SwingHelper.sidePanelPreferredWidth;
		setPreferredSize(new Dimension(width, mainWindow.getContentPane().getHeight()));
		setMinimumSize(new Dimension(SwingHelper.sidePanelMinimumWidth, getMinimumSize().height));

		addTab("Background", createBackgroundPanel(mainWindow));
		addTab("Border", createBorderPanel(mainWindow));
		addTab("Effects", createEffectsPanel(mainWindow));
		addTab("Fonts", createFontsPanel(mainWindow));
	}

	private Component createBackgroundPanel(MainWindow mainWindow)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();
		JPanel backgroundPanel = organizer.panel;

		backgroundImageButtonGroupListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				updateBackgroundAndRegionFieldStates();
				updateBackgroundImageDisplays();
				if (!(e.getSource() == fileRadioButton && StringUtils.isEmpty(textureImageFilename.getText())))
				{
					handleFullRedraw();
				}
			}
		};

		{
			rdbtnFractal = new JRadioButton("Fractal noise");
			rdbtnFractal.addActionListener(backgroundImageButtonGroupListener);

			rdbtnGeneratedFromTexture = new JRadioButton("Generated from texture");
			rdbtnGeneratedFromTexture.addActionListener(backgroundImageButtonGroupListener);

			solidColorButton = new JRadioButton("Solid color");
			solidColorButton.addActionListener(backgroundImageButtonGroupListener);

			ButtonGroup backgroundImageButtonGroup = new ButtonGroup();
			backgroundImageButtonGroup.add(rdbtnGeneratedFromTexture);
			backgroundImageButtonGroup.add(rdbtnFractal);
			backgroundImageButtonGroup.add(solidColorButton);

			organizer.addLabelAndComponentsVertical("Background:", "Select how to generate the background image.",
					Arrays.asList(rdbtnFractal, rdbtnGeneratedFromTexture, solidColorButton));
		}

		{
			assetsRadioButton = new JRadioButton("Assets");
			assetsRadioButton.addActionListener(backgroundImageButtonGroupListener);

			fileRadioButton = new JRadioButton("File");
			fileRadioButton.addActionListener(backgroundImageButtonGroupListener);

			ButtonGroup buttonGroup = new ButtonGroup();
			buttonGroup.add(assetsRadioButton);
			buttonGroup.add(fileRadioButton);
			textureSourceButtonsHider = organizer.addLabelAndComponentsVertical("Texture source:",
					"Select where to get the texture seed image from.", Arrays.asList(assetsRadioButton, fileRadioButton));
		}

		{
			textureImageComboBox = new JComboBox<>();
			textureImageComboBoxHider = organizer.addLabelAndComponent("Texture:",
					"Select a texture image as a seed from installed images that came with Nortantis, or from art packs or a custom images folder.",
					textureImageComboBox);
			textureImageComboBox.addActionListener(backgroundImageButtonGroupListener);
			textureImageComboBox.setMinimumSize(new Dimension(100, textureImageComboBox.getMinimumSize().height));
		}

		textureImageFilename = new JTextField();
		textureImageFilename.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				if (FileHelper.isFile(textureImageFilename.getText()))
				{
					handleFullRedraw();
				}
			}

			public void removeUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				if (FileHelper.isFile(textureImageFilename.getText()))
				{
					handleFullRedraw();
				}
			}

			public void insertUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				if (FileHelper.isFile(textureImageFilename.getText()))
				{
					handleFullRedraw();
				}
			}
		});

		JButton btnsBrowseTextureImage = new JButton("Browse");
		btnsBrowseTextureImage.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String filename = SwingHelper.chooseImageFile(backgroundPanel, FilenameUtils.getFullPath(textureImageFilename.getText()));
				if (filename != null)
				{
					textureImageFilename.setText(filename);
				}
			}
		});

		JPanel textureFileChooseButtonPanel = new JPanel();
		textureFileChooseButtonPanel.setLayout(new BoxLayout(textureFileChooseButtonPanel, BoxLayout.X_AXIS));
		textureFileChooseButtonPanel.add(btnsBrowseTextureImage);
		textureFileChooseButtonPanel.add(Box.createHorizontalGlue());

		textureImageHider = organizer.addLabelAndComponentsVertical("Texture image:",
				"Texture image that will be used to randomly generate a background.",
				Arrays.asList(textureImageFilename, Box.createVerticalStrut(5), textureFileChooseButtonPanel));

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.65);

		backgroundSeedTextField = new JTextField();
		backgroundSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
		backgroundSeedTextField.setColumns(10);
		backgroundSeedTextField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				handleFullRedraw();
			}

			public void removeUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				if (!backgroundSeedTextField.getText().isEmpty())
				{
					handleFullRedraw();
				}
			}

			public void insertUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				handleFullRedraw();
			}
		});

		btnNewBackgroundSeed = new JButton("New Seed");
		btnNewBackgroundSeed.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				backgroundSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
				updateBackgroundImageDisplays();
			}
		});
		btnNewBackgroundSeed.setToolTipText("Generate a new random seed.");
		organizer.addLabelAndComponentsHorizontal("Random seed:",
				"The random seed used to generate the background image. Note that the background texture will also change based on the"
						+ " resolution you draw at.",
				Arrays.asList(backgroundSeedTextField, btnNewBackgroundSeed));

		organizer.addSeperator();

		drawRegionBoundariesCheckbox = new JCheckBox("Draw political region boundaries");
		drawRegionBoundariesCheckbox.setToolTipText("Whether to show political region boundaires");
		drawRegionBoundariesCheckbox.addItemListener(new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
				updateBackgroundAndRegionFieldStates();
				handleTerrainChange();
			}
		});
		organizer.addLeftAlignedComponent(drawRegionBoundariesCheckbox);

		regionBoundaryTypeComboBox = new JComboBox<>(StrokeType.values());
		regionBoundaryTypeComboBoxHider = organizer.addLabelAndComponent("Style:", "Line style for drawing region boundaries",
				regionBoundaryTypeComboBox);
		createMapChangeListenerForTerrainChange(regionBoundaryTypeComboBox);

		{
			regionBoundaryWidthSlider = new JSlider();
			regionBoundaryWidthSlider.setPaintLabels(false);
			regionBoundaryWidthSlider.setValue(10);
			regionBoundaryWidthSlider.setMaximum(100);
			regionBoundaryWidthSlider.setMinimum(10);
			createMapChangeListenerForTerrainChange(regionBoundaryWidthSlider);
			SwingHelper.setSliderWidthForSidePanel(regionBoundaryWidthSlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(regionBoundaryWidthSlider,
					(value) -> String.format("%.1f", value / SettingsGenerator.maxLineWidthInEditor), null);
			regionBoundaryWidthSliderHider = sliderWithDisplay.addToOrganizer(organizer, "Width:", "Line width of region boundaries");
		}

		regionBoundaryColorDisplay = SwingHelper.createColorPickerPreviewPanel();
		JButton buttonChooseRegionBoundaryColor = new JButton("Choose");
		buttonChooseRegionBoundaryColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(backgroundPanel, regionBoundaryColorDisplay, "Region Boundary Color",
						() -> handleTerrainChange());
			}
		});
		regionBoundaryColorHider = organizer.addLabelAndComponentsHorizontal("Color:", "The line color of region boundaries",
				Arrays.asList(regionBoundaryColorDisplay, buttonChooseRegionBoundaryColor), SwingHelper.colorPickerLeftPadding);

		organizer.addSeperator();

		colorizeLandCheckbox = new JCheckBox("Color land");
		colorizeLandCheckbox
				.setToolTipText("Whether to change the land texture to a custom color versus use the color of the texture image");
		colorizeLandCheckboxHider = organizer.addLeftAlignedComponent(colorizeLandCheckbox);

		landColoringMethodComboBox = new JComboBox<LandColoringMethod>();
		for (LandColoringMethod method : LandColoringMethod.values())
		{
			landColoringMethodComboBox.addItem(method);
		}

		landColoringMethodComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleLandColoringMethodChanged();
				handleFullRedraw();
			}
		});
		organizer.addLabelAndComponent("Land coloring method:", "How to color the land.", landColoringMethodComboBox);

		colorizeCheckboxListener = new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
				updateBackgroundAndRegionFieldStates();
				updateBackgroundImageDisplays();
				handleFullRedraw();
			}
		};

		landDisplayPanel = new BGColorPreviewPanel();
		landDisplayPanel.setLayout(null);
		landDisplayPanel.setPreferredSize(backgroundDisplaySize);
		landDisplayPanel.setMinimumSize(backgroundDisplaySize);
		landDisplayPanel.setBackground(Color.BLACK);

		btnChooseLandColor = new JButton("Choose");
		btnChooseLandColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JColorChooser colorChooser = SwingHelper.createColorChooserWithOnlyGoodPanels(landDisplayPanel.getColor());

				colorChooser.getSelectionModel().addChangeListener(landDisplayPanel);
				colorChooser.setPreviewPanel(new JPanel());
				landDisplayPanel.setColorChooser(colorChooser);
				BGColorCancelHandler cancelHandler = new BGColorCancelHandler(landDisplayPanel.getColor(), landDisplayPanel);
				ActionListener okHandler = new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						landDisplayPanel.finishSelectingColor();
						handleFullRedraw();
					}
				};
				Dialog dialog = JColorChooser.createDialog(mainWindow, "Land Color", false, colorChooser, okHandler, cancelHandler);
				dialog.setVisible(true);
			}
		});

		{
			JPanel container = new JPanel();
			container.setLayout(new FlowLayout());
			container.add(landDisplayPanel);
			btnChooseLandColor.setAlignmentX(CENTER_ALIGNMENT);

			landColorHider = organizer.addLabelAndComponentsVertical("Land color:", "The color of the land background.",
					Arrays.asList(container, Box.createVerticalStrut(5), btnChooseLandColor));
		}

		organizer.addSeperator();
		colorizeOceanCheckbox = new JCheckBox("Color ocean");
		colorizeOceanCheckbox
				.setToolTipText("Whether to change the ocean texture to a custom color versus use the color of the texture image");
		colorizeOceanCheckboxHider = organizer.addLeftAlignedComponent(colorizeOceanCheckbox);

		oceanDisplayPanel = new BGColorPreviewPanel();
		oceanDisplayPanel.setLayout(null);
		oceanDisplayPanel.setPreferredSize(backgroundDisplaySize);
		oceanDisplayPanel.setMinimumSize(backgroundDisplaySize);
		oceanDisplayPanel.setBackground(Color.BLACK);

		btnChooseOceanColor = new JButton("Choose");
		btnChooseOceanColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				JColorChooser colorChooser = SwingHelper.createColorChooserWithOnlyGoodPanels(oceanDisplayPanel.getColor());

				colorChooser.getSelectionModel().addChangeListener(oceanDisplayPanel);
				colorChooser.setPreviewPanel(new JPanel());
				oceanDisplayPanel.setColorChooser(colorChooser);
				BGColorCancelHandler cancelHandler = new BGColorCancelHandler(oceanDisplayPanel.getColor(), oceanDisplayPanel);
				ActionListener okHandler = new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent e)
					{
						oceanDisplayPanel.finishSelectingColor();
						handleFullRedraw();
					}
				};
				Dialog dialog = JColorChooser.createDialog(mainWindow, "Ocean Color", false, colorChooser, okHandler, cancelHandler);
				dialog.setVisible(true);
			}
		});

		{
			JPanel container = new JPanel();
			container.setLayout((new FlowLayout()));
			container.add(oceanDisplayPanel);
			btnChooseOceanColor.setAlignmentX(CENTER_ALIGNMENT);

			organizer.addLabelAndComponentsVertical("Ocean color:", "The color of the ocean.",
					Arrays.asList(container, Box.createVerticalStrut(5), btnChooseOceanColor));
		}

		organizer.addVerticalFillerRow();
		updateBackgroundAndRegionFieldVisibility();

		return organizer.createScrollPane();
	}

	private Component createBorderPanel(MainWindow mainWindow)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();
		JPanel borderPanel = organizer.panel;

		drawBorderCheckbox = new JCheckBox("Create border");
		drawBorderCheckbox.setToolTipText("When checked, a border will be drawn around the map.");
		drawBorderCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleEnablingAndDisabling();
				handleFullRedraw();
			}
		});
		organizer.addLeftAlignedComponent(drawBorderCheckbox);

		borderTypeComboBox = new JComboBox<>();
		createMapChangeListenerForFullRedraw(borderTypeComboBox);
		organizer.addLabelAndComponent("Type:", "The set of images to draw for the border", borderTypeComboBox);

		{
			borderWidthSlider = new JSlider();
			borderWidthSlider.setToolTipText("");
			borderWidthSlider.setValue(100);
			borderWidthSlider.setSnapToTicks(false);
			borderWidthSlider.setPaintTicks(true);
			borderWidthSlider.setPaintLabels(true);
			borderWidthSlider.setMinorTickSpacing(50);
			borderWidthSlider.setMaximum(600);
			borderWidthSlider.setMajorTickSpacing(200);
			createMapChangeListenerForFullRedraw(borderWidthSlider);
			SwingHelper.setSliderWidthForSidePanel(borderWidthSlider);
			organizer.addLabelAndComponent("Width:",
					"Width of the border in pixels, scaled according to the resolution the map is drawn at.", borderWidthSlider);
		}
		
		{
			borderPositionComboBox = new JComboBox<BorderPosition>();
			for (BorderPosition option : BorderPosition.values())
			{
				borderPositionComboBox.addItem(option);
			}
			createMapChangeListenerForFullRedraw(borderPositionComboBox);
			organizer.addLabelAndComponent("Position:",
					"Whether the border should draw outside the map or cover the map. Covering avoids changing the exported map's aspect ratio.", borderPositionComboBox);
		}

		borderColorOptionComboBox = new JComboBox<BorderColorOption>();
		borderColorOptionComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (borderColorHider != null)
				{
					borderColorHider.setVisible(
							((BorderColorOption) borderColorOptionComboBox.getSelectedItem()) == BorderColorOption.Choose_color);
					handleFullRedraw();
				}
			}
		});
		for (BorderColorOption option : BorderColorOption.values())
		{
			borderColorOptionComboBox.addItem(option);
		}
		organizer.addLabelAndComponent("Color:",
				"Transparent pixels in the border will show the background texture drawn with this color.", borderColorOptionComboBox);

		borderColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		borderColorChooseButton = new JButton("Choose");
		borderColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				SwingHelper.showColorPicker(borderPanel, borderColorDisplay, "Border Color", () -> handleFullRedraw());
			}
		});
		borderColorHider = organizer.addLabelAndComponentsHorizontal("", "", Arrays.asList(borderColorDisplay, borderColorChooseButton),
				SwingHelper.colorPickerLeftPadding);
		borderColorHider.setVisible(false);

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.6);
		organizer.addSeperator();

		frayedEdgeCheckbox = new JCheckBox("Fray edges");
		frayedEdgeCheckbox.setToolTipText("Whether to fray the edges of the map.");
		frayedEdgeCheckboxActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				handleEnablingAndDisabling();
				handleFrayedEdgeOrGrungeChange();
			}
		};
		frayedEdgeCheckbox.addActionListener(frayedEdgeCheckboxActionListener);
		organizer.addLeftAlignedComponent(frayedEdgeCheckbox);

		frayedEdgeShadingSlider = new JSlider();
		frayedEdgeShadingSlider.setValue(30);
		frayedEdgeShadingSlider.setPaintTicks(true);
		frayedEdgeShadingSlider.setPaintLabels(true);
		frayedEdgeShadingSlider.setMinorTickSpacing(50);
		frayedEdgeShadingSlider.setMaximum(500);
		frayedEdgeShadingSlider.setMajorTickSpacing(100);
		createMapChangeListenerForFrayedEdgeOrGrungeChange(frayedEdgeShadingSlider);
		SwingHelper.setSliderWidthForSidePanel(frayedEdgeShadingSlider);
		organizer.addLabelAndComponent("Shading width:",
				"The width of shading drawn around frayed edges. The color used is the grunge color.", frayedEdgeShadingSlider);

		frayedEdgeSizeSlider = new JSlider();
		frayedEdgeSizeSlider.setPaintTicks(true);
		frayedEdgeSizeSlider.setPaintLabels(true);
		frayedEdgeSizeSlider.setMinorTickSpacing(1);
		frayedEdgeSizeSlider.setMaximum(SettingsGenerator.maxFrayedEdgeSizeForUI);
		frayedEdgeSizeSlider.setMinimum(1);
		frayedEdgeSizeSlider.setMajorTickSpacing(2);
		createMapChangeListenerForFrayedEdgeOrGrungeChange(frayedEdgeSizeSlider);
		SwingHelper.setSliderWidthForSidePanel(frayedEdgeSizeSlider);
		organizer.addLabelAndComponent("Fray size:",
				"Determines the number of polygons used when creating the frayed border. Higher values make the fray larger.",
				frayedEdgeSizeSlider);

		frayedEdgesSeedTextField = new JTextField();
		frayedEdgesSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
		frayedEdgesSeedTextField.setColumns(10);
		frayedEdgesSeedTextField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				handleFrayedEdgeOrGrungeChange();
			}

			public void removeUpdate(DocumentEvent e)
			{
				if (!frayedEdgesSeedTextField.getText().isEmpty())
				{
					handleFrayedEdgeOrGrungeChange();
				}
			}

			public void insertUpdate(DocumentEvent e)
			{
				handleFrayedEdgeOrGrungeChange();
			}
		});

		newFrayedEdgesSeedButton = new JButton("New Seed");
		newFrayedEdgesSeedButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				frayedEdgesSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
			}
		});
		newFrayedEdgesSeedButton.setToolTipText("Generate a new random seed.");
		organizer.addLabelAndComponentsHorizontal("Random seed:", "The random seed used to generate the frayed edges.",
				Arrays.asList(frayedEdgesSeedTextField, newFrayedEdgesSeedButton));

		organizer.addSeperator();

		drawGrungeCheckbox = new JCheckBox("Draw grunge");
		drawGrungeCheckbox.setToolTipText("Whether to draw grunge around the edges of the map.");
		drawGrungeCheckboxActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				handleEnablingAndDisabling();
				handleFrayedEdgeOrGrungeChange();
			}
		};
		drawGrungeCheckbox.addActionListener(drawGrungeCheckboxActionListener);
		organizer.addLeftAlignedComponent(drawGrungeCheckbox);

		grungeSlider = new JSlider();
		grungeSlider.setValue(0);
		grungeSlider.setPaintTicks(true);
		grungeSlider.setPaintLabels(true);
		grungeSlider.setMinorTickSpacing(250);
		grungeSlider.setMaximum(2000);
		grungeSlider.setMajorTickSpacing(1000);
		createMapChangeListenerForFrayedEdgeOrGrungeChange(grungeSlider);
		SwingHelper.setSliderWidthForSidePanel(grungeSlider);
		organizer.addLabelAndComponent("Width:", "Determines the width of grunge on the edges of the map. 0 means none.",
				grungeSlider);

		grungeColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		grungeColorChooseButton = new JButton("Choose");
		grungeColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				SwingHelper.showColorPicker(borderPanel, grungeColorDisplay, "Grunge Color", () -> handleFrayedEdgeOrGrungeChange());
			}
		});
		organizer.addLabelAndComponentsHorizontal("Grunge/frayed edge color:", "Grunge and frayed edge shading will be this color",
				Arrays.asList(grungeColorDisplay, grungeColorChooseButton), SwingHelper.colorPickerLeftPadding);

		organizer.addVerticalFillerRow();
		return organizer.createScrollPane();
	}

	private Component createEffectsPanel(MainWindow mainWindow)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel effectsPanel = organizer.panel;

		jaggedLinesButton = new JRadioButton("Jagged");
		createMapChangeListenerForFullRedraw(jaggedLinesButton);
		splinesLinesButton = new JRadioButton("Splines");
		createMapChangeListenerForFullRedraw(splinesLinesButton);
		splinesWithSmoothedCoastlinesButton = new JRadioButton("Splines with smoothed coastlines");
		createMapChangeListenerForFullRedraw(splinesWithSmoothedCoastlinesButton);
		ButtonGroup lineStyleButtonGroup = new ButtonGroup();
		lineStyleButtonGroup.add(jaggedLinesButton);
		lineStyleButtonGroup.add(splinesLinesButton);
		lineStyleButtonGroup.add(splinesWithSmoothedCoastlinesButton);
		organizer.addLabelAndComponentsVertical("Line style:",
				"The style of lines to use when drawing coastlines, lakeshores, and region boundaries",
				Arrays.asList(jaggedLinesButton, splinesLinesButton, splinesWithSmoothedCoastlinesButton));
		organizer.addSeperator();

		{
			coastlineWidthSlider = new JSlider();
			coastlineWidthSlider.setPaintLabels(false);
			coastlineWidthSlider.setMinimum(10);
			coastlineWidthSlider.setMaximum(100);
			coastlineWidthSlider.setValue(10);
			createMapChangeListenerForTerrainChange(coastlineWidthSlider);
			SwingHelper.setSliderWidthForSidePanel(coastlineWidthSlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(coastlineWidthSlider,
					(value) -> String.format("%.1f", value / SettingsGenerator.maxLineWidthInEditor), null);
			sliderWithDisplay.addToOrganizer(organizer, "Coastline width:", "Line width of coastlines");
		}

		coastlineColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		JButton buttonChooseCoastlineColor = new JButton("Choose");
		buttonChooseCoastlineColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(effectsPanel, coastlineColorDisplay, "Coastline Color", () -> handleTerrainChange());
			}
		});
		organizer.addLabelAndComponentsHorizontal("Coastline color:", "The color of the coastline",
				Arrays.asList(coastlineColorDisplay, buttonChooseCoastlineColor), SwingHelper.colorPickerLeftPadding);

		coastShadingSlider = new JSlider();
		coastShadingSlider.setValue(30);
		coastShadingSlider.setPaintTicks(true);
		coastShadingSlider.setPaintLabels(true);
		coastShadingSlider.setMinorTickSpacing(5);
		coastShadingSlider.setMaximum(100);
		coastShadingSlider.setMajorTickSpacing(20);
		createMapChangeListenerForTerrainChange(coastShadingSlider);
		SwingHelper.setSliderWidthForSidePanel(coastShadingSlider);
		organizer.addLabelAndComponent("Coast shading width:",
				"How far in from coastlines to shade land. Also applies to region boundaries if regions are drawn.", coastShadingSlider);

		{
			coastShadingTransparencySlider = new JSlider(0, 100);
			final int initialValue = 0;
			coastShadingTransparencySlider.setValue(initialValue);
			SwingHelper.setSliderWidthForSidePanel(coastShadingTransparencySlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(coastShadingTransparencySlider, null, () ->
			{
				updateCoastShadingColorDisplayFromCoastShadingTransparencySlider();
				handleTerrainChange();
			});
			coastShadingTransparencyHider = sliderWithDisplay.addToOrganizer(organizer, "Coast shading transparency:",
					"Transparency of shading of land near coastlines");
		}

		{
			coastShadingColorDisplay = SwingHelper.createColorPickerPreviewPanel();

			btnChooseCoastShadingColor = new JButton("Choose");
			btnChooseCoastShadingColor.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					SwingHelper.showColorPicker(effectsPanel, coastShadingColorDisplay, "Coast Shading Color", () ->
					{
						updateCoastShadingTransparencySliderFromCoastShadingColorDisplay();
						handleTerrainChange();
					});
				}
			});
			String coastShadingColorLabelText = "Coast shading color:";
			coastShadingColorHider = organizer.addLabelAndComponentsHorizontal(coastShadingColorLabelText,
					"Land near coastlines and political region boundaries will be shaded this color.",
					Arrays.asList(coastShadingColorDisplay, btnChooseCoastShadingColor), SwingHelper.colorPickerLeftPadding);

			final String message = "<html>Disabled because the land coloring" + " method is '" + LandColoringMethod.ColorPoliticalRegions
					+ "'.<html>";
			coastShadingColorDisabledMessageHider = organizer.addLabelAndComponent(coastShadingColorLabelText, "", new JLabel(message));
			coastShadingColorDisabledMessageHider.setVisible(false);
		}

		organizer.addSeperator();

		oceanShadingSlider = new JSlider();
		oceanShadingSlider.setPaintTicks(true);
		oceanShadingSlider.setPaintLabels(true);
		oceanShadingSlider.setMinorTickSpacing(5);
		oceanShadingSlider.setMaximum(100);
		oceanShadingSlider.setMajorTickSpacing(20);
		createMapChangeListenerForTerrainChange(oceanShadingSlider);
		SwingHelper.setSliderWidthForSidePanel(oceanShadingSlider);
		organizer.addLabelAndComponent("Ocean shading width:", "How far from coastlines to shade ocean.", oceanShadingSlider);

		{
			oceanShadingColorDisplay = SwingHelper.createColorPickerPreviewPanel();
			btnChooseOceanShadingColor = new JButton("Choose");
			btnChooseOceanShadingColor.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					SwingHelper.showColorPicker(effectsPanel, oceanShadingColorDisplay, "Ocean Shading Color", () ->
					{
						handleTerrainChange();
					});
				}
			});
			organizer.addLabelAndComponentsHorizontal("Ocean shading color:",
					"Ocean near coastlines will be shaded this color.",
					Arrays.asList(oceanShadingColorDisplay, btnChooseOceanShadingColor), SwingHelper.colorPickerLeftPadding);
		}

		organizer.addSeperator();

		ButtonGroup oceanEffectButtonGroup = new ButtonGroup();

		concentricWavesButton = new JRadioButton("Concentric waves");
		oceanEffectButtonGroup.add(concentricWavesButton);
		oceanEffectsListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				concentricWavesLevelSlider.setVisible(concentricWavesButton.isSelected());
				concentricWavesOptionsHider.setVisible(concentricWavesButton.isSelected());
				concentricWavesLevelSliderHider.setVisible(concentricWavesButton.isSelected());
				rippleWavesLevelSlider.setVisible(ripplesRadioButton.isSelected());
				rippleWavesLevelSliderHider.setVisible(ripplesRadioButton.isSelected());
				oceanWavesColorHider.setVisible(!noneRadioButton.isSelected());
				handleTerrainChange();
			}
		};
		concentricWavesButton.addActionListener(oceanEffectsListener);

		ripplesRadioButton = new JRadioButton("Ripples");
		oceanEffectButtonGroup.add(ripplesRadioButton);
		ripplesRadioButton.addActionListener(oceanEffectsListener);

		noneRadioButton = new JRadioButton("None");
		oceanEffectButtonGroup.add(noneRadioButton);
		noneRadioButton.addActionListener(oceanEffectsListener);
		organizer.addLabelAndComponentsVertical("Wave type:", "Which type of wave to draw in the ocean along coastlines.",
				Arrays.asList(concentricWavesButton, ripplesRadioButton, noneRadioButton));

		fadeWavesCheckbox = new JCheckBox("Fade outer waves");
		createMapChangeListenerForTerrainChange(fadeWavesCheckbox);

		jitterWavesCheckbox = new JCheckBox("Jitter");
		createMapChangeListenerForTerrainChange(jitterWavesCheckbox);

		brokenLinesCheckbox = new JCheckBox("Broken lines");
		createMapChangeListenerForTerrainChange(brokenLinesCheckbox);
		concentricWavesOptionsHider = organizer.addLabelAndComponentsVertical("Style options:", "Options for how to draw concentric waves.",
				Arrays.asList(fadeWavesCheckbox, jitterWavesCheckbox, brokenLinesCheckbox));

		concentricWavesLevelSlider = new JSlider();
		concentricWavesLevelSlider.setMinimum(1);
		concentricWavesLevelSlider.setPaintTicks(true);
		concentricWavesLevelSlider.setPaintLabels(true);
		concentricWavesLevelSlider.setMinorTickSpacing(1);
		concentricWavesLevelSlider.setMaximum(SettingsGenerator.maxConcentricWaveCountInEditor);
		concentricWavesLevelSlider.setMajorTickSpacing(1);
		createMapChangeListenerForTerrainChange(concentricWavesLevelSlider);
		SwingHelper.setSliderWidthForSidePanel(concentricWavesLevelSlider);
		concentricWavesLevelSliderHider = organizer.addLabelAndComponent("Wave count:", "The number of concentric waves to draw.",
				concentricWavesLevelSlider);

		rippleWavesLevelSlider = new JSlider();
		rippleWavesLevelSlider.setMinorTickSpacing(5);
		rippleWavesLevelSlider.setPaintTicks(true);
		rippleWavesLevelSlider.setPaintLabels(true);
		rippleWavesLevelSlider.setMajorTickSpacing(20);
		rippleWavesLevelSlider.setMaximum(100);
		createMapChangeListenerForTerrainChange(rippleWavesLevelSlider);
		SwingHelper.setSliderWidthForSidePanel(rippleWavesLevelSlider);
		rippleWavesLevelSliderHider = organizer.addLabelAndComponent("Wave width:", "How far from coastlines waves should draw.",
				rippleWavesLevelSlider);

		{
			oceanWavesColorDisplay = SwingHelper.createColorPickerPreviewPanel();

			JButton btnChooseOceanEffectsColor = new JButton("Choose");
			btnChooseOceanEffectsColor.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					SwingHelper.showColorPicker(effectsPanel, oceanWavesColorDisplay, "Wave Color", () -> handleTerrainChange());
				}
			});
			btnChooseOceanEffectsColor.setToolTipText("Choose a color for waves near coastlines.");
			oceanWavesColorHider = organizer.addLabelAndComponentsHorizontal("Wave color:",
					"The color of the ocean waves.",
					Arrays.asList(oceanWavesColorDisplay, btnChooseOceanEffectsColor), SwingHelper.colorPickerLeftPadding);
		}

		drawOceanEffectsInLakesCheckbox = new JCheckBox("Draw ocean waves/shading in lakes.");
		createMapChangeListenerForTerrainChange(drawOceanEffectsInLakesCheckbox);
		organizer.addLeftAlignedComponent(drawOceanEffectsInLakesCheckbox);

		{
			organizer.addSeperator();
			riverColorDisplay = SwingHelper.createColorPickerPreviewPanel();

			JButton riverColorChooseButton = new JButton("Choose");
			riverColorChooseButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					SwingHelper.showColorPicker(effectsPanel, riverColorDisplay, "River Color", () -> handleTerrainChange());
				}
			});
			organizer.addLabelAndComponentsHorizontal("River color:", "Rivers will be drawn this color.",
					Arrays.asList(riverColorDisplay, riverColorChooseButton), SwingHelper.colorPickerLeftPadding);
		}

		{
			organizer.addSeperator();
			drawRoadsCheckbox = new JCheckBox("Draw roads");
			drawRoadsCheckbox.setToolTipText("Whether to show/hide roads");
			drawRoadsCheckbox.addItemListener(new ItemListener()
			{
				@Override
				public void itemStateChanged(ItemEvent e)
				{
					updateRoadFieldVisibility();
					handleTerrainChange();
				}
			});
			organizer.addLeftAlignedComponent(drawRoadsCheckbox);

			roadStyleComboBox = new JComboBox<>(StrokeType.values());
			roadStyleComboBoxHider = organizer.addLabelAndComponent("Style:", "Line style for drawing roads", roadStyleComboBox);
			createMapChangeListenerForTerrainChange(roadStyleComboBox);

			{
				roadWidthSlider = new JSlider();
				roadWidthSlider.setPaintLabels(false);
				roadWidthSlider.setValue(10);
				roadWidthSlider.setMaximum(100);
				roadWidthSlider.setMinimum(10);
				createMapChangeListenerForTerrainChange(roadWidthSlider);
				SwingHelper.setSliderWidthForSidePanel(roadWidthSlider);
				SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(roadWidthSlider,
						(value) -> String.format("%.1f", value / SettingsGenerator.maxLineWidthInEditor), null);
				roadWidthSliderHider = sliderWithDisplay.addToOrganizer(organizer, "Width:", "Line width of roads");
			}

			roadColorDisplay = SwingHelper.createColorPickerPreviewPanel();
			JButton buttonRoadColor = new JButton("Choose");
			buttonRoadColor.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					SwingHelper.showColorPicker(effectsPanel, roadColorDisplay, "Road Color", () -> handleTerrainChange());
				}
			});
			roadColorHider = organizer.addLabelAndComponentsHorizontal("Color:", "The color of roads",
					Arrays.asList(roadColorDisplay, buttonRoadColor), SwingHelper.colorPickerLeftPadding);
		}

		organizer.addSeperator();
		mountainScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		mountainScaleSlider.setMajorTickSpacing(2);
		mountainScaleSlider.setMinorTickSpacing(1);
		mountainScaleSlider.setPaintTicks(true);
		mountainScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(mountainScaleSlider);
		SwingHelper.addListener(mountainScaleSlider, () ->
		{
			if (enableSizeSliderListeners)
			{
				unselectAnyIconBeingEdited();
				repositionMountainsForNewScaleAndTriggerTerrainChange();
			}
		});
		enableSizeSliderListeners = true;
		organizer.addLabelAndComponent("Mountain size:", "Changes the size of all mountains on the map", mountainScaleSlider);

		hillScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		hillScaleSlider.setMajorTickSpacing(2);
		hillScaleSlider.setMinorTickSpacing(1);
		hillScaleSlider.setPaintTicks(true);
		hillScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(hillScaleSlider);
		SwingHelper.addListener(hillScaleSlider, () ->
		{
			if (enableSizeSliderListeners)
			{
				unselectAnyIconBeingEdited();
				handleTerrainChange();
			}
		});
		organizer.addLabelAndComponent("Hill size:", "Changes the size of all hills on the map", hillScaleSlider);

		duneScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		duneScaleSlider.setMajorTickSpacing(2);
		duneScaleSlider.setMinorTickSpacing(1);
		duneScaleSlider.setPaintTicks(true);
		duneScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(duneScaleSlider);
		SwingHelper.addListener(duneScaleSlider, () ->
		{
			if (enableSizeSliderListeners)
			{
				unselectAnyIconBeingEdited();
				handleTerrainChange();
			}
		});
		organizer.addLabelAndComponent("Dune size:", "Changes the size of all sand dunes on the map", duneScaleSlider);

		// If I change the maximum here, also update densityScale in IconDrawer.drawTreesForCenters.
		treeHeightSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		treeHeightSlider.setMajorTickSpacing(2);
		treeHeightSlider.setMinorTickSpacing(1);
		treeHeightSlider.setPaintTicks(true);
		treeHeightSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(treeHeightSlider);
		SwingHelper.addListener(treeHeightSlider, () ->
		{
			if (enableSizeSliderListeners)
			{
				unselectAnyIconBeingEdited();
				triggerRebuildAllAnchoredTrees();
				handleTerrainChange();
			}
		});
		enableSizeSliderListeners = true;
		organizer.addLabelAndComponent("Tree height:",
				"Changes the height of all trees on the map, and redistributes trees to preserve forest density", treeHeightSlider);

		cityScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		cityScaleSlider.setMajorTickSpacing(2);
		cityScaleSlider.setMinorTickSpacing(1);
		cityScaleSlider.setPaintTicks(true);
		cityScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(cityScaleSlider);
		SwingHelper.addListener(cityScaleSlider, () ->
		{
			if (enableSizeSliderListeners)
			{
				unselectAnyIconBeingEdited();
				handleTerrainChange();
			}
		});
		organizer.addLabelAndComponent("City size:", "Changes the size of all cities on the map", cityScaleSlider);

		updateRoadFieldVisibility();

		organizer.addVerticalFillerRow();
		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.6);
		return organizer.createScrollPane();
	}


	private void unselectAnyIconBeingEdited()
	{
		if (mainWindow.toolsPanel != null && mainWindow.toolsPanel.currentTool != null
				&& mainWindow.toolsPanel.currentTool instanceof IconsTool)
		{
			((IconsTool) mainWindow.toolsPanel.currentTool).unselectAnyIconsBeingEdited();
		}
	}

	private void triggerRebuildAllAnchoredTrees()
	{
		mainWindow.edits.freeIcons.doWithLock(() ->
		{
			Random rand = new Random();
			// Reassign the random seeds to all CenterTrees that still exist because they failed to create any trees in their previous
			// attempt
			// to draw. Doing this causes those center trees to possibly show up. Without it, they would gradually disappear as you changed
			// the
			// tree height slider, especially on the higher ends of the tree height values.
			// Also mark CenterTrees as not dormant so they will try to draw again.
			// Also remove CenterTrees that are not close to any trees that are visible so that they don't randomly pop up when you change
			// the
			// tree height slider.
			for (Map.Entry<Integer, CenterEdit> entry : mainWindow.edits.centerEdits.entrySet())
			{
				CenterTrees cTrees = entry.getValue().trees;
				if (cTrees != null)
				{
					if (mainWindow.edits.freeIcons.hasTrees(entry.getKey()))
					{
						// Visible trees override invisible ones.
						mainWindow.edits.centerEdits.put(entry.getKey(), entry.getValue().copyWithTrees(null));
					}
					else
					{
						if (hasVisibleTreeWithinDistance(entry.getKey(), cTrees.treeType, 3))
						{
							mainWindow.edits.centerEdits.put(entry.getKey(), entry.getValue().copyWithTrees(
									new CenterTrees(cTrees.artPack, cTrees.treeType, cTrees.density, rand.nextLong(), false)));
						}
						else
						{
							mainWindow.edits.centerEdits.put(entry.getKey(), entry.getValue().copyWithTrees(null));
						}
					}
				}
			}

			for (int centerIndex : mainWindow.edits.freeIcons.iterateTreeAnchors())
			{
				List<FreeIcon> trees = mainWindow.edits.freeIcons.getTrees(centerIndex);
				if (trees == null || trees.isEmpty())
				{
					continue;
				}

				Tuple2Comp<String, String> tuple = getMostCommonTreeType(trees);
				if (tuple == null)
				{
					// This shouldn't happen because we checked that trees was not null or empty.
					assert false;
					continue;
				}
				String artPack = tuple.getFirst();
				String treeType = tuple.getSecond();
				assert artPack != null;
				assert treeType != null;

				double density = trees.stream().mapToDouble(t -> t.density).average().getAsDouble();

				assert density > 0;

				CenterTrees cTrees = new CenterTrees(artPack, treeType, density, rand.nextLong());
				CenterEdit cEdit = mainWindow.edits.centerEdits.get(centerIndex);
				mainWindow.edits.centerEdits.put(centerIndex, cEdit.copyWithTrees(cTrees));
			}
		});
	}

	/**
	 * Recalculates where mountains attached to Centers should be positioned so that changing the mountain scale slider keeps the base of
	 * mountains in approximately the same location.
	 * 
	 * I didn't bother doing this with dunes or hills because they tend to be short anyway, and so I've anchored them to the centroid of
	 * centers rather the the bottom.
	 */
	private void repositionMountainsForNewScaleAndTriggerTerrainChange()
	{
		// This is a bit of a hack, but I only call innerRepositionMountainsForNewScaleWithIconDrawer when iconDrawer and graph are not null
		// rather than always call it in doWhenMapIsReadyForInteractions because for many drawing cases the graph and icon drawer are
		// available, and for those cases I don't want to do this step later because it causes trouble with undo points (the changes
		// from this method get mixed in with an undo point from a later actions from the user).
		IconDrawer iconDrawer = mainWindow.updater.mapParts.iconDrawer;
		WorldGraph graph = mainWindow.updater.mapParts.graph;
		if (iconDrawer != null && graph != null)
		{
			innerRepositionMountainsForNewScaleWithIconDrawer(graph, iconDrawer);
			handleTerrainChange();
		}
		else
		{
			mainWindow.updater.doWhenMapIsReadyForInteractions(() ->
			{
				IconDrawer iconDrawer2 = mainWindow.updater.mapParts.iconDrawer;
				WorldGraph graph2 = mainWindow.updater.mapParts.graph;
				if (iconDrawer2 != null && graph2 != null)
				{
					innerRepositionMountainsForNewScaleWithIconDrawer(graph2, iconDrawer2);
					handleTerrainChange();
				}
			});
		}
	}

	private void innerRepositionMountainsForNewScaleWithIconDrawer(WorldGraph graph, IconDrawer iconDrawer)
	{
		FreeIconCollection freeIcons = mainWindow.edits.freeIcons;
		double resolution = mainWindow.displayQualityScale;
		double mountainScale = getScaleForSliderValue(mountainScaleSlider.getValue());
		freeIcons.doWithLock(() ->
		{
			for (FreeIcon icon : freeIcons.iterateAnchoredNonTreeIcons())
			{
				ListMap<String, ImageAndMasks> iconsByGroup = ImageCache.getInstance(icon.artPack, mainWindow.customImagesPath)
						.getIconGroupsAsListsForType(IconType.mountains);
				if (icon.type == IconType.mountains)
				{
					if (!iconsByGroup.containsKey(icon.groupId))
					{
						// I don't think this should happen
						assert false;
						continue;
					}
					if (iconsByGroup.get(icon.groupId).isEmpty())
					{
						// I don't think this should happen
						assert false;
						continue;
					}
					Point loc = iconDrawer.getAnchoredMountainDrawPoint(graph.centers.get(icon.centerIndex), icon.groupId, icon.iconIndex,
							mountainScale, iconsByGroup);
					freeIcons.addOrReplace(icon.copyWithLocation(resolution, loc));
				}
			}

			// Do something similar for non-anchored mountains. In this case, we don't have the center the mountain was originally drawn on,
			// so we use the average center height to calculate approximately what the why offset of the image would have been.
			for (FreeIcon icon : freeIcons.iterateNonAnchoredIcons())
			{
				if (icon.type == IconType.mountains)
				{
					ImageAndMasks imageAndMasks = ImageCache.getInstance(icon.artPack, mainWindow.customImagesPath).getImageAndMasks(icon);
					double yChange = mainWindow.updater.mapParts.iconDrawer.getUnanchoredMountainYChangeFromMountainScaleChange(icon,
							mountainScale, imageAndMasks);
					Point scaledLocation = icon.getScaledLocation(resolution);
					FreeIcon updated = icon.copyWithLocation(resolution, new Point(scaledLocation.x, scaledLocation.y + yChange));
					freeIcons.replace(icon, updated);
				}
			}
		});

	}

	private Tuple2Comp<String, String> getMostCommonTreeType(List<FreeIcon> trees)
	{
		Counter<Tuple2Comp<String, String>> counter = new ComparableCounter<>();
		trees.stream().forEach(tree -> counter.incrementCount(new Tuple2Comp<>(tree.artPack, tree.groupId)));
		return counter.argmax();
	}

	private boolean hasVisibleTreeWithinDistance(int centerStartIndex, String treeType, int maxSearchDistance)
	{
		MapEdits edits = mainWindow.edits;
		WorldGraph graph = mainWindow.updater.mapParts.graph;
		Center start = graph.centers.get(centerStartIndex);
		Center found = graph.breadthFirstSearchForGoal((_, _, distanceFromStart) ->
		{
			return distanceFromStart < maxSearchDistance;
		}, (c) ->
		{
			return edits.freeIcons.hasTrees(c.index);
		}, start);

		return found != null;
	}

	private boolean disableCoastShadingColorDisplayHandler = false;

	private void updateCoastShadingColorDisplayFromCoastShadingTransparencySlider()
	{
		if (!disableCoastShadingColorDisplayHandler)
		{
			Color background = coastShadingColorDisplay.getBackground();
			int alpha = (int) ((1.0 - coastShadingTransparencySlider.getValue() / 100.0) * 255);
			coastShadingColorDisplay.setBackground(new Color(background.getRed(), background.getGreen(), background.getBlue(), alpha));
		}
	}

	private void updateCoastShadingTransparencySliderFromCoastShadingColorDisplay()
	{
		coastShadingTransparencySlider.setValue((int) (((1.0 - coastShadingColorDisplay.getBackground().getAlpha() / 255.0) * 100)));
	}

	private Component createFontsPanel(MainWindow mainWindow)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel fontsPanel = organizer.panel;

		enableTextCheckBox = new JCheckBox("Enable text");
		enableTextCheckBox.setToolTipText("Enable/disable drawing text. When unselected, text will still exist, but will not be shown.");
		organizer.addLeftAlignedComponent(enableTextCheckBox);
		organizer.addSeperator();

		Tuple2<JLabel, JButton> tupleTitle = organizer.addFontChooser("Title font:", 70, () -> handleFontsChange());
		titleFontDisplay = tupleTitle.getFirst();
		btnTitleFont = tupleTitle.getSecond();

		Tuple2<JLabel, JButton> tupleRegion = organizer.addFontChooser("Region font:", 40, () -> handleFontsChange());
		regionFontDisplay = tupleRegion.getFirst();
		btnRegionFont = tupleRegion.getSecond();

		Tuple2<JLabel, JButton> tupleMountainRange = organizer.addFontChooser("Mountain range font:", 30, () -> handleFontsChange());
		mountainRangeFontDisplay = tupleMountainRange.getFirst();
		btnMountainRangeFont = tupleMountainRange.getSecond();

		Tuple2<JLabel, JButton> tupleCitiesMountains = organizer.addFontChooser("Cities/mountains font:", 30, () -> handleFontsChange());
		otherMountainsFontDisplay = tupleCitiesMountains.getFirst();
		btnOtherMountainsFont = tupleCitiesMountains.getSecond();

		Tuple2<JLabel, JButton> tupleRiver = organizer.addFontChooser("River/lake font:", 30, () -> handleFontsChange());
		riverFontDisplay = tupleRiver.getFirst();
		btnRiverFont = tupleRiver.getSecond();

		organizer.addSeperator();
		textColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		btnChooseTextColor = new JButton("Choose");
		btnChooseTextColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(fontsPanel, textColorDisplay, "Text Color", () -> handleFontsChange());
			}
		});
		organizer.addLabelAndComponentsHorizontal("Text color:", "", Arrays.asList(textColorDisplay, btnChooseTextColor),
				SwingHelper.colorPickerLeftPadding);

		organizer.addSeperator();
		drawBoldBackgroundCheckbox = new JCheckBox("Bold background for region and title names");
		drawBoldBackgroundCheckbox.setToolTipText("Whether to draw bolded letters behind region and title text to highlight them.");
		organizer.addLeftAlignedComponent(drawBoldBackgroundCheckbox);

		boldBackgroundColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		btnChooseBoldBackgroundColor = new JButton("Choose");
		btnChooseBoldBackgroundColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(fontsPanel, boldBackgroundColorDisplay, "Bold Background Color", () -> handleFontsChange());
			}
		});
		organizer.addLabelAndComponentsHorizontal("Bold background color:",
				"If '" + drawBoldBackgroundCheckbox.getText()
						+ "' is checked, title and region names will be given a bold background in this color.",
				Arrays.asList(boldBackgroundColorDisplay, btnChooseBoldBackgroundColor), SwingHelper.colorPickerLeftPadding);

		drawBoldBackgroundCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleEnablingAndDisabling();
				handleFontsChange();
			}
		});

		enableTextCheckboxActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				handleEnablingAndDisabling();
				handleTextChange();
			}
		};

		enableTextCheckBox.addActionListener(enableTextCheckboxActionListener);

		organizer.addVerticalFillerRow();
		organizer.addLeftAlignedComponent(Box.createHorizontalStrut(100));
		return organizer.createScrollPane();
	}

	private boolean landSupportsColoring()
	{
		return rdbtnFractal.isSelected() || (rdbtnGeneratedFromTexture.isSelected() && colorizeLandCheckbox.isSelected())
				|| solidColorButton.isSelected();
	}

	private boolean oceanSupportsColoring()
	{
		return rdbtnFractal.isSelected() || (rdbtnGeneratedFromTexture.isSelected() && colorizeOceanCheckbox.isSelected())
				|| solidColorButton.isSelected();
	}

	private void updateBackgroundAndRegionFieldVisibility()
	{
		textureSourceButtonsHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		textureImageComboBoxHider.setVisible(rdbtnGeneratedFromTexture.isSelected() && assetsRadioButton.isSelected());
		textureImageHider.setVisible(rdbtnGeneratedFromTexture.isSelected() && fileRadioButton.isSelected());
		colorizeLandCheckboxHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeOceanCheckboxHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		regionBoundaryTypeComboBoxHider.setVisible(drawRegionBoundariesCheckbox.isSelected());
		regionBoundaryWidthSliderHider.setVisible(drawRegionBoundariesCheckbox.isSelected());
		regionBoundaryColorHider.setVisible(drawRegionBoundariesCheckbox.isSelected());
	}

	private void updateRoadFieldVisibility()
	{
		roadStyleComboBoxHider.setVisible(drawRoadsCheckbox.isSelected());
		roadWidthSliderHider.setVisible(drawRoadsCheckbox.isSelected());
		roadColorHider.setVisible(drawRoadsCheckbox.isSelected());
	}

	private void updateBackgroundAndRegionFieldStates()
	{
		if (!landSupportsColoring())
		{
			landColoringMethodComboBox.setSelectedItem(LandColoringMethod.SingleColor);
		}

		updateBackgroundAndRegionFieldVisibility();
		handleEnablingAndDisabling();
	}

	private void updateBackgroundImageDisplays()
	{
		IntDimension size = new IntDimension(backgroundDisplaySize.width, backgroundDisplaySize.height);

		SwingWorker<Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm>, Void> worker = new SwingWorker<Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm>, Void>()
		{

			@Override
			protected Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm> doInBackground() throws Exception
			{
				MapSettings tempSettings = new MapSettings();
				getSettingsFromGUI(tempSettings);
				String texturePath;
				Tuple2<Path, String> texturePathWithWarning = tempSettings.getBackgroundImagePath();
				if (texturePathWithWarning != null && texturePathWithWarning.getFirst() != null)
				{
					texturePath = tempSettings.getBackgroundImagePath().getFirst().toString();
				}
				else
				{
					return null;
				}

				long seed = parseBackgroundSeed();
				return createBackgroundImageDisplaysImages(size, seed, colorizeOceanCheckbox.isSelected(),
						colorizeLandCheckbox.isSelected(), rdbtnFractal.isSelected(), rdbtnGeneratedFromTexture.isSelected(),
						solidColorButton.isSelected(), texturePath);
			}

			@Override
			public void done()
			{
				Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm> tuple;
				try
				{
					tuple = get();
				}
				catch (InterruptedException | ExecutionException e)
				{
					throw new RuntimeException(e);
				}

				if (tuple == null)
				{
					return;
				}

				Image oceanBackground = tuple.getFirst();
				ImageHelper.ColorifyAlgorithm oceanColorifyAlgorithm = tuple.getSecond();
				Image landBackground = tuple.getThird();
				ImageHelper.ColorifyAlgorithm landColorifyAlgorithm = tuple.getFourth();

				oceanDisplayPanel.setColorifyAlgorithm(oceanColorifyAlgorithm);
				oceanDisplayPanel.setImage(AwtFactory.unwrap(oceanBackground));
				oceanDisplayPanel.repaint();

				landDisplayPanel.setColorifyAlgorithm(landColorifyAlgorithm);
				landDisplayPanel.setImage(AwtFactory.unwrap(landBackground));
				landDisplayPanel.repaint();
			}
		};

		worker.execute();
	}

	static Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm> createBackgroundImageDisplaysImages(
			IntDimension size, long seed, boolean colorizeOcean, boolean colorizeLand, boolean isFractal, boolean isFromTexture,
			boolean isSolidColor, String textureImageFileName)
	{

		Image oceanBackground;
		ImageHelper.ColorifyAlgorithm oceanColorifyAlgorithm;
		Image landBackground;
		ImageHelper.ColorifyAlgorithm landColorifyAlgorithm;

		if (isFractal)
		{
			oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm2;
			landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm2;

			oceanBackground = landBackground = FractalBGGenerator.generate(new Random(seed), 1.3f, size.width, size.height, 0.75f);
		}
		else if (isFromTexture)
		{
			Image texture;
			try
			{
				texture = Assets.readImage(textureImageFileName);

				if (colorizeOcean)
				{
					oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;

					oceanBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(seed),
							ImageHelper.convertToGrayscale(texture), size.height, size.width);
				}
				else
				{
					oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;

					oceanBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(seed), texture, size.height,
							size.width);
				}

				if (colorizeLand == colorizeOcean)
				{
					// No need to generate the same image twice.
					landBackground = oceanBackground;
					if (colorizeLand)
					{
						landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;
					}
					else
					{
						landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
					}
				}
				else
				{
					if (colorizeLand)
					{
						landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;

						landBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(seed),
								ImageHelper.convertToGrayscale(texture), size.height, size.width);
					}
					else
					{
						landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;

						landBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(seed), texture, size.height,
								size.width);
					}
				}
			}
			catch (RuntimeException e)
			{
				oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
				landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
				oceanBackground = landBackground = Image.create(size.width, size.height, ImageType.ARGB);
			}
		}
		else if (isSolidColor)
		{
			oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.solidColor;
			landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.solidColor;
			oceanBackground = landBackground = Image.create(size.width, size.height, ImageType.Grayscale8Bit);
		}
		else
		{
			oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
			landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
			oceanBackground = landBackground = Image.create(size.width, size.height, ImageType.RGB);
		}

		return new Tuple4<>(oceanBackground, oceanColorifyAlgorithm, landBackground, landColorifyAlgorithm);
	}

	private void handleLandColoringMethodChanged()
	{
		boolean colorRegions = areRegionColorsVisible();
		handleEnablingAndDisabling();

		coastShadingColorHider.setVisible(!colorRegions);
		coastShadingColorDisabledMessageHider.setVisible(colorRegions);
		coastShadingTransparencyHider.setVisible(colorRegions);

		landColorHider.setVisible(!colorRegions);
	}

	/**
	 * Loads a map settings file into the GUI.
	 * 
	 * @param path
	 * @return True if the change affects the map's background image. False otherwise.
	 */
	public boolean loadSettingsIntoGUI(MapSettings settings)
	{
		boolean changeEffectsBackgroundImages = doesChangeEffectBackgroundDisplays(settings);

		coastShadingSlider.setValue(settings.coastShadingLevel);
		oceanShadingSlider.setValue(settings.oceanShadingLevel);
		rippleWavesLevelSlider.setValue(settings.oceanWavesLevel);
		concentricWavesLevelSlider.setValue(settings.concentricWaveCount);
		ripplesRadioButton.setSelected(settings.oceanWavesType == OceanWaves.Ripples);
		noneRadioButton.setSelected(settings.oceanWavesType == OceanWaves.None);
		concentricWavesButton.setSelected(settings.oceanWavesType == OceanWaves.ConcentricWaves);
		fadeWavesCheckbox.setSelected(settings.fadeConcentricWaves);
		jitterWavesCheckbox.setSelected(settings.jitterToConcentricWaves);
		brokenLinesCheckbox.setSelected(settings.brokenLinesForConcentricWaves);
		drawOceanEffectsInLakesCheckbox.setSelected(settings.drawOceanEffectsInLakes);
		oceanEffectsListener.actionPerformed(null);
		coastShadingColorDisplay.setBackground(AwtFactory.unwrap(settings.coastShadingColor));

		// Temporarily disable events on coastShadingColorDisplay while initially setting the value for coastShadingTransparencySlider so
		// that
		// the action listener on coastShadingTransparencySlider doesn't fire and then update coastShadingColorDisplay, because doing so can
		// cause changes in the settings due to integer truncation of the alpha value.
		disableCoastShadingColorDisplayHandler = true;
		updateCoastShadingTransparencySliderFromCoastShadingColorDisplay();
		disableCoastShadingColorDisplayHandler = false;

		coastlineColorDisplay.setBackground(AwtFactory.unwrap(settings.coastlineColor));
		coastlineWidthSlider.setValue((int) (settings.coastlineWidth * 10.0));
		oceanWavesColorDisplay.setBackground(AwtFactory.unwrap(settings.oceanWavesColor));
		oceanShadingColorDisplay.setBackground(AwtFactory.unwrap(settings.oceanShadingColor));
		riverColorDisplay.setBackground(AwtFactory.unwrap(settings.riverColor));
		frayedEdgeCheckbox.setSelected(settings.frayedBorder);
		// Do a click here to update other components on the panel as enabled or
		// disabled.
		frayedEdgeCheckboxActionListener.actionPerformed(null);
		drawGrungeCheckbox.setSelected(settings.drawGrunge);
		drawGrungeCheckboxActionListener.actionPerformed(null);
		grungeColorDisplay.setBackground(AwtFactory.unwrap(settings.frayedBorderColor));
		frayedEdgeShadingSlider.setValue(settings.frayedBorderBlurLevel);
		frayedEdgeSizeSlider.setValue(frayedEdgeSizeSlider.getMaximum() - settings.frayedBorderSize);
		// Only do this if there is a change so we don't trigger the document listeners unnecessarily.
		if (!frayedEdgesSeedTextField.getText().equals(String.valueOf(settings.frayedBorderSeed)))
		{
			frayedEdgesSeedTextField.setText(String.valueOf(settings.frayedBorderSeed));
		}
		grungeSlider.setValue(settings.grungeWidth);
		if (settings.lineStyle.equals(LineStyle.Jagged))
		{
			jaggedLinesButton.setSelected(true);
		}
		else if (settings.lineStyle.equals(LineStyle.Splines))
		{
			splinesLinesButton.setSelected(true);
		}
		else if (settings.lineStyle.equals(LineStyle.SplinesWithSmoothedCoastlines))
		{
			splinesWithSmoothedCoastlinesButton.setSelected(true);
		}

		// Settings for background images.
		// Remove and add item listeners to the colorize checkboxes to avoid
		// generating backgrounds for display multiple times.
		colorizeOceanCheckbox.removeItemListener(colorizeCheckboxListener);
		colorizeOceanCheckbox.setSelected((settings.colorizeOcean));
		colorizeOceanCheckbox.addItemListener(colorizeCheckboxListener);
		colorizeLandCheckbox.removeItemListener(colorizeCheckboxListener);
		colorizeLandCheckbox.setSelected((settings.colorizeLand));
		colorizeLandCheckbox.addItemListener(colorizeCheckboxListener);
		rdbtnGeneratedFromTexture.setSelected(settings.generateBackgroundFromTexture);
		solidColorButton.setSelected(settings.solidColorBackground);
		rdbtnFractal.setSelected(settings.generateBackground);

		// Only set radio buttons selected if there was a change in case doing so causes change listeners to fire.
		if (!assetsRadioButton.isSelected() && settings.backgroundTextureSource == TextureSource.Assets)
		{
			assetsRadioButton.setSelected(true);
		}
		else if (!fileRadioButton.isSelected() && settings.backgroundTextureSource == TextureSource.File)
		{
			fileRadioButton.setSelected(true);
		}

		// Only set the selected item if there was a change in case doing so causes change listeners to fire.
		if (settings.backgroundTextureResource != null
				&& !Objects.equals(settings.backgroundTextureResource, textureImageComboBox.getSelectedItem()))
		{
			textureImageComboBox.setSelectedItem(settings.backgroundTextureResource);
		}

		// Only do this if there is a change so we don't trigger the document listeners unnecessarily.
		if (!textureImageFilename.getText().equals(FileHelper.replaceHomeFolderPlaceholder(settings.backgroundTextureImage)))
		{
			textureImageFilename.setText(FileHelper.replaceHomeFolderPlaceholder(settings.backgroundTextureImage));
		}

		// Only do this if there is a change so we don't trigger the document listeners unnecessarily.
		if (!backgroundSeedTextField.getText().equals(String.valueOf(settings.backgroundRandomSeed)))
		{
			backgroundSeedTextField.setText(String.valueOf(settings.backgroundRandomSeed));
		}

		updateBackgroundAndRegionFieldStates();

		oceanDisplayPanel.setColor(AwtFactory.unwrap(settings.oceanColor));
		landDisplayPanel.setColor(AwtFactory.unwrap(settings.landColor));

		if (settings.drawRegionColors)
		{
			landColoringMethodComboBox.setSelectedItem(LandColoringMethod.ColorPoliticalRegions);
		}
		else
		{
			landColoringMethodComboBox.setSelectedItem(LandColoringMethod.SingleColor);
		}
		handleLandColoringMethodChanged();

		drawRegionBoundariesCheckbox.setSelected(settings.drawRegionBoundaries);
		regionBoundaryTypeComboBox.setSelectedItem(settings.regionBoundaryStyle.type);
		regionBoundaryWidthSlider.setValue((int) (settings.regionBoundaryStyle.width * 10f));
		regionBoundaryColorDisplay.setBackground(AwtFactory.unwrap(settings.regionBoundaryColor));

		drawRoadsCheckbox.setSelected(settings.drawRoads);
		roadStyleComboBox.setSelectedItem(settings.roadStyle.type);
		roadWidthSlider.setValue((int) (settings.roadStyle.width * 10f));
		roadColorDisplay.setBackground(AwtFactory.unwrap(settings.roadColor));
		updateRoadFieldVisibility();

		// Do a click to update other components on the panel as enabled or
		// disabled.
		enableTextCheckBox.setSelected(settings.drawText);
		enableTextCheckboxActionListener.actionPerformed(null);

		titleFontDisplay.setFont(AwtFactory.unwrap(settings.titleFont));
		titleFontDisplay.setText(settings.titleFont.getName());
		regionFontDisplay.setFont(AwtFactory.unwrap(settings.regionFont));
		regionFontDisplay.setText(settings.regionFont.getName());
		mountainRangeFontDisplay.setFont(AwtFactory.unwrap(settings.mountainRangeFont));
		mountainRangeFontDisplay.setText(settings.mountainRangeFont.getName());
		otherMountainsFontDisplay.setFont(AwtFactory.unwrap(settings.otherMountainsFont));
		otherMountainsFontDisplay.setText(settings.otherMountainsFont.getName());
		riverFontDisplay.setFont(AwtFactory.unwrap(settings.riverFont));
		riverFontDisplay.setText(settings.riverFont.getName());
		textColorDisplay.setBackground(AwtFactory.unwrap(settings.textColor));
		boldBackgroundColorDisplay.setBackground(AwtFactory.unwrap(settings.boldBackgroundColor));
		drawBoldBackgroundCheckbox.setSelected(settings.drawBoldBackground);
		drawBoldBackgroundCheckbox.getActionListeners()[0].actionPerformed(null);

		// Borders
		initializeComboBoxItems(settings);
		borderWidthSlider.setValue(settings.borderWidth);
		drawBorderCheckbox.setSelected(settings.drawBorder);
		drawBorderCheckbox.getActionListeners()[0].actionPerformed(null);
		borderPositionComboBox.setSelectedItem(settings.borderPosition);
		borderColorOptionComboBox.setSelectedItem(settings.borderColorOption);
		borderColorDisplay.setBackground(AwtFactory.unwrap(settings.borderColor));

		enableSizeSliderListeners = false;
		treeHeightSlider.setValue((int) (Math.round((settings.treeHeightScale - 0.1) * 20.0)));
		mountainScaleSlider.setValue(getSliderValueForScale(settings.mountainScale));
		hillScaleSlider.setValue(getSliderValueForScale(settings.hillScale));
		duneScaleSlider.setValue(getSliderValueForScale(settings.duneScale));
		cityScaleSlider.setValue(getSliderValueForScale(settings.cityScale));
		enableSizeSliderListeners = true;

		if (changeEffectsBackgroundImages)
		{
			updateBackgroundImageDisplays();
		}

		// For some reason I have to repaint to get color display panels to draw
		// correctly.
		repaint();

		return changeEffectsBackgroundImages;
	}

	private final double scaleMax = 3.0;
	private final double scaleMin = 0.5;
	private final double sliderValueFor1Scale = 5;
	private final int minScaleSliderValue = 1;
	private final int maxScaleSliderValue = 15;

	private int getSliderValueForScale(double scale)
	{
		if (scale <= 1.0)
		{
			double slope = (sliderValueFor1Scale - minScaleSliderValue) / (1.0 - scaleMin);
			double yIntercept = sliderValueFor1Scale - slope;
			return (int) Math.round(scale * slope + yIntercept);
		}
		else
		{
			double slope = (maxScaleSliderValue - sliderValueFor1Scale) / (scaleMax - 1.0);
			double yIntercept = sliderValueFor1Scale - slope * (1.0);
			return (int) Math.round(scale * slope + yIntercept);
		}
	}

	private double getScaleForSliderValue(int sliderValue)
	{
		if (sliderValue <= sliderValueFor1Scale)
		{
			double slope = (sliderValueFor1Scale - minScaleSliderValue) / (1.0 - scaleMin);
			double yIntercept = sliderValueFor1Scale - slope;
			return (sliderValue - yIntercept) / slope;
		}
		else
		{
			double slope = (maxScaleSliderValue - sliderValueFor1Scale) / (scaleMax - 1.0);
			double yIntercept = sliderValueFor1Scale - slope * (1.0);
			return (sliderValue - yIntercept) / slope;
		}
	}

	private void initializeComboBoxItems(MapSettings settings)
	{
		SwingHelper.initializeComboBoxItems(borderTypeComboBox,
				Assets.listAllBorderTypes(settings == null ? null : settings.customImagesPath),
				settings == null ? null : settings.borderResource, true);
		SwingHelper.initializeComboBoxItems(textureImageComboBox,
				Assets.listBackgroundTexturesForAllArtPacks(settings == null ? null : settings.customImagesPath),
				settings == null ? null : settings.backgroundTextureResource, true);
	}

	private boolean doesChangeEffectBackgroundDisplays(MapSettings settings)
	{
		if (parseBackgroundSeed() != settings.backgroundRandomSeed)
		{
			return true;
		}

		if (colorizeOceanCheckbox.isSelected() != settings.colorizeOcean)
		{
			return true;
		}

		if (colorizeLandCheckbox.isSelected() != settings.colorizeLand)
		{
			return true;
		}

		if (rdbtnFractal.isSelected() != settings.generateBackground)
		{
			return true;
		}

		if (rdbtnGeneratedFromTexture.isSelected() != settings.generateBackgroundFromTexture)
		{
			return true;
		}

		if (solidColorButton.isSelected() != settings.solidColorBackground)
		{
			return true;
		}

		if (!textureImageFilename.getText().equals(FileHelper.replaceHomeFolderPlaceholder(settings.backgroundTextureImage)))
		{
			return true;
		}

		if (!Objects.equals(textureImageComboBox.getSelectedItem(), settings.backgroundTextureResource))
		{
			return true;
		}

		if (!assetsRadioButton.isSelected() && settings.backgroundTextureSource == TextureSource.Assets
				|| !fileRadioButton.isSelected() && settings.backgroundTextureSource == TextureSource.File)
		{
			return true;
		}

		if (!landDisplayPanel.getColor().equals(AwtFactory.unwrap(settings.landColor)))
		{
			return true;
		}

		if (!oceanDisplayPanel.getColor().equals(AwtFactory.unwrap(settings.oceanColor)))
		{
			return true;
		}

		return false;
	}

	private long parseBackgroundSeed()
	{
		try
		{
			return Long.parseLong(backgroundSeedTextField.getText());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private long parseFrayedBorderSeed()
	{
		try
		{
			return Long.parseLong(frayedEdgesSeedTextField.getText());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.coastShadingLevel = coastShadingSlider.getValue();
		settings.oceanWavesLevel = rippleWavesLevelSlider.getValue();
		settings.oceanShadingLevel = oceanShadingSlider.getValue();
		settings.concentricWaveCount = concentricWavesLevelSlider.getValue();
		settings.oceanWavesType = ripplesRadioButton.isSelected() ? OceanWaves.Ripples
				: noneRadioButton.isSelected() ? OceanWaves.None : OceanWaves.ConcentricWaves;
		settings.fadeConcentricWaves = fadeWavesCheckbox.isSelected();
		settings.jitterToConcentricWaves = jitterWavesCheckbox.isSelected();
		settings.brokenLinesForConcentricWaves = brokenLinesCheckbox.isSelected();
		settings.drawOceanEffectsInLakes = drawOceanEffectsInLakesCheckbox.isSelected();
		settings.coastShadingColor = AwtFactory.wrap(coastShadingColorDisplay.getBackground());
		settings.coastlineColor = AwtFactory.wrap(coastlineColorDisplay.getBackground());
		settings.coastlineWidth = coastlineWidthSlider.getValue() / 10.0;
		settings.oceanWavesColor = AwtFactory.wrap(oceanWavesColorDisplay.getBackground());
		settings.oceanShadingColor = AwtFactory.wrap(oceanShadingColorDisplay.getBackground());
		settings.riverColor = AwtFactory.wrap(riverColorDisplay.getBackground());
		settings.drawText = enableTextCheckBox.isSelected();
		settings.frayedBorder = frayedEdgeCheckbox.isSelected();
		settings.frayedBorderColor = AwtFactory.wrap(grungeColorDisplay.getBackground());
		settings.frayedBorderBlurLevel = frayedEdgeShadingSlider.getValue();
		// Make increasing frayed edge values cause the number of polygons to
		// decrease so that the fray gets large with
		// larger values of the slider.
		settings.frayedBorderSize = frayedEdgeSizeSlider.getMaximum() - frayedEdgeSizeSlider.getValue();
		settings.frayedBorderSeed = parseFrayedBorderSeed();

		settings.drawGrunge = drawGrungeCheckbox.isSelected();
		settings.grungeWidth = grungeSlider.getValue();
		settings.lineStyle = jaggedLinesButton.isSelected() ? LineStyle.Jagged
				: splinesLinesButton.isSelected() ? LineStyle.Splines : LineStyle.SplinesWithSmoothedCoastlines;

		// Background image settings
		settings.generateBackground = rdbtnFractal.isSelected();
		settings.generateBackgroundFromTexture = rdbtnGeneratedFromTexture.isSelected();
		settings.solidColorBackground = solidColorButton.isSelected();
		settings.colorizeOcean = colorizeOceanCheckbox.isSelected();
		settings.colorizeLand = colorizeLandCheckbox.isSelected();
		settings.backgroundTextureSource = assetsRadioButton.isSelected() ? TextureSource.Assets : TextureSource.File;
		settings.backgroundTextureImage = FileHelper.replaceHomeFolderWithPlaceholder(textureImageFilename.getText());
		settings.backgroundTextureResource = (NamedResource) textureImageComboBox.getSelectedItem();
		settings.backgroundRandomSeed = parseBackgroundSeed();
		settings.oceanColor = AwtFactory.wrap(oceanDisplayPanel.getColor());
		settings.drawRegionColors = areRegionColorsVisible();
		settings.drawRegionBoundaries = drawRegionBoundariesCheckbox.isSelected();
		settings.regionBoundaryStyle = new Stroke((StrokeType) regionBoundaryTypeComboBox.getSelectedItem(),
				regionBoundaryWidthSlider.getValue() / 10f);
		settings.regionBoundaryColor = AwtFactory.wrap(regionBoundaryColorDisplay.getBackground());
		settings.landColor = AwtFactory.wrap(landDisplayPanel.getColor());

		settings.titleFont = AwtFactory.wrap(titleFontDisplay.getFont());
		settings.regionFont = AwtFactory.wrap(regionFontDisplay.getFont());
		settings.mountainRangeFont = AwtFactory.wrap(mountainRangeFontDisplay.getFont());
		settings.otherMountainsFont = AwtFactory.wrap(otherMountainsFontDisplay.getFont());
		settings.riverFont = AwtFactory.wrap(riverFontDisplay.getFont());
		settings.textColor = AwtFactory.wrap(textColorDisplay.getBackground());
		settings.boldBackgroundColor = AwtFactory.wrap(boldBackgroundColorDisplay.getBackground());
		settings.drawBoldBackground = drawBoldBackgroundCheckbox.isSelected();

		settings.drawBorder = drawBorderCheckbox.isSelected();
		settings.borderResource = (NamedResource) borderTypeComboBox.getSelectedItem();
		settings.borderWidth = borderWidthSlider.getValue();
		settings.borderPosition = (BorderPosition) borderPositionComboBox.getSelectedItem();
		settings.borderColorOption = (BorderColorOption) borderColorOptionComboBox.getSelectedItem();
		settings.borderColor = AwtFactory.wrap(borderColorDisplay.getBackground());

		settings.treeHeightScale = 0.1 + (treeHeightSlider.getValue() * 0.05);
		settings.mountainScale = getScaleForSliderValue(mountainScaleSlider.getValue());
		settings.hillScale = getScaleForSliderValue(hillScaleSlider.getValue());
		settings.duneScale = getScaleForSliderValue(duneScaleSlider.getValue());
		settings.cityScale = getScaleForSliderValue(cityScaleSlider.getValue());

		settings.drawRoads = drawRoadsCheckbox.isSelected();
		settings.roadStyle = new Stroke((StrokeType) roadStyleComboBox.getSelectedItem(), roadWidthSlider.getValue() / 10f);
		settings.roadColor = AwtFactory.wrap(roadColorDisplay.getBackground());
	}

	private boolean areRegionColorsVisible()
	{
		return getLandColoringMethod().equals(LandColoringMethod.ColorPoliticalRegions);
	}

	/**
	 * This should be used instead of directly calling landColoringMethodComboBox.getSelectedItem() because sometimes the
	 * landColoringMethodComboBox is hidden and its value should be assumed to be LandColoringMethod.SingleColor.
	 */
	private LandColoringMethod getLandColoringMethod()
	{
		if (!landSupportsColoring())
		{
			return LandColoringMethod.SingleColor;
		}

		return (LandColoringMethod) landColoringMethodComboBox.getSelectedItem();
	}

	public Color getLandColor()
	{
		return landDisplayPanel.getColor();
	}

	public enum LandColoringMethod
	{
		SingleColor("Single Color"), ColorPoliticalRegions("Color Political Regions");

		private final String name;

		private LandColoringMethod(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private void createMapChangeListenerForTerrainChange(Component component)
	{
		SwingHelper.addListener(component, () -> handleTerrainChange());
	}

	private void handleTerrainChange()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.Terrain, null);
		mainWindow.handleThemeChange(false);
		mainWindow.updater.createAndShowMapTerrainChange();
	}

	private void handleFontsChange()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.Fonts, null);
		mainWindow.handleThemeChange(false);
		mainWindow.updater.createAndShowMapFontsChange();
	}

	private void handleTextChange()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.Text, null);
		mainWindow.handleThemeChange(false);
		mainWindow.updater.createAndShowMapTextChange();
	}

	private void createMapChangeListenerForFullRedraw(Component component)
	{
		SwingHelper.addListener(component, () -> handleFullRedraw());
	}

	private void handleFullRedraw()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.Full, null);
		mainWindow.handleThemeChange(true);
		mainWindow.updater.createAndShowMapFull();
	}

	private void createMapChangeListenerForFrayedEdgeOrGrungeChange(Component component)
	{
		SwingHelper.addListener(component, () -> handleFrayedEdgeOrGrungeChange());
	}

	private void handleFrayedEdgeOrGrungeChange()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.GrungeAndFray, null);
		mainWindow.handleThemeChange(false);
		mainWindow.updater.createAndShowMapGrungeOrFrayedEdgeChange();
	}

	private void handleEnablingAndDisabling()
	{
		borderWidthSlider.setEnabled(drawBorderCheckbox.isSelected());
		borderTypeComboBox.setEnabled(drawBorderCheckbox.isSelected());
		borderPositionComboBox.setEnabled(drawBorderCheckbox.isSelected());
		borderColorOptionComboBox.setEnabled(drawBorderCheckbox.isSelected());
		borderColorChooseButton.setEnabled(drawBorderCheckbox.isSelected());

		frayedEdgeShadingSlider.setEnabled(frayedEdgeCheckbox.isSelected());
		frayedEdgeSizeSlider.setEnabled(frayedEdgeCheckbox.isSelected());
		frayedEdgesSeedTextField.setEnabled(frayedEdgeCheckbox.isSelected());
		newFrayedEdgesSeedButton.setEnabled(frayedEdgeCheckbox.isSelected());

		grungeColorChooseButton.setEnabled(drawGrungeCheckbox.isSelected());
		grungeSlider.setEnabled(drawGrungeCheckbox.isSelected());

		btnTitleFont.setEnabled(enableTextCheckBox.isSelected());
		btnRegionFont.setEnabled(enableTextCheckBox.isSelected());
		btnMountainRangeFont.setEnabled(enableTextCheckBox.isSelected());
		btnOtherMountainsFont.setEnabled(enableTextCheckBox.isSelected());
		btnRiverFont.setEnabled(enableTextCheckBox.isSelected());
		btnChooseTextColor.setEnabled(enableTextCheckBox.isSelected());
		drawBoldBackgroundCheckbox.setEnabled(enableTextCheckBox.isSelected());
		btnChooseBoldBackgroundColor.setEnabled(enableTextCheckBox.isSelected() && drawBoldBackgroundCheckbox.isSelected());

		btnChooseOceanColor.setEnabled(oceanSupportsColoring());
		btnChooseLandColor.setEnabled(landSupportsColoring());

		btnChooseCoastShadingColor.setEnabled(!areRegionColorsVisible());

		landColoringMethodComboBox.setEnabled(landSupportsColoring());
	}

	void enableOrDisableEverything(boolean enable)
	{
		SwingHelper.setEnabled(this, enable);

		if (enable)
		{
			// Call this to disable any fields that should be disabled.
			handleEnablingAndDisabling();
		}
	}

	void handleImagesRefresh(MapSettings settings)
	{
		initializeComboBoxItems(settings);
	}
}
