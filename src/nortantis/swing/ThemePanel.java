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
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.FilenameUtils;

import nortantis.BackgroundGenerator;
import nortantis.FractalBGGenerator;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanEffect;
import nortantis.SettingsGenerator;
import nortantis.editor.CenterTrees;
import nortantis.editor.FreeIcon;
import nortantis.geom.IntDimension;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.ImageHelper;
import nortantis.util.Tuple2;
import nortantis.util.Tuple4;

@SuppressWarnings("serial")
public class ThemePanel extends JTabbedPane
{
	private MainWindow mainWindow;
	private JSlider coastShadingSlider;
	private JSlider oceanEffectsLevelSlider;
	private JSlider concentricWavesLevelSlider;
	private JRadioButton ripplesRadioButton;
	private JRadioButton shadeRadioButton;
	private JRadioButton concentricWavesButton;
	private JRadioButton fadingConcentricWavesButton;
	private JPanel coastShadingColorDisplay;
	private JPanel coastlineColorDisplay;
	private JSlider coastShadingTransparencySlider;
	private RowHider coastShadingTransparencyHider;
	private JPanel oceanEffectsColorDisplay;
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
	private JComboBox<String> borderTypeComboBox;
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
	private RowHider textHiddenMessageHider;
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
	private JSlider mountainScaleSlider;
	private JSlider hillScaleSlider;
	private JSlider duneScaleSlider;
	private JSlider cityScaleSlider;


