package nortantis.swing;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;
import nortantis.BGColorCancelHandler;
import nortantis.BGColorPreviewPanel;
import nortantis.BackgroundGenerator;
import nortantis.FractalBGGenerator;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanEffect;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.JFontChooser;
import nortantis.util.SwingHelper;

@SuppressWarnings("serial")
public class ThemePanel extends JScrollPane
{
	JSlider coastShadingSlider;
	JSlider oceanEffectsLevelSlider;
	JSlider concentricWavesLevelSlider;
	JRadioButton ripplesRadioButton;
	JRadioButton shadeRadioButton;
	JPanel coastShadingColorDisplay;
	JPanel coastlineColorDisplay;
	JPanel oceanEffectsColorDisplay;
	JPanel riverColorDisplay;
	JCheckBox drawTextCheckBox;
	JPanel grungeColorDisplay;
	private JTextField backgroundSeedTextField;
	private JRadioButton rdbtnGeneratedFromTexture;
	private JRadioButton rdbtnFractal;
	private BGColorPreviewPanel oceanDisplayPanel;
	private BGColorPreviewPanel landDisplayPanel;
	private ActionListener backgroundImageButtonGroupListener;
	private Dimension backgroundDisplaySize = new Dimension(200, 100);
	private JCheckBox colorRegionsCheckBox;
	private JSlider grungeSlider;
	private JLabel lblBackgroundRandomSeed;
	private JTextField textureImageFilename;
	private JLabel lblTextureImage;
	private JLabel lblOceanColor;
	private JLabel lblLandColor;
	private JButton btnsBrowseTextureImage;
	private JCheckBox colorizeLandCheckbox;
	private JCheckBox colorizeOceanCheckbox;
	private JButton btnChooseLandColor;
	private JButton btnChooseOceanColor;
	private JButton btnNewBackgroundSeed;
	private ItemListener colorizeCheckboxListener;
	private JComboBox<String> borderTypeComboBox;
	private JSlider borderWidthSlider;
	private JCheckBox drawBorderCheckbox;
	private JLabel lblFrayedEdgeSize;
	private JSlider frayedEdgePolygonCountSlider;
	private JSlider frayedEdgeShadingSlider;
	private JCheckBox frayedEdgeCheckbox;
	public final double cityFrequencySliderScale = 100.0 * 1.0 / SettingsGenerator.maxCityProbabillity;
	private JButton btnChooseCoastShadingColor;
	private JRadioButton jaggedLinesButton;
	private JRadioButton smoothLinesButton;
	private JRadioButton concentricWavesButton;
	private ActionListener oceanEffectsListener;
	private JPanel textureFilePanel;
	JLabel titleFontDisplay;
	JLabel regionFontDisplay;
	JLabel mountainRangeFontDisplay;
	JLabel otherMountainsFontDisplay;
	JLabel riverFontDisplay;
	JPanel textColorDisplay;
	JPanel boldBackgroundColorDisplay;
	private JCheckBox chckbxDrawBoldBackground;
	


	public ThemePanel(MainWindow mainWindow)
	{
		setPreferredSize(new Dimension(SwingHelper.sidePanelWidth, mainWindow.getContentPane().getHeight()));
		JXTaskPaneContainer taskPaneContainer = new JXTaskPaneContainer();
		JScrollPane scrollPane = new JScrollPane(taskPaneContainer);
		add(scrollPane);
		
		taskPaneContainer.add(createBackgroundPane(mainWindow));
		taskPaneContainer.add(createEffectsPane(mainWindow));
		taskPaneContainer.add(createBorderPane(mainWindow));
		taskPaneContainer.add(createFontsPane(mainWindow));
	}
	
