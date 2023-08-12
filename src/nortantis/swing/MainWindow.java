package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.FilenameUtils;
import org.imgscalr.Scalr.Method;

import com.formdev.flatlaf.FlatDarkLaf;

import nortantis.DimensionDouble;
import nortantis.ImageCache;
import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.SettingsGenerator;
import nortantis.TextDrawer;
import nortantis.UserPreferences;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class MainWindow extends JFrame
{
	private static JTextArea txtConsoleOutput;
	Path openSettingsFilePath;
	private boolean forceSaveAs;
	MapSettings lastSettingsLoadedOrSaved;
	static final String frameTitleBase = "Nortantis Fantasy Map Generator";
	public MapEdits edits;
	public JMenuItem clearEditsMenuItem;

	JScrollPane mapEditingScrollPane;
	// Controls how large 100% zoom is, in pixels.
	final double oneHundredPercentMapWidth = 4096;
	public MapEditingPanel mapEditingPanel;
	boolean areToolToggleButtonsEnabled = true;
	JMenuItem undoButton;
	JMenuItem redoButton;
	private JMenuItem clearEntireMapButton;
	public boolean isMapReadyForInteractions;
	public Undoer undoer;
	MapParts mapParts;
	double zoom;
	double imageQualityScale;
	private boolean mapNeedsFullRedraw;
	private ArrayDeque<IncrementalUpdate> incrementalUpdatesToDraw;
	public boolean isMapBeingDrawn;
	private ReentrantLock drawLock;
	private JMenu imageQualityMenu;
	private JRadioButtonMenuItem radioButton75Percent;
	private JRadioButtonMenuItem radioButton100Percent;
	private JRadioButtonMenuItem radioButton125Percent;
	private JRadioButtonMenuItem radioButton150Percent;
	private ThemePanel themePanel;
	private ToolsPanel toolsPanel;
	private double resolutionForImageExport;

	
	public MainWindow(String fileToOpen)
	{
		super(frameTitleBase);

		createGUI();

		MapSettings settings = null;
		try
		{
			if (fileToOpen != null && !fileToOpen.isEmpty() && fileToOpen.endsWith(MapSettings.fileExtensionWithDot) 
					&& new File(fileToOpen).exists())
			{
				settings = new MapSettings(fileToOpen);
				openSettingsFilePath = Paths.get(fileToOpen);
				updateFrameTitle();
			}
			else if (!UserPreferences.getInstance().lastLoadedSettingsFile.isEmpty() 
					&& Files.exists(Paths.get(UserPreferences.getInstance().lastLoadedSettingsFile)))
			{
				settings = new MapSettings(UserPreferences.getInstance().lastLoadedSettingsFile);
				openSettingsFilePath = Paths.get(UserPreferences.getInstance().lastLoadedSettingsFile);
				updateFrameTitle();
			}
		}
		catch (Exception e)
		{
			// This means they moved their settings or their settings were corrupted somehow.
			e.printStackTrace();
		}
		
		if (settings != null)
		{
			loadSettingsIntoGUI(settings);
		}
		else
		{
			// TODO make sure this message is correct.
			mapEditingPanel.image = createPlaceholderImage(new String[] {
					"Welcome to Nortantis. To create a map, go to", 
					"File > New Random Map."});
			mapEditingPanel.repaint();
		}
	}
	
	private void createGUI()
	{
		getContentPane().setPreferredSize(new Dimension(1214, 701));
		getContentPane().setLayout(new BorderLayout());

		setIconImage(ImageHelper.read(Paths.get(AssetsPath.get(), "internal/taskbar icon.png").toString()));
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		addWindowListener(new WindowAdapter()
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
						dispose();
						System.exit(0);
					}
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}

			@Override
			public void windowActivated(WindowEvent e)
			{
				// There's a bug in Windows where all of my swing components
				// disappear when you lock the screen and unlock it.
				// The is a fix that works most of the time.
				repaint();
			}
		});
				
		createMenuBar();
				
		themePanel = new ThemePanel(this);
		createMapEditingPanel();
		mapEditingScrollPane.setMinimumSize(new Dimension(500, themePanel.getMinimumSize().height));
		toolsPanel = new ToolsPanel(this, mapEditingPanel);
		
		JSplitPane splitPane1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, themePanel, mapEditingScrollPane);
		splitPane1.setOneTouchExpandable(true);
		JSplitPane splitPane2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane1, toolsPanel);
		splitPane2.setResizeWeight(1.0);
		splitPane2.setOneTouchExpandable(true);
		getContentPane().add(splitPane2, BorderLayout.CENTER);
		

		
		createConsoleOutput();

		pack();
				
		drawLock = new ReentrantLock();
		incrementalUpdatesToDraw = new ArrayDeque<>();
	}
	
	private void launchNewSettingsDialog()
	{
		NewSettingsDialog dialog = new NewSettingsDialog(this);
		dialog.setVisible(true);
	}
	
	private void createConsoleOutput()
	{
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(10, 417, 389, 260);
		txtConsoleOutput = new JTextArea();
		scrollPane.setViewportView(txtConsoleOutput);
		txtConsoleOutput.setEditable(false);
		
		// TODO Add the scrollPane somewhere.
		// TODO Either use a JSplitPane or create this in a separate popup.
	}
	
	private void createMapEditingPanel()
	{
		mapEditingPanel = new MapEditingPanel(null, this);
		mapEditingPanel.setLayout(new BorderLayout());

		mapEditingPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					toolsPanel.currentTool.handleMouseClickOnMap(e);
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					toolsPanel.currentTool.handleMousePressedOnMap(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					toolsPanel.currentTool.handleMouseReleasedOnMap(e);
				}
			}

		});

		mapEditingPanel.addMouseMotionListener(new MouseMotionListener()
		{

			@Override
			public void mouseMoved(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					toolsPanel.currentTool.handleMouseMovedOnMap(e);
				}
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					toolsPanel.currentTool.handleMouseDraggedOnMap(e);
				}
			}
		});

		mapEditingPanel.addMouseListener(new MouseListener()
		{

			@Override
			public void mouseReleased(MouseEvent e)
			{
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					toolsPanel.currentTool.handleMouseExitedMap(e);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
			}
		});
		
		mapEditingScrollPane = new JScrollPane(mapEditingPanel);

		// TODO Make sure the below works. It use to be on the frame.
		mapEditingScrollPane.addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent componentEvent)
			{
				if (ToolsPanel.fitToWindowZoomLevel.equals(toolsPanel.getZoomString()))
				{
					createAndShowMapIncrementalUsingCenters(null);
				}
			}
		});

		// Speed up the scroll speed.
		mapEditingScrollPane.getVerticalScrollBar().setUnitIncrement(16);
	}
	
	private void createMenuBar()
	{
		final MainWindow mainWindow = this;
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);

		final JMenuItem mntmNew = new JMenuItem("New Random Map");
		fileMenu.add(mntmNew);
		mntmNew.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				boolean cancelPressed = checkForUnsavedChanges();
				if (!cancelPressed)
				{
					launchNewSettingsDialog();
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
						return f.isDirectory() || f.getName().toLowerCase().endsWith(".properties")
								|| f.getName().toLowerCase().endsWith(MapSettings.fileExtensionWithDot);
					}
				});
				int status = fileChooser.showOpenDialog(mainWindow);
				if (status == JFileChooser.APPROVE_OPTION)
				{
					openSettingsFilePath = Paths.get(fileChooser.getSelectedFile().getAbsolutePath());
					MapSettings settings = new MapSettings(openSettingsFilePath.toString());
					loadSettingsIntoGUI(settings);
					updateFrameTitle();

					if (MapSettings.isOldPropertiesFile(openSettingsFilePath.toString()))
					{
						JOptionPane.showMessageDialog(mainWindow, FilenameUtils.getName(openSettingsFilePath.toString())
								+ " is an older format '.properties' file. \nWhen you save, it will be converted to the newer format, a '"
								+ MapSettings.fileExtensionWithDot + "' file.", "File Converted", JOptionPane.INFORMATION_MESSAGE);
						openSettingsFilePath = Paths.get(FilenameUtils.getFullPath(openSettingsFilePath.toString()), 
								FilenameUtils.getBaseName(openSettingsFilePath.toString()) + MapSettings.fileExtensionWithDot);
						forceSaveAs = true;
					}

				}

			}
		});

		final JMenuItem mntmSave = new JMenuItem("Save");
		mntmSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
		fileMenu.add(mntmSave);
		mntmSave.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				saveSettings(mainWindow);
			}
		});

		final JMenuItem mntmSaveAs = new JMenuItem("Save As...");
		fileMenu.add(mntmSaveAs);
		mntmSaveAs.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				saveSettingsAs(mainWindow);
			}
		});
		
		// TODO Convert this to a PNG export workflow
		JMenuItem mntmExportMapAsImage = new JMenuItem("Export As Image");
		fileMenu.add(mntmExportMapAsImage);
		mntmExportMapAsImage.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				handleExportAsImagePressed();
			}
		});
		

		JMenuItem mntmExportHeightmap = new JMenuItem("Export Heightmap");
		fileMenu.add(mntmExportHeightmap);
		mntmExportHeightmap.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				// TODO Show a dialog that prompts for resolution. Make it modal to prevent other map generation stuff while it's happening.
				// TODO Maybe also prevent it from running while the map is drawing.
				
				showHeightMapWithEditsWarning();

				Logger.clear();
				SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
				{
					@Override
					public BufferedImage doInBackground() throws IOException
					{
						MapSettings settings = getSettingsFromGUI();
						
						double resolutionScale = 1.0; // TODO Pull this from a setting in a new heightmap export dialog.
						settings.resolution = resolutionScale;
						
						Logger.println("Creating a heightmap...");
						BufferedImage heightMap = new MapCreator().createHeightMap(settings);
						Logger.println("Opening the heightmap in your system's default image editor.");
						String fileName = ImageHelper.openImageInSystemDefaultEditor(heightMap, "heightmap");
						Logger.println("Heightmap written to " + fileName);
						return heightMap;
					}

					@Override
					public void done()
					{
						try
						{
							get();
						}
						catch (Exception ex)
						{
							handleBackgroundThreadException(ex);
						}
					}
				};
				worker.execute();

			}

		});
		
		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);

		undoButton = new JMenuItem("Undo");
		undoButton.setEnabled(false);
		mnEdit.add(undoButton);
		undoButton.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));
		undoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (toolsPanel.currentTool != null)
				{
					undoer.undo();
				}
				undoer.updateUndoRedoEnabled();
			}
		});

		redoButton = new JMenuItem("Redo");
		redoButton.setEnabled(false);
		mnEdit.add(redoButton);
		redoButton.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		redoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (toolsPanel.currentTool != null)
				{
					undoer.redo();
				}
				undoer.updateUndoRedoEnabled();
			}
		});

		clearEntireMapButton = new JMenuItem("Clear Entire Map");
		mnEdit.add(clearEntireMapButton);
		clearEntireMapButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				clearEntireMap();
			}
		});
		clearEntireMapButton.setEnabled(false);

		JMenu mnView = new JMenu("View");
		menuBar.add(mnView);

		imageQualityMenu = new JMenu("Image Quality");
		mnView.add(imageQualityMenu);
		imageQualityMenu.setToolTipText("Change the quality of the map displayed in the editor. Does not apply when exporting the map to an image using the Generate button in the main window. Higher values make the editor slower.");

		ActionListener resolutionListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				String text = ((JRadioButtonMenuItem) e.getSource()).getText();
				UserPreferences.getInstance().editorImageQuality = text;
				handleImageQualityChange(text);
				
				showImageQualityMessage();
			}
		};

		ButtonGroup resolutionButtonGroup = new ButtonGroup();

		radioButton75Percent = new JRadioButtonMenuItem("Low");
		radioButton75Percent.addActionListener(resolutionListener);
		imageQualityMenu.add(radioButton75Percent);
		resolutionButtonGroup.add(radioButton75Percent);

		radioButton100Percent = new JRadioButtonMenuItem("Medium");
		radioButton100Percent.addActionListener(resolutionListener);
		imageQualityMenu.add(radioButton100Percent);
		resolutionButtonGroup.add(radioButton100Percent);

		radioButton125Percent = new JRadioButtonMenuItem("High");
		radioButton125Percent.addActionListener(resolutionListener);
		imageQualityMenu.add(radioButton125Percent);
		resolutionButtonGroup.add(radioButton125Percent);
		
		radioButton150Percent = new JRadioButtonMenuItem("Very High");
		radioButton150Percent.addActionListener(resolutionListener);
		imageQualityMenu.add(radioButton150Percent);
		resolutionButtonGroup.add(radioButton150Percent);

		if (UserPreferences.getInstance().editorImageQuality != null && !UserPreferences.getInstance().editorImageQuality.equals(""))
		{
			boolean found = false;
			for (JRadioButtonMenuItem resolutionOption : new JRadioButtonMenuItem[] 
					{ radioButton75Percent, radioButton100Percent, radioButton125Percent, radioButton150Percent })
			{
				if (UserPreferences.getInstance().editorImageQuality.equals(resolutionOption.getText()))
				{
					resolutionOption.setSelected(true);
					found = true;
					break;
				}
			}
			if (!found)
			{
				// Shouldn't happen. This means the user preferences have a bad
				// value.
				radioButton100Percent.setSelected(true);
				UserPreferences.getInstance().editorImageQuality = radioButton100Percent.getText();
			}
		}
		else
		{
			radioButton100Percent.setSelected(true);
			UserPreferences.getInstance().editorImageQuality = radioButton100Percent.getText();
		}
	}
	
	public void handleMouseWheelChangingZoom(MouseWheelEvent e)
	{
		if (isMapReadyForInteractions)
		{
			int scrollDirection = e.getUnitsToScroll() > 0 ? -1 : 1;
			int newIndex = toolsPanel.zoomComboBox.getSelectedIndex() + scrollDirection;
			if (newIndex < 0)
			{
				newIndex = 0;
			}
			else if (newIndex > toolsPanel.zoomComboBox.getItemCount() - 1)
			{
				newIndex = toolsPanel.zoomComboBox.getItemCount() - 1;
			}
			if (newIndex != toolsPanel.zoomComboBox.getSelectedIndex())
			{
				toolsPanel.zoomComboBox.setSelectedIndex(newIndex);
				updateDisplayedMapFromGeneratedMap(true);
			}
		}
	}

	public void updateDisplayedMapFromGeneratedMap(boolean updateScrollLocationIfZoomChanged)
	{
		double oldZoom = zoom;
		zoom = translateZoomLevel((String) toolsPanel.zoomComboBox.getSelectedItem());
				
		if (mapEditingPanel.mapFromMapCreator != null)
		{
			java.awt.Rectangle scrollTo = null;

			if (updateScrollLocationIfZoomChanged && zoom != oldZoom)
			{
				java.awt.Rectangle visible = mapEditingPanel.getVisibleRect();
				double scale = zoom / oldZoom;
				java.awt.Point mousePosition = mapEditingPanel.getMousePosition();
				if (mousePosition != null && (zoom > oldZoom))
				{
					// Zoom toward the mouse's position, keeping the point currently under the mouse the same if possible.
					scrollTo = new java.awt.Rectangle(
							(int)(mousePosition.x * scale) - mousePosition.x + visible.x, 
							(int)(mousePosition.y * scale) - mousePosition.y + visible.y, 
							visible.width, 
							visible.height);
				}
				else
				{
					// Zoom toward or away from the current center of the screen.
					java.awt.Point 	currentCentroid = new java.awt.Point(visible.x + (visible.width / 2), visible.y + (visible.height / 2));
					java.awt.Point targetCentroid = new java.awt.Point((int)(currentCentroid.x * scale),(int)(currentCentroid.y * scale));
					scrollTo = new java.awt.Rectangle(
							targetCentroid.x - visible.width/2, 
							targetCentroid.y - visible.height/2, 
							visible.width, 
							visible.height);
				}
			}

			BufferedImage mapWithExtraStuff = toolsPanel.currentTool.onBeforeShowMap(mapEditingPanel.mapFromMapCreator);
			mapEditingPanel.setZoom(zoom);
			Method method = zoom < 0.3 ? Method.QUALITY : Method.BALANCED;
			mapEditingPanel.image = ImageHelper.scaleByWidth(mapWithExtraStuff, (int) (mapWithExtraStuff.getWidth() * zoom),
					method);
			
			if (scrollTo != null)
			{
				// For some reason I have to do a whole bunch of revalidation or else scrollRectToVisible doesn't realize the map has changed size.
				mapEditingPanel.revalidate();
				mapEditingScrollPane.revalidate();
				this.revalidate();
				
				mapEditingPanel.scrollRectToVisible(scrollTo);
			}
			
			mapEditingPanel.revalidate();
			mapEditingScrollPane.revalidate();
			mapEditingPanel.repaint();
			mapEditingScrollPane.repaint();
		}
	}

	private double translateZoomLevel(String zoomLevel)
	{
		if (zoomLevel == null)
		{
			return 1.0;
		}
		else if (zoomLevel.equals(ToolsPanel.fitToWindowZoomLevel))
		{
			if (mapEditingPanel.mapFromMapCreator != null)
			{
				final int additionalWidthToRemoveIDontKnowWhereItsCommingFrom = 2;
				Dimension size = new Dimension(mapEditingScrollPane.getSize().width - additionalWidthToRemoveIDontKnowWhereItsCommingFrom,
						mapEditingScrollPane.getSize().height - additionalWidthToRemoveIDontKnowWhereItsCommingFrom);

				DimensionDouble fitted = ImageHelper.fitDimensionsWithinBoundingBox(size, mapEditingPanel.mapFromMapCreator.getWidth(),
						mapEditingPanel.mapFromMapCreator.getHeight());
				return fitted.getWidth() / mapEditingPanel.mapFromMapCreator.getWidth();
			}
			else
			{
				return 1.0;
			}
		}
		else
		{
			double percentage = parsePercentage(zoomLevel);
			if (mapEditingPanel.mapFromMapCreator != null)
			{
				return (oneHundredPercentMapWidth * percentage) / mapEditingPanel.mapFromMapCreator.getWidth();
			}
			else
			{
				return 1.0;
			}
		}
	}
	
	private void showImageQualityMessage()
	{
		if (!UserPreferences.getInstance().hideImageQualityMessage)
		{
			Dimension size = new Dimension(400, 115);
			JPanel panel = new JPanel();
			panel.setPreferredSize(size);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			JLabel label = new JLabel("<html>Changing the image quality in the editor is only for the purpose of either speeding"
					+ " up the editor by using a lower quality, or viewing more details at higher zoom levels using a higher quality."
					+ " It does not affect the quality of the map exported to an image when you press the Generate button in the"
					+ " main window.</html>");
			panel.add(label);
			label.setMaximumSize(size);
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(this, panel, "Tip", JOptionPane.INFORMATION_MESSAGE);
			UserPreferences.getInstance().hideImageQualityMessage = checkBox.isSelected();
		}
	}

	public void enableOrDisableToolToggleButtonsAndZoom(boolean enable)
	{
		areToolToggleButtonsEnabled = enable;

		clearEntireMapButton.setEnabled(enable);
		if (imageQualityMenu != null)
		{
			radioButton75Percent.setEnabled(enable);
			radioButton100Percent.setEnabled(enable);
			radioButton125Percent.setEnabled(enable);
			radioButton150Percent.setEnabled(enable);
		}

		toolsPanel.enableOrDisableToolToggleButtonsAndZoom(enable);
	}

	private double parsePercentage(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		return zoomPercent / 100.0;
	}

	private class IncrementalUpdate
	{
		public IncrementalUpdate(Set<Center> centersChanged, Set<Edge> edgesChanged)
		{
			if (centersChanged != null)
			{
				this.centersChanged = new HashSet<Center>(centersChanged);
			}
			if (edgesChanged != null)
			{
				this.edgesChanged = new HashSet<Edge>(edgesChanged);
			}
		}

		Set<Center> centersChanged;
		Set<Edge> edgesChanged;

		public void add(IncrementalUpdate other)
		{
			if (other == null)
			{
				return;
			}

			if (centersChanged != null && other.centersChanged != null)
			{
				centersChanged.addAll(other.centersChanged);
			}
			else if (centersChanged == null && other.centersChanged != null)
			{
				centersChanged = new HashSet<>(other.centersChanged);
			}

			if (edgesChanged != null && other.edgesChanged != null)
			{
				edgesChanged.addAll(other.edgesChanged);
			}
			else if (edgesChanged == null && other.edgesChanged != null)
			{
				edgesChanged = new HashSet<>(other.edgesChanged);
			}
		}
	}

	public void updateChangedCentersOnMap(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null);
	}

	public void updateChangedEdgesOnMap(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged);
	}

	/**
	 * Redraws the map, then displays it. Use only with UpdateType.Full and
	 * UpdateType.Quick.
	 */
	public void createAndShowMapFull()
	{
		createAndShowMap(UpdateType.Full, null, null);
	}

	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null);
	}

	public void createAndShowMapIncrementalUsingEdges(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged);
	}

	public void createAndShowMapFromChange(MapChange change)
	{
		if (change.updateType == UpdateType.Full)
		{
			createAndShowMapFull();
		}
		else
		{
			Set<Center> centersChanged = getCentersWithChangesInEdits(change.edits);
			Set<Edge> edgesChanged = null;
			// Currently createAndShowMap doesn't support drawing both center
			// edits and edge edits at the same time, so there is no
			// need to find edges changed if centers were changed.
			if (centersChanged.size() == 0)
			{
				edgesChanged = getEdgesWithChangesInEdits(change.edits);
			}
			createAndShowMap(UpdateType.Incremental, centersChanged, edgesChanged);
		}
	}

	private Set<Center> getCentersWithChangesInEdits(MapEdits changeEdits)
	{
		Set<Center> changedCenters = edits.centerEdits.stream()
				.filter(cEdit -> !cEdit.equals(changeEdits.centerEdits.get(cEdit.index)))
				.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());

		Set<RegionEdit> regionChanges = edits.regionEdits.values().stream()
				.filter(rEdit -> !rEdit.equals(changeEdits.regionEdits.get(rEdit.regionId))).collect(Collectors.toSet());
		for (RegionEdit rEdit : regionChanges)
		{
			Set<Center> regionCenterEdits = changeEdits.centerEdits.stream()
					.filter(cEdit -> cEdit.regionId != null && cEdit.regionId == rEdit.regionId)
					.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());
			changedCenters.addAll(regionCenterEdits);
		}

		return changedCenters;
	}

	private Set<Edge> getEdgesWithChangesInEdits(MapEdits changeEdits)
	{
		return edits.edgeEdits.stream().filter(eEdit -> !eEdit.equals(changeEdits.edgeEdits.get(eEdit.index)))
				.map(eEdit -> mapParts.graph.edges.get(eEdit.index)).collect(Collectors.toSet());
	}

	/**
	 * Redraws the map, then displays it
	 */
	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged)
	{
		if (isMapBeingDrawn)
		{
			if (updateType == UpdateType.Full)
			{
				mapNeedsFullRedraw = true;
				incrementalUpdatesToDraw.clear();
			}
			else if (updateType == UpdateType.Incremental)
			{
				incrementalUpdatesToDraw.add(new IncrementalUpdate(centersChanged, edgesChanged));
			}
			return;
		}

		isMapBeingDrawn = true;
		enableOrDisableToolToggleButtonsAndZoom(false);
		
		final MapSettings settings = getSettingsFromGUI();
		settings.resolution = imageQualityScale;
		settings.alwaysUpdateLandBackgroundWithOcean = true;
		
		if (settings.edits.isEmpty())
		{
			settings.edits.bakeGeneratedTextAsEdits = true;
		}

		// TODO remove these and draw everything
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;

		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
		{
			@Override
			public BufferedImage doInBackground() throws IOException
			{				
				Logger.clear();
				drawLock.lock();
				try
				{
					if (updateType == UpdateType.Full)
					{
						if (mapParts == null)
						{
							mapParts = new MapParts();
						}
						BufferedImage map = new MapCreator().createMap(settings, null, mapParts);
						System.gc();
						return map;
					}
					else
					{
						BufferedImage map = mapEditingPanel.mapFromMapCreator;
						// Incremental update
						if (centersChanged != null && centersChanged.size() > 0)
						{
							new MapCreator().incrementalUpdateCenters(settings, mapParts, map, centersChanged);
							return map;
						}
						else if (edgesChanged != null && edgesChanged.size() > 0)
						{
							new MapCreator().incrementalUpdateEdges(settings, mapParts, map, edgesChanged);
							return map;
						}
						else
						{
							// Nothing to do.
							return map;
						}
					}
				}
				finally
				{
					drawLock.unlock();
				}
			}

			@Override
			public void done()
			{
				try
				{
					mapEditingPanel.mapFromMapCreator = get();
				}
				catch (InterruptedException ex)
				{
					throw new RuntimeException(ex);
				}
				catch (Exception ex)
				{
					if (isCausedByOutOfMemoryError(ex))
					{
						ex.printStackTrace();
						String outOfMemoryMessage = "Out of memory. Try lowering the zoom or allocating more memory to the Java heap space.";
						JOptionPane.showMessageDialog(null, outOfMemoryMessage, "Error", JOptionPane.ERROR_MESSAGE);
					}
					else
					{
						ex.printStackTrace();
						JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}

				if (mapEditingPanel.mapFromMapCreator != null)
				{
					mapEditingPanel.setGraph(mapParts.graph);

					initializeCenterEditsIfEmpty();
					initializeRegionEditsIfEmpty();
					initializeEdgeEditsIfEmpty();

					if (undoer.copyOfEditsWhenEditorWasOpened == null)
					{
						// This has to be done after the map is drawn rather
						// than when the editor frame is first created because
						// the first time the map is drawn is when the edits are
						// created.
						undoer.copyOfEditsWhenEditorWasOpened = edits.deepCopy();
					}

					updateDisplayedMapFromGeneratedMap(false);

					enableOrDisableToolToggleButtonsAndZoom(true);

					isMapBeingDrawn = false;
					if (mapNeedsFullRedraw)
					{
						createAndShowMapFull();
					}
					else if (updateType == UpdateType.Incremental && incrementalUpdatesToDraw.size() > 0)
					{
						IncrementalUpdate incrementalUpdate = combineAndGetNextIncrementalUpdateToDraw();
						createAndShowMap(UpdateType.Incremental, incrementalUpdate.centersChanged, incrementalUpdate.edgesChanged);
					}

					if (updateType == UpdateType.Full)
					{
						mapNeedsFullRedraw = false;
					}

					// Tell the scroll pane to update itself.
					mapEditingPanel.revalidate();
					mapEditingPanel.repaint();
					
					isMapReadyForInteractions = true;
				}
				else
				{
					enableOrDisableToolToggleButtonsAndZoom(true);
					mapEditingPanel.clearSelectedCenters();
					isMapBeingDrawn = false;
				}
			}

		};
		worker.execute();
	}

	/**
	 * Combines the incremental updates in incrementalUpdatesToDraw so they can
	 * be drawn together. Clears out incrementalUpdatesToDraw.
	 * 
	 * @return The combined update to draw
	 */
	private IncrementalUpdate combineAndGetNextIncrementalUpdateToDraw()
	{
		if (incrementalUpdatesToDraw.size() == 0)
		{
			return null;
		}

		IncrementalUpdate result = incrementalUpdatesToDraw.pop();
		if (incrementalUpdatesToDraw.size() == 1)
		{
			return result;
		}

		while (incrementalUpdatesToDraw.size() > 0)
		{
			IncrementalUpdate next = incrementalUpdatesToDraw.pop();
			result.add(next);
		}
		return result;
	}

	private boolean isCausedByOutOfMemoryError(Throwable ex)
	{
		if (ex == null)
		{
			return false;
		}

		if (ex instanceof OutOfMemoryError)
		{
			return true;
		}

		return isCausedByOutOfMemoryError(ex.getCause());
	}

	private void initializeCenterEditsIfEmpty()
	{
		if (edits.centerEdits.isEmpty())
		{
			edits.initializeCenterEdits(mapParts.graph.centers, mapParts.iconDrawer);
		}
	}

	private void initializeEdgeEditsIfEmpty()
	{
		if (edits.edgeEdits.isEmpty())
		{
			edits.initializeEdgeEdits(mapParts.graph.edges);
		}
	}

	private void initializeRegionEditsIfEmpty()
	{
		if (edits.regionEdits.isEmpty())
		{
			edits.initializeRegionEdits(mapParts.graph.regions.values());
		}
	}

	/**
	 * Handles when zoom level changes in the display.
	 */
	public void handleImageQualityChange(String resolutionText)
	{
		updateImageQualityScale(resolutionText);
		
		isMapReadyForInteractions = false;
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.repaint();

		ImageCache.getInstance().clear();
		mapParts = null;
		createAndShowMapFull();
	}
	
	private void updateImageQualityScale(String imageQualityText)
	{
		if (imageQualityText.equals(radioButton75Percent.getText()))
		{
			imageQualityScale = 0.75;
		}
		else if (imageQualityText.equals(radioButton100Percent.getText()))
		{
			imageQualityScale = 1.0;
		}
		else if (imageQualityText.equals(radioButton125Percent.getText()))
		{
			imageQualityScale = 1.25;
		}
		else if (imageQualityText.equals(radioButton150Percent.getText()))
		{
			imageQualityScale = 1.5;
		}
	}

	public void clearEntireMap()
	{
		if (mapParts == null || mapParts.graph == null)
		{
			return;
		}

		// Erase text
		for (MapText text : edits.text)
		{
			text.value = "";
		}

		for (Center center : mapParts.graph.centers)
		{
			// Change land to ocean
			edits.centerEdits.get(center.index).isWater = true;
			edits.centerEdits.get(center.index).isLake = false;

			// Erase icons
			edits.centerEdits.get(center.index).trees = null;
			edits.centerEdits.get(center.index).icon = null;

			// Erase rivers
			for (Edge edge : center.borders)
			{
				EdgeEdit eEdit = edits.edgeEdits.get(edge.index);
				eEdit.riverLevel = 0;
			}
		}

		undoer.setUndoPoint(UpdateType.Full, null);
		createAndShowMapFull();
	}
		
	void handleBackgroundThreadException(Exception ex)
	{
		if (ex instanceof ExecutionException)
		{
			if (ex.getCause() != null)
			{
				ex.getCause().printStackTrace();
				if (ex.getCause() instanceof OutOfMemoryError)
				{
					JOptionPane.showMessageDialog(null,
							"Out of memory. Try allocating more memory to the Java heap space, or decrease the resolution in the Background tab.",
							"Error", JOptionPane.ERROR_MESSAGE);
				}
				else
				{
					JOptionPane.showMessageDialog(null, ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
			else
			{
				// Should never happen.
				ex.printStackTrace();
				JOptionPane.showMessageDialog(null, "An ExecutionException error occured with no cause: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
		else
		{
			ex.printStackTrace();
			JOptionPane.showMessageDialog(null, "An unexpected error occured: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	public static boolean isRunning()
	{
		return txtConsoleOutput != null;
	}
	
	public static JTextArea getConsoleOutputTextArea()
	{
		return txtConsoleOutput;
	}

	private void handleExportAsImagePressed()
	{
		// TODO Put these UI elements somewhere.
		JSlider scaleSlider = new JSlider();
		scaleSlider.setPaintLabels(true);
		scaleSlider.setBounds(131, 12, 245, 79);
		scaleSlider.setValue(100);
		scaleSlider.setSnapToTicks(true);
		scaleSlider.setPaintTicks(true);
		scaleSlider.setMinorTickSpacing(25);
		scaleSlider.setMajorTickSpacing(25);
		scaleSlider.setMinimum(25);
		scaleSlider.setMaximum(calcMaximumResolution());
		int labelFrequency = scaleSlider.getMaximum() < 300 ? 50 : 100;
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = scaleSlider.getMinimum(); i < scaleSlider.getMaximum() + 1; i += scaleSlider.getMinorTickSpacing())
			{
				if (i % labelFrequency == 0)
				{
					labelTable.put(i, new JLabel(i + "%"));
				}
			}
			scaleSlider.setLabelTable(labelTable);
		}
		
		
		// TODO Show a dialog that prompts for resolution. Make it modal to prevent other map generation stuff while it's happening.
		// TODO Maybe also prevent it from running while the map is drawing.
		
		// TODO Pull this from the setting in the export dialog, and store it into resolutionForImageExport.
		resolutionForImageExport = 1.0; 
		
		final MapSettings settings = getSettingsFromGUI();

		Logger.clear();
		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
		{
			@Override
			public BufferedImage doInBackground() throws Exception
			{
				ImageCache.getInstance().clear();

				BufferedImage map = new MapCreator().createMap(settings.deepCopy(), null, null);
				System.gc();

				Logger.println("Opening the map in your system's default image editor.");
				String fileName = ImageHelper.openImageInSystemDefaultEditor(map, "map_" + settings.randomSeed);
				Logger.println("Map written to " + fileName);
				return map;
			}

			@Override
			public void done()
			{
				try
				{
					get();
				}
				catch (Exception ex)
				{
					handleBackgroundThreadException(ex);
				}
			}
		};
		worker.execute();
	}
	
	private int calcMaximumResolution()
	{
		long maxBytes = Runtime.getRuntime().maxMemory();
		// The required memory is quadratic in the resolution used.
		// To generate a map at resolution 225 takes 7GB, so 7×1024^3÷(225^2)
		// = 148468.
		int maxResolution = (int) Math.sqrt(maxBytes / 148468L);

		// The FFT-based code will create arrays in powers of 2.
		int nextPowerOf2 = ImageHelper.getPowerOf2EqualOrLargerThan(maxResolution / 100.0);
		int resolutionAtNextPowerOf2 = nextPowerOf2 * 100;
		// Average with the original prediction because not all code is
		// FFT-based.
		maxResolution = (maxResolution + resolutionAtNextPowerOf2) / 2;

		if (maxResolution > 500)
		{
			// This is in case Runtime.maxMemory returns Long's max value, which
			// it says it will if it fails.
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

	private void showHeightMapWithEditsWarning()
	{
		if (edits != null && !edits.isEmpty() && !UserPreferences.getInstance().hideHeightMapWithEditsWarning)
		{
			Dimension size = new Dimension(400, 80);
			JPanel panel = new JPanel();
			panel.setPreferredSize(size);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			JLabel label = new JLabel(
					"<html>Edits made in the editor, such as land, water, and mountains, " + "are not applied to height maps. </html>");
			panel.add(label);
			label.setMaximumSize(size);
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(this, panel, "", JOptionPane.INFORMATION_MESSAGE);
			UserPreferences.getInstance().hideHeightMapWithEditsWarning = checkBox.isSelected();
		}
	}

	public boolean checkForUnsavedChanges()
	{
		if (lastSettingsLoadedOrSaved == null)
		{
			return false;
		}
		
		final MapSettings currentSettings = getSettingsFromGUI();
		
		if (!currentSettings.equals(lastSettingsLoadedOrSaved))
		{
			int n = JOptionPane.showConfirmDialog(this, "Settings have been modfied. Save changes?", "", JOptionPane.YES_NO_CANCEL_OPTION);
			if (n == JOptionPane.YES_OPTION)
			{
				saveSettings(this);
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
	
	public void saveSettings(Component parent)
	{
		if (openSettingsFilePath == null || forceSaveAs)
		{
			saveSettingsAs(parent);
			forceSaveAs = false;
		}
		else
		{
			final MapSettings settings = getSettingsFromGUI();
			try
			{
				settings.writeToFile(openSettingsFilePath.toString());
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

	public void saveSettingsAs(Component parent)
	{
		Path curPath = openSettingsFilePath == null ? Paths.get(".") : openSettingsFilePath;
		File currentFolder = openSettingsFilePath == null ? curPath.toFile() : new File(FilenameUtils.getFullPath(curPath.toString()));
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
				return f.isDirectory() || f.getName().endsWith(MapSettings.fileExtensionWithDot);
			}
		});
		
		// This is necessary when we want to automatically select a file that doesn't exist to save into, which is done 
		// when converting a properties file into a nort file.
		if (openSettingsFilePath != null && !FilenameUtils.getName(openSettingsFilePath.toString()).equals(""))
		{
			fileChooser.setSelectedFile(new File(openSettingsFilePath.toString()));
		}
		
		int status = fileChooser.showSaveDialog(parent);
		if (status == JFileChooser.APPROVE_OPTION)
		{
			openSettingsFilePath = Paths.get(fileChooser.getSelectedFile().getAbsolutePath());
			if (!openSettingsFilePath.getFileName().toString().endsWith(MapSettings.fileExtensionWithDot))
			{
				openSettingsFilePath = Paths.get(openSettingsFilePath.toString() + MapSettings.fileExtensionWithDot);
			}

			final MapSettings settings = getSettingsFromGUI();
			try
			{
				settings.writeToFile(openSettingsFilePath.toString());
				getConsoleOutputTextArea().append("Settings saved to " + openSettingsFilePath.toString() + "\n");
				updateLastSettingsLoadedOrSaved(settings);
			}
			catch (IOException e)
			{
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to save settings.", JOptionPane.ERROR_MESSAGE);
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
		setTitle(title);
	}
	
	private void loadSettingsIntoGUI(MapSettings settings)
	{
		updateLastSettingsLoadedOrSaved(settings);
		edits = settings.edits;
		themePanel.loadSettingsIntoGUI(settings);
		toolsPanel.loadSettingsIntoGUI(settings);
		
		updateFrameTitle();
		
		// TODO Make the undoer settings based instead of edits based.
		undoer = new Undoer(settings, this, toolsPanel);
		undoer.updateUndoRedoEnabled();
	}
	
	private void updateLastSettingsLoadedOrSaved(MapSettings settings)
	{
		lastSettingsLoadedOrSaved = settings.deepCopy();
	}
	
	private MapSettings getSettingsFromGUI()
	{
		MapSettings settings = new MapSettings();
		if (edits != null)
		{
			settings.edits = edits.deepCopy();
		}
		else
		{
			edits = new MapEdits();
		}
		
		settings.resolution = resolutionForImageExport;
		
		// TODO get these settings from wherever I end up storing them behind the scenes
		settings.worldSize = lastSettingsLoadedOrSaved.worldSize;
		settings.randomSeed = lastSettingsLoadedOrSaved.randomSeed;
		settings.edgeLandToWaterProbability = lastSettingsLoadedOrSaved.edgeLandToWaterProbability;
		settings.centerLandToWaterProbability = lastSettingsLoadedOrSaved.centerLandToWaterProbability;
		settings.generatedWidth = lastSettingsLoadedOrSaved.generatedWidth;
		settings.generatedHeight = lastSettingsLoadedOrSaved.generatedHeight;

		
		themePanel.getSettingsFromGUI(settings);
		toolsPanel.getSettingsFromGUI(settings);

		if (lastSettingsLoadedOrSaved != null)
		{
			// Copy over any settings which do not have a UI element.
			settings.pointPrecision = lastSettingsLoadedOrSaved.pointPrecision;
			settings.textRandomSeed = lastSettingsLoadedOrSaved.textRandomSeed;
			settings.regionsRandomSeed = lastSettingsLoadedOrSaved.regionsRandomSeed;
			settings.randomSeed = lastSettingsLoadedOrSaved.randomSeed;
		}
		
		settings.books = toolsPanel.getSelectedBooks();
		
		return settings;
	}
	
	public void handleColorRegionsChanged(boolean colorRegions)
	{
		toolsPanel.handleColorRegionsChanged(colorRegions);
	}
	
	public boolean areRegionColorsVisible()
	{
		return themePanel.areRegionColorsVisible();
	}
	
	public Color getLandColor()
	{
		return themePanel.getLandColor();
	}
	
	public void handleDrawTextChanged(boolean drawText)
	{
		toolsPanel.handleDrawTextChanged(drawText);
	}
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		try
		{
			//UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			UIManager.setLookAndFeel( new FlatDarkLaf() );
		}
		catch (Exception e)
		{
			System.out.println("Error while setting look and feel: " + e.getMessage());
			e.printStackTrace();
		}
				
		final String subFolderAddedByWindowsInstaller = "app";
		if (!Files.isDirectory(Paths.get(AssetsPath.get()))
				&& Files.isDirectory(Paths.get(subFolderAddedByWindowsInstaller, AssetsPath.get())))
		{
			AssetsPath.set(Paths.get(subFolderAddedByWindowsInstaller, AssetsPath.get()).toString());
		}

		String fileToOpen = args.length > 0 ? args[0] : "";
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					MainWindow mainWindow = new MainWindow(fileToOpen);
					mainWindow.setVisible(true);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		});
	}
	
	public static JScrollPane createBooksScrollPane(JPanel booksPanel, Set<String> selectedBooks)
	{
		JScrollPane booksScrollPane = new JScrollPane();

		booksPanel.removeAll();
		booksPanel.setLayout(new BoxLayout(booksPanel, BoxLayout.Y_AXIS));
		booksScrollPane.setViewportView(booksPanel);
		
		createBooksCheckboxes(booksPanel, selectedBooks);
		
		return booksScrollPane;
	}
	
	public static void createBooksCheckboxes(JPanel booksPanel, Set<String> selectedBooks)
	{
		for (String book : SettingsGenerator.getAllBooks())
		{
			final JCheckBox checkBox = new JCheckBox(book);
			booksPanel.add(checkBox);
			checkBox.setSelected(selectedBooks.contains(book));
		}
	}
	
	public static Set<String> getSelectedBooks(JPanel booksPanel)
	{
		Set<String> books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				if (checkBox.isSelected())
				{
					books.add(checkBox.getText());
				}
			}
		}
		
		return books;
	}

	private BufferedImage createPlaceholderImage(String[] message)
	{
		if (message.length == 0)
		{
			return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		}
		
		final int scale = 2;
		Font font = MapSettings.parseFont("URW Chancery L\t0\t" + 30 * scale);
		int fontHeight = TextDrawer.getFontHeight(font);
		
		Dimension textBounds = TextDrawer.getTextBounds(message[0], font);
		for (int i : new Range(1, message.length))
		{
			Dimension lineBounds = TextDrawer.getTextBounds(message[i], font);
			textBounds = new Dimension(Math.max(textBounds.width, lineBounds.width), 
					textBounds.height + lineBounds.height);
		}
		
		BufferedImage placeHolder = new BufferedImage((textBounds.width + 15), (textBounds.height + 20), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = placeHolder.createGraphics();
		g.setFont(font);
		g.setColor(new Color(168, 168, 168));
		
		for (int i : new Range(message.length))
		{
			g.drawString(message[i], 14, fontHeight + (i * fontHeight));
		}
		
		return ImageHelper.scaleByWidth(placeHolder, placeHolder.getWidth() / scale, Method.QUALITY);
	}
}