	public ThemePanel(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;

		setPreferredSize(new Dimension(SwingHelper.sidePanelPreferredWidth, mainWindow.getContentPane().getHeight()));
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
				updateBackgroundAndRegionFieldStates(mainWindow);
				updateBackgroundImageDisplays();
				handleFullRedraw();
			}
		};

		rdbtnFractal = new JRadioButton("Fractal noise");
		rdbtnFractal.addActionListener(backgroundImageButtonGroupListener);

		rdbtnGeneratedFromTexture = new JRadioButton("Generated from texture");
		rdbtnGeneratedFromTexture.addActionListener(backgroundImageButtonGroupListener);

		ButtonGroup backgoundImageButtonGroup = new ButtonGroup();
		backgoundImageButtonGroup.add(rdbtnGeneratedFromTexture);
		backgoundImageButtonGroup.add(rdbtnFractal);

		organizer.addLabelAndComponentsVertical("Background:", "Select how to generate the background image.",
				Arrays.asList(rdbtnFractal, rdbtnGeneratedFromTexture));

		textureImageFilename = new JTextField();
		textureImageFilename.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				if (new File(textureImageFilename.getText()).exists())
				{
					handleFullRedraw();
				}
			}

			public void removeUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				if (new File(textureImageFilename.getText()).exists())
				{
					handleFullRedraw();
				}
			}

			public void insertUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
				if (new File(textureImageFilename.getText()).exists())
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
				String filename = chooseImageFile(backgroundPanel, FilenameUtils.getFullPath(textureImageFilename.getText()));
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
				updateBackgroundAndRegionFieldStates(mainWindow);
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

		borderTypeComboBox = new JComboBox<String>();
		createMapChangeListenerForFullRedraw(borderTypeComboBox);
		organizer.addLabelAndComponent("Border type:", "The set of images to draw for the border", borderTypeComboBox);

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
		organizer.addLabelAndComponent("Border width:",
				"Width of the border in pixels, scaled according to the resolution the map is drawn at.", borderWidthSlider);

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
		organizer.addLabelAndComponent("Fray shading width:",
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


		grungeColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		grungeColorChooseButton = new JButton("Choose");
		grungeColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				SwingHelper.showColorPicker(borderPanel, grungeColorDisplay, "Grunge Color", () -> handleFrayedEdgeOrGrungeChange());
			}
		});
		organizer.addLabelAndComponentsHorizontal("Edge/Grunge color:", "Grunge and frayed edge shading will be this color",
				Arrays.asList(grungeColorDisplay, grungeColorChooseButton), SwingHelper.colorPickerLeftPadding);

		grungeSlider = new JSlider();
		grungeSlider.setValue(0);
		grungeSlider.setPaintTicks(true);
		grungeSlider.setPaintLabels(true);
		grungeSlider.setMinorTickSpacing(250);
		grungeSlider.setMaximum(2000);
		grungeSlider.setMajorTickSpacing(1000);
		createMapChangeListenerForFrayedEdgeOrGrungeChange(grungeSlider);
		SwingHelper.setSliderWidthForSidePanel(grungeSlider);
		organizer.addLabelAndComponent("Grunge width:", "Determines the width of grunge on the edges of the map. 0 means none.",
				grungeSlider);

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
				"Land near coastlines will be shaded this color. Transparency is supported.",
				Arrays.asList(coastShadingColorDisplay, btnChooseCoastShadingColor), SwingHelper.colorPickerLeftPadding);

		final String message = "<html>Disabled because the land coloring" + " method is '" + LandColoringMethod.ColorPoliticalRegions
				+ "'.<html>";
		coastShadingColorDisabledMessageHider = organizer.addLabelAndComponent(coastShadingColorLabelText, "", new JLabel(message));
		coastShadingColorDisabledMessageHider.setVisible(false);


		{
			coastShadingTransparencySlider = new JSlider(0, 100);
			final int initialValue = 0;
			coastShadingTransparencySlider.setValue(initialValue);
			SwingHelper.setSliderWidthForSidePanel(coastShadingTransparencySlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(coastShadingTransparencySlider, () ->
			{
				updateCoastShadingColorDisplayFromCoastShadingTransparencySlider();
				handleTerrainChange();
			});
			coastShadingTransparencyHider = sliderWithDisplay.addToOrganizer(organizer, "Coast shading transparency:",
					"Transparency of shading of land near coastlines");
		}


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

		ButtonGroup oceanEffectButtonGroup = new ButtonGroup();

		concentricWavesButton = new JRadioButton("Concentric waves");
		oceanEffectButtonGroup.add(concentricWavesButton);
		oceanEffectsListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				concentricWavesLevelSlider.setVisible(concentricWavesButton.isSelected() || fadingConcentricWavesButton.isSelected());
				oceanEffectsLevelSlider.setVisible(ripplesRadioButton.isSelected() || shadeRadioButton.isSelected());
				handleTerrainChange();
			}
		};
		concentricWavesButton.addActionListener(oceanEffectsListener);

		fadingConcentricWavesButton = new JRadioButton("Fading concentric waves");
		oceanEffectButtonGroup.add(fadingConcentricWavesButton);
		fadingConcentricWavesButton.addActionListener(oceanEffectsListener);

		ripplesRadioButton = new JRadioButton("Ripples");
		oceanEffectButtonGroup.add(ripplesRadioButton);
		ripplesRadioButton.addActionListener(oceanEffectsListener);

		shadeRadioButton = new JRadioButton("Shade");
		oceanEffectButtonGroup.add(shadeRadioButton);
		shadeRadioButton.addActionListener(oceanEffectsListener);
		organizer.addLabelAndComponentsVertical("Ocean effects type:", "How to draw either waves or shading in the ocean along coastlines",
				Arrays.asList(concentricWavesButton, fadingConcentricWavesButton, ripplesRadioButton, shadeRadioButton));

		oceanEffectsColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		JButton btnChooseOceanEffectsColor = new JButton("Choose");
		btnChooseOceanEffectsColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(effectsPanel, oceanEffectsColorDisplay, "Ocean Effects Color", () -> handleTerrainChange());
			}
		});
		btnChooseOceanEffectsColor.setToolTipText("Choose a color for ocean effects near coastlines. Transparency is supported.");
		organizer.addLabelAndComponentsHorizontal("Ocean effects color:", "The color of the ocean effects. Transparency is supported.",
				Arrays.asList(oceanEffectsColorDisplay, btnChooseOceanEffectsColor), SwingHelper.colorPickerLeftPadding);

		concentricWavesLevelSlider = new JSlider();
		concentricWavesLevelSlider.setMinimum(1);
		concentricWavesLevelSlider.setPaintTicks(true);
		concentricWavesLevelSlider.setPaintLabels(true);
		concentricWavesLevelSlider.setMinorTickSpacing(1);
		concentricWavesLevelSlider.setMaximum(SettingsGenerator.maxConcentricWaveCountInEditor);
		concentricWavesLevelSlider.setMajorTickSpacing(1);
		createMapChangeListenerForTerrainChange(concentricWavesLevelSlider);
		SwingHelper.setSliderWidthForSidePanel(concentricWavesLevelSlider);

		oceanEffectsLevelSlider = new JSlider();
		oceanEffectsLevelSlider.setMinorTickSpacing(5);
		oceanEffectsLevelSlider.setValue(2);
		oceanEffectsLevelSlider.setPaintTicks(true);
		oceanEffectsLevelSlider.setPaintLabels(true);
		oceanEffectsLevelSlider.setMajorTickSpacing(20);
		createMapChangeListenerForTerrainChange(oceanEffectsLevelSlider);
		SwingHelper.setSliderWidthForSidePanel(oceanEffectsLevelSlider);
		organizer.addLabelAndComponentsVertical("Ocean effects width:", "How far from coastlines ocean effects should extend.",
				Arrays.asList(concentricWavesLevelSlider, oceanEffectsLevelSlider));

		drawOceanEffectsInLakesCheckbox = new JCheckBox("Draw ocean waves/shading in lakes.");
		createMapChangeListenerForTerrainChange(drawOceanEffectsInLakesCheckbox);
		organizer.addLeftAlignedComponent(drawOceanEffectsInLakesCheckbox);
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


		organizer.addSeperator();
		// If I change the maximum here, also update densityScale in IconDrawer.drawTreesForCenters.
		treeHeightSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		treeHeightSlider.setMajorTickSpacing(2);
		treeHeightSlider.setMinorTickSpacing(1);
		treeHeightSlider.setPaintTicks(true);
		treeHeightSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(treeHeightSlider);
		SwingHelper.addListener(treeHeightSlider, () ->
		{
			handleTerrainChange(() -> triggerRebuildAllAnchoredTrees());
		});
		organizer.addLabelAndComponent("Tree height:", "Changes the height of all trees on the map", treeHeightSlider);

		mountainScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		mountainScaleSlider.setMajorTickSpacing(2);
		mountainScaleSlider.setMinorTickSpacing(1);
		mountainScaleSlider.setPaintTicks(true);
		mountainScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(mountainScaleSlider);
		createMapChangeListenerForTerrainChange(mountainScaleSlider);
		organizer.addLabelAndComponent("Mountain size:", "Changes the size of all mountains on the map", mountainScaleSlider);

		hillScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		hillScaleSlider.setMajorTickSpacing(2);
		hillScaleSlider.setMinorTickSpacing(1);
		hillScaleSlider.setPaintTicks(true);
		hillScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(hillScaleSlider);
		createMapChangeListenerForTerrainChange(hillScaleSlider);
		organizer.addLabelAndComponent("Hill size:", "Changes the size of all hills on the map", hillScaleSlider);

		duneScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		duneScaleSlider.setMajorTickSpacing(2);
		duneScaleSlider.setMinorTickSpacing(1);
		duneScaleSlider.setPaintTicks(true);
		duneScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(duneScaleSlider);
		createMapChangeListenerForTerrainChange(duneScaleSlider);
		organizer.addLabelAndComponent("Dune size:", "Changes the size of all sand dunes on the map", duneScaleSlider);

		cityScaleSlider = new JSlider(minScaleSliderValue, maxScaleSliderValue);
		cityScaleSlider.setMajorTickSpacing(2);
		cityScaleSlider.setMinorTickSpacing(1);
		cityScaleSlider.setPaintTicks(true);
		cityScaleSlider.setPaintLabels(true);
		SwingHelper.setSliderWidthForSidePanel(cityScaleSlider);
		createMapChangeListenerForTerrainChange(cityScaleSlider);
		organizer.addLabelAndComponent("City size:", "Changes the size of all cities on the map", cityScaleSlider);


		organizer.addVerticalFillerRow();
		return organizer.createScrollPane();
	}
	
	private void triggerRebuildAllAnchoredTrees()
	{
		Random rand = new Random();
		for (int centerIndex : mainWindow.edits.freeIcons.iterateTreeAnchors())
		{
			List<FreeIcon> trees = mainWindow.edits.freeIcons.getTrees(centerIndex);
			if (trees == null || trees.isEmpty())
			{
				continue;
			}
			
			String treeType = trees.get(0).groupId;
			assert treeType != null && !treeType.isEmpty();
			
			double density = trees.stream().mapToDouble(t -> t.density).average().getAsDouble();
			assert density > 0;
			
			CenterTrees cTrees = new CenterTrees(treeType, density, rand.nextLong());
			mainWindow.edits.centerEdits.get(centerIndex).setTreesWithLock(cTrees);
		}
	}

	private void updateCoastShadingColorDisplayFromCoastShadingTransparencySlider()
	{
		Color background = coastShadingColorDisplay.getBackground();
		int alpha = (int) ((1.0 - coastShadingTransparencySlider.getValue() / 100.0) * 255);
		coastShadingColorDisplay.setBackground(new Color(background.getRed(), background.getGreen(), background.getBlue(), alpha));
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
		textHiddenMessageHider = organizer.addLeftAlignedComponent(
				new JLabel("<html>Text is currently hidden because the selected editing tool does not display text.</html>"));
		showOrHideTextHiddenMessage();
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
				showOrHideTextHiddenMessage();
				handleTextChange();
			}
		};

		enableTextCheckBox.addActionListener(enableTextCheckboxActionListener);

		organizer.addVerticalFillerRow();
		organizer.addLeftAlignedComponent(Box.createHorizontalStrut(100));
		return organizer.createScrollPane();
	}

	void showOrHideTextHiddenMessage()
	{
		boolean currentToolSupportsText = true;
		if (mainWindow.toolsPanel != null && mainWindow.toolsPanel.currentTool != null)
		{
			currentToolSupportsText = mainWindow.toolsPanel.currentTool.shouldShowTextWhenTextIsEnabled();
		}
		textHiddenMessageHider.setVisible(enableTextCheckBox.isSelected() && !currentToolSupportsText);
	}

	private void updateDrawRegionsCheckboxEnabledAndSelected()
	{
		if (!landSupportsColoring())
		{
			landColoringMethodComboBox.setSelectedItem(LandColoringMethod.SingleColor);
		}

		handleEnablingAndDisabling();
	}

	private boolean landSupportsColoring()
	{
		return rdbtnFractal.isSelected() || (rdbtnGeneratedFromTexture.isSelected() && colorizeLandCheckbox.isSelected());
	}

	private boolean oceanSupportsColoring()
	{
		return rdbtnFractal.isSelected() || (rdbtnGeneratedFromTexture.isSelected() && colorizeOceanCheckbox.isSelected());
	}

	private void updateBackgroundAndRegionFieldStates(MainWindow mainWindow)
	{
		textureImageHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeLandCheckboxHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeOceanCheckboxHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		handleEnablingAndDisabling();

		updateDrawRegionsCheckboxEnabledAndSelected();
	}

	private void updateBackgroundImageDisplays()
	{
		IntDimension size = new IntDimension(backgroundDisplaySize.width, backgroundDisplaySize.height);

		SwingWorker<Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm>, Void> worker = new SwingWorker<Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm>, Void>()
		{

			@Override
			protected Tuple4<Image, ImageHelper.ColorifyAlgorithm, Image, ImageHelper.ColorifyAlgorithm> doInBackground() throws Exception
			{
				long seed = parseBackgroundSeed();
				return createBackgroundImageDisplaysImages(size, seed, colorizeOceanCheckbox.isSelected(),
						colorizeLandCheckbox.isSelected(), rdbtnFractal.isSelected(), rdbtnGeneratedFromTexture.isSelected(),
						textureImageFilename.getText());
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
			String textureImageFileName)
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
				texture = ImageHelper.read(textureImageFileName);

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
		else
		{
			oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
			landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
			oceanBackground = landBackground = ImageHelper.createBlackImage(size.width, size.height);
		}

		return new Tuple4<>(oceanBackground, oceanColorifyAlgorithm, landBackground, landColorifyAlgorithm);
	}

	private static String chooseImageFile(Component parent, String curFolder)
	{
		File currentFolder = new File(curFolder);
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(currentFolder);
		fileChooser.setFileFilter(new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return null;
			}

			@Override
			public boolean accept(File f)
			{
				String extension = FilenameUtils.getExtension(f.getName()).toLowerCase();
				return f.isDirectory() || extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg");
			}
		});
		int status = fileChooser.showOpenDialog(parent);
		if (status == JFileChooser.APPROVE_OPTION)
		{
			return fileChooser.getSelectedFile().toString();
		}
		return null;
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
		oceanEffectsLevelSlider.setValue(settings.oceanEffectsLevel);
		concentricWavesLevelSlider.setValue(settings.concentricWaveCount);
		ripplesRadioButton.setSelected(settings.oceanEffect == OceanEffect.Ripples);
		shadeRadioButton.setSelected(settings.oceanEffect == OceanEffect.Blur);
		concentricWavesButton.setSelected(settings.oceanEffect == OceanEffect.ConcentricWaves);
		fadingConcentricWavesButton.setSelected(settings.oceanEffect == OceanEffect.FadingConcentricWaves);
		drawOceanEffectsInLakesCheckbox.setSelected(settings.drawOceanEffectsInLakes);
		oceanEffectsListener.actionPerformed(null);
		coastShadingColorDisplay.setBackground(AwtFactory.unwrap(settings.coastShadingColor));
		updateCoastShadingTransparencySliderFromCoastShadingColorDisplay();
		coastlineColorDisplay.setBackground(AwtFactory.unwrap(settings.coastlineColor));
		oceanEffectsColorDisplay.setBackground(AwtFactory.unwrap(settings.oceanEffectsColor));
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
		rdbtnFractal.setSelected(settings.generateBackground);
		updateBackgroundAndRegionFieldStates(mainWindow);

		// Only do this if there is a change so we don't trigger the document listeners unnecessarily.
		if (!textureImageFilename.getText().equals(settings.backgroundTextureImage))
		{
			textureImageFilename.setText(settings.backgroundTextureImage);
		}

		// Only do this if there is a change so we don't trigger the document listeners unnecessarily.
		if (!backgroundSeedTextField.getText().equals(String.valueOf(settings.backgroundRandomSeed)))
		{
			backgroundSeedTextField.setText(String.valueOf(settings.backgroundRandomSeed));
		}

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
		initializeBorderTypeComboBoxItems(settings);
		borderWidthSlider.setValue(settings.borderWidth);
		drawBorderCheckbox.setSelected(settings.drawBorder);
		drawBorderCheckbox.getActionListeners()[0].actionPerformed(null);

		treeHeightSlider.setValue((int) (Math.round((settings.treeHeightScale - 0.1) * 20.0)));
		mountainScaleSlider.setValue(getSliderValueForScale(settings.mountainScale));
		hillScaleSlider.setValue(getSliderValueForScale(settings.hillScale));
		duneScaleSlider.setValue(getSliderValueForScale(settings.duneScale));
		cityScaleSlider.setValue(getSliderValueForScale(settings.cityScale));

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


	private void initializeBorderTypeComboBoxItems(MapSettings settings)
	{
		SwingHelper.initializeComboBoxItems(borderTypeComboBox, MapCreator.getAvailableBorderTypes(settings.customImagesPath),
				settings.borderType);

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

		if (!textureImageFilename.getText().equals(settings.backgroundTextureImage))
		{
			return true;
		}

		if (!landDisplayPanel.getColor().equals(AwtFactory.unwrap(settings.landColor)))
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

	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.coastShadingLevel = coastShadingSlider.getValue();
		settings.oceanEffectsLevel = oceanEffectsLevelSlider.getValue();
		settings.concentricWaveCount = concentricWavesLevelSlider.getValue();
		settings.oceanEffect = ripplesRadioButton.isSelected() ? OceanEffect.Ripples
				: shadeRadioButton.isSelected() ? OceanEffect.Blur
						: concentricWavesButton.isSelected() ? OceanEffect.ConcentricWaves : OceanEffect.FadingConcentricWaves;
		settings.drawOceanEffectsInLakes = drawOceanEffectsInLakesCheckbox.isSelected();
		settings.coastShadingColor = AwtFactory.wrap(coastShadingColorDisplay.getBackground());
		settings.coastlineColor = AwtFactory.wrap(coastlineColorDisplay.getBackground());
		settings.oceanEffectsColor = AwtFactory.wrap(oceanEffectsColorDisplay.getBackground());
		settings.riverColor = AwtFactory.wrap(riverColorDisplay.getBackground());
		settings.drawText = enableTextCheckBox.isSelected();
		settings.frayedBorder = frayedEdgeCheckbox.isSelected();
		settings.frayedBorderColor = AwtFactory.wrap(grungeColorDisplay.getBackground());
		settings.frayedBorderBlurLevel = frayedEdgeShadingSlider.getValue();
		// Make increasing frayed edge values cause the number of polygons to
		// decrease so that the fray gets large with
		// larger values of the slider.
		settings.frayedBorderSize = frayedEdgeSizeSlider.getMaximum() - frayedEdgeSizeSlider.getValue();
		settings.drawGrunge = drawGrungeCheckbox.isSelected();
		settings.grungeWidth = grungeSlider.getValue();
		settings.lineStyle = jaggedLinesButton.isSelected() ? LineStyle.Jagged
				: splinesLinesButton.isSelected() ? LineStyle.Splines : LineStyle.SplinesWithSmoothedCoastlines;

		// Background image settings
		settings.generateBackground = rdbtnFractal.isSelected();
		settings.generateBackgroundFromTexture = rdbtnGeneratedFromTexture.isSelected();
		settings.colorizeOcean = colorizeOceanCheckbox.isSelected();
		settings.colorizeLand = colorizeLandCheckbox.isSelected();
		settings.backgroundTextureImage = textureImageFilename.getText();
		try
		{
			settings.backgroundRandomSeed = Long.parseLong(backgroundSeedTextField.getText());
		}
		catch (NumberFormatException e)
		{
			settings.backgroundRandomSeed = 0;
		}
		settings.oceanColor = AwtFactory.wrap(oceanDisplayPanel.getColor());
		settings.drawRegionColors = areRegionColorsVisible();
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
		settings.borderType = (String) borderTypeComboBox.getSelectedItem();
		settings.borderWidth = borderWidthSlider.getValue();

		settings.treeHeightScale = 0.1 + (treeHeightSlider.getValue() * 0.05);
		settings.mountainScale = getScaleForSliderValue(mountainScaleSlider.getValue());
		settings.hillScale = getScaleForSliderValue(hillScaleSlider.getValue());
		settings.duneScale = getScaleForSliderValue(duneScaleSlider.getValue());
		settings.cityScale = getScaleForSliderValue(cityScaleSlider.getValue());
	}

	private boolean areRegionColorsVisible()
	{
		return landColoringMethodComboBox.getSelectedItem().equals(LandColoringMethod.ColorPoliticalRegions);
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
		handleTerrainChange(null);
	}
	
	private void handleTerrainChange(Runnable preRun)
	{
		mainWindow.handleThemeChange(false);
		mainWindow.undoer.setUndoPoint(UpdateType.Terrain, null);
		mainWindow.updater.createAndShowMapTerrainChange(preRun);
	}

	private void handleFontsChange()
	{
		mainWindow.handleThemeChange(false);
		mainWindow.undoer.setUndoPoint(UpdateType.Fonts, null);
		mainWindow.updater.createAndShowMapFontsChange();
	}

	private void handleTextChange()
	{
		mainWindow.handleThemeChange(false);
		mainWindow.undoer.setUndoPoint(UpdateType.Text, null);
		mainWindow.updater.createAndShowMapTextChange();
	}

	private void createMapChangeListenerForFullRedraw(Component component)
	{
		SwingHelper.addListener(component, () -> handleFullRedraw());
	}

	private void handleFullRedraw()
	{
		mainWindow.handleThemeChange(true);
		mainWindow.undoer.setUndoPoint(UpdateType.Full, null);
		mainWindow.updater.createAndShowMapFull();
	}

	private void createMapChangeListenerForFrayedEdgeOrGrungeChange(Component component)
	{
		SwingHelper.addListener(component, () -> handleFrayedEdgeOrGrungeChange());
	}

	private void handleFrayedEdgeOrGrungeChange()
	{
		mainWindow.handleThemeChange(false);
		mainWindow.undoer.setUndoPoint(UpdateType.GrungeAndFray, null);
		mainWindow.updater.createAndShowMapGrungeOrFrayedEdgeChange();
	}

	private void handleEnablingAndDisabling()
	{
		borderWidthSlider.setEnabled(drawBorderCheckbox.isSelected());
		borderTypeComboBox.setEnabled(drawBorderCheckbox.isSelected());

		frayedEdgeShadingSlider.setEnabled(frayedEdgeCheckbox.isSelected());
		frayedEdgeSizeSlider.setEnabled(frayedEdgeCheckbox.isSelected());

		grungeColorChooseButton.setEnabled(drawGrungeCheckbox.isSelected());
		grungeSlider.setEnabled(drawGrungeCheckbox.isSelected());

		btnChooseBoldBackgroundColor.setEnabled(drawBoldBackgroundCheckbox.isSelected());

		btnTitleFont.setEnabled(enableTextCheckBox.isSelected());
		btnRegionFont.setEnabled(enableTextCheckBox.isSelected());
		btnMountainRangeFont.setEnabled(enableTextCheckBox.isSelected());
		btnOtherMountainsFont.setEnabled(enableTextCheckBox.isSelected());
		btnRiverFont.setEnabled(enableTextCheckBox.isSelected());
		btnChooseTextColor.setEnabled(enableTextCheckBox.isSelected());
		btnChooseBoldBackgroundColor.setEnabled(enableTextCheckBox.isSelected());
		drawBoldBackgroundCheckbox.setEnabled(enableTextCheckBox.isSelected());

		landColoringMethodComboBox.setEnabled(landSupportsColoring());

		btnChooseOceanColor.setEnabled(oceanSupportsColoring());
		btnChooseLandColor.setEnabled(landSupportsColoring());

		btnChooseCoastShadingColor.setEnabled(!areRegionColorsVisible());

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
		initializeBorderTypeComboBoxItems(settings);
	}
}