	private JXTaskPane createBackgroundPane(MainWindow mainWindow)
	{
		final JXTaskPane backgroundPane = new JXTaskPane("Background");
		backgroundPane.setLayout(new BoxLayout(backgroundPane, BoxLayout.Y_AXIS));

		backgroundImageButtonGroupListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				updateBackgroundAndRegionFieldStates(mainWindow);
			}
		};

		JLabel lblBackground = new JLabel("Background:");
		lblBackground.setToolTipText("Select how to generate the background image.");
		
		rdbtnFractal = new JRadioButton("Fractal noise");
		rdbtnFractal.addActionListener(backgroundImageButtonGroupListener);

		rdbtnGeneratedFromTexture = new JRadioButton("Generated from texture");
		rdbtnGeneratedFromTexture.addActionListener(backgroundImageButtonGroupListener);

		ButtonGroup backgoundImageButtonGroup = new ButtonGroup();
		backgoundImageButtonGroup.add(rdbtnGeneratedFromTexture);
		backgoundImageButtonGroup.add(rdbtnFractal);
		
		SwingHelper.addLabelAndComponentsToPanelVertical(backgroundPane, lblBackground, Arrays.asList(rdbtnFractal, rdbtnGeneratedFromTexture));
		
		
		lblBackgroundRandomSeed = new JLabel("Random seed:");
		lblBackgroundRandomSeed.setToolTipText("The random seed used to generate the background image. Note that the background texture will also change based on the resolution you draw at.");

		backgroundSeedTextField = new JTextField();
		backgroundSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
		backgroundSeedTextField.setColumns(10);

		btnNewBackgroundSeed = new JButton("New Seed");
		btnNewBackgroundSeed.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				backgroundSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));

				// Update the background image for the land/ocean background
				// displays.
				updateBackgroundImageDisplays();
			}
		});
		btnNewBackgroundSeed.setToolTipText("Generate a new random seed.");
		SwingHelper.addLabelAndComponentsToPanelHorizontal(backgroundPane, lblBackgroundRandomSeed, 
				Arrays.asList(backgroundSeedTextField, btnNewBackgroundSeed));
		
		
		colorizeLandCheckbox = new JCheckBox("");
		colorizeLandCheckbox.setToolTipText("Whether to change the land texture to a custom color");

		colorizeCheckboxListener = new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
				updateBackgroundAndRegionFieldStates(mainWindow);
			}
		};
		
		lblLandColor = new JLabel("Land color:");
		lblLandColor.setToolTipText("Choose the land color.");
		
		landDisplayPanel = new BGColorPreviewPanel();
		landDisplayPanel.setLayout(null);
		landDisplayPanel.setSize(backgroundDisplaySize);
		landDisplayPanel.setBackground(Color.WHITE);

		btnChooseLandColor = new JButton("Choose");
		btnChooseLandColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JColorChooser colorChooser = new JColorChooser(landDisplayPanel.getColor());

				colorChooser.getSelectionModel().addChangeListener(landDisplayPanel);
				colorChooser.setPreviewPanel(new JPanel());
				landDisplayPanel.setColorChooser(colorChooser);
				BGColorCancelHandler cancelHandler = new BGColorCancelHandler(landDisplayPanel.getColor(), landDisplayPanel);
				Dialog dialog = JColorChooser.createDialog(mainWindow, "Land Color", false, colorChooser, null, cancelHandler);
				dialog.setVisible(true);
			}
		});
		
		{
			JPanel container = new JPanel();
			container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
			JPanel leftPanel = new JPanel();
			leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
			leftPanel.add(colorizeLandCheckbox);
			leftPanel.add(lblLandColor);
			leftPanel.add(btnChooseLandColor);
			container.add(leftPanel);
			container.add(landDisplayPanel);
			
			backgroundPane.add(container);
		}
		
	
		colorizeOceanCheckbox = new JCheckBox("");
		colorizeOceanCheckbox.setToolTipText("Whether to change the ocean texture to a custom color");
		
		lblOceanColor = new JLabel("Ocean color:");
		lblOceanColor.setToolTipText("Choose the ocean color.");
		
		oceanDisplayPanel = new BGColorPreviewPanel();
		oceanDisplayPanel.setLayout(null);
		oceanDisplayPanel.setSize(backgroundDisplaySize);
		oceanDisplayPanel.setBackground(Color.WHITE);
		
		btnChooseOceanColor = new JButton("Choose");
		btnChooseOceanColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				JColorChooser colorChooser = new JColorChooser(oceanDisplayPanel.getColor());

				colorChooser.getSelectionModel().addChangeListener(oceanDisplayPanel);
				colorChooser.setPreviewPanel(new JPanel());
				oceanDisplayPanel.setColorChooser(colorChooser);
				BGColorCancelHandler cancelHandler = new BGColorCancelHandler(oceanDisplayPanel.getColor(), oceanDisplayPanel);
				Dialog dialog = JColorChooser.createDialog(mainWindow, "Ocean Color", false, colorChooser, null, cancelHandler);
				dialog.setVisible(true);
			}
		});
				
		{
			JPanel container = new JPanel();
			container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
			JPanel leftPanel = new JPanel();
			leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
			leftPanel.add(colorizeOceanCheckbox);
			leftPanel.add(lblOceanColor);
			leftPanel.add(btnChooseOceanColor);
			container.add(leftPanel);
			container.add(oceanDisplayPanel);
			
			backgroundPane.add(container);
		}


		lblTextureImage = new JLabel("Texture image:");
		backgroundPane.add(lblTextureImage);

		textureImageFilename = new JTextField();
		textureImageFilename.setColumns(10);
		textureImageFilename.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
			}

			public void removeUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
			}

			public void insertUpdate(DocumentEvent e)
			{
				updateBackgroundImageDisplays();
			}
		});

		btnsBrowseTextureImage = new JButton("Browse");
		btnsBrowseTextureImage.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String filename = chooseImageFile(backgroundPane, textureImageFilename.getText());
				if (filename != null)
					textureImageFilename.setText(filename);
			}
		});
		
		textureFilePanel = new JPanel();
		textureFilePanel.setLayout(new BoxLayout(textureFilePanel, BoxLayout.Y_AXIS));
		textureFilePanel.add(textureImageFilename);
		textureFilePanel.add(btnsBrowseTextureImage);
		
		return backgroundPane;
	}		
	
	private JXTaskPane createEffectsPane(MainWindow mainWindow)
	{
		final JXTaskPane effectsPane = new JXTaskPane("Effects");
		effectsPane.setLayout(new BoxLayout(effectsPane, BoxLayout.Y_AXIS));

		JLabel lblLineStyle = new JLabel("Line style:");

		jaggedLinesButton = new JRadioButton("Jagged");
		effectsPane.add(jaggedLinesButton);
		smoothLinesButton = new JRadioButton("Smooth");
		effectsPane.add(smoothLinesButton);
		ButtonGroup lineStyleButtonGroup = new ButtonGroup();
		lineStyleButtonGroup.add(jaggedLinesButton);
		lineStyleButtonGroup.add(smoothLinesButton);
		SwingHelper.addLabelAndComponentsToPanelVertical(effectsPane, lblLineStyle, 
				Arrays.asList(jaggedLinesButton, smoothLinesButton));
		
		
		colorRegionsCheckBox = new JCheckBox("Color political regions");
		colorRegionsCheckBox.setToolTipText(
				"When checked, political region borders and background will be colored.");
		colorRegionsCheckBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mainWindow.handleColorRegionsChanged(colorizeLandCheckbox.isSelected());
				btnChooseCoastShadingColor.setEnabled(!colorRegionsCheckBox.isSelected());
				final String message = "Coast shading color selection is disabled because it will use the region color when draw regions is checked.";
				if (colorRegionsCheckBox.isSelected())
				{
					addToTooltip(btnChooseCoastShadingColor, message);
				}
				else
				{
					removeFromToolTip(btnChooseCoastShadingColor, message);
				}
			}
		});
		effectsPane.add(colorRegionsCheckBox);
		
		JLabel lblCoastShadingColor = new JLabel("Coast shading color:");
		
		coastShadingColorDisplay = new JPanel();
		coastShadingColorDisplay.setSize(82, 23);

		btnChooseCoastShadingColor = new JButton("Choose");
		btnChooseCoastShadingColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showColorPicker(effectsPane, coastShadingColorDisplay, "Coast Shading Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPane, lblCoastShadingColor, 
				Arrays.asList(coastlineColorDisplay, btnChooseCoastShadingColor));

		
		JLabel lblCoastlineShading = new JLabel("Coast shading:");
		lblCoastlineShading.setToolTipText("How far in from coastlines to shade land.");

		coastShadingSlider = new JSlider();
		coastShadingSlider.setValue(30);
		coastShadingSlider.setPaintTicks(true);
		coastShadingSlider.setPaintLabels(true);
		coastShadingSlider.setMinorTickSpacing(5);
		coastShadingSlider.setMaximum(100);
		coastShadingSlider.setMajorTickSpacing(20);
		SwingHelper.addLabelAndComponentToPanel(effectsPane, lblCoastlineShading, coastShadingSlider);
		
		
		JLabel lblCoastlineColor = new JLabel("Coastline color:");

		coastlineColorDisplay = new JPanel();
		coastlineColorDisplay.setSize(82, 23);

		JButton buttonChooseCoastlineColor = new JButton("Choose");
		buttonChooseCoastlineColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showColorPicker(effectsPane, coastlineColorDisplay, "Coastline Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPane, lblCoastlineColor, 
				Arrays.asList(coastlineColorDisplay, buttonChooseCoastlineColor));
		

		JLabel lblOceanEffectType = new JLabel("Ocean effects type:");
		
		ButtonGroup oceanEffectButtonGroup = new ButtonGroup();

		concentricWavesButton = new JRadioButton("Concentric waves");
		oceanEffectButtonGroup.add(concentricWavesButton);
		oceanEffectsListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				concentricWavesLevelSlider.setVisible(concentricWavesButton.isSelected());
				oceanEffectsLevelSlider.setVisible(!concentricWavesButton.isSelected());
			}
		};
		concentricWavesButton.addActionListener(oceanEffectsListener);

		ripplesRadioButton = new JRadioButton("Ripples");
		effectsPane.add(ripplesRadioButton);
		oceanEffectButtonGroup.add(ripplesRadioButton);
		ripplesRadioButton.addActionListener(oceanEffectsListener);

		shadeRadioButton = new JRadioButton("Shade");
		effectsPane.add(shadeRadioButton);
		oceanEffectButtonGroup.add(shadeRadioButton);
		shadeRadioButton.addActionListener(oceanEffectsListener);
		SwingHelper.addLabelAndComponentsToPanelVertical(effectsPane, lblOceanEffectType, 
				Arrays.asList(concentricWavesButton, ripplesRadioButton, shadeRadioButton));

		JLabel lblOceanEffectsColor = new JLabel("Ocean effects color:");
		effectsPane.add(lblOceanEffectsColor);

		oceanEffectsColorDisplay = new JPanel();
		oceanEffectsColorDisplay.setSize(82, 23);
		
		JButton btnChooseOceanEffectsColor = new JButton("Choose");
		btnChooseOceanEffectsColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showColorPicker(effectsPane, oceanEffectsColorDisplay, "Ocean Effects Color");
			}
		});
		btnChooseOceanEffectsColor.setToolTipText("Choose a color for ocean effects near coastlines. Transparency is supported.");
		effectsPane.add(btnChooseOceanEffectsColor);
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPane, lblOceanEffectsColor, 
				Arrays.asList(oceanEffectsColorDisplay, btnChooseOceanEffectsColor));
		
		JLabel lblOceanEffectsLevel = new JLabel("Ocean effects level:");
		lblOceanEffectsLevel.setToolTipText("How far from coastlines ocean effects should extend.");

		concentricWavesLevelSlider = new JSlider();
		concentricWavesLevelSlider.setMinimum(1);
		concentricWavesLevelSlider.setValue(30);
		concentricWavesLevelSlider.setPaintTicks(true);
		concentricWavesLevelSlider.setPaintLabels(true);
		concentricWavesLevelSlider.setMinorTickSpacing(1);
		concentricWavesLevelSlider.setMaximum(4);
		concentricWavesLevelSlider.setMajorTickSpacing(1);

		oceanEffectsLevelSlider = new JSlider();
		oceanEffectsLevelSlider.setMinorTickSpacing(5);
		oceanEffectsLevelSlider.setValue(2);
		oceanEffectsLevelSlider.setPaintTicks(true);
		oceanEffectsLevelSlider.setPaintLabels(true);
		oceanEffectsLevelSlider.setMajorTickSpacing(20);
		SwingHelper.addLabelAndComponentsToPanelVertical(effectsPane, lblOceanEffectsLevel, 
				Arrays.asList(concentricWavesLevelSlider, oceanEffectsLevelSlider));


		JLabel lblRiverColor = new JLabel("River color:");
		lblRiverColor.setToolTipText("Rivers will be drawn this color.");

		riverColorDisplay = new JPanel();
		riverColorDisplay.setSize(82, 23);

		JButton riverColorChooseButton = new JButton("Choose");
		riverColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showColorPicker(effectsPane, riverColorDisplay, "River Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPane, lblRiverColor, 
				Arrays.asList(riverColorDisplay, riverColorChooseButton));
		

		JLabel grungeColorLabel = new JLabel("Edge/Grunge color:");
		grungeColorLabel.setToolTipText("Frayed edges and grunge will be this color");

		grungeColorDisplay = new JPanel();
		grungeColorDisplay.setSize(82, 23);

		final JButton grungeColorChooseButton = new JButton("Choose");
		grungeColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				showColorPicker(effectsPane, grungeColorDisplay, "Grunge Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPane, grungeColorLabel, 
				Arrays.asList(grungeColorDisplay, grungeColorChooseButton));


		JLabel lblGrunge = new JLabel("Grunge:");
		lblGrunge.setToolTipText("Determines the width of grunge on the edges of the map. 0 means none. ");

		grungeSlider = new JSlider();
		grungeSlider.setValue(0);
		grungeSlider.setPaintTicks(true);
		grungeSlider.setPaintLabels(true);
		grungeSlider.setMinorTickSpacing(100);
		grungeSlider.setMaximum(2000);
		grungeSlider.setMajorTickSpacing(500);
		SwingHelper.addLabelAndComponentToPanel(effectsPane, lblGrunge, grungeSlider);
		
		return effectsPane;
	}
	
	private JXTaskPane createBorderPane(MainWindow mainWindow)
	{
		final JXTaskPane borderPane = new JXTaskPane("Border");
		borderPane.setLayout(new BoxLayout(borderPane, BoxLayout.Y_AXIS));

		drawBorderCheckbox = new JCheckBox("Draw border");
		drawBorderCheckbox.setToolTipText("When checked, a border will be drawn around the map.");
		drawBorderCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				borderWidthSlider.setEnabled(drawBorderCheckbox.isSelected());
				borderTypeComboBox.setEnabled(drawBorderCheckbox.isSelected());
			}
		});
		borderPane.add(drawBorderCheckbox);
		

		JLabel lblBorderType = new JLabel("Border type:");
		lblBorderType.setToolTipText("The set of images to draw for the border");

		borderTypeComboBox = new JComboBox<String>();
		SwingHelper.addLabelAndComponentToPanel(borderPane, lblBorderType, borderTypeComboBox);

		
		JLabel lblBorderWidth = new JLabel("Border width:");
		lblBorderWidth.setToolTipText("Width of the border in pixels, scaled if resolution is scaled");

		borderWidthSlider = new JSlider();
		borderWidthSlider.setToolTipText("");
		borderWidthSlider.setValue(100);
		borderWidthSlider.setSnapToTicks(false);
		borderWidthSlider.setPaintTicks(true);
		borderWidthSlider.setPaintLabels(true);
		borderWidthSlider.setMinorTickSpacing(50);
		borderWidthSlider.setMaximum(800);
		borderWidthSlider.setMajorTickSpacing(200);
		SwingHelper.addLabelAndComponentToPanel(borderPane, lblBorderWidth, borderWidthSlider);


		frayedEdgeCheckbox = new JCheckBox("Draw frayed edges");
		frayedEdgeCheckbox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				frayedEdgeShadingSlider.setEnabled(frayedEdgeCheckbox.isSelected());
				frayedEdgePolygonCountSlider.setEnabled(frayedEdgeCheckbox.isSelected());
			}
		});
		borderPane.add(frayedEdgeCheckbox);

		
		JLabel lblFrayedEdgeShading = new JLabel("Shading width:");
		lblFrayedEdgeShading.setToolTipText("The width of shading drawn around frayed edges. The color used is the grunge color.");

		frayedEdgeShadingSlider = new JSlider();
		frayedEdgeShadingSlider.setValue(30);
		frayedEdgeShadingSlider.setPaintTicks(true);
		frayedEdgeShadingSlider.setPaintLabels(true);
		frayedEdgeShadingSlider.setMinorTickSpacing(50);
		frayedEdgeShadingSlider.setMaximum(500);
		frayedEdgeShadingSlider.setMajorTickSpacing(100);
		SwingHelper.addLabelAndComponentToPanel(borderPane, lblFrayedEdgeShading, frayedEdgeShadingSlider);
		

		lblFrayedEdgeSize = new JLabel("Polygon count:");
		lblFrayedEdgeSize.setToolTipText("The number of polygons used when creating the frayed border. "
				+ "Higher values make the fray smaller.");

		frayedEdgePolygonCountSlider = new JSlider();
		frayedEdgePolygonCountSlider.setPaintTicks(true);
		frayedEdgePolygonCountSlider.setPaintLabels(true);
		frayedEdgePolygonCountSlider.setMinorTickSpacing(5000);
		frayedEdgePolygonCountSlider.setMaximum(50000);
		frayedEdgePolygonCountSlider.setMinimum(100);
		frayedEdgePolygonCountSlider.setMajorTickSpacing(20000);
		SwingHelper.addLabelAndComponentToPanel(borderPane, lblFrayedEdgeSize, frayedEdgePolygonCountSlider);
		
		return borderPane;
	}
	
	private JXTaskPane createFontsPane(MainWindow mainWindow)
	{
		final JXTaskPane fontsPane = new JXTaskPane("Fonts");
		fontsPane.setLayout(new BoxLayout(fontsPane, BoxLayout.Y_AXIS));

		drawTextCheckBox = new JCheckBox("Draw text");
		drawTextCheckBox.setToolTipText("Enable/disable drawing of generated names.");
		fontsPane.add(drawTextCheckBox);
		
		
		JLabel lblTitleFont = new JLabel("Title font:");

		titleFontDisplay = new JLabel("");

		final JButton btnTitleFont = new JButton("Choose");
		btnTitleFont.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				runFontChooser(fontsPane, titleFontDisplay);
			}
		});
		SwingHelper.addLabelAndComponentsToPanelVertical(fontsPane, lblTitleFont, 
				Arrays.asList(titleFontDisplay, btnTitleFont));
		
		
		JLabel lblRegionFont = new JLabel("Region font:");

		regionFontDisplay = new JLabel("");

		final JButton btnRegionFont = new JButton("Choose");
		btnRegionFont.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				runFontChooser(fontsPane, regionFontDisplay);
			}
		});
		SwingHelper.addLabelAndComponentsToPanelVertical(fontsPane, lblRegionFont, 
				Arrays.asList(regionFontDisplay, btnRegionFont));



		JLabel lblMountainRangeFont = new JLabel("Mountain range font:");

		mountainRangeFontDisplay = new JLabel("");

		final JButton btnMountainRangeFont = new JButton("Choose");
		btnMountainRangeFont.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				runFontChooser(fontsPane, mountainRangeFontDisplay);
			}
		});
		SwingHelper.addLabelAndComponentsToPanelVertical(fontsPane, lblMountainRangeFont, 
				Arrays.asList(mountainRangeFontDisplay, btnMountainRangeFont));

		

		JLabel lblMountainGroupFont = new JLabel("Cities/mountains font:");

		otherMountainsFontDisplay = new JLabel("");

		final JButton btnOtherMountainsFont = new JButton("Choose");
		btnOtherMountainsFont.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				runFontChooser(fontsPane, otherMountainsFontDisplay);
			}
		});
		SwingHelper.addLabelAndComponentsToPanelVertical(fontsPane, lblMountainGroupFont, 
				Arrays.asList(otherMountainsFontDisplay, btnOtherMountainsFont));
		

		JLabel lblRiverFont = new JLabel("River font:");

		riverFontDisplay = new JLabel("");

		final JButton btnRiverFont = new JButton("Choose");
		btnRiverFont.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				runFontChooser(fontsPane, riverFontDisplay);
			}
		});
		SwingHelper.addLabelAndComponentsToPanelVertical(fontsPane, lblRiverFont, 
				Arrays.asList(riverFontDisplay, btnRiverFont));

		

		JLabel lblTextColor = new JLabel("Text color:");

		textColorDisplay = new JPanel();
		textColorDisplay.setSize(82, 23);

		final JButton btnChooseTextColor = new JButton("Choose");
		btnChooseTextColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showColorPickerWithPreviewPanel(fontsPane, textColorDisplay, "Text Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(fontsPane, lblTextColor, 
				Arrays.asList(textColorDisplay, btnChooseTextColor));
		

		chckbxDrawBoldBackground = new JCheckBox("Draw bold background");
		fontsPane.add(chckbxDrawBoldBackground);
		
		
		JLabel lblBoldBackgroundColor = new JLabel("Bold background color:");
		lblBoldBackgroundColor.setToolTipText("Title and region names will be given a bold background in this color.");

		boldBackgroundColorDisplay = new JPanel();
		boldBackgroundColorDisplay.setBackground(new Color(244, 226, 194));
		boldBackgroundColorDisplay.setSize(82, 23);

		final JButton btnChooseBoldBackgroundColor = new JButton("Choose");
		btnChooseBoldBackgroundColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				showColorPickerWithPreviewPanel(fontsPane, boldBackgroundColorDisplay, "Bold Background Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(fontsPane, lblBoldBackgroundColor, 
				Arrays.asList(boldBackgroundColorDisplay, btnChooseBoldBackgroundColor));
		
		
		chckbxDrawBoldBackground.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				btnChooseBoldBackgroundColor.setEnabled(chckbxDrawBoldBackground.isSelected());
			}
		});


		drawTextCheckBox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				btnTitleFont.setEnabled(drawTextCheckBox.isSelected());
				btnRegionFont.setEnabled(drawTextCheckBox.isSelected());
				btnMountainRangeFont.setEnabled(drawTextCheckBox.isSelected());
				btnOtherMountainsFont.setEnabled(drawTextCheckBox.isSelected());
				btnRiverFont.setEnabled(drawTextCheckBox.isSelected());
				btnChooseTextColor.setEnabled(drawTextCheckBox.isSelected());
				btnChooseBoldBackgroundColor.setEnabled(drawTextCheckBox.isSelected());
				chckbxDrawBoldBackground.setEnabled(drawTextCheckBox.isSelected());
				
				mainWindow.handleDrawTextChanged(drawTextCheckBox.isSelected());
			}
		});

		return fontsPane;
	}

	private void updateDrawRegionsCheckboxEnabledAndSelected()
	{
		if (rdbtnFractal.isSelected() || (rdbtnGeneratedFromTexture.isSelected() && colorizeLandCheckbox.isSelected()))
		{
			colorRegionsCheckBox.setEnabled(true);
		}
		else
		{
			colorRegionsCheckBox.setSelected(false);
			colorRegionsCheckBox.setEnabled(false);
		}
	}

	private void updateBackgroundAndRegionFieldStates(MainWindow mainWindow)
	{
		lblTextureImage.setVisible(rdbtnGeneratedFromTexture.isSelected());
		textureImageFilename.setVisible(rdbtnGeneratedFromTexture.isSelected());
		btnsBrowseTextureImage.setVisible(rdbtnGeneratedFromTexture.isSelected());
		textureFilePanel.setVisible(rdbtnGeneratedFromTexture.isSelected());;
		colorizeOceanCheckbox.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeLandCheckbox.setVisible(rdbtnGeneratedFromTexture.isSelected());
		btnChooseOceanColor.setEnabled(colorizeOceanCheckbox.isSelected());
		btnChooseLandColor.setEnabled(colorizeLandCheckbox.isSelected());

		updateDrawRegionsCheckboxEnabledAndSelected();
		mainWindow.handleColorRegionsChanged(colorRegionsCheckBox.isSelected());

		updateBackgroundImageDisplays();
	}

	private void updateBackgroundImageDisplays()
	{
		Dimension bounds = new Dimension(backgroundDisplaySize.width, backgroundDisplaySize.height * 2);

		BufferedImage oceanBackground;
		BufferedImage landBackground;
		if (rdbtnFractal.isSelected())
		{
			oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.algorithm2);
			landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.algorithm2);

			oceanBackground = landBackground = FractalBGGenerator.generate(new Random(Integer.parseInt(backgroundSeedTextField.getText())),
					1.3f, bounds.width, bounds.height, 0.75f);
		}
		else if (rdbtnGeneratedFromTexture.isSelected())
		{
			BufferedImage texture;
			try
			{
				texture = ImageHelper.read(textureImageFilename.getText());

				if (colorizeOceanCheckbox.isSelected())
				{
					oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.algorithm3);

					oceanBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(Integer.parseInt(backgroundSeedTextField.getText())), ImageHelper.convertToGrayscale(texture),
							bounds.height, bounds.width);
				}
				else
				{
					oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);

					oceanBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(Integer.parseInt(backgroundSeedTextField.getText())), texture, bounds.height,
							bounds.width);
				}

				if (colorizeLandCheckbox.isSelected() == colorizeOceanCheckbox.isSelected())
				{
					// No need to generate the same image twice.
					landBackground = oceanBackground;
					if (colorizeLandCheckbox.isSelected())
					{
						landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.algorithm3);
					}
					else
					{
						landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
					}
				}
				else
				{
					if (colorizeLandCheckbox.isSelected())
					{
						landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.algorithm3);

						landBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
								new Random(Integer.parseInt(backgroundSeedTextField.getText())), ImageHelper.convertToGrayscale(texture),
								bounds.height, bounds.width);
					}
					else
					{
						landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);

						landBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
								new Random(Integer.parseInt(backgroundSeedTextField.getText())), texture, bounds.height,
								bounds.width);
					}
				}
			}
			catch (RuntimeException e)
			{
				oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
				landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
				oceanBackground = landBackground = new BufferedImage(bounds.width, bounds.height,
						BufferedImage.TYPE_INT_ARGB);
			}
		}
		else
		{
			oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
			landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
			oceanBackground = landBackground = ImageHelper.createWhiteTransparentImage(bounds.width, bounds.height);
		}

		oceanDisplayPanel.setImage(
				ImageHelper.extractRegion(oceanBackground, 0, 0, oceanBackground.getWidth(), oceanDisplayPanel.getHeight() / 2));
		oceanDisplayPanel.setSize(new Dimension(oceanBackground.getWidth(), oceanBackground.getHeight() / 2));
		oceanDisplayPanel.repaint();

		landDisplayPanel.setImage(
				ImageHelper.extractRegion(landBackground, 0, landBackground.getHeight() / 2, landBackground.getWidth(), 
						landDisplayPanel.getHeight() / 2));
		landDisplayPanel.setSize(new Dimension(landBackground.getWidth(), landBackground.getHeight() / 2));
		landDisplayPanel.repaint();
	}



	private static String chooseImageFile(JComponent parent, String curFolder)
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
				return true;
			}
		});
		int status = fileChooser.showOpenDialog(parent);
		if (status == JFileChooser.APPROVE_OPTION)
		{
			return fileChooser.getSelectedFile().toString();
		}
		return null;
	}

	private static void runFontChooser(JComponent parent, JLabel fontDisplay)
	{
		JFontChooser fontChooser = new JFontChooser();
		fontChooser.setSelectedFont(fontDisplay.getFont());
		int status = fontChooser.showDialog(parent);
		if (status == JFontChooser.OK_OPTION)
		{
			Font font = fontChooser.getSelectedFont();
			fontDisplay.setText(font.getFontName());
			fontDisplay.setFont(font);
		}
	}

	private static void showColorPicker(JComponent parent, final JPanel colorDisplay, String title)
	{
		final JColorChooser colorChooser = new JColorChooser(colorDisplay.getBackground());
		colorChooser.setPreviewPanel(new JPanel());

		ActionListener okHandler = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorDisplay.setBackground(colorChooser.getColor());
			}

		};
		Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false, colorChooser, okHandler, null);
		dialog.setVisible(true);

	}

	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title)
	{
		Color c = JColorChooser.showDialog(parent, "", colorDisplay.getBackground());
		if (c != null)
			colorDisplay.setBackground(c);
	}

	/**
	 * Loads a map settings file into the GUI.
	 * 
	 * @param path
	 */
	public void loadSettingsIntoGUI(MapSettings settings)
	{
		coastShadingSlider.setValue(settings.coastShadingLevel);
		oceanEffectsLevelSlider.setValue(settings.oceanEffectsLevel);
		concentricWavesLevelSlider.setValue(settings.concentricWaveCount);
		ripplesRadioButton.setSelected(settings.oceanEffect == OceanEffect.Ripples);
		shadeRadioButton.setSelected(settings.oceanEffect == OceanEffect.Blur);
		concentricWavesButton.setSelected(settings.oceanEffect == OceanEffect.ConcentricWaves);
		oceanEffectsListener.actionPerformed(null);
		coastShadingColorDisplay.setBackground(settings.coastShadingColor);
		coastlineColorDisplay.setBackground(settings.coastlineColor);
		oceanEffectsColorDisplay.setBackground(settings.oceanEffectsColor);
		riverColorDisplay.setBackground(settings.riverColor);
		frayedEdgeCheckbox.setSelected(!settings.frayedBorder);
		// Do a click here to update other components on the panel as enabled or
		// disabled.
		frayedEdgeCheckbox.doClick();
		grungeColorDisplay.setBackground(settings.frayedBorderColor);
		frayedEdgeShadingSlider.setValue(settings.frayedBorderBlurLevel);
		frayedEdgePolygonCountSlider.setValue(settings.frayedBorderSize);
		grungeSlider.setValue(settings.grungeWidth);
		if (settings.lineStyle.equals(LineStyle.Jagged))
		{
			jaggedLinesButton.setSelected(true);
		}
		else if (settings.lineStyle.equals(LineStyle.Smooth))
		{
			smoothLinesButton.setSelected(true);
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
		backgroundImageButtonGroupListener.actionPerformed(null);
		textureImageFilename.setText(settings.backgroundTextureImage);
		backgroundSeedTextField.setText(String.valueOf(settings.backgroundRandomSeed));
		oceanDisplayPanel.setColor(settings.oceanColor);
		landDisplayPanel.setColor(settings.landColor);

		colorRegionsCheckBox.setSelected(!settings.drawRegionColors);
		colorRegionsCheckBox.doClick();
		// doClick seems to be ignored if the checkbox is disabled, so I must
		// set the value again.
		colorRegionsCheckBox.setSelected(settings.drawRegionColors);

		// Do a click to update other components on the panel as enabled or
		// disabled.
		drawTextCheckBox.setSelected(!settings.drawText);
		drawTextCheckBox.doClick();

		titleFontDisplay.setFont(settings.titleFont);
		titleFontDisplay.setText(settings.titleFont.getName());
		regionFontDisplay.setFont(settings.regionFont);
		regionFontDisplay.setText(settings.regionFont.getName());
		mountainRangeFontDisplay.setFont(settings.mountainRangeFont);
		mountainRangeFontDisplay.setText(settings.mountainRangeFont.getName());
		otherMountainsFontDisplay.setFont(settings.otherMountainsFont);
		otherMountainsFontDisplay.setText(settings.otherMountainsFont.getName());
		riverFontDisplay.setFont(settings.riverFont);
		riverFontDisplay.setText(settings.riverFont.getName());
		textColorDisplay.setBackground(settings.textColor);
		boldBackgroundColorDisplay.setBackground(settings.boldBackgroundColor);
		chckbxDrawBoldBackground.setSelected(!settings.drawBoldBackground);
		chckbxDrawBoldBackground.doClick();

		// Borders
		SwingHelper.initializeComboBoxItems(borderTypeComboBox, MapCreator.getAvailableBorderTypes(), settings.borderType);
		borderWidthSlider.setValue(settings.borderWidth);
		drawBorderCheckbox.setSelected(!settings.drawBorder);
		drawBorderCheckbox.doClick();

		updateBackgroundImageDisplays();

	}

	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.coastShadingLevel = coastShadingSlider.getValue();
		settings.oceanEffectsLevel = oceanEffectsLevelSlider.getValue();
		settings.concentricWaveCount = concentricWavesLevelSlider.getValue();
		settings.oceanEffect = ripplesRadioButton.isSelected() ? OceanEffect.Ripples
				: shadeRadioButton.isSelected() ? OceanEffect.Blur : OceanEffect.ConcentricWaves;
		settings.coastShadingColor = coastShadingColorDisplay.getBackground();
		settings.coastlineColor = coastlineColorDisplay.getBackground();
		settings.oceanEffectsColor = oceanEffectsColorDisplay.getBackground();
		settings.riverColor = riverColorDisplay.getBackground();
		settings.drawText = drawTextCheckBox.isSelected();
		settings.frayedBorder = frayedEdgeCheckbox.isSelected();
		settings.frayedBorderColor = grungeColorDisplay.getBackground();
		settings.frayedBorderBlurLevel = frayedEdgeShadingSlider.getValue();
		settings.frayedBorderSize = frayedEdgePolygonCountSlider.getValue();
		settings.grungeWidth = grungeSlider.getValue();
		settings.lineStyle = jaggedLinesButton.isSelected() ? LineStyle.Jagged : LineStyle.Smooth;

		// Background image settings
		settings.generateBackground = rdbtnFractal.isSelected();
		settings.generateBackgroundFromTexture = rdbtnGeneratedFromTexture.isSelected();
		settings.colorizeOcean = colorizeOceanCheckbox.isSelected();
		settings.colorizeLand = colorizeLandCheckbox.isSelected();
		settings.backgroundTextureImage = textureImageFilename.getText();
		settings.backgroundRandomSeed = Long.parseLong(backgroundSeedTextField.getText());
		settings.oceanColor = oceanDisplayPanel.getColor();
		settings.landColor = landDisplayPanel.getColor();

		settings.drawRegionColors = colorRegionsCheckBox.isSelected();

		settings.titleFont = titleFontDisplay.getFont();
		settings.regionFont = regionFontDisplay.getFont();
		settings.mountainRangeFont = mountainRangeFontDisplay.getFont();
		settings.otherMountainsFont = otherMountainsFontDisplay.getFont();
		settings.riverFont = riverFontDisplay.getFont();
		settings.textColor = textColorDisplay.getBackground();
		settings.boldBackgroundColor = boldBackgroundColorDisplay.getBackground();
		settings.drawBoldBackground = chckbxDrawBoldBackground.isSelected();

		settings.drawBorder = drawBorderCheckbox.isSelected();
		settings.borderType = (String) borderTypeComboBox.getSelectedItem();
		settings.borderWidth = borderWidthSlider.getValue();
	}

	private void addToTooltip(JComponent component, String message)
	{
		String currentToolTop = component.getToolTipText();
		if (currentToolTop == null)
		{
			currentToolTop = "";
		}
		if (!currentToolTop.contains(message))
		{
			component.setToolTipText(currentToolTop + message);
		}

	}

	private void removeFromToolTip(JComponent component, String message)
	{

		String currentToolTop = component.getToolTipText();
		if (currentToolTop == null)
		{
			return;
		}
		component.setToolTipText(currentToolTop.replace(message, ""));
	}

}
