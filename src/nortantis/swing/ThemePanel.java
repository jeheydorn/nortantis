package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import nortantis.BackgroundGenerator;
import nortantis.FractalBGGenerator;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanEffect;
import nortantis.SettingsGenerator;
import nortantis.util.ImageHelper;
import nortantis.util.JFontChooser;
import nortantis.util.Tuple2;

@SuppressWarnings("serial")
public class ThemePanel extends JTabbedPane
{
	MainWindow mainWindow;
	JSlider coastShadingSlider;
	JSlider oceanEffectsLevelSlider;
	JSlider concentricWavesLevelSlider;
	JRadioButton ripplesRadioButton;
	JRadioButton shadeRadioButton;
	JPanel coastShadingColorDisplay;
	JPanel coastlineColorDisplay;
	JPanel oceanEffectsColorDisplay;
	JPanel riverColorDisplay;
	JCheckBox enableTextCheckBox;
	JPanel grungeColorDisplay;
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
	private JSlider frayedEdgePolygonCountSlider;
	private JSlider frayedEdgeShadingSlider;
	private JCheckBox frayedEdgeCheckbox;
	public final double cityFrequencySliderScale = 100.0 * 1.0 / SettingsGenerator.maxCityProbabillity;
	private JButton btnChooseCoastShadingColor;
	private JRadioButton jaggedLinesButton;
	private JRadioButton smoothLinesButton;
	private JRadioButton concentricWavesButton;
	private ActionListener oceanEffectsListener;
	JLabel titleFontDisplay;
	JLabel regionFontDisplay;
	JLabel mountainRangeFontDisplay;
	JLabel otherMountainsFontDisplay;
	JLabel riverFontDisplay;
	JPanel textColorDisplay;
	JPanel boldBackgroundColorDisplay;
	private JCheckBox chckbxDrawBoldBackground;
	final int widthToSubtractFromTabPanels = 2;
	private RowHider textureImageHider;
	private RowHider colorizeOceanCheckboxHider;
	private RowHider colorizeLandCheckboxHider;
	


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
		SwingHelper.resetGridY();
		
