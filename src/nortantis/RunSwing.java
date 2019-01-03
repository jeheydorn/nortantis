package nortantis;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.FilenameUtils;

import nortantis.editor.EditorDialog;
import nortantis.editor.MapEdits;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.JFontChooser;
import nortantis.util.Logger;
import nortantis.util.Range;

public class RunSwing
{
	private static JTextArea txtConsoleOutput;
	private JFrame frame;
	private JTextField randomSeedTextField;
	private JTextField oceanBackgroundImageFilename;
	private JTextField landBackgroundImageFilename;
	JSlider sizeSlider;
	JSlider edgeLandToWaterProbSlider;
	JSlider centerLandToWaterProbSlider;
	JSlider scaleSlider;
	JSlider landBlurSlider;
	JSlider oceanEffectsSlider;
	JRadioButton wavesRadioButton;
	JRadioButton blurRadioButton;
	JPanel landBlurColorDisplay;
	JPanel coastlineColorDisplay;
	JPanel oceanEffectsColorDisplay;
	JPanel riverColorDisplay;
	JCheckBox drawTextCheckBox;
	JPanel booksPanel;
	JLabel titleFontDisplay;
	JLabel regionFontDisplay;
	JLabel mountainRangeFontDisplay;
	JLabel otherMountainsFontDisplay;
	JLabel riverFontDisplay;
	JPanel textColorDisplay;
	JPanel boldBackgroundColorDisplay;
	JButton btnGenerate;
	JButton btnPreview;
	Path openSettingsFilePath;
	MapSettings lastSettingsLoadedOrSaved;
	String frameTitleBase = "Nortantis Fantasy Map Generator";
	JPanel grungeColorDisplay;
	private JTextField backgroundSeedTextField;
	private JRadioButton rdbtnGeneratedFromTexture;
	private JRadioButton rdbtnFromFiles;
	private JRadioButton rdbtnFractal;
	private BGColorPreviewPanel oceanDisplayPanel;
	private BGColorPreviewPanel landDisplayPanel;
	private ActionListener backgroundImageButtonGroupListener;
	private JComboBox<String> dimensionsComboBox;
	private Dimension backgroundDisplayMaxSize = new Dimension(512, 288);
	private int backgroundDisplayCenterX = 667;
	float fractalPower;
	private JTextField textRandomSeedTextField;
	public MapEdits edits;
	private boolean showTextWarning = true;
	/**
	 * A flag to prevent warnings about text edits while loading settings into the gui.
	 */
	boolean loadingSettings;
	private JCheckBox chckbxDrawBoldBackground;
	private JSlider hueSlider;
	private JSlider saturationSlider;
	private JSlider brightnessSlider;
	private JCheckBox drawRegionsCheckBox;
	private JTextField regionsSeedTextField;
	private JButton newRegionSeedButton;
	private JSlider grungeSlider;
	private ImagePanel previewPanel;
	private JLabel lblOceanBackgroundImage;
	private JLabel lblLandBackgroundImage;
	private JLabel lblDimensions;
	private JLabel lblBackgroundRandomSeed;
	private JTextField textureImageFilename;
	private JLabel lblTextureImage;
	private JButton btnBrowseLandBackground;
	private JLabel lblOceanColor;
	private JLabel lblLandColor;
	private JButton btnsBrowseTextureImage;
	private JCheckBox colorizeLandCheckbox;
	private JCheckBox colorizeOceanCheckbox;
	private JButton btnChooseLandColor;
	private JButton btnChooseOceanColor;
	private JButton btnNewBackgroundSeed;
	private JButton btnBrowseOceanBackground;
	private ItemListener colorizeCheckboxListener;
	private JComboBox<String> borderTypeComboBox;
	private JSlider borderWidthSlider;
	private JCheckBox drawBorderCheckbox;
	private JRadioButton rdbtnTransparent;
	private JLabel lblFrayedEdgeSize;
	private JSlider frayedEdgeSizeSlider;
	private JSlider frayedEdgeBlurSlider;
	private JCheckBox frayedEdgeCheckbox;
	public JMenuItem clearEditsMenuItem;
	private JMenu editorMenu;
	private JMenuItem launchEditorMenuItem;
	private JLabel lblSize;
	private JLabel lblEdgeLandtowaterRatio;
	private JLabel lblCenterLandtowaterRatio;
	private JLabel lblRandomSeed;
	private JLabel lblTextRandomSeed;
	private JButton btnNewSeed;
	private JButton btnNewTextRandomSeed;
	private JLabel regionsRandomSeedLabel;
	private JLabel lblHueRange;
	private JLabel lblSaturationRange;
	private JLabel lblBrightnessRange;
	private JLabel lblMapEditsMessage;
	private JSlider cityProbabilitySlider;
	public final double cityFrequencySliderScale = 100.0 * 1.0/SettingsGenerator.maxCityProbabillity;
	private JLabel cityProbabilityLabel;

	
	public static boolean isRunning()
	{
		return txtConsoleOutput != null;
	}
	