		Tuple2<JPanel, JScrollPane> panelAndScrollPane = SwingHelper.createPanelAndScrollPaneForLabeledComponents();
		JPanel backgroundPanel = panelAndScrollPane.getFirst();
		backgroundPanel.setLayout(new GridBagLayout());

		
		backgroundImageButtonGroupListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				updateBackgroundAndRegionFieldStates(mainWindow);
			}
		};

		
		rdbtnFractal = new JRadioButton("Fractal noise");
		rdbtnFractal.addActionListener(backgroundImageButtonGroupListener);

		rdbtnGeneratedFromTexture = new JRadioButton("Generated from texture");
		rdbtnGeneratedFromTexture.addActionListener(backgroundImageButtonGroupListener);

		ButtonGroup backgoundImageButtonGroup = new ButtonGroup();
		backgoundImageButtonGroup.add(rdbtnGeneratedFromTexture);
		backgoundImageButtonGroup.add(rdbtnFractal);
		
		SwingHelper.addLabelAndComponentsToPanelVertical(backgroundPanel, "Background:", "Select how to generate the background image.",
				Arrays.asList(rdbtnFractal, rdbtnGeneratedFromTexture));
		
		
		textureImageFilename = new JTextField();
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

		JButton btnsBrowseTextureImage = new JButton("Browse");
		btnsBrowseTextureImage.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String filename = chooseImageFile(backgroundPanel, textureImageFilename.getText());
				if (filename != null)
					textureImageFilename.setText(filename);
			}
		});
		
		JPanel textureFileChooseButtonPanel = new JPanel();
		textureFileChooseButtonPanel.setLayout(new BoxLayout(textureFileChooseButtonPanel, BoxLayout.X_AXIS));
		textureFileChooseButtonPanel.add(btnsBrowseTextureImage);
		textureFileChooseButtonPanel.add(Box.createHorizontalGlue());
		
		textureImageHider = SwingHelper.addLabelAndComponentsToPanelVertical(backgroundPanel, "Texture image:", 
				"Text that will be used to randomly generate a background.", 
				Arrays.asList(textureImageFilename, Box.createVerticalStrut(5), textureFileChooseButtonPanel));

		
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
		SwingHelper.addLabelAndComponentsToPanelHorizontal(backgroundPanel, "Random seed:", 
				"The random seed used to generate the background image. Note that the background texture will also change based on the"
				+ " resolution you draw at.",
				0,
				Arrays.asList(backgroundSeedTextField, btnNewBackgroundSeed));
		
		
		SwingHelper.addSeperator(backgroundPanel);
		colorizeLandCheckbox = new JCheckBox("Color land");
		colorizeLandCheckbox.setToolTipText("Whether to change the land texture to a custom color");
		colorizeLandCheckboxHider = SwingHelper.addLeftAlignedComponent(backgroundPanel, colorizeLandCheckbox);
		
		
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
			}
		});
		SwingHelper.addLabelAndComponentToPanel(backgroundPanel, "Land coloring method:", 
				"How to color the land.", landColoringMethodComboBox);
		

		colorizeCheckboxListener = new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
				updateBackgroundAndRegionFieldStates(mainWindow);
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
			container.setLayout(new FlowLayout());
			container.add(landDisplayPanel);
			btnChooseLandColor.setAlignmentX(CENTER_ALIGNMENT);

			SwingHelper.addLabelAndComponentsToPanelVertical(backgroundPanel, "Land color:", "The color of the land background.", 
					Arrays.asList(container, Box.createVerticalStrut(5), btnChooseLandColor));
		}
		
	
		SwingHelper.addSeperator(backgroundPanel);
		colorizeOceanCheckbox = new JCheckBox("Color ocean");
		colorizeOceanCheckbox.setToolTipText("Whether to change the ocean texture to a custom color");
		colorizeOceanCheckboxHider = SwingHelper.addLeftAlignedComponent(backgroundPanel, colorizeOceanCheckbox);
		
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
			container.setLayout((new FlowLayout()));
			container.add(oceanDisplayPanel);
			btnChooseOceanColor.setAlignmentX(CENTER_ALIGNMENT);

			SwingHelper.addLabelAndComponentsToPanelVertical(backgroundPanel, "Ocean color:", "The color of the ocean.", 
					Arrays.asList(container, Box.createVerticalStrut(5), btnChooseOceanColor));
		}

		
		SwingHelper.addVerticalFillerRow(backgroundPanel);
		return panelAndScrollPane.getSecond();
	}		
	
	private Component createBorderPanel(MainWindow mainWindow)
	{
		SwingHelper.resetGridY();

		Tuple2<JPanel, JScrollPane> panelAndScrollPane = SwingHelper.createPanelAndScrollPaneForLabeledComponents();
		JPanel borderPanel = panelAndScrollPane.getFirst();
		borderPanel.setLayout(new GridBagLayout());


		drawBorderCheckbox = new JCheckBox("Create border");
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
		SwingHelper.addLeftAlignedComponent(borderPanel, drawBorderCheckbox);
		

		borderTypeComboBox = new JComboBox<String>();
		SwingHelper.addLabelAndComponentToPanel(borderPanel, "Border type:", "The set of images to draw for the border", borderTypeComboBox);


		borderWidthSlider = new JSlider();
		borderWidthSlider.setToolTipText("");
		borderWidthSlider.setValue(100);
		borderWidthSlider.setSnapToTicks(false);
		borderWidthSlider.setPaintTicks(true);
		borderWidthSlider.setPaintLabels(true);
		borderWidthSlider.setMinorTickSpacing(50);
		borderWidthSlider.setMaximum(800);
		borderWidthSlider.setMajorTickSpacing(200);
		SwingHelper.setSliderWidthForSidePanel(borderWidthSlider);
		SwingHelper.addLabelAndComponentToPanel(borderPanel, "Border width:", "Width of the border in pixels, scaled if resolution is scaled",
				borderWidthSlider);


		SwingHelper.addSeperator(borderPanel);
		frayedEdgeCheckbox = new JCheckBox("Fray edges");
		frayedEdgeCheckbox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				frayedEdgeShadingSlider.setEnabled(frayedEdgeCheckbox.isSelected());
				frayedEdgePolygonCountSlider.setEnabled(frayedEdgeCheckbox.isSelected());
			}
		});
		SwingHelper.addLeftAlignedComponent(borderPanel, frayedEdgeCheckbox);

		
		frayedEdgeShadingSlider = new JSlider();
		frayedEdgeShadingSlider.setValue(30);
		frayedEdgeShadingSlider.setPaintTicks(true);
		frayedEdgeShadingSlider.setPaintLabels(true);
		frayedEdgeShadingSlider.setMinorTickSpacing(50);
		frayedEdgeShadingSlider.setMaximum(500);
		frayedEdgeShadingSlider.setMajorTickSpacing(100);
		SwingHelper.setSliderWidthForSidePanel(frayedEdgeShadingSlider);
		SwingHelper.addLabelAndComponentToPanel(borderPanel, "Fray shading width:",
				"The width of shading drawn around frayed edges. The color used is the grunge color."
				, frayedEdgeShadingSlider);
		

		frayedEdgePolygonCountSlider = new JSlider();
		frayedEdgePolygonCountSlider.setPaintTicks(true);
		frayedEdgePolygonCountSlider.setPaintLabels(true);
		frayedEdgePolygonCountSlider.setMinorTickSpacing(5000);
		frayedEdgePolygonCountSlider.setMaximum(50000);
		frayedEdgePolygonCountSlider.setMinimum(100);
		frayedEdgePolygonCountSlider.setMajorTickSpacing(20000);
		SwingHelper.setSliderWidthForSidePanel(frayedEdgePolygonCountSlider);
		SwingHelper.addLabelAndComponentToPanel(borderPanel, "Fray polygon count:", "The number of polygons used when creating the frayed border. "
				+ "Higher values make the fray smaller.",
				frayedEdgePolygonCountSlider);
		
		SwingHelper.addVerticalFillerRow(borderPanel);
		return panelAndScrollPane.getSecond();
	}
	
	private Component createEffectsPanel(MainWindow mainWindow)
	{
		SwingHelper.resetGridY();

		Tuple2<JPanel, JScrollPane> panelAndScrollPane = SwingHelper.createPanelAndScrollPaneForLabeledComponents();
		JPanel effectsPanel = panelAndScrollPane.getFirst();
		effectsPanel.setLayout(new GridBagLayout());
		
		
		jaggedLinesButton = new JRadioButton("Jagged");
		smoothLinesButton = new JRadioButton("Smooth");
		ButtonGroup lineStyleButtonGroup = new ButtonGroup();
		lineStyleButtonGroup.add(jaggedLinesButton);
		lineStyleButtonGroup.add(smoothLinesButton);
		SwingHelper.addLabelAndComponentsToPanelVertical(effectsPanel, "Line style:", 
				"The style of lines to use when drawing coastlines, lakeshores, and region boundaries",
				Arrays.asList(jaggedLinesButton, smoothLinesButton));
		SwingHelper.addSeperator(effectsPanel);
		
		
		coastlineColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		JButton buttonChooseCoastlineColor = new JButton("Choose");
		buttonChooseCoastlineColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(effectsPanel, coastlineColorDisplay, "Coastline Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPanel, "Coastline color:", "", 
				SwingHelper.colorPickerLeftPadding,
				Arrays.asList(coastlineColorDisplay, buttonChooseCoastlineColor));
		
		
		coastShadingColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		btnChooseCoastShadingColor = new JButton("Choose");
		btnChooseCoastShadingColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(effectsPanel, coastShadingColorDisplay, "Coast Shading Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPanel, "Coast shading color:", "", 
				SwingHelper.colorPickerLeftPadding,
				Arrays.asList(coastShadingColorDisplay, btnChooseCoastShadingColor));


		coastShadingSlider = new JSlider();
		coastShadingSlider.setValue(30);
		coastShadingSlider.setPaintTicks(true);
		coastShadingSlider.setPaintLabels(true);
		coastShadingSlider.setMinorTickSpacing(5);
		coastShadingSlider.setMaximum(100);
		coastShadingSlider.setMajorTickSpacing(20);
		SwingHelper.setSliderWidthForSidePanel(coastShadingSlider);
		SwingHelper.addLabelAndComponentToPanel(effectsPanel, "Coast shading width:", "How far in from coastlines to shade land.",
				coastShadingSlider);
		

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
		oceanEffectButtonGroup.add(ripplesRadioButton);
		ripplesRadioButton.addActionListener(oceanEffectsListener);

		shadeRadioButton = new JRadioButton("Shade");
		oceanEffectButtonGroup.add(shadeRadioButton);
		shadeRadioButton.addActionListener(oceanEffectsListener);
		SwingHelper.addLabelAndComponentsToPanelVertical(effectsPanel, "Ocean effects type:", "", 
				Arrays.asList(concentricWavesButton, ripplesRadioButton, shadeRadioButton));

		oceanEffectsColorDisplay = SwingHelper.createColorPickerPreviewPanel();
		
		JButton btnChooseOceanEffectsColor = new JButton("Choose");
		btnChooseOceanEffectsColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(effectsPanel, oceanEffectsColorDisplay, "Ocean Effects Color");
			}
		});
		btnChooseOceanEffectsColor.setToolTipText("Choose a color for ocean effects near coastlines. Transparency is supported.");
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPanel, "Ocean effects color:", "",
				SwingHelper.colorPickerLeftPadding,
				Arrays.asList(oceanEffectsColorDisplay, btnChooseOceanEffectsColor));
		

		concentricWavesLevelSlider = new JSlider();
		concentricWavesLevelSlider.setMinimum(1);
		concentricWavesLevelSlider.setValue(30);
		concentricWavesLevelSlider.setPaintTicks(true);
		concentricWavesLevelSlider.setPaintLabels(true);
		concentricWavesLevelSlider.setMinorTickSpacing(1);
		concentricWavesLevelSlider.setMaximum(4);
		concentricWavesLevelSlider.setMajorTickSpacing(1);
		SwingHelper.setSliderWidthForSidePanel(concentricWavesLevelSlider);

		oceanEffectsLevelSlider = new JSlider();
		oceanEffectsLevelSlider.setMinorTickSpacing(5);
		oceanEffectsLevelSlider.setValue(2);
		oceanEffectsLevelSlider.setPaintTicks(true);
		oceanEffectsLevelSlider.setPaintLabels(true);
		oceanEffectsLevelSlider.setMajorTickSpacing(20);
		SwingHelper.setSliderWidthForSidePanel(oceanEffectsLevelSlider);
		SwingHelper.addLabelAndComponentsToPanelVertical(effectsPanel, "Ocean effects width:", "How far from coastlines ocean effects should extend.",
				Arrays.asList(concentricWavesLevelSlider, oceanEffectsLevelSlider));
		SwingHelper.addSeperator(effectsPanel);


		riverColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		JButton riverColorChooseButton = new JButton("Choose");
		riverColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPicker(effectsPanel, riverColorDisplay, "River Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPanel, "River color:", "Rivers will be drawn this color.",
				SwingHelper.colorPickerLeftPadding,
				Arrays.asList(riverColorDisplay, riverColorChooseButton));
		SwingHelper.addSeperator(effectsPanel);
		

		grungeColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		final JButton grungeColorChooseButton = new JButton("Choose");
		grungeColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				SwingHelper.showColorPicker(effectsPanel, grungeColorDisplay, "Grunge Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(effectsPanel, "Edge/Grunge color:", "Grunge and frayed edge shading will be this color",
				SwingHelper.colorPickerLeftPadding,
				Arrays.asList(grungeColorDisplay, grungeColorChooseButton));


		grungeSlider = new JSlider();
		grungeSlider.setValue(0);
		grungeSlider.setPaintTicks(true);
		grungeSlider.setPaintLabels(true);
		grungeSlider.setMinorTickSpacing(100);
		grungeSlider.setMaximum(2000);
		grungeSlider.setMajorTickSpacing(500);
		SwingHelper.setSliderWidthForSidePanel(grungeSlider);
		SwingHelper.addLabelAndComponentToPanel(effectsPanel, "Grunge width:", "Determines the width of grunge on the edges of the map. 0 means none.", grungeSlider);
		
		
		SwingHelper.addVerticalFillerRow(effectsPanel);
		return panelAndScrollPane.getSecond();
	}
	
	private Component createFontsPanel(MainWindow mainWindow)
	{		
		SwingHelper.resetGridY();
	
		Tuple2<JPanel, JScrollPane> panelAndScrollPane = SwingHelper.createPanelAndScrollPaneForLabeledComponents();
		JPanel fontsPanel = panelAndScrollPane.getFirst();
		fontsPanel.setLayout(new GridBagLayout());


		enableTextCheckBox = new JCheckBox("Enable text");
		enableTextCheckBox.setToolTipText("Enable/disable drawing of generated names.");
		SwingHelper.addLeftAlignedComponent(fontsPanel, enableTextCheckBox);
		SwingHelper.addSeperator(fontsPanel);

		Tuple2<JLabel, JButton> tupleTitle = SwingHelper.createFontChooser(fontsPanel, "Title font:", 70);
		titleFontDisplay = tupleTitle.getFirst();
		JButton btnTitleFont = tupleTitle.getSecond();
	
		Tuple2<JLabel, JButton> tupleRegion = SwingHelper.createFontChooser(fontsPanel, "Region font:", 40);
		regionFontDisplay = tupleRegion.getFirst();
		JButton btnRegionFont = tupleRegion.getSecond();
	
		Tuple2<JLabel, JButton> tupleMountainRange = SwingHelper.createFontChooser(fontsPanel, "Mountain range font:", 30);
		mountainRangeFontDisplay = tupleMountainRange.getFirst();
		JButton btnMountainRangeFont = tupleMountainRange.getSecond();
	
		Tuple2<JLabel, JButton> tupleCitiesMountains = SwingHelper.createFontChooser(fontsPanel, "Cities/mountains font:", 30);
		otherMountainsFontDisplay = tupleCitiesMountains.getFirst();
		JButton btnOtherMountainsFont = tupleCitiesMountains.getSecond();
	
		Tuple2<JLabel, JButton> tupleRiver = SwingHelper.createFontChooser(fontsPanel, "River font:", 30);
		riverFontDisplay = tupleRiver.getFirst();
		JButton btnRiverFont = tupleRiver.getSecond();
	

		SwingHelper.addSeperator(fontsPanel);
		textColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		final JButton btnChooseTextColor = new JButton("Choose");
		btnChooseTextColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPickerWithPreviewPanel(fontsPanel, textColorDisplay, "Text Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(fontsPanel, "Text color:", "", 
				SwingHelper.colorPickerLeftPadding,
				Arrays.asList(textColorDisplay, btnChooseTextColor));
		
		
		SwingHelper.addSeperator(fontsPanel);
		chckbxDrawBoldBackground = new JCheckBox("Bold background");
		SwingHelper.addLeftAlignedComponent(fontsPanel, chckbxDrawBoldBackground);
		
		

		boldBackgroundColorDisplay = SwingHelper.createColorPickerPreviewPanel();

		final JButton btnChooseBoldBackgroundColor = new JButton("Choose");
		btnChooseBoldBackgroundColor.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.showColorPickerWithPreviewPanel(fontsPanel, boldBackgroundColorDisplay, "Bold Background Color");
			}
		});
		SwingHelper.addLabelAndComponentsToPanelHorizontal(fontsPanel, "Bold background color:", 
				"If '" + chckbxDrawBoldBackground.getText() + "' is checked, title and region names will be given a bold background in this color.",
				SwingHelper.colorPickerLeftPadding,
				Arrays.asList(boldBackgroundColorDisplay, btnChooseBoldBackgroundColor));
		
		
		chckbxDrawBoldBackground.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				btnChooseBoldBackgroundColor.setEnabled(chckbxDrawBoldBackground.isSelected());
			}
		});


		enableTextCheckBox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				btnTitleFont.setEnabled(enableTextCheckBox.isSelected());
				btnRegionFont.setEnabled(enableTextCheckBox.isSelected());
				btnMountainRangeFont.setEnabled(enableTextCheckBox.isSelected());
				btnOtherMountainsFont.setEnabled(enableTextCheckBox.isSelected());
				btnRiverFont.setEnabled(enableTextCheckBox.isSelected());
				btnChooseTextColor.setEnabled(enableTextCheckBox.isSelected());
				btnChooseBoldBackgroundColor.setEnabled(enableTextCheckBox.isSelected());
				chckbxDrawBoldBackground.setEnabled(enableTextCheckBox.isSelected());
			}
		});
		
		SwingHelper.addVerticalFillerRow(fontsPanel);
		return panelAndScrollPane.getSecond();
	}

	private void updateDrawRegionsCheckboxEnabledAndSelected()
	{
		if (landSupportsColoring())
		{
			landColoringMethodComboBox.setEnabled(true);
		}
		else
		{
			landColoringMethodComboBox.setSelectedItem(LandColoringMethod.SingleColor);
			landColoringMethodComboBox.setEnabled(false);
		}
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
		// TODO These should all use hiders
		textureImageHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeLandCheckboxHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeOceanCheckboxHider.setVisible(rdbtnGeneratedFromTexture.isSelected());
		btnChooseOceanColor.setEnabled(oceanSupportsColoring());
		btnChooseLandColor.setEnabled(landSupportsColoring());

		updateDrawRegionsCheckboxEnabledAndSelected();

		updateBackgroundImageDisplays();
	}

	private void updateBackgroundImageDisplays()
	{
		Dimension bounds = new Dimension(backgroundDisplaySize.width, backgroundDisplaySize.height);

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
			oceanBackground = landBackground = ImageHelper.createBlackImage(bounds.width, bounds.height);
		}

		oceanDisplayPanel.setImage(oceanBackground);
		oceanDisplayPanel.repaint();

		landDisplayPanel.setImage(landBackground);
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
	
	private void handleLandColoringMethodChanged()
	{
		boolean colorRegions = areRegionColorsVisible();
		btnChooseCoastShadingColor.setEnabled(!colorRegions);
		final String message = "Coast shading color selection is disabled because it will use the region color when draw"
				+ " regions is checked.";
		if (colorRegions)
		{
			addToTooltip(btnChooseCoastShadingColor, message);
		}
		else
		{
			removeFromToolTip(btnChooseCoastShadingColor, message);
		}
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
		enableTextCheckBox.setSelected(!settings.drawText);
		enableTextCheckBox.doClick();

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
		settings.drawText = enableTextCheckBox.isSelected();
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

		settings.drawRegionColors = areRegionColorsVisible();

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

	private boolean areRegionColorsVisible()
	{
		return landColoringMethodComboBox.getSelectedItem().equals(LandColoringMethod.ColorPoliticalRegions);
	}
	
	public Color getLandColor()
	{
		return landDisplayPanel.getColor();
	}
	
	private enum LandColoringMethod
	{
		SingleColor("Single Color"),
		ColorPoliticalRegions("Color Political Regions");
		
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
}