	public static JTextArea getConsoleOutputTextArea()
	{
		return txtConsoleOutput;
	}
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | UnsupportedLookAndFeelException e)
		{
			e.printStackTrace();
		}
		
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					RunSwing window = new RunSwing();
					window.frame.setVisible(true);
				} catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public RunSwing()
	{
		createGUI();
		
		try
		{
			if (Files.exists(Paths.get(UserPreferences.getInstance().lastLoadedSettingsFile)))
			{
				loadSettingsIntoGUI(UserPreferences.getInstance().lastLoadedSettingsFile);
				openSettingsFilePath = Paths.get(UserPreferences.getInstance().lastLoadedSettingsFile);
				updateFrameTitle();
			}
			else
			{
				generateAndloadNewSettings();
			}
		}
		catch(Exception e)
		{
			// This means they moved their settings or their settings were corrupted somehow. Load the defaults.
			generateAndloadNewSettings();
		}		
	}
	
	private void generateAndloadNewSettings()
	{
		openSettingsFilePath = null;
		MapSettings randomSettings = SettingsGenerator.generate();
		loadSettingsIntoGUI(randomSettings);
		updateFrameTitle();		
		updateBackgroundImageDisplays();
		UserPreferences.getInstance().lastLoadedSettingsFile = "";
		previewPanel.setImage(null);
		previewPanel.repaint();
	}

	private void createGUI()
	{		
		final RunSwing runSwing = this;
		frame = new JFrame(frameTitleBase);

		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE );
		frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent event)
            {
            	try
            	{
            		boolean cancelPressed = checkForUnsavedChanges();
            		if (!cancelPressed)
            		{
            			if (openSettingsFilePath != null)
            			{
            				UserPreferences.getInstance().lastLoadedSettingsFile = openSettingsFilePath.toString();
            			}
            			else
            			{
            				UserPreferences.getInstance().lastLoadedSettingsFile = "";
            			}
            			UserPreferences.getInstance().save();
            			frame.dispose();
            			System.exit(0);
            		}
            	}
            	catch(Exception ex)
            	{
            		ex.printStackTrace();
            		JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);         		
            	}
            }
        });
		frame.getContentPane().setLayout(new BorderLayout());
		
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(null);
		topPanel.setPreferredSize(new Dimension(935, 713));

		JScrollPane topScrollPane = new JScrollPane(topPanel);
		frame.getContentPane().add(topScrollPane, BorderLayout.CENTER);
		

		final JPanel generatePanel = new JPanel();
		generatePanel.setBounds(new Rectangle(10, 385, 389, 25));
		generatePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		generatePanel.setLayout(null);
		topPanel.add(generatePanel);

		
		btnGenerate = new JButton("Generate");
		btnGenerate.setToolTipText("Generate a map with the settings above.");
		btnGenerate.setBounds(0, 0, 112, 25);
		generatePanel.add(btnGenerate);
		btnGenerate.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent arg0) 
			{	
				btnGenerate.setEnabled(false);
				btnPreview.setEnabled(false);
				final MapSettings settings = getSettingsFromGUI();
				
				txtConsoleOutput.setText("");
			    SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() 
			    {
			        @Override
			        public BufferedImage doInBackground() 
			        {
						try
						{
							BufferedImage map = new MapCreator().createMap(settings, null, null);
							
							Logger.println("Opening the map in your system's default image editor.");
							String fileName = ImageHelper.openImageInSystemDefaultEditor(map, "map_" + settings.randomSeed);
							Logger.println("Map written to " + fileName);
							return map;
						} 
						catch (Exception e)
						{
							e.printStackTrace();
					        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
						}
			        	
			        	return null;
			        }		
			        
			        @Override
			        public void done()
			        {
			        	btnGenerate.setEnabled(true);
						btnPreview.setEnabled(true);
			        }
			    };
			    worker.execute();
			 
			}
		});
		
		previewPanel = new ImagePanel();
		previewPanel.setBounds(411, 385, 512, 288);
		topPanel.add(previewPanel);
		previewPanel.setLayout(null);
		previewPanel.setPreferredSize(new Dimension(512, 288));
		previewPanel.setBackground(Color.WHITE);

		btnPreview = new JButton("Preview");
		btnPreview.setToolTipText("Quickly generate a low resolution map.");
		btnPreview.setBounds(124, 0, 100, 25);
		btnPreview.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent arg0) 
			{
				btnGenerate.setEnabled(false);
				btnPreview.setEnabled(false);

				final MapSettings settings = getSettingsFromGUI();

				txtConsoleOutput.setText("");
			    SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() 
			    {
			        @Override
			        public BufferedImage doInBackground() 
			        {	
			        	Dimension bounds = new Dimension(previewPanel.getWidth(), previewPanel.getHeight());

						try
						{
							return new MapCreator().createMap(settings, bounds, null);
						} 
						catch (Exception e)
						{
							e.printStackTrace();
					        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
						} 
			        	
			        	return null;
			        }
			        
			        @Override
			        public void done()
			        {
			        	BufferedImage map = null;
			            try 
			            {
			                map = get();
			            } 
			            catch (InterruptedException e) 
			            {
			                throw new RuntimeException(e.getMessage());
			            }
			            catch (java.util.concurrent.ExecutionException e) 
			            {
			                throw new RuntimeException(e);
			            }
			            if (map != null)
			            {
			            	previewPanel.image = map;
			            	previewPanel.repaint();
			            }
						btnGenerate.setEnabled(true);
						btnPreview.setEnabled(true);
			        }
			 
			    };
			    worker.execute();
			 
			}
		});		
		generatePanel.add(btnPreview);
		
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setLocation(0, 0);
		tabbedPane.setSize(1067, 378);
		topPanel.add(tabbedPane);
						
		final JPanel terrainPanel = new JPanel();
		tabbedPane.addTab("Terrain", terrainPanel);
		terrainPanel.setLayout(null);
		
		randomSeedTextField = new JTextField();
		randomSeedTextField.setBounds(131, 10, 141, 25);
		terrainPanel.add(randomSeedTextField);
		randomSeedTextField.setColumns(10);
		randomSeedTextField.setText(Math.abs(new Random().nextInt()) + "");
		
		lblRandomSeed = new JLabel("Random seed:");
		lblRandomSeed.setToolTipText("The random seed for the terrain and frayed edges.");
		lblRandomSeed.setBounds(12, 12, 122, 15);
		terrainPanel.add(lblRandomSeed);
				
		btnNewSeed = new JButton("New Seed");
		btnNewSeed.setToolTipText("Generate a new random seed for the terrain and text.");
		btnNewSeed.setBounds(284, 10, 105, 25);
		terrainPanel.add(btnNewSeed);
		
		sizeSlider = new JSlider();
		sizeSlider.setValue(6000);
		sizeSlider.setSnapToTicks(true);
		sizeSlider.setMajorTickSpacing(8000);
		sizeSlider.setMinorTickSpacing(SettingsGenerator.worldSizePrecision);
		sizeSlider.setPaintLabels(true);
		sizeSlider.setPaintTicks(true);
		sizeSlider.setMinimum(SettingsGenerator.minWorldSize);
		sizeSlider.setMaximum(SettingsGenerator.maxWorldSize);
		sizeSlider.setBounds(131, 45, 245, 79);
		terrainPanel.add(sizeSlider);
		
		lblSize = new JLabel("World size:");
		lblSize.setToolTipText("The size of the world.");
		lblSize.setBounds(12, 59, 87, 15);
		terrainPanel.add(lblSize);
						
		lblEdgeLandtowaterRatio = new JLabel("Edge land probability:");
		lblEdgeLandtowaterRatio.setToolTipText("The probability that a tectonic plate touching the edge of the map will be land rather than ocean.");
		lblEdgeLandtowaterRatio.setBounds(461, 12, 239, 22);
		terrainPanel.add(lblEdgeLandtowaterRatio);
		
		edgeLandToWaterProbSlider = new JSlider();
		edgeLandToWaterProbSlider.setValue(70);
		edgeLandToWaterProbSlider.setPaintTicks(true);
		edgeLandToWaterProbSlider.setPaintLabels(true);
		edgeLandToWaterProbSlider.setMinorTickSpacing(25);
		edgeLandToWaterProbSlider.setMajorTickSpacing(25);
		edgeLandToWaterProbSlider.setBounds(565, 32, 245, 79);
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = edgeLandToWaterProbSlider.getMinimum(); i < edgeLandToWaterProbSlider.getMaximum() + 1;  i += edgeLandToWaterProbSlider.getMajorTickSpacing())
			{
				labelTable.put(i, new JLabel(Double.toString(i/100.0)));
			}
			edgeLandToWaterProbSlider.setLabelTable( labelTable );
		}
		terrainPanel.add(edgeLandToWaterProbSlider);
		
		lblCenterLandtowaterRatio = new JLabel("Center land probability:");
		lblCenterLandtowaterRatio.setToolTipText("The probability that a tectonic plate not touching the edge of the map will be land rather than ocean.");
		lblCenterLandtowaterRatio.setBounds(461, 111, 254, 22);
		terrainPanel.add(lblCenterLandtowaterRatio);
		
		centerLandToWaterProbSlider = new JSlider();
		centerLandToWaterProbSlider.setValue(70);
		centerLandToWaterProbSlider.setPaintTicks(true);
		centerLandToWaterProbSlider.setPaintLabels(true);
		centerLandToWaterProbSlider.setMinorTickSpacing(25);
		centerLandToWaterProbSlider.setMajorTickSpacing(25);
		centerLandToWaterProbSlider.setBounds(565, 131, 245, 79);
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = centerLandToWaterProbSlider.getMinimum(); i < centerLandToWaterProbSlider.getMaximum() + 1;
					i += centerLandToWaterProbSlider.getMajorTickSpacing())
			{
				labelTable.put(i, new JLabel(Double.toString(i/100.0)));
			}
			centerLandToWaterProbSlider.setLabelTable( labelTable );
		}
		terrainPanel.add(centerLandToWaterProbSlider);		
		
		// For the background tab.
		final JPanel backgroundPanel = new JPanel();
		tabbedPane.addTab("Background", null, backgroundPanel, null);
		backgroundPanel.setLayout(null);
		
		JLabel label = new JLabel("Resolution:");
		label.setToolTipText("The resolution of the result will be multiplied by this value. Larger values will take longer to run. The maximum resolution is automatically adjusted based on how much RAM you allocate to the java virtual machine.");
		label.setBounds(12, 20, 101, 15);
		backgroundPanel.add(label);
		
		scaleSlider = new JSlider();
		scaleSlider.setPaintLabels(true);
		scaleSlider.setBounds(131, 12, 245, 79);
		scaleSlider.setValue(100);
		scaleSlider.setSnapToTicks(true);
		scaleSlider.setPaintTicks(true);
		scaleSlider.setMinorTickSpacing(25);
		scaleSlider.setMinimum(25);
		scaleSlider.setMaximum(calcMaximumResolution());
		scaleSlider.setMajorTickSpacing(100);
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = scaleSlider.getMinimum(); i < scaleSlider.getMaximum() + 1;  i += scaleSlider.getMajorTickSpacing())
			{
				labelTable.put(i, new JLabel(Double.toString(i/100.0)));
			}
			scaleSlider.setLabelTable(labelTable);
		}
		backgroundPanel.add(scaleSlider);


		lblOceanBackgroundImage = new JLabel("Ocean background image:");
		lblOceanBackgroundImage.setBounds(12, 218, 185, 15);
		backgroundPanel.add(lblOceanBackgroundImage);
		
		oceanBackgroundImageFilename = new JTextField();
		oceanBackgroundImageFilename.setBounds(12, 239, 278, 28);
		backgroundPanel.add(oceanBackgroundImageFilename);
		oceanBackgroundImageFilename.setColumns(10);
		oceanBackgroundImageFilename.getDocument().addDocumentListener(new DocumentListener() 
		{
			public void changedUpdate(DocumentEvent e) 
			{
				if (oceanBackgroundImageFilename.getText().length() > 1)
				{
					showAspectRatioWarning();
				}
			}

			public void removeUpdate(DocumentEvent e) 
			{
				if (oceanBackgroundImageFilename.getText().length() > 1)
				{
					showAspectRatioWarning();
				}
			}

			public void insertUpdate(DocumentEvent e) 
			{
				if (oceanBackgroundImageFilename.getText().length() > 1)
				{
					showAspectRatioWarning();
				}
			}
		});
		

		btnBrowseOceanBackground = new JButton("Browse");
		btnBrowseOceanBackground.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent arg0) 
			{
				String filename = chooseImageFile(backgroundPanel, oceanBackgroundImageFilename.getText());
				if (filename != null)
					oceanBackgroundImageFilename.setText(filename);
			}
		});
		btnBrowseOceanBackground.setBounds(302, 240, 87, 25);
		backgroundPanel.add(btnBrowseOceanBackground);
		
		lblLandBackgroundImage = new JLabel("Land background image:");
		lblLandBackgroundImage.setBounds(12, 279, 175, 15);
		backgroundPanel.add(lblLandBackgroundImage);
		
		landBackgroundImageFilename = new JTextField();
		landBackgroundImageFilename.setColumns(10);
		landBackgroundImageFilename.setBounds(12, 300, 278, 28);
		backgroundPanel.add(landBackgroundImageFilename);
		landBackgroundImageFilename.getDocument().addDocumentListener(new DocumentListener() 
		{
			public void changedUpdate(DocumentEvent e) 
			{
				if (landBackgroundImageFilename.getText().length() > 1)
				{
					showAspectRatioWarning();
				}
			}

			public void removeUpdate(DocumentEvent e) 
			{
				if (landBackgroundImageFilename.getText().length() > 1)
				{
					showAspectRatioWarning();
				}
			}

			public void insertUpdate(DocumentEvent e) 
			{
				if (landBackgroundImageFilename.getText().length() > 1)
				{
					showAspectRatioWarning();
				}
			}
		});
		
		btnBrowseLandBackground = new JButton("Browse");
		btnBrowseLandBackground.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				String filename = chooseImageFile(backgroundPanel, landBackgroundImageFilename.getText());
				if (filename != null)
					landBackgroundImageFilename.setText(filename);

			}
		});
		btnBrowseLandBackground.setBounds(302, 300, 87, 25);
		backgroundPanel.add(btnBrowseLandBackground);
		
		lblLandColor = new JLabel("Land color:");
		lblLandColor.setToolTipText("The color of the land.");
		lblLandColor.setBounds(728, 319, 79, 15);
		backgroundPanel.add(lblLandColor);
		lblLandColor.setBackground(new Color(119, 91, 36));
				
		btnChooseLandColor = new JButton("Choose");
		btnChooseLandColor.setBounds(814, 314, 87, 25);
		btnChooseLandColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				JColorChooser colorChooser = new JColorChooser(landDisplayPanel.getColor());

				colorChooser.getSelectionModel().addChangeListener(landDisplayPanel);
				colorChooser.setPreviewPanel(new JPanel());
				landDisplayPanel.setColorChooser(colorChooser);
				BGColorCancelHandler cancelHandler = new BGColorCancelHandler(landDisplayPanel.getColor(), landDisplayPanel);
		        Dialog dialog = JColorChooser.createDialog(previewPanel, "Land Color", false,
		        		colorChooser, null, cancelHandler);
		        dialog.setVisible(true);
			}
		});

		backgroundPanel.add(btnChooseLandColor);
		
		lblBackgroundRandomSeed = new JLabel("Random seed:");
		lblBackgroundRandomSeed.setToolTipText("The random seed used to generate the background image.");
		lblBackgroundRandomSeed.setBounds(12, 194, 122, 15);
		backgroundPanel.add(lblBackgroundRandomSeed);
		
		backgroundSeedTextField = new JTextField();
		backgroundSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
		backgroundSeedTextField.setColumns(10);
		backgroundSeedTextField.setBounds(131, 194, 141, 25);
		backgroundPanel.add(backgroundSeedTextField);
		
		btnNewBackgroundSeed = new JButton("New Seed");
		btnNewBackgroundSeed.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) 
			{
				backgroundSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
				
				// Update the background image for the land/ocean background displays.
				updateBackgroundImageDisplays();
			}
		});
		btnNewBackgroundSeed.setToolTipText("Generate a new random seed.");
		btnNewBackgroundSeed.setBounds(284, 194, 105, 25);
		backgroundPanel.add(btnNewBackgroundSeed);
		
		JLabel lblBackgroundImage = new JLabel("Background image:");
		lblBackgroundImage.setToolTipText("Select whether to generate a new background image or use images from files.");
		lblBackgroundImage.setBounds(12, 97, 156, 15);
		backgroundPanel.add(lblBackgroundImage);
		
		oceanDisplayPanel = new BGColorPreviewPanel();
		oceanDisplayPanel.setLayout(null);
		oceanDisplayPanel.setPreferredSize(new Dimension(512, 288));
		oceanDisplayPanel.setBackground(Color.WHITE);
		oceanDisplayPanel.setBounds(411, 12, (int)backgroundDisplayMaxSize.getWidth()/2, 
				(int)backgroundDisplayMaxSize.getHeight());
		backgroundPanel.add(oceanDisplayPanel);
		
		lblOceanColor = new JLabel("Ocean color:");
		lblOceanColor.setToolTipText("The color of the ocean.");
		lblOceanColor.setBackground(Color.red);
		lblOceanColor.setBounds(471, 319, 95, 15);
		backgroundPanel.add(lblOceanColor);
		
		btnChooseOceanColor = new JButton("Choose");
		btnChooseOceanColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JColorChooser colorChooser = new JColorChooser(oceanDisplayPanel.getColor());

				colorChooser.getSelectionModel().addChangeListener(oceanDisplayPanel);
				colorChooser.setPreviewPanel(new JPanel());
				oceanDisplayPanel.setColorChooser(colorChooser);
				BGColorCancelHandler cancelHandler = new BGColorCancelHandler(oceanDisplayPanel.getColor(), oceanDisplayPanel);
		        Dialog dialog = JColorChooser.createDialog(previewPanel, "Ocean Color", false,
		        		colorChooser, null, cancelHandler);
		        dialog.setVisible(true);
			}
		});
		btnChooseOceanColor.setBounds(568, 314, 87, 25);
		backgroundPanel.add(btnChooseOceanColor);
		
		landDisplayPanel = new BGColorPreviewPanel();
		landDisplayPanel.setLayout(null);
		landDisplayPanel.setPreferredSize(new Dimension(512, 288));
		landDisplayPanel.setBackground(Color.WHITE);
		landDisplayPanel.setBounds(backgroundDisplayCenterX, 12, (int)backgroundDisplayMaxSize.getWidth()/2, 
				(int)backgroundDisplayMaxSize.getHeight());
		backgroundPanel.add(landDisplayPanel);
		
		dimensionsComboBox = new JComboBox<>();
		dimensionsComboBox.addItem("4096 x 4096 (square)");
		dimensionsComboBox.addItem("4096 x 2304 (16 by 9)");
		dimensionsComboBox.addItem("4096 x 2531 (golden ratio)");
		dimensionsComboBox.setBounds(131, 233, 258, 28);
		dimensionsComboBox.addActionListener(new ActionListener()
		{	
			public void actionPerformed(ActionEvent e)
			{
				updateBackgroundImageDisplays();		
			}
		});
		backgroundPanel.add(dimensionsComboBox);
		
		backgroundImageButtonGroupListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				warnOfEdits();
				updateBackgroundPanelFieldStates();
			}		
		};
		
		rdbtnFractal = new JRadioButton("Fractal noise");
		rdbtnFractal.setBounds(165, 95, 185, 23);
		rdbtnFractal.addActionListener(backgroundImageButtonGroupListener);
		backgroundPanel.add(rdbtnFractal);

		rdbtnGeneratedFromTexture = new JRadioButton("Generated from texture");
		rdbtnGeneratedFromTexture.setBounds(165, 117, 211, 23);
		rdbtnGeneratedFromTexture.addActionListener(backgroundImageButtonGroupListener);
		backgroundPanel.add(rdbtnGeneratedFromTexture);

		rdbtnFromFiles = new JRadioButton("From files");
		rdbtnFromFiles.setBounds(165, 139, 211, 23);
		rdbtnFromFiles.addActionListener(backgroundImageButtonGroupListener);
		backgroundPanel.add(rdbtnFromFiles);
		
		rdbtnTransparent = new JRadioButton("Transparent");
		rdbtnTransparent.setBounds(165, 161, 211, 23);
		rdbtnTransparent.addActionListener(backgroundImageButtonGroupListener);
		//backgroundPanel.add(rdbtnTransparent); Doesn't work yet
		
		ButtonGroup backgoundImageButtonGroup = new ButtonGroup();
		backgoundImageButtonGroup.add(rdbtnGeneratedFromTexture);
		backgoundImageButtonGroup.add(rdbtnFractal);
		backgoundImageButtonGroup.add(rdbtnFromFiles);
		backgoundImageButtonGroup.add(rdbtnTransparent);
		
		lblDimensions = new JLabel("Dimensions:");
		lblDimensions.setToolTipText("The dimensions of the result before being multiplied by the resolution below. This also doesn't include the border, if you add one.");
		lblDimensions.setBounds(12, 233, 122, 15);
		backgroundPanel.add(lblDimensions);
		
		lblTextureImage = new JLabel("Texture image:");
		lblTextureImage.setBounds(12, 273, 156, 15);
		backgroundPanel.add(lblTextureImage);
		
		textureImageFilename = new JTextField();
		textureImageFilename.setColumns(10);
		textureImageFilename.setBounds(12, 294, 278, 28);
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
		backgroundPanel.add(textureImageFilename);
		
		btnsBrowseTextureImage = new JButton("Browse");
		btnsBrowseTextureImage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			}
		});
		btnsBrowseTextureImage.setBounds(302, 294, 87, 25);
		btnsBrowseTextureImage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				String filename = chooseImageFile(backgroundPanel, textureImageFilename.getText());
				if (filename != null)
					textureImageFilename.setText(filename);
			}
		});
		backgroundPanel.add(btnsBrowseTextureImage);
		
		colorizeOceanCheckbox = new JCheckBox("");
		colorizeOceanCheckbox.setToolTipText("Whether or not to change the ocean texture to a custom color");
		colorizeOceanCheckbox.setBounds(448, 315, 129, 23);
		backgroundPanel.add(colorizeOceanCheckbox);
		
		colorizeLandCheckbox = new JCheckBox("");
		colorizeLandCheckbox.setToolTipText("Whether or not to change the land texture to a custom color");
		colorizeLandCheckbox.setBounds(705, 315, 129, 23);
		backgroundPanel.add(colorizeLandCheckbox);
		
		colorizeCheckboxListener = new ItemListener() 
		{	
			@Override
			public void itemStateChanged(ItemEvent e) 
			{
				updateBackgroundPanelFieldStates();
			}
		};

		JPanel regionsPanel = new JPanel();
		tabbedPane.addTab("Regions", null, regionsPanel, null);
		regionsPanel.setLayout(null);
		
		drawRegionsCheckBox = new JCheckBox("Draw regions");
		drawRegionsCheckBox.setToolTipText("When checked, political region borders and background colors will be drawn. This will only work with generated background image.");
		drawRegionsCheckBox.setBounds(8, 8, 129, 23);
		drawRegionsCheckBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				hueSlider.setEnabled(drawRegionsCheckBox.isSelected());
				saturationSlider.setEnabled(drawRegionsCheckBox.isSelected());
				brightnessSlider.setEnabled(drawRegionsCheckBox.isSelected());
				regionsSeedTextField.setEnabled(drawRegionsCheckBox.isSelected());
				newRegionSeedButton.setEnabled(drawRegionsCheckBox.isSelected());
			}
		});
		regionsPanel.add(drawRegionsCheckBox);
		
		hueSlider = new JSlider();
		hueSlider.setPaintTicks(true);
		hueSlider.setPaintLabels(true);
		hueSlider.setMinorTickSpacing(20);
		hueSlider.setMajorTickSpacing(100);
		hueSlider.setMaximum(360);
		hueSlider.setBounds(150, 82, 245, 79);
		regionsPanel.add(hueSlider);
		
		lblHueRange = new JLabel("Hue range:");
		lblHueRange.setToolTipText("The possible range of hue values for generated region colors. The range is centered at the land color hue.");
		lblHueRange.setBounds(12, 94, 101, 23);
		regionsPanel.add(lblHueRange);
		
		lblSaturationRange = new JLabel("Saturation range:");
		lblSaturationRange.setToolTipText("The possible range of saturation values for generated region colors. The range is centered at the land color saturation.");
		lblSaturationRange.setBounds(12, 175, 129, 23);
		regionsPanel.add(lblSaturationRange);
		
		saturationSlider = new JSlider();
		saturationSlider.setPaintTicks(true);
		saturationSlider.setPaintLabels(true);
		saturationSlider.setMinorTickSpacing(20);
		saturationSlider.setMaximum(255);
		saturationSlider.setMajorTickSpacing(100);
		saturationSlider.setBounds(150, 163, 245, 79);
		regionsPanel.add(saturationSlider);
		
		lblBrightnessRange = new JLabel("Brightness range:");
		lblBrightnessRange.setToolTipText("The possible range of brightness values for generated region colors. The range is centered at the land color brightness.");
		lblBrightnessRange.setBounds(12, 255, 129, 23);
		regionsPanel.add(lblBrightnessRange);
		
		brightnessSlider = new JSlider();
		brightnessSlider.setPaintTicks(true);
		brightnessSlider.setPaintLabels(true);
		brightnessSlider.setMinorTickSpacing(20);
		brightnessSlider.setMaximum(255);
		brightnessSlider.setMajorTickSpacing(100);
		brightnessSlider.setBounds(150, 243, 245, 79);
		regionsPanel.add(brightnessSlider);
		
		regionsRandomSeedLabel = new JLabel("Random seed:");
		regionsRandomSeedLabel.setToolTipText("The random seed for region colors.");
		regionsRandomSeedLabel.setBounds(12, 42, 122, 15);
		regionsPanel.add(regionsRandomSeedLabel);
		
		regionsSeedTextField = new JTextField();
		regionsSeedTextField.setText("844645077");
		regionsSeedTextField.setColumns(10);
		regionsSeedTextField.setBounds(131, 42, 141, 25);
		regionsPanel.add(regionsSeedTextField);
		
		newRegionSeedButton = new JButton("New Seed");
		newRegionSeedButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				regionsSeedTextField.setText(String.valueOf(Math.abs(new Random().nextInt())));
			}
		});
		newRegionSeedButton.setToolTipText("Generate a new random seed for the terrain and text.");
		newRegionSeedButton.setBounds(284, 42, 105, 25);
		regionsPanel.add(newRegionSeedButton);
		
		final JPanel effectsPanel = new JPanel();
		tabbedPane.addTab("Effects", null, effectsPanel, null);
		effectsPanel.setLayout(null);
				
		JLabel label_1 = new JLabel("Land blur:");
		label_1.setToolTipText("Adds fading color to coastlines.");
		label_1.setBounds(12, 23, 82, 15);
		effectsPanel.add(label_1);
		
		landBlurSlider = new JSlider();
		landBlurSlider.setValue(30);
		landBlurSlider.setPaintTicks(true);
		landBlurSlider.setPaintLabels(true);
		landBlurSlider.setMinorTickSpacing(5);
		landBlurSlider.setMaximum(100);
		landBlurSlider.setMajorTickSpacing(20);
		landBlurSlider.setBounds(131, 12, 245, 79);
		effectsPanel.add(landBlurSlider);
		
		JLabel label_2 = new JLabel("Ocean effects:");
		label_2.setToolTipText("Adds fading color or waves to oceans at coastlines.");
		label_2.setBounds(12, 96, 122, 15);
		effectsPanel.add(label_2);
		
		oceanEffectsSlider = new JSlider();
		oceanEffectsSlider.setValue(30);
		oceanEffectsSlider.setPaintTicks(true);
		oceanEffectsSlider.setPaintLabels(true);
		oceanEffectsSlider.setMinorTickSpacing(5);
		oceanEffectsSlider.setMaximum(100);
		oceanEffectsSlider.setMajorTickSpacing(20);
		oceanEffectsSlider.setBounds(131, 84, 245, 79);
		effectsPanel.add(oceanEffectsSlider);
		
		JLabel lblOceanEffectType = new JLabel("Ocean effect type:");
		lblOceanEffectType.setBounds(12, 171, 134, 15);
		effectsPanel.add(lblOceanEffectType);
		
		wavesRadioButton = new JRadioButton("Waves");
		wavesRadioButton.setBounds(148, 167, 185, 23);
		effectsPanel.add(wavesRadioButton);
		
		blurRadioButton = new JRadioButton("Blur");
		blurRadioButton.setBounds(148, 189, 185, 23);
		effectsPanel.add(blurRadioButton);
		
		ButtonGroup group = new ButtonGroup();
	    group.add(wavesRadioButton);
	    group.add(blurRadioButton);
		
		JLabel label_4 = new JLabel("Land blur color:");
		label_4.setBounds(461, 82, 134, 23);
		effectsPanel.add(label_4);
		
		landBlurColorDisplay = new JPanel();
		landBlurColorDisplay.setBackground(new Color(119, 91, 36));
		landBlurColorDisplay.setBounds(631, 79, 82, 23);
		effectsPanel.add(landBlurColorDisplay);
		
		JButton button = new JButton("Choose");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
		        showColorPicker(effectsPanel, landBlurColorDisplay, "Land blur color");
			}
		});
		button.setBounds(725, 79, 87, 25);
		effectsPanel.add(button);
		
		JLabel label_5 = new JLabel("Ocean effects color:");
		label_5.setBounds(461, 120, 152, 23);
		effectsPanel.add(label_5);
		
		oceanEffectsColorDisplay = new JPanel();
		oceanEffectsColorDisplay.setBackground(Color.BLACK);
		oceanEffectsColorDisplay.setBounds(631, 114, 82, 23);
		effectsPanel.add(oceanEffectsColorDisplay);
		
		JButton button_1 = new JButton("Choose");
		button_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showColorPicker(effectsPanel, oceanEffectsColorDisplay, "Ocean effects color");
			}
		});
		button_1.setBounds(725, 114, 87, 25);
		effectsPanel.add(button_1);
		
		JLabel label_6 = new JLabel("River color:");
		label_6.setToolTipText("Rivers will be drawn this color.");
		label_6.setBounds(461, 149, 152, 23);
		effectsPanel.add(label_6);
		
		riverColorDisplay = new JPanel();
		riverColorDisplay.setBackground(new Color(56, 48, 33));
		riverColorDisplay.setBounds(631, 149, 82, 23);
		effectsPanel.add(riverColorDisplay);
		
		JButton button_2 = new JButton("Choose");
		button_2.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				showColorPicker(effectsPanel, riverColorDisplay, "River color");
			}
		});
		button_2.setBounds(725, 149, 87, 25);
		effectsPanel.add(button_2);
				
		JLabel grungeColorLabel = new JLabel("Edge/Grunge color:");
		grungeColorLabel.setToolTipText("Frayed edges and grunge will be this color");
		grungeColorLabel.setBounds(461, 221, 152, 23);
		effectsPanel.add(grungeColorLabel);
		
		grungeColorDisplay = new JPanel();
		grungeColorDisplay.setBounds(631, 221, 82, 23);
		effectsPanel.add(grungeColorDisplay);
		
		final JButton grungeColorChooseButton= new JButton("Choose");
		grungeColorChooseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) 
			{
				showColorPicker(effectsPanel, grungeColorDisplay, "Grunge color");
			}
		});
		grungeColorChooseButton.setBounds(725, 221, 87, 25);
		effectsPanel.add(grungeColorChooseButton);
				
		JLabel lblCoastlineColor = new JLabel("Coastline color:");
		lblCoastlineColor.setBounds(461, 47, 134, 23);
		effectsPanel.add(lblCoastlineColor);
		
		coastlineColorDisplay = new JPanel();
		//coastlineColorDisplay.setBackground(settings.);
		coastlineColorDisplay.setBounds(631, 44, 82, 23);
		effectsPanel.add(coastlineColorDisplay);
		
		JButton buttonChooseCoastlineColor = new JButton("Choose");
		buttonChooseCoastlineColor.setBounds(725, 44, 87, 25);
		effectsPanel.add(buttonChooseCoastlineColor);
		
		JLabel lblGrunge = new JLabel("Grunge:");
		lblGrunge.setToolTipText("Determines the width of grunge on the edges of the map. 0 means none. ");
		lblGrunge.setBounds(461, 270, 152, 23);
		effectsPanel.add(lblGrunge);
		
		grungeSlider = new JSlider();
		grungeSlider.setValue(0);
		grungeSlider.setPaintTicks(true);
		grungeSlider.setPaintLabels(true);
		grungeSlider.setMinorTickSpacing(100);
		grungeSlider.setMaximum(2000);
		grungeSlider.setMajorTickSpacing(500);
		grungeSlider.setBounds(580, 252, 245, 79);
		effectsPanel.add(grungeSlider);

		final JPanel borderPanel = new JPanel();
		tabbedPane.addTab("Border", borderPanel);
		borderPanel.setLayout(null);
		
		drawBorderCheckbox = new JCheckBox("Draw border");
		drawBorderCheckbox.setToolTipText("When checked, a border will be drawn around the map.");
		drawBorderCheckbox.setBounds(8, 8, 129, 23);
		borderPanel.add(drawBorderCheckbox);
		drawBorderCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				borderWidthSlider.setEnabled(drawBorderCheckbox.isSelected());
				borderTypeComboBox.setEnabled(drawBorderCheckbox.isSelected());
			}
		});

		
		JLabel lblBorderType = new JLabel("Border type:");
		lblBorderType.setToolTipText("The set of images to draw for the border");
		lblBorderType.setBounds(12, 70, 109, 15);
		borderPanel.add(lblBorderType);
		
		borderTypeComboBox = new JComboBox<String>();
		borderTypeComboBox.setBounds(133, 70, 236, 30);
		borderPanel.add(borderTypeComboBox);
		
		borderWidthSlider = new JSlider();
		borderWidthSlider.setToolTipText("");
		borderWidthSlider.setValue(100);
		borderWidthSlider.setSnapToTicks(false);
		borderWidthSlider.setPaintTicks(true);
		borderWidthSlider.setPaintLabels(true);
		borderWidthSlider.setMinorTickSpacing(50);
		borderWidthSlider.setMaximum(800);
		borderWidthSlider.setMajorTickSpacing(200);
		borderWidthSlider.setBounds(131, 132, 245, 79);
		borderPanel.add(borderWidthSlider);
		
		JLabel lblBorderWidth = new JLabel("Border width:");
		lblBorderWidth.setVerticalAlignment(SwingConstants.BOTTOM);
		lblBorderWidth.setToolTipText("Width of the border in pixels, scaled if resolution is scaled");
		lblBorderWidth.setBounds(12, 148, 105, 15);
		borderPanel.add(lblBorderWidth);
		
		frayedEdgeCheckbox = new JCheckBox("Draw frayed edges");
		frayedEdgeCheckbox.setBounds(461, 8, 191, 23);
		frayedEdgeCheckbox.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				frayedEdgeBlurSlider.setEnabled(frayedEdgeCheckbox.isSelected());
				frayedEdgeSizeSlider.setEnabled(frayedEdgeCheckbox.isSelected());
			}
		});

		borderPanel.add(frayedEdgeCheckbox);
		
		JLabel label_3 = new JLabel("Frayed edge blur:");
		label_3.setToolTipText("The width of color drawn around frayed edges. The color used is the grunge color in the Effects tab.");
		label_3.setBounds(461, 81, 152, 15);
		borderPanel.add(label_3);
		
		frayedEdgeBlurSlider = new JSlider();
		frayedEdgeBlurSlider.setValue(30);
		frayedEdgeBlurSlider.setPaintTicks(true);
		frayedEdgeBlurSlider.setPaintLabels(true);
		frayedEdgeBlurSlider.setMinorTickSpacing(50);
		frayedEdgeBlurSlider.setMaximum(500);
		frayedEdgeBlurSlider.setMajorTickSpacing(100);
		frayedEdgeBlurSlider.setBounds(627, 63, 245, 79);
		borderPanel.add(frayedEdgeBlurSlider);
		
		lblFrayedEdgeSize = new JLabel("Frayed edge size:");
		lblFrayedEdgeSize.setToolTipText("The number of polygons used when creating the frayed border. Higher values make the fray smaller.");
		lblFrayedEdgeSize.setBounds(461, 192, 163, 15);
		borderPanel.add(lblFrayedEdgeSize);
		
		frayedEdgeSizeSlider = new JSlider();
		frayedEdgeSizeSlider.setPaintTicks(true);
		frayedEdgeSizeSlider.setPaintLabels(true);
		frayedEdgeSizeSlider.setMinorTickSpacing(5000);
		frayedEdgeSizeSlider.setMaximum(50000);
		frayedEdgeSizeSlider.setMinimum(100);
		frayedEdgeSizeSlider.setMajorTickSpacing(20000);
		frayedEdgeSizeSlider.setBounds(627, 174, 245, 79);
		borderPanel.add(frayedEdgeSizeSlider);
		
		final JPanel iconsPanel = new JPanel();
		tabbedPane.addTab("Icons", iconsPanel);
		iconsPanel.setLayout(null);
		
		cityProbabilityLabel = new JLabel("City probability:");
		cityProbabilityLabel.setToolTipText("Higher values create more cities. Lower values create less cities. Zero means no cities.");
		cityProbabilityLabel.setBounds(12, 20, 114, 15);
		iconsPanel.add(cityProbabilityLabel);
		
		cityProbabilitySlider = new JSlider();
		cityProbabilitySlider.setPaintLabels(true);
		cityProbabilitySlider.setBounds(131, 12, 245, 79);
		cityProbabilitySlider.setSnapToTicks(false);
		cityProbabilitySlider.setPaintTicks(true);
		cityProbabilitySlider.setMinorTickSpacing(10);
		cityProbabilitySlider.setMinimum(0);
		cityProbabilitySlider.setMaximum(100);
		cityProbabilitySlider.setMajorTickSpacing(25);
		iconsPanel.add(cityProbabilitySlider);

		final JPanel textPanel = new JPanel();
		tabbedPane.addTab("Text", textPanel);
		textPanel.setLayout(null);
		
		JLabel lblBooks = new JLabel("Books:");
		lblBooks.setToolTipText("Selected books will be used to generate (potentially) new names.");
		lblBooks.setBounds(528, 43, 70, 15);
		textPanel.add(lblBooks);
		
		JLabel lblFont = new JLabel("Region font:");
		lblFont.setBounds(8, 76, 105, 15);
		textPanel.add(lblFont);
		
		regionFontDisplay = new JLabel("");
		regionFontDisplay.setBounds(181, 70, 195, 25);
		
		textPanel.add(regionFontDisplay);
				
		final JButton btnRegionFont = new JButton("Choose");
		btnRegionFont.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent arg0) 
			{
				runFontChooser(textPanel, regionFontDisplay);
			}
		});
		btnRegionFont.setBounds(383, 72, 87, 25);
		textPanel.add(btnRegionFont);
		
		JLabel lblTitleFont = new JLabel("Title font:");
		lblTitleFont.setBounds(8, 43, 105, 15);
		textPanel.add(lblTitleFont);
		
		titleFontDisplay = new JLabel("");
		titleFontDisplay.setBounds(181, 12, 195, 49);
		textPanel.add(titleFontDisplay);
		
		final JButton btnTitleFont = new JButton("Choose");
		btnTitleFont.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) 
			{
				runFontChooser(textPanel, titleFontDisplay);
			}
		});
		btnTitleFont.setBounds(383, 39, 87, 25);
		textPanel.add(btnTitleFont);
		
		JLabel lblMountainRangeFont = new JLabel("Mountain range font:");
		lblMountainRangeFont.setBounds(8, 109, 161, 15);
		textPanel.add(lblMountainRangeFont);
		
		mountainRangeFontDisplay = new JLabel("");
		mountainRangeFontDisplay.setBounds(181, 103, 195, 25);
		textPanel.add(mountainRangeFontDisplay);
		
		final JButton btnMountainRangeFont = new JButton("Choose");
		btnMountainRangeFont.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				runFontChooser(textPanel, mountainRangeFontDisplay);
			}
		});
		btnMountainRangeFont.setBounds(383, 105, 87, 25);
		textPanel.add(btnMountainRangeFont);
		
		JLabel lblMountainGroupFont = new JLabel("Cities/mountains font:");
		lblMountainGroupFont.setBounds(8, 142, 161, 15);
		textPanel.add(lblMountainGroupFont);
		
		otherMountainsFontDisplay = new JLabel("");
		otherMountainsFontDisplay.setBounds(181, 139, 195, 25);
		textPanel.add(otherMountainsFontDisplay);
		
		final JButton btnOtherMountainsFont = new JButton("Choose");
		btnOtherMountainsFont.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				runFontChooser(textPanel, otherMountainsFontDisplay);
			}
		});
		btnOtherMountainsFont.setBounds(383, 138, 87, 25);
		textPanel.add(btnOtherMountainsFont);
		
		JLabel lblRiverFont = new JLabel("River font:");
		lblRiverFont.setBounds(8, 175, 116, 15);
		textPanel.add(lblRiverFont);
		
		riverFontDisplay = new JLabel("");
		riverFontDisplay.setBounds(181, 175, 195, 25);
		textPanel.add(riverFontDisplay);
		
		final JButton btnRiverFont = new JButton("Choose");
		btnRiverFont.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				runFontChooser(textPanel, riverFontDisplay);
			}
		});
		btnRiverFont.setBounds(383, 171, 87, 25);
		textPanel.add(btnRiverFont);
		
		JLabel lblTextColor = new JLabel("Text color:");
		lblTextColor.setBounds(8, 208, 134, 23);
		textPanel.add(lblTextColor);
		
		textColorDisplay = new JPanel();
		textColorDisplay.setBackground(Color.BLACK);
		textColorDisplay.setBounds(237, 204, 82, 23);
		textPanel.add(textColorDisplay);
		
		final JButton btnChooseTextColor = new JButton("Choose");
		btnChooseTextColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showColorPickerWithPreviewPanel(textPanel, textColorDisplay, "Text color");
			}
		});
		btnChooseTextColor.setBounds(383, 204, 87, 25);
		textPanel.add(btnChooseTextColor);
		
		JLabel lblBoldBackgroundColor = new JLabel("Bold background color:");
		lblBoldBackgroundColor.setToolTipText("Title and region names will be given a bold background in this color.");
		lblBoldBackgroundColor.setBounds(8, 274, 186, 23);
		textPanel.add(lblBoldBackgroundColor);
		
		boldBackgroundColorDisplay = new JPanel();
		boldBackgroundColorDisplay.setBackground(new Color(244,226,194));
		boldBackgroundColorDisplay.setBounds(237, 270, 82, 23);
		textPanel.add(boldBackgroundColorDisplay);
		
		final JButton btnChooseBoldBackgroundColor = new JButton("Choose");
		btnChooseBoldBackgroundColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showColorPickerWithPreviewPanel(textPanel, boldBackgroundColorDisplay, "Bold background color");
			}
		});
		btnChooseBoldBackgroundColor.setBounds(383, 270, 87, 25);
		textPanel.add(btnChooseBoldBackgroundColor);
		
		btnNewTextRandomSeed = new JButton("New Seed");
		btnNewTextRandomSeed.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				warnOfEdits();
				textRandomSeedTextField.setText(Math.abs(new Random().nextInt()) + "");
			}
		});
		btnNewTextRandomSeed.setToolTipText("Generate a new random seed for creating text.");
		btnNewTextRandomSeed.setBounds(800, 12, 105, 25);
		textPanel.add(btnNewTextRandomSeed);
						
		drawTextCheckBox = new JCheckBox("Draw text");
		drawTextCheckBox.setToolTipText("Enable/disable drawing of generated names.");
		drawTextCheckBox.setBounds(8, 8, 125, 23);
		drawTextCheckBox.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
				btnTitleFont.setEnabled(drawTextCheckBox.isSelected());
				booksPanel.setEnabled(drawTextCheckBox.isSelected());
				for (Component component : booksPanel.getComponents())
				{
					if (component instanceof JCheckBox)
					{
						JCheckBox checkBox = (JCheckBox)component;
						checkBox.setEnabled(drawTextCheckBox.isSelected());
					}
				}				
				btnRegionFont.setEnabled(drawTextCheckBox.isSelected());
				btnMountainRangeFont.setEnabled(drawTextCheckBox.isSelected());
				btnOtherMountainsFont.setEnabled(drawTextCheckBox.isSelected());
				btnRiverFont.setEnabled(drawTextCheckBox.isSelected());
				btnChooseTextColor.setEnabled(drawTextCheckBox.isSelected());
				btnChooseBoldBackgroundColor.setEnabled(drawTextCheckBox.isSelected());
				textRandomSeedTextField.setEnabled(drawTextCheckBox.isSelected());
				btnNewTextRandomSeed.setEnabled(drawTextCheckBox.isSelected());
				chckbxDrawBoldBackground.setEnabled(drawTextCheckBox.isSelected());
			}			
		});
		textPanel.add(drawTextCheckBox);
		
		JScrollPane booksScrollPane = new JScrollPane();
		booksScrollPane.setBounds(538, 70, 311, 270);
		textPanel.add(booksScrollPane);
		
		booksPanel = new JPanel();
		booksPanel.setLayout(new BoxLayout(booksPanel, BoxLayout.Y_AXIS));
		booksScrollPane.setViewportView(booksPanel);
		
		lblTextRandomSeed = new JLabel("Random seed:");
		lblTextRandomSeed.setToolTipText("The random seed for text.");
		lblTextRandomSeed.setBounds(528, 14, 122, 15);
		textPanel.add(lblTextRandomSeed);
		
		textRandomSeedTextField = new JTextField();
		textRandomSeedTextField.setText(new Random().nextInt() + "");
		textRandomSeedTextField.setColumns(10);
		textRandomSeedTextField.setBounds(647, 12, 141, 25);
		textPanel.add(textRandomSeedTextField);
		
		chckbxDrawBoldBackground = new JCheckBox("Draw bold background");
		chckbxDrawBoldBackground.setBounds(8, 241, 209, 23);
		chckbxDrawBoldBackground.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				btnChooseBoldBackgroundColor.setEnabled(chckbxDrawBoldBackground.isSelected());
			}
		});
		textPanel.add(chckbxDrawBoldBackground);
		buttonChooseCoastlineColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showColorPicker(effectsPanel, coastlineColorDisplay, "Coastline color");
			}
		});
								
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(10, 417, 389, 260);
		topPanel.add(scrollPane);
		txtConsoleOutput = new JTextArea();
		scrollPane.setViewportView(txtConsoleOutput);
		txtConsoleOutput.setEditable(false);
														
		btnNewSeed.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				warnOfEdits();
				int seed = Math.abs(new Random().nextInt());
				randomSeedTextField.setText(seed + "");
				textRandomSeedTextField.setText(seed + "");
				previewPanel.setImage(null);
				previewPanel.repaint();
			}			
		});
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		
		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		
		final JMenuItem mntmNew = new JMenuItem("New");
		fileMenu.add(mntmNew);
		mntmNew.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				boolean cancelPressed = checkForUnsavedChanges();
				if (!cancelPressed)
				{
					generateAndloadNewSettings();
				}
			}
		});
		
		final JMenuItem mntmLoadSettings = new JMenuItem("Open");
		fileMenu.add(mntmLoadSettings);
		mntmLoadSettings.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				boolean cancelPressed = checkForUnsavedChanges();
				if (cancelPressed)
					return;
				
				Path curPath = openSettingsFilePath == null ? Paths.get(".") : openSettingsFilePath;
				File currentFolder = new File(curPath.toString());
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
						return f.isDirectory() || f.getName().endsWith(".properties");
					}
				});
				int status = fileChooser.showOpenDialog(mntmLoadSettings);
				if (status == JFileChooser.APPROVE_OPTION)
				{
					openSettingsFilePath = Paths.get(fileChooser.getSelectedFile().getAbsolutePath());
					loadSettingsIntoGUI(fileChooser.getSelectedFile().getAbsolutePath());
					updateFrameTitle();
				}
			
			}
		});
	
		final JMenuItem mntmSave = new JMenuItem("Save");
		mntmSave.setAccelerator(KeyStroke.getKeyStroke(
		        java.awt.event.KeyEvent.VK_S, 
		        java.awt.Event.CTRL_MASK));
		fileMenu.add(mntmSave);
		mntmSave.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				saveSettings(mntmSave);
			}
		});
		
		final JMenuItem mntmSaveAs = new JMenuItem("Save As...");
		fileMenu.add(mntmSaveAs);	
		mntmSaveAs.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				saveSettingsAs(mntmSaveAs);
			}			
		});
		
		JMenuItem mntmExportHeightmap = new JMenuItem("Export Heightmap");
		fileMenu.add(mntmExportHeightmap);
		mntmExportHeightmap.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				showHeightMapWithEditsWarning();
				
				txtConsoleOutput.setText("");
			    SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() 
			    {
			        @Override
			        public BufferedImage doInBackground() 
			        {
						try
						{
							MapSettings settings = getSettingsFromGUI();
							BufferedImage heightMap = new MapCreator().createHeightMap(settings);
							Logger.println("Opening the heightmap in your system's default image editor.");
							String fileName = ImageHelper.openImageInSystemDefaultEditor(heightMap, "heightmap");	
							Logger.println("Heightmap written to " + fileName);
						} 
						catch (Exception e)
						{
							e.printStackTrace();
					        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
						}
			        	
			        	return null;
			        }			 

			        @Override
			        public void done()
			        {
						btnGenerate.setEnabled(true);
						btnPreview.setEnabled(true);
			        }
			    };
			    worker.execute();

				
				
			}	
			
		});

		editorMenu = new JMenu("Editor");
		menuBar.add(editorMenu);
		
		launchEditorMenuItem = new JMenuItem("Launch Editor");
		launchEditorMenuItem.setAccelerator(KeyStroke.getKeyStroke(
		        java.awt.event.KeyEvent.VK_E, 
		        java.awt.Event.CTRL_MASK));
		launchEditorMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				boolean hadEdits = !edits.isEmpty();
		        Dialog dialog;
		        dialog = new EditorDialog(getSettingsFromGUI(), runSwing);
				dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
				dialog.setVisible(true);
		    	runSwing.updateFieldsWhenEditsChange();	
				if (!hadEdits && !edits.isEmpty())
				{
					showMapChangesMessage();
				}
			}			
		});
		editorMenu.add(launchEditorMenuItem);
		
		clearEditsMenuItem = new JMenuItem("Clear Edits");
		clearEditsMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
	        	int n = JOptionPane.showConfirmDialog(
	                    frame, "All map edits will be deleted. Do you wish to continue?", "",
	                    JOptionPane.YES_NO_OPTION);
	            if (n == JOptionPane.YES_OPTION) 
	            {
					edits = new MapEdits();
					updateFieldsWhenEditsChange();
	            }
			}			
		});
		editorMenu.add(clearEditsMenuItem);
		
		lblMapEditsMessage = new JLabel("<html>Fields on this tab and some on other tabs are disabled because this"
				+ " map has edits. If you wish to enable those fields, you can either clear your "
				+ "edits (" + editorMenu.getText() + " -> " + clearEditsMenuItem.getText() + "),"
					+ " or create a new random map by going to File > New.</html>");
		lblMapEditsMessage.setBounds(12, 285, 913, 50);
		lblMapEditsMessage.setVisible(false);
		terrainPanel.add(lblMapEditsMessage);
		
		JMenuBar menuBar_1 = new JMenuBar();
		menuBar.add(menuBar_1);
		
		frame.pack();
	}
	
	private void showHeightMapWithEditsWarning()
	{
		if (edits != null && !edits.isEmpty() && !UserPreferences.getInstance().hideHeightMapWithEditsWarning)
		{
			Dimension size = new Dimension(400, 80);
			JPanel panel = new JPanel();
			panel.setPreferredSize(size);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			JLabel label = new JLabel("<html>Edits made in the editor, such as land, water, and mountains, "
					+ "are not applied to height maps. </html>");
			panel.add(label);
			label.setMaximumSize(size);
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(frame, panel, "", JOptionPane.INFORMATION_MESSAGE);
			UserPreferences.getInstance().hideHeightMapWithEditsWarning = checkBox.isSelected();
		}
	}

	
	private void showAspectRatioWarning()
	{
		if (edits != null && !edits.isEmpty() && !UserPreferences.getInstance().hideAspectRatioWarning)
		{
			Dimension size = new Dimension(400, 130);
			JPanel panel = new JPanel();
			panel.setPreferredSize(size);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			JLabel label = new JLabel("<html>The new background image must have exactly the same aspect ratio as the old one "
					+ "or your edits won't work. If you want to use a background image with a different aspect ratio, "
					+ "first clear your edits. Note that changing the aspect ratio will create a very different map. </html>");
			label.setMaximumSize(size);
			panel.add(label);
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(frame, panel, "", JOptionPane.WARNING_MESSAGE);
			UserPreferences.getInstance().hideAspectRatioWarning = checkBox.isSelected();
		}
	}
	
	private void showMapChangesMessage()
	{
		if (!UserPreferences.getInstance().hideMapChangesWarning)
		{
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.add(new JLabel("<html>Some fields in the generator are now disabled to ensure your map remains"
					+ "<br>compatible with your edits. If a field is disabled for this reason, a message is added"
					+ "<br>to the field's tool tip. If you wish to enable those fields, you can either clear your "
					+ "<br>edits (Editor > Clear Edits), or create a new random map by going to File > New.</html>"));
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(frame, panel, "", JOptionPane.INFORMATION_MESSAGE);
			UserPreferences.getInstance().hideMapChangesWarning = checkBox.isSelected();
		}
	}
	
	private int calcMaximumResolution() 
	{
		long maxBytes = Runtime.getRuntime().maxMemory();
		// The required memory is quadratic in the resolution used. 
		// To generate a map at resolution 225 takes 7GB, so 7×1024^3÷(225^2) = 148468.
		int maxResolution = (int)Math.sqrt(maxBytes / 148468L);
		
		// The FFT-based code will create arrays in powers of 2.
		int nextPowerOf2 = ImageHelper.getPowerOf2EqualOrLargerThan(maxResolution / 100.0);
		int resolutionAtNextPowerOf2 = nextPowerOf2 * 100;
		// Average with the original prediction because not all code is FFT-based.
		maxResolution = (maxResolution + resolutionAtNextPowerOf2) / 2;
		
		if (maxResolution > 500)
		{
			// This is in case Runtime.maxMemory returns Long's max value, which it says it will if it fails.
			return 1000;
		}
		if (maxResolution < 100)
		{
			return 100;
		}
		// The resolution slider uses multiples of 25.
		maxResolution -= maxResolution % 25;
		return maxResolution;
	}

	/**
	 * Informs the user that if they continue an action they must delete text edits.
	 * @return true if the action should continue. false if the user canceled the action to keep text edits.
	 */
	private void warnOfEdits()
	{
		
		if (!loadingSettings && showTextWarning  && edits != null && !edits.text.isEmpty())
		{
	        int n = JOptionPane.showOptionDialog(frame, "Some options are disabled because you have edited the map. \n"
                    + "You can clear edits by going to " + editorMenu.getText() + " -> " + clearEditsMenuItem.getText() + "\n\nWould you like to continue to see this warning?", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null);
	        if (n == JOptionPane.NO_OPTION)
	        {
	        	showTextWarning = false;
	        }
		}
	}
	
		
	private Dimension getGeneratedBackgroundDimensionsFromGUI()
	{
		String selected = (String)dimensionsComboBox.getSelectedItem();
		return parseGenerateBackgroundDimensionsFromDropdown(selected);
	}
	
	private Dimension parseGenerateBackgroundDimensionsFromDropdown(String selected)
	{
		selected = selected.substring(0, selected.indexOf("("));
		String[] parts = selected.split("x");
		return new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
	}
	
	private void updateBackgroundPanelFieldStates()
	{
		boolean isGeneratedBackground = rdbtnGeneratedFromTexture.isSelected() || rdbtnFractal.isSelected();
		btnChooseLandColor.setVisible(isGeneratedBackground);
		btnChooseOceanColor.setVisible(isGeneratedBackground);
		btnNewBackgroundSeed.setVisible(isGeneratedBackground);
		lblDimensions.setVisible(isGeneratedBackground);
		dimensionsComboBox.setVisible(isGeneratedBackground);
		backgroundSeedTextField.setVisible(isGeneratedBackground);
		lblBackgroundRandomSeed.setVisible(isGeneratedBackground);
		oceanDisplayPanel.setVisible(isGeneratedBackground);
		landDisplayPanel.setVisible(isGeneratedBackground);
		lblOceanColor.setVisible(isGeneratedBackground);
		lblLandColor.setVisible(isGeneratedBackground);
		
		lblTextureImage.setVisible(rdbtnGeneratedFromTexture.isSelected());
		textureImageFilename.setVisible(rdbtnGeneratedFromTexture.isSelected());
		btnBrowseLandBackground.setVisible(rdbtnGeneratedFromTexture.isSelected());
		btnsBrowseTextureImage.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeOceanCheckbox.setVisible(rdbtnGeneratedFromTexture.isSelected());
		colorizeLandCheckbox.setVisible(rdbtnGeneratedFromTexture.isSelected());
		btnChooseOceanColor.setEnabled(colorizeOceanCheckbox.isSelected());
		btnChooseLandColor.setEnabled(colorizeLandCheckbox.isSelected());

		btnBrowseLandBackground.setVisible(rdbtnFromFiles.isSelected());
		btnBrowseOceanBackground.setVisible(rdbtnFromFiles.isSelected());
		lblOceanBackgroundImage.setVisible(rdbtnFromFiles.isSelected());
		oceanBackgroundImageFilename.setVisible(rdbtnFromFiles.isSelected());
		lblLandBackgroundImage.setVisible(rdbtnFromFiles.isSelected());
		landBackgroundImageFilename.setVisible(rdbtnFromFiles.isSelected());
		
		drawRegionsCheckBox.setEnabled(rdbtnFractal.isSelected() || rdbtnGeneratedFromTexture.isSelected());
		boolean regionControlsSelected = (rdbtnFractal.isSelected() || rdbtnGeneratedFromTexture.isSelected()) && drawRegionsCheckBox.isSelected();
		hueSlider.setEnabled(regionControlsSelected);
		saturationSlider.setEnabled(regionControlsSelected);
		brightnessSlider.setEnabled(regionControlsSelected);
		regionsSeedTextField.setEnabled(regionControlsSelected);
		newRegionSeedButton.setEnabled(regionControlsSelected);
		
		if (isGeneratedBackground)
		{
			updateBackgroundImageDisplays();
		}

	}
	
	private void updateBackgroundImageDisplays()
	{
		Dimension dim = getGeneratedBackgroundDimensionsFromGUI();
		
		DimensionDouble bounds = ImageHelper.fitDimensionsWithinBoundingBox(
				backgroundDisplayMaxSize, dim.getWidth(), dim.getHeight());

		
		BufferedImage oceanBackground;
		BufferedImage landBackground;
		if (rdbtnFractal.isSelected())
		{
			oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.algorithm2);
			landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.algorithm2);

			oceanBackground = landBackground = FractalBGGenerator.generate(
					new Random(Integer.parseInt(backgroundSeedTextField.getText())), fractalPower, (int)bounds.getWidth(),
					(int)bounds.getHeight(), 0.75f);
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
							(int)bounds.getHeight(), (int)bounds.getWidth());
				}
				else
				{
					oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);

					oceanBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(Integer.parseInt(backgroundSeedTextField.getText())), texture,
							(int)bounds.getHeight(), (int)bounds.getWidth());
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
								(int)bounds.getHeight(), (int)bounds.getWidth());
					}
					else
					{
						landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
						
						landBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
								new Random(Integer.parseInt(backgroundSeedTextField.getText())), texture,
								(int)bounds.getHeight(), (int)bounds.getWidth());
					}
				}	
			}
			catch (RuntimeException e)
			{
				oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
				landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
				oceanBackground = landBackground = new BufferedImage((int)bounds.getWidth(), (int)bounds.getHeight(), BufferedImage.TYPE_INT_ARGB);
			}
		}
		else
		{
			oceanDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
			landDisplayPanel.setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm.none);
			oceanBackground = landBackground = ImageHelper.createWhiteTransparentImage((int)bounds.getWidth(), (int)bounds.getHeight());
		}

		oceanDisplayPanel.setImage(ImageHelper.extractRegion(oceanBackground, 0, 0, oceanBackground.getWidth()/2, 
				oceanDisplayPanel.getHeight()));
		oceanDisplayPanel.setLocation(backgroundDisplayCenterX - oceanBackground.getWidth()/2,
				oceanDisplayPanel.getLocation().y);
		oceanDisplayPanel.setSize(new Dimension(oceanBackground.getWidth()/2, oceanBackground.getHeight()));
		oceanDisplayPanel.repaint();
		
		landDisplayPanel.setImage(ImageHelper.extractRegion(landBackground, landBackground.getWidth()/2, 0, 
				landBackground.getWidth()/2, landDisplayPanel.getHeight()));
		landDisplayPanel.setSize(new Dimension(landBackground.getWidth()/2, landBackground.getHeight()));
		landDisplayPanel.repaint();
	}
	
	public void newSettings(JComponent parent)
	{
		if (openSettingsFilePath == null)
		{
			saveSettingsAs(parent);
		}
		else
		{
			generateAndloadNewSettings();
		}
	}
	
	public void saveSettings(JComponent parent)
	{
		if (openSettingsFilePath == null)
		{
			saveSettingsAs(parent);
		}
		else
		{
			final MapSettings settings = getSettingsFromGUI();
			Properties props = settings.toPropertiesFile();
			try
			{
				props.store(new PrintWriter(openSettingsFilePath.toString()), "");
				updateLastSettingsLoadedOrSaved(settings);
				getConsoleOutputTextArea().append("Settings saved to " + openSettingsFilePath.toString() + "\n");
			} 
			catch (IOException e)
			{
				e.printStackTrace();
		        JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to save settings.", JOptionPane.ERROR_MESSAGE);
			}
			updateFrameTitle();
		}
	}
	
	public void saveSettingsAs(JComponent parent)
	{
		Path curPath = openSettingsFilePath == null ? Paths.get(".") : openSettingsFilePath;
		File currentFolder = new File(curPath.toString());
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
				return f.isDirectory() || f.getName().endsWith(".properties");
			}
		});
		int status = fileChooser.showSaveDialog(parent);
		if (status == JFileChooser.APPROVE_OPTION)
		{
			openSettingsFilePath = Paths.get(fileChooser.getSelectedFile().getAbsolutePath());
			if (!openSettingsFilePath.getFileName().toString().contains("."))
			{
				openSettingsFilePath = Paths.get(openSettingsFilePath.toString() + ".properties");
			}
			
			final MapSettings settings = getSettingsFromGUI();
			Properties props = settings.toPropertiesFile();
			try
			{
				props.store(new PrintWriter(openSettingsFilePath.toString()), "");
				getConsoleOutputTextArea().append("Settings saved to " + openSettingsFilePath.toString() + "\n");
				updateLastSettingsLoadedOrSaved(settings);
			} catch (IOException e)
			{
				e.printStackTrace();
		        JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to save settings.",
		        		JOptionPane.ERROR_MESSAGE);
			}
			updateFrameTitle();
		}		
	}
	
	private void updateFrameTitle()
	{
		String title = frameTitleBase;
		if (openSettingsFilePath != null)
		{
			title += " - " + FilenameUtils.getName(openSettingsFilePath.toString());
		}
		frame.setTitle(title);
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
        Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false,
        		colorChooser, okHandler, null);
        dialog.setVisible(true);

	}

	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title)
	{
		Color c = JColorChooser.showDialog(parent, "", colorDisplay.getBackground());
		if (c != null)
			colorDisplay.setBackground(c);
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
		throw new IllegalArgumentException("No dropdown menu option with dimentions " + generatedWidth 
				+ " x " + generatedHeight);
	}
	
	private void loadSettingsIntoGUI(String propertiesFilePath)
	{
		loadingSettings = true;
		
		MapSettings settings = new MapSettings(propertiesFilePath);
		loadSettingsIntoGUI(settings);
	}
	
	/**
	 * Loads a map settings file into the GUI.
	 * @param path
	 */
	private void loadSettingsIntoGUI(MapSettings settings)
	{
		loadingSettings = true;

		sizeSlider.setValue(settings.worldSize);
		randomSeedTextField.setText(Long.toString(settings.randomSeed));
		edgeLandToWaterProbSlider.setValue((int)(settings.edgeLandToWaterProbability * 100));
		centerLandToWaterProbSlider.setValue((int)(settings.centerLandToWaterProbability * 100));
		scaleSlider.setValue((int)(settings.resolution * 100));
		landBlurSlider.setValue(settings.landBlur);
		oceanEffectsSlider.setValue(settings.oceanEffects);
		wavesRadioButton.setSelected(settings.addWavesToOcean);
		blurRadioButton.setSelected(!settings.addWavesToOcean);
		landBlurColorDisplay.setBackground(settings.landBlurColor);
		coastlineColorDisplay.setBackground(settings.coastlineColor);
		oceanEffectsColorDisplay.setBackground(settings.oceanEffectsColor);
		riverColorDisplay.setBackground(settings.riverColor);
		frayedEdgeCheckbox.setSelected(!settings.frayedBorder);
		// Do a click here to update other components on the panel as enabled or disabled.
		frayedEdgeCheckbox.doClick();
		grungeColorDisplay.setBackground(settings.frayedBorderColor);
		frayedEdgeBlurSlider.setValue(settings.frayedBorderBlurLevel);
		frayedEdgeSizeSlider.setValue(settings.frayedBorderSize);
		grungeSlider.setValue(settings.grungeWidth);
		cityProbabilitySlider.setValue((int)(settings.cityProbability * cityFrequencySliderScale));
		
		// Settings for background images.
		// Remove and add item listeners to the colorize checkboxes to avoid generating backgrounds for display multiple times.
		colorizeOceanCheckbox.removeItemListener(colorizeCheckboxListener);
		colorizeOceanCheckbox.setSelected((settings.colorizeOcean));
		colorizeOceanCheckbox.addItemListener(colorizeCheckboxListener);
		colorizeLandCheckbox.removeItemListener(colorizeCheckboxListener);
		colorizeLandCheckbox.setSelected((settings.colorizeLand));
		colorizeLandCheckbox.addItemListener(colorizeCheckboxListener);
		rdbtnGeneratedFromTexture.setSelected(settings.generateBackgroundFromTexture);
		rdbtnFractal.setSelected(settings.generateBackground);
		rdbtnFromFiles.setSelected(!settings.generateBackground && !settings.generateBackgroundFromTexture && !settings.transparentBackground);
		rdbtnTransparent.setSelected(settings.transparentBackground);
		backgroundImageButtonGroupListener.actionPerformed(null);
		textureImageFilename.setText(settings.backgroundTextureImage);
		landBackgroundImageFilename.setText(settings.landBackgroundImage);
		oceanBackgroundImageFilename.setText(settings.oceanBackgroundImage);
		backgroundSeedTextField.setText(String.valueOf(settings.backgroundRandomSeed));
		oceanDisplayPanel.setColor(settings.oceanColor);
		landDisplayPanel.setColor(settings.landColor);
		dimensionsComboBox.setSelectedIndex(getDimensionIndexFromDimensions(settings.generatedWidth, settings.generatedHeight));
		fractalPower = settings.fractalPower;
		
		drawRegionsCheckBox.setSelected(!settings.drawRegionColors);
		regionsSeedTextField.setText(String.valueOf(settings.regionsRandomSeed));
		drawRegionsCheckBox.doClick();
		// doClick seems to be ignored if the checkbox is disabled, so I must set the value again.
		drawRegionsCheckBox.setSelected(settings.drawRegionColors);
		hueSlider.setValue(settings.hueRange);
		saturationSlider.setValue(settings.saturationRange);
		brightnessSlider.setValue(settings.brightnessRange);
		
		booksPanel.removeAll();
		for (String book : getAllBooks())
		{
			final JCheckBox checkBox = new JCheckBox(book);
			booksPanel.add(checkBox);
			checkBox.setSelected(settings.books.contains(book));
			checkBox.addActionListener(new ActionListener()
			{			
				@Override
				public void actionPerformed(ActionEvent e)
				{
					warnOfEdits();
				}
			});
		}
		
		// Do a click to update other components on the panel as enabled or disabled.
		drawTextCheckBox.setSelected(!settings.drawText);
		drawTextCheckBox.doClick();

		textRandomSeedTextField.setText(settings.textRandomSeed + "");
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
		Set<String> borderTypes = MapCreator.getAvailableBorderTypes();
		for (String borderType : borderTypes)
		{
			borderTypeComboBox.addItem(borderType);
		}
		if (!settings.borderType.isEmpty())
		{
			if (!borderTypes.contains(settings.borderType))
			{
				borderTypeComboBox.addItem(settings.borderType);
			}
			borderTypeComboBox.setSelectedItem(settings.borderType);
		}
		borderWidthSlider.setValue(settings.borderWidth);
		drawBorderCheckbox.setSelected(!settings.drawBorder);
		drawBorderCheckbox.doClick();
		
		edits = settings.edits;
		
		updateFieldsWhenEditsChange();
		updateBackgroundImageDisplays();
		updateFrameTitle();

		updateLastSettingsLoadedOrSaved(settings);
		lastSettingsLoadedOrSaved.edits = Helper.deepCopy(lastSettingsLoadedOrSaved.edits);
		
		loadingSettings = false;	
	}
	
	private void updateLastSettingsLoadedOrSaved(MapSettings settings)
	{
		lastSettingsLoadedOrSaved = settings;
		lastSettingsLoadedOrSaved.edits = Helper.deepCopy(lastSettingsLoadedOrSaved.edits);
	}
	
	private List<String> getAllBooks()
	{
		String[] filenames = new File("assets/books").list(new FilenameFilter()
		{
			public boolean accept(File arg0, String name)
			{
				return name.endsWith("_place_names.txt");
			}
		});
	
		List<String> result = new ArrayList<>();
		for (String filename : filenames)
		{
			result.add(filename.replace("_place_names.txt", ""));
		}
		Collections.sort(result);
		return result;
	}
	
	private MapSettings getSettingsFromGUI()
	{
		MapSettings settings = new MapSettings();
		settings.worldSize = sizeSlider.getValue();
		settings.randomSeed = Long.parseLong(randomSeedTextField.getText());
		settings.landBackgroundImage = landBackgroundImageFilename.getText();
		settings.oceanBackgroundImage = oceanBackgroundImageFilename.getText();
		settings.edgeLandToWaterProbability = edgeLandToWaterProbSlider.getValue() / 100.0;
		settings.centerLandToWaterProbability = centerLandToWaterProbSlider.getValue() / 100.0;
		settings.resolution = scaleSlider.getValue() / 100.0;
		settings.landBlur = landBlurSlider.getValue();
		settings.oceanEffects = oceanEffectsSlider.getValue();
		settings.addWavesToOcean = wavesRadioButton.isSelected();
		settings.landBlurColor = landBlurColorDisplay.getBackground();
		settings.coastlineColor = coastlineColorDisplay.getBackground();
		settings.oceanEffectsColor = oceanEffectsColorDisplay.getBackground();
		settings.riverColor = riverColorDisplay.getBackground();
		settings.drawText = drawTextCheckBox.isSelected();
		settings.frayedBorder = frayedEdgeCheckbox.isSelected();
		settings.frayedBorderColor = grungeColorDisplay.getBackground();
		settings.frayedBorderBlurLevel = frayedEdgeBlurSlider.getValue();
		settings.frayedBorderSize = frayedEdgeSizeSlider.getValue();
		settings.grungeWidth = grungeSlider.getValue();
		settings.cityProbability = cityProbabilitySlider.getValue() / cityFrequencySliderScale;
		
		// Background image settings
		settings.generateBackground = rdbtnFractal.isSelected();
		settings.generateBackgroundFromTexture = rdbtnGeneratedFromTexture.isSelected();
		settings.transparentBackground = rdbtnTransparent.isSelected();
		settings.colorizeOcean = colorizeOceanCheckbox.isSelected();
		settings.colorizeLand = colorizeLandCheckbox.isSelected();
		settings.backgroundTextureImage = textureImageFilename.getText();
		settings.backgroundRandomSeed = Long.parseLong(backgroundSeedTextField.getText());
		settings.oceanColor = oceanDisplayPanel.getColor();
		settings.landColor = landDisplayPanel.getColor();
		Dimension generatedDimensions = getGeneratedBackgroundDimensionsFromGUI();
		settings.generatedWidth = (int)generatedDimensions.getWidth();
		settings.generatedHeight = (int)generatedDimensions.getHeight();
		settings.fractalPower = fractalPower;
		
		settings.regionsRandomSeed = Long.parseLong(regionsSeedTextField.getText());
		settings.drawRegionColors = drawRegionsCheckBox.isSelected();
		settings.hueRange = hueSlider.getValue();
		settings.saturationRange = saturationSlider.getValue();
		settings.brightnessRange = brightnessSlider.getValue();
		
		settings.books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox)component;
				if (checkBox.isSelected())
					settings.books.add(checkBox.getText());
			}
		}

		settings.textRandomSeed = Long.parseLong(textRandomSeedTextField.getText());
		settings.titleFont = titleFontDisplay.getFont();
		settings.regionFont = regionFontDisplay.getFont();
		settings.mountainRangeFont = mountainRangeFontDisplay.getFont();
		settings.otherMountainsFont = otherMountainsFontDisplay.getFont();
		settings.riverFont = riverFontDisplay.getFont();
		settings.textColor = textColorDisplay.getBackground();
		settings.boldBackgroundColor = boldBackgroundColorDisplay.getBackground();
		settings.drawBoldBackground = chckbxDrawBoldBackground.isSelected();
		
		settings.drawBorder = drawBorderCheckbox.isSelected();
		settings.borderType = (String)borderTypeComboBox.getSelectedItem();
		settings.borderWidth = borderWidthSlider.getValue();
		
		settings.edits = edits;
		return settings;
	}
	
	public boolean checkForUnsavedChanges()
	{
		final MapSettings currentSettings = getSettingsFromGUI();

        if (!currentSettings.equals(lastSettingsLoadedOrSaved))
        {
        	int n = JOptionPane.showConfirmDialog(
                    frame, "Settings have been modfied. Save changes?", "",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (n == JOptionPane.YES_OPTION) 
            {
            	saveSettings(null);
            }
            else if (n == JOptionPane.NO_OPTION)
            {
            }
            else if (n == JOptionPane.CANCEL_OPTION)
            {
            	return true;
            }
         }
        
        return false;
	}
	
	public void updateFieldsWhenEditsChange()
	{
		boolean hasEdits = !edits.isEmpty();
		
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblSize, sizeSlider, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblEdgeLandtowaterRatio, edgeLandToWaterProbSlider, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblCenterLandtowaterRatio, centerLandToWaterProbSlider, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblRandomSeed, randomSeedTextField, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(null, btnNewSeed, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblDimensions, dimensionsComboBox, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblTextRandomSeed, textRandomSeedTextField, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(null, btnNewTextRandomSeed, hasEdits);	
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(regionsRandomSeedLabel, regionsSeedTextField, hasEdits);	
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblHueRange, hueSlider, hasEdits);	
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblSaturationRange, saturationSlider, hasEdits);	
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(lblBrightnessRange, brightnessSlider, hasEdits);
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(null, newRegionSeedButton, hasEdits);	
		lockOrUnlockBecauseOfEditsAndUpdateTooltip(cityProbabilityLabel, cityProbabilitySlider, hasEdits);	
		lblMapEditsMessage.setVisible(hasEdits);
		clearEditsMenuItem.setEnabled(hasEdits);
	}
	
	public void lockOrUnlockBecauseOfEditsAndUpdateTooltip(JLabel label, JComponent component, boolean hasEdits)
	{
		if (component == null)
		{
			return;
		}
		
		String lockedMessage = " This is locked because this map has edits.";
		
		component.setEnabled(!hasEdits);
		
		if (hasEdits)
		{
			if (label != null)
			{
				addToTooltip(label, lockedMessage);
			}
			addToTooltip(component, lockedMessage);
		}
		else
		{
			if (label != null)
			{
				removeFromToolTip(label, lockedMessage);
			}
			removeFromToolTip(component, lockedMessage);
		}
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
