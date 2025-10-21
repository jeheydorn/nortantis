package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
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
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
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
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.imgscalr.Scalr.Method;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import nortantis.CancelledException;
import nortantis.DebugFlags;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.editor.CenterEdit;
import nortantis.editor.DisplayQuality;
import nortantis.editor.EdgeEdit;
import nortantis.editor.ExportAction;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ILoggerTarget;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.OSHelper;

@SuppressWarnings("serial")
public class MainWindow extends JFrame implements ILoggerTarget
{
	private JTextArea txtConsoleOutput;
	private Path openSettingsFilePath;
	private boolean forceSaveAs;
	MapSettings lastSettingsLoadedOrSaved;
	boolean hasDrawnCurrentMapAtLeastOnce;
	static final String frameTitleBase = "Nortantis";
	public MapEdits edits;
	public JMenuItem clearEditsMenuItem;

	JScrollPane mapEditingScrollPane;
	// Controls how large 100% zoom is, in pixels.
	final double oneHundredPercentMapWidth = 4096;
	public MapEditingPanel mapEditingPanel;
	JMenuItem undoButton;
	JMenuItem redoButton;
	private JMenuItem clearEntireMapButton;
	public Undoer undoer;
	double zoom;
	double displayQualityScale;
	ThemePanel themePanel;
	ToolsPanel toolsPanel;
	MapUpdater updater;
	private JCheckBoxMenuItem highlightLakesButton;
	private JCheckBoxMenuItem highlightRiversButton;
	private JScrollPane consoleOutputPane;
	double exportResolution;
	ExportAction defaultMapExportAction;
	ExportAction defaultHeightmapExportAction;
	String imageExportPath;
	double heightmapExportResolution;
	String heightmapExportPath;
	private JMenuItem saveMenuItem;
	private JMenuItem saveAsMenItem;
	private JMenuItem exportMapAsImageMenuItem;
	private JMenuItem exportHeightmapMenuItem;
	private JMenu editMenu;
	private JMenu viewMenu;
	private JMenu recentSettingsMenuItem;
	java.awt.Point mouseLocationForMiddleButtonDrag;
	private JMenu helpMenu;
	private JMenuItem refreshMenuItem;
	private JMenuItem customImagesMenuItem;
	private JMenu toolsMenu;
	private JMenuItem nameGeneratorMenuItem;
	protected String customImagesPath;
	private JMenu fileMenu;
	private JMenuItem newMapWithSameThemeMenuItem;
	private JMenuItem searchTextMenuItem;
	private TextSearchDialog textSearchDialog;
	private JMenu highlightIconsInArtPackMenu;
	private List<JCheckBoxMenuItem> artPacksToHighlight;

	public MainWindow(String fileToOpen) throws Exception
	{
		super(frameTitleBase);

		Logger.setLoggerTarget(this);

		try
		{
			createGUI();
		}
		catch (Exception ex)
		{
			try
			{
				JOptionPane.showMessageDialog(null,
						"Unnable to create GUI because of error: " + ex.getMessage() + "\nVersion: " + MapSettings.currentVersion
								+ "\nOS Name: " + System.getProperty("os.name") + "\nStack trace: " + ExceptionUtils.getStackTrace(ex),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
			catch (Exception inner)
			{
				Logger.printError("Error while trying to log an error at startup: " + inner.getMessage(), inner);
			}
			throw ex;
		}

		boolean isMapOpen = false;
		try
		{
			if (fileToOpen != null && !fileToOpen.isEmpty() && fileToOpen.endsWith(MapSettings.fileExtensionWithDot)
					&& new File(fileToOpen).exists())
			{
				openMap(new File(fileToOpen).getAbsolutePath());
				isMapOpen = true;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Logger.printError("Error while opening map passed in on the command line:", e);
		}

		if (!isMapOpen)
		{
			setPlaceholderImage(new String[] { "Welcome to Nortantis. To create or open a map,", "use the File menu." });
			enableOrDisableFieldsThatRequireMap(false, null);
		}

		launchNewVersionCheck();
	}

	private void launchNewVersionCheck()
	{
		LocalDateTime lastVersionCheckTime = UserPreferences.getInstance().lastVersionCheckTime;
		LocalDateTime currentTime = LocalDateTime.now();
		if (lastVersionCheckTime == null || ChronoUnit.HOURS.between(lastVersionCheckTime, currentTime) >= 24)
		{
			PlatformFactory.getInstance().doInBackgroundThread(new BackgroundTask<String>()
			{

				@Override
				public String doInBackground() throws IOException, CancelledException
				{
					return getLatestVersion();
				}

				@Override
				public void done(String latestVersion)
				{
					try
					{
						if (StringUtils.isEmpty(latestVersion))
						{
							return;
						}

						String lastCheckedVersion = UserPreferences.getInstance().lastVersionFromCheck;

						if (MapSettings.isVersionGreatherThanCurrent(latestVersion) && (StringUtils.isEmpty(lastCheckedVersion)
								|| MapSettings.isVersionGreaterThan(latestVersion, lastCheckedVersion)))
						{
							UserPreferences.getInstance().lastVersionFromCheck = latestVersion;
							UserPreferences.getInstance().lastVersionCheckTime = currentTime;

							String message = "Version " + latestVersion + " is now available. You can download it at";
							String url = "https://jandjheydorn.com/nortantis";

							JPanel messagePanel = new JPanel();
							messagePanel.setLayout(new FlowLayout());

							JLabel messageLabel = new JLabel(message);
							messagePanel.add(messageLabel);

							JLabel hyperlink = SwingHelper.createHyperlink(url, url);
							messagePanel.add(hyperlink);

							JOptionPane.showMessageDialog(MainWindow.this, messagePanel, "Update Available",
									JOptionPane.INFORMATION_MESSAGE);
						}
					}
					catch (Exception e)
					{
						Logger.printError("Unexpected error while checking if version " + latestVersion + " is a new release.", e);
					}
				}
			});
		}
	}

	private String getLatestVersion()
	{
		try
		{
			// URL of the JSON file with the latest released version.
			String urlString = "https://jandjheydorn.com/s/current-version.json";
			URL url = new URI(urlString).toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");

			// Set timeouts
			connection.setConnectTimeout(30000);
			connection.setReadTimeout(30000);

			// Parse the JSON response
			JSONParser parser = new JSONParser();
			try (InputStreamReader reader = new InputStreamReader(connection.getInputStream()))
			{
				JSONObject jsonObject = (JSONObject) parser.parse(reader);

				String version = (String) jsonObject.get("version");
				return version;
			}

		}
		catch (Exception e)
		{
			// I intentionally do not log this error to Logger because doing so causes the I causes the theme panel to we created extra
			// wide when a map is being immediately opened, and I don't want network errors when checking the latest version to cause any
			// noticeable issue.
			e.printStackTrace();
			return null;
		}
	}

	void enableOrDisableFieldsThatRequireMap(boolean enable, MapSettings settings)
	{
		newMapWithSameThemeMenuItem.setEnabled(enable);
		saveMenuItem.setEnabled(enable);
		saveAsMenItem.setEnabled(enable);
		exportMapAsImageMenuItem.setEnabled(enable);
		exportHeightmapMenuItem.setEnabled(enable);

		if (!enable || undoer == null)
		{
			undoButton.setEnabled(false);
			redoButton.setEnabled(false);
		}
		else
		{
			undoer.updateUndoRedoEnabled();
		}
		clearEntireMapButton.setEnabled(enable);
		customImagesMenuItem.setEnabled(enable);

		nameGeneratorMenuItem.setEnabled(enable);
		searchTextMenuItem.setEnabled(enable);

		highlightLakesButton.setEnabled(enable);
		highlightRiversButton.setEnabled(enable);

		refreshMenuItem.setEnabled(enable);

		themePanel.enableOrDisableEverything(enable);
		toolsPanel.enableOrDisableEverything(enable, settings);
	}

	private void createGUI()
	{
		getContentPane().setPreferredSize(new Dimension(1400, 780));
		getContentPane().setLayout(new BorderLayout());

		setIconImage(AwtFactory.unwrap(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal/taskbar icon.png").toString())));
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
						UserPreferences.getInstance().toolsPanelWidth = toolsPanel.getWidth();
						UserPreferences.getInstance().themePanelWidth = themePanel.getWidth();
						UserPreferences.getInstance().save();
						dispose();
						System.exit(0);
					}
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					Logger.printError("Error while closing:", ex);
				}
			}

			@Override
			public void windowActivated(WindowEvent e)
			{
			}
		});

		createMenuBar();

		undoer = new Undoer(this);

		themePanel = new ThemePanel(this);
		createMapEditingPanel();
		createMapUpdater();
		toolsPanel = new ToolsPanel(this, mapEditingPanel, updater);
		int toolsPanelWidth = UserPreferences.getInstance().toolsPanelWidth > SwingHelper.sidePanelMinimumWidth
				? UserPreferences.getInstance().toolsPanelWidth
				: SwingHelper.sidePanelPreferredWidth;
		toolsPanel.setPreferredSize(new Dimension(toolsPanelWidth, toolsPanel.getPreferredSize().height));
		toolsPanel.setMinimumSize(new Dimension(SwingHelper.sidePanelMinimumWidth, toolsPanel.getMinimumSize().height));

		createConsoleOutput();

		JSplitPane splitPane0 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, themePanel, consoleOutputPane);
		splitPane0.setDividerLocation(9999999);
		splitPane0.setResizeWeight(1.0);

		JSplitPane splitPane1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane0, mapEditingScrollPane);
		splitPane1.setOneTouchExpandable(true);
		JSplitPane splitPane2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane1, toolsPanel);
		splitPane2.setResizeWeight(1.0);
		splitPane2.setOneTouchExpandable(true);

		getContentPane().add(splitPane2, BorderLayout.CENTER);

		pack();

	}

	private void launchNewSettingsDialog(MapSettings settingsToKeepThemeFrom)
	{
		NewSettingsDialog dialog = new NewSettingsDialog(this, settingsToKeepThemeFrom);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void createConsoleOutput()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		txtConsoleOutput = new JTextArea();
		txtConsoleOutput.setEditable(false);
		panel.add(txtConsoleOutput);

		consoleOutputPane = new JScrollPane(panel);
		consoleOutputPane.setMinimumSize(new Dimension(0, 0));
		consoleOutputPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
	}

	private void createMapEditingPanel()
	{
		mapEditingPanel = new MapEditingPanel(null);

		mapEditingPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseClickOnMap(e));
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e))
				{
					mouseLocationForMiddleButtonDrag = e.getPoint();
				}
				else if (SwingUtilities.isLeftMouseButton(e))
				{
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMousePressedOnMap(e));
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseReleasedOnMap(e));
				}
			}

		});

		mapEditingPanel.addMouseMotionListener(new MouseMotionListener()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseMovedOnMap(e));
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e))
				{
					if (mouseLocationForMiddleButtonDrag != null)
					{
						int deltaX = mouseLocationForMiddleButtonDrag.x - e.getX();
						int deltaY = mouseLocationForMiddleButtonDrag.y - e.getY();
						mapEditingScrollPane.getVerticalScrollBar()
								.setValue(mapEditingScrollPane.getVerticalScrollBar().getValue() + deltaY);
						mapEditingScrollPane.getHorizontalScrollBar()
								.setValue(mapEditingScrollPane.getHorizontalScrollBar().getValue() + deltaX);
					}
				}
				else if (SwingUtilities.isLeftMouseButton(e))
				{
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseDraggedOnMap(e));
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
				updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseExitedMap(e));
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

		mapEditingPanel.addMouseWheelListener(new MouseWheelListener()
		{
			@Override
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				MainWindow.this.handleMouseWheelChangingZoom(e);
			}

		});

		mapEditingScrollPane = new JScrollPane(mapEditingPanel);
		mapEditingScrollPane.setMinimumSize(new Dimension(500, themePanel.getMinimumSize().height));

		mapEditingScrollPane.addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent componentEvent)
			{
				updateZoomOptionsBasedOnWindowSize();
				if (ToolsPanel.fitToWindowZoomLevel.equals(toolsPanel.getZoomString()))
				{
					updateDisplayedMapFromGeneratedMap(true, null, true);
				}
			}
		});

		// Speed up the scroll speed.
		mapEditingScrollPane.getVerticalScrollBar().setUnitIncrement(16);
	}

	private void createMapUpdater()
	{
		updater = new MapUpdater(true)
		{

			@Override
			protected void onBeginDraw()
			{
				showAsDrawing(true);
			}

			@Override
			public MapSettings getSettingsFromGUI()
			{
				MapSettings settings = MainWindow.this.getSettingsFromGUI(false);
				settings.resolution = displayQualityScale;
				return settings;
			}

			@Override
			protected void onFinishedDrawing(Image map, boolean anotherDrawIsQueued, int borderPaddingAsDrawn,
					Rectangle incrementalChangeArea, List<String> warningMessages)
			{
				mapEditingPanel.mapFromMapCreator = AwtFactory.unwrap(map);
				mapEditingPanel.setBorderPadding(borderPaddingAsDrawn);
				mapEditingPanel.setGraph(mapParts.graph);
				mapEditingPanel.setFreeIcons(edits == null ? null : edits.freeIcons);
				mapEditingPanel.setIconDrawer(mapParts.iconDrawer);

				if (!undoer.isInitialized())
				{
					// This has to be done after the map is drawn rather
					// than when the editor frame is first created because
					// the first time the map is drawn is when the edits are
					// created.
					undoer.initialize(MainWindow.this.getSettingsFromGUI(true));
					enableOrDisableFieldsThatRequireMap(true, MainWindow.this.getSettingsFromGUI(false));
				}

				if (!hasDrawnCurrentMapAtLeastOnce)
				{
					hasDrawnCurrentMapAtLeastOnce = true;
					// Drawing for the first time can create or modify the
					// edits, so update them in lastSettingsLoadedOrSaved.
					lastSettingsLoadedOrSaved.edits = edits.deepCopy();
				}

				updateDisplayedMapFromGeneratedMap(false, incrementalChangeArea, false);

				if (!anotherDrawIsQueued)
				{
					showAsDrawing(false);
				}

				mapEditingPanel.setHighlightRivers(highlightRiversButton.isSelected());
				mapEditingPanel.setHighlightLakes(highlightLakesButton.isSelected());

				// Tell the scroll pane to update itself.
				mapEditingPanel.revalidate();
				mapEditingPanel.repaint();

				if (warningMessages != null && warningMessages.size() > 0)
				{
					JTextArea textArea = new JTextArea(String.join("\n\n", warningMessages));
					textArea.setEditable(false);
					textArea.setLineWrap(true);
					textArea.setWrapStyleWord(true);
					textArea.setCaretPosition(0);
					textArea.setSelectionStart(0);
					textArea.setSelectionEnd(0);
					textArea.setBorder(BorderFactory.createEmptyBorder());

					JScrollPane scrollPane = new JScrollPane(textArea);
					scrollPane.setPreferredSize(new Dimension(500, 150));

					JOptionPane.showMessageDialog(MainWindow.this, scrollPane, "Map Drew With Warnings", JOptionPane.WARNING_MESSAGE);

				}

				boolean isChange = settingsHaveUnsavedChanges();
				updateFrameTitle(isChange, !isChange);
			}

			@Override
			protected void onFailedToDraw()
			{
				showAsDrawing(false);
				mapEditingPanel.clearAllSelectionsAndHighlights();
				setPlaceholderImage(new String[] { "Map failed to draw due to an error.",
						"To retry, use " + fileMenu.getText() + " -> " + refreshMenuItem.getText() + "." });

				// In theory, enabling fields now could lead to the undoer not
				// working quite right since edits might not have been created.
				// But leaving fields disabled makes the user unable to fix the
				// error.
				enableOrDisableFieldsThatRequireMap(true, MainWindow.this.getSettingsFromGUI(false));
			}

			@Override
			protected MapEdits getEdits()
			{
				return edits;
			}

			@Override
			protected Image getCurrentMapForIncrementalUpdate()
			{
				return AwtFactory.wrap(mapEditingPanel.mapFromMapCreator);
			}

			@Override
			protected void onDrawSubmitted(UpdateType updateType)
			{
				// Incremental changes are handled in onFinishedDrawing to make
				// the drawing more responsive and to pick up changes caused by
				// the drawing code, such as when icons are removed because they
				// couldn't draw in the space provided.
				if (updateType != UpdateType.Incremental)
				{
					boolean isChange = settingsHaveUnsavedChanges();
					updateFrameTitle(isChange, !isChange);
				}
			}

		};
	}

	private void createMenuBar()
	{
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		fileMenu = new JMenu("File");
		menuBar.add(fileMenu);

		final JMenuItem newRandomMapMenuItem = new JMenuItem("New Random Map");
		newRandomMapMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
		fileMenu.add(newRandomMapMenuItem);
		newRandomMapMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				boolean cancelPressed = checkForUnsavedChanges();
				if (!cancelPressed)
				{
					launchNewSettingsDialog(null);
				}
			}
		});

		newMapWithSameThemeMenuItem = new JMenuItem("New Map With Same Theme");
		fileMenu.add(newMapWithSameThemeMenuItem);
		newMapWithSameThemeMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				boolean cancelPressed = checkForUnsavedChanges();
				if (!cancelPressed)
				{
					MapSettings settingsToKeepThemeFrom = getSettingsFromGUI(false);
					settingsToKeepThemeFrom.edits = new MapEdits();

					if (settingsToKeepThemeFrom.drawRegionColors
							&& !UserPreferences.getInstance().hideNewMapWithSameThemeRegionColorsMessage)
					{
						UserPreferences.getInstance().hideNewMapWithSameThemeRegionColorsMessage = SwingHelper.showDismissibleMessage(
								"Region Colors",
								"New region colors will be generated based on the " + LandWaterTool.colorGeneratorSettingsName + " in the "
										+ LandWaterTool.toolbarName + " tool"
										+ ", not the actual colors used in your current map. This means that if you chose your region colors"
										+ " by hand rather than generating them, the region colors in your new map may look substantially different"
										+ " than those in your current map.",
								new Dimension(400, 133), MainWindow.this);
					}

					launchNewSettingsDialog(settingsToKeepThemeFrom);
				}
			}
		});

		final JMenuItem loadSettingsMenuItem = new JMenuItem("Open");
		loadSettingsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
		fileMenu.add(loadSettingsMenuItem);
		loadSettingsMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				boolean cancelPressed = checkForUnsavedChanges();
				if (cancelPressed)
					return;

				Path curPath = openSettingsFilePath == null ? FileSystemView.getFileSystemView().getDefaultDirectory().toPath()
						: openSettingsFilePath;
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
				int status = fileChooser.showOpenDialog(MainWindow.this);
				if (status == JFileChooser.APPROVE_OPTION)
				{
					openMap(fileChooser.getSelectedFile().getAbsolutePath());

					if (openSettingsFilePath != null && MapSettings.isOldPropertiesFile(openSettingsFilePath.toString()))
					{
						JOptionPane.showMessageDialog(MainWindow.this, FilenameUtils.getName(openSettingsFilePath.toString())
								+ " is an older format '.properties' file. When you save, it will be converted to the newer format, a '"
								+ MapSettings.fileExtensionWithDot + "' file.", "File Converted", JOptionPane.INFORMATION_MESSAGE);
						openSettingsFilePath = Paths.get(FilenameUtils.getFullPath(openSettingsFilePath.toString()),
								FilenameUtils.getBaseName(openSettingsFilePath.toString()) + MapSettings.fileExtensionWithDot);
						forceSaveAs = true;
					}

				}

			}
		});

		recentSettingsMenuItem = new JMenu("Open Recent");
		fileMenu.add(recentSettingsMenuItem);
		createOrUpdateRecentMapMenuButtons();

		saveMenuItem = new JMenuItem("Save");
		saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
		fileMenu.add(saveMenuItem);
		saveMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				saveSettings(MainWindow.this);
			}
		});

		saveAsMenItem = new JMenuItem("Save As...");
		fileMenu.add(saveAsMenItem);
		saveAsMenItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				saveSettingsAs(MainWindow.this);
			}
		});

		exportMapAsImageMenuItem = new JMenuItem("Export as Image");
		fileMenu.add(exportMapAsImageMenuItem);
		exportMapAsImageMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				handleExportAsImagePressed();
			}
		});

		exportHeightmapMenuItem = new JMenuItem("Export Heightmap");
		fileMenu.add(exportHeightmapMenuItem);
		exportHeightmapMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				handleExportHeightmapPressed();
			}
		});

		refreshMenuItem = new JMenuItem("Refresh Images and Redraw");
		refreshMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
		fileMenu.add(refreshMenuItem);
		refreshMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleImagesRefresh();
				updater.createAndShowMapFull(() -> mapEditingPanel.clearAllSelectionsAndHighlights());
			}
		});

		editMenu = new JMenu("Edit");
		menuBar.add(editMenu);

		undoButton = new JMenuItem("Undo");
		undoButton.setEnabled(false);
		editMenu.add(undoButton);
		undoButton.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));
		undoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (toolsPanel.currentTool != null)
				{
					updater.dowWhenMapIsNotDrawing(() ->
					{
						undoer.undo();
					});
				}
			}
		});

		redoButton = new JMenuItem("Redo");
		redoButton.setEnabled(false);
		editMenu.add(redoButton);
		redoButton.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		redoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (toolsPanel.currentTool != null)
				{
					updater.dowWhenMapIsNotDrawing(() ->
					{
						undoer.redo();
					});
				}
			}
		});

		clearEntireMapButton = new JMenuItem("Clear Entire Map");
		editMenu.add(clearEntireMapButton);
		clearEntireMapButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				clearEntireMap();
			}
		});
		clearEntireMapButton.setEnabled(false);

		customImagesMenuItem = new JMenuItem("Custom Images Folder");
		editMenu.add(customImagesMenuItem);
		customImagesMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleCustomImagesPressed();
			}
		});

		viewMenu = new JMenu("View");
		menuBar.add(viewMenu);

		highlightLakesButton = new JCheckBoxMenuItem("Highlight Lakes");
		highlightLakesButton.setToolTipText("Highlight lakes to make them easier to see.");
		highlightLakesButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.setHighlightLakes(highlightLakesButton.isSelected());
				mapEditingPanel.repaint();
			}
		});
		viewMenu.add(highlightLakesButton);

		highlightRiversButton = new JCheckBoxMenuItem("Highlight Rivers");
		highlightRiversButton.setToolTipText("Highlight rivers to make them easier to see.");
		highlightRiversButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.setHighlightRivers(highlightRiversButton.isSelected());
				mapEditingPanel.repaint();
			}
		});
		viewMenu.add(highlightRiversButton);

		{
			// Create the theme menu
			JMenu themeMenu = new JMenu("Theme");

			JRadioButtonMenuItem darkTheme = new JRadioButtonMenuItem("Dark");
			JRadioButtonMenuItem lightTheme = new JRadioButtonMenuItem("Light");
			JRadioButtonMenuItem systemTheme = new JRadioButtonMenuItem("System");

			ButtonGroup themeGroup = new ButtonGroup();
			themeGroup.add(darkTheme);
			themeGroup.add(lightTheme);
			themeGroup.add(systemTheme);

			LookAndFeel theme = UserPreferences.getInstance().lookAndFeel;
			if (theme == LookAndFeel.Dark)
			{
				darkTheme.setSelected(true);
			}
			else if (theme == LookAndFeel.Light)
			{
				lightTheme.setSelected(true);
			}
			else
			{
				systemTheme.setSelected(true);
			}

			ActionListener listener = new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					LookAndFeel theme;
					if (darkTheme.isSelected())
					{
						theme = LookAndFeel.Dark;
					}
					else if (lightTheme.isSelected())
					{
						theme = LookAndFeel.Light;
					}
					else
					{
						theme = LookAndFeel.System;
					}
					handleLookAndFeelChange(theme);
				}
			};

			darkTheme.addActionListener(listener);

			lightTheme.addActionListener(listener);

			systemTheme.addActionListener(listener);

			// Add the radio button menu items to the theme menu
			themeMenu.add(darkTheme);
			themeMenu.add(lightTheme);
			themeMenu.add(systemTheme);

			// Add the theme menu to the view menu
			viewMenu.add(themeMenu);
		}

		toolsMenu = new JMenu("Tools");
		menuBar.add(toolsMenu);

		nameGeneratorMenuItem = new JMenuItem("Name Generator");
		toolsMenu.add(nameGeneratorMenuItem);
		nameGeneratorMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleNameGeneratorPressed();
			}
		});

		searchTextMenuItem = new JMenuItem("Search Text");
		searchTextMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK));
		toolsMenu.add(searchTextMenuItem);
		searchTextMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleSearchTextPressed();
			}
		});

		JMenu artPacksMenu = new JMenu("Art Packs");
		toolsMenu.add(artPacksMenu);

		JMenuItem addArtPackItem = new JMenuItem("Add Art Pack");
		artPacksMenu.add(addArtPackItem);
		addArtPackItem.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleAddArtPack();
			}
		});

		JMenuItem openArtPacksFolderItem = new JMenuItem("Open Art Packs Folder");
		artPacksMenu.add(openArtPacksFolderItem);
		openArtPacksFolderItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleOpenArtPacksFolder();
			}
		});

		artPacksToHighlight = new ArrayList<>();
		highlightIconsInArtPackMenu = new JMenu("Highlight Icons in Art Packs");
		artPacksMenu.add(highlightIconsInArtPackMenu);
		updateArtPackHighlightOptions();

		helpMenu = new JMenu("Help");
		menuBar.add(helpMenu);

		JMenuItem keyboardShortcutsItem = new JMenuItem("Keyboard Shortcuts");
		helpMenu.add(keyboardShortcutsItem);
		keyboardShortcutsItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				JOptionPane.showMessageDialog(MainWindow.this, "<html>Keyboard shortcuts for navigating the map:" + "<ul>"
						+ "<li>Zoom: Mouse wheel</li>" + "<li>Pan: Hold mouse middle button, or Shift and mouse left click, then drag</li>"
						+ "</ul>"
						+ "<br>Each editor tool has a keyboard shortcut for switching to it. Hover over the tool's icon to see the shortcut."
						+ "</html>", "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
			}
		});

		JMenuItem aboutNortantisItem = new JMenuItem("About Nortantis");
		helpMenu.add(aboutNortantisItem);
		aboutNortantisItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				showAboutNortantisDialog();
			}
		});
	}

	private void handleLookAndFeelChange(LookAndFeel lookAndFeel)
	{
		setLookAndFeel(lookAndFeel);
		UserPreferences.getInstance().lookAndFeel = lookAndFeel;
		SwingUtilities.updateComponentTreeUI(this);
		toolsPanel.handleLookAndFeelChange(lookAndFeel);
		if (textSearchDialog != null)
		{
			textSearchDialog.handleLookAndFeelChange();
		}
	}

	private static void setLookAndFeel(LookAndFeel lookAndFeel)
	{
		try
		{
			if (lookAndFeel == LookAndFeel.Dark)
			{
				UIManager.setLookAndFeel(new FlatDarkLaf());
			}
			else if (lookAndFeel == LookAndFeel.Light)
			{
				UIManager.setLookAndFeel(new FlatLightLaf());
			}
			else
			{
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}
		}
		catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e)
		{
			String message = "Error while setting look and feel: " + e.getMessage();
			System.out.println(message);
			e.printStackTrace();
			Logger.printError(message, e);
			JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
	}

	private void updateArtPackHighlightOptions()
	{
		Set<String> highlightedArtPacks = getSelectedArtPacksToHighlight();
		highlightIconsInArtPackMenu.removeAll();
		artPacksToHighlight.clear();
		List<String> artPacks = Assets.listArtPacks(!StringUtils.isEmpty(customImagesPath));
		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				updateArtPackHighlights();
			}
		};
		for (String artPack : artPacks)
		{
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(artPack);
			highlightIconsInArtPackMenu.add(item);
			artPacksToHighlight.add(item);
			if (highlightedArtPacks.contains(artPack))
			{
				item.setSelected(true);
			}
			item.addActionListener(listener);
		}

	}

	private void updateArtPackHighlights()
	{
		if (mapEditingPanel != null)
		{
			mapEditingPanel.setArtPacksToHighlight(getSelectedArtPacksToHighlight());
			mapEditingPanel.repaint();
		}
	}

	private Set<String> getSelectedArtPacksToHighlight()
	{
		Set<String> result = new TreeSet<>();
		for (JCheckBoxMenuItem item : artPacksToHighlight)
		{
			if (item.isSelected())
			{
				result.add(item.getText());
			}
		}
		return result;
	}

	private void handleOpenArtPacksFolder()
	{
		Path artPacksPath = Assets.getArtPacksFolder();

		if (!artPacksPath.toFile().exists())
		{
			try
			{
				Files.createDirectories(artPacksPath);
			}
			catch (IOException ex)
			{
				String message = "An error occurred while creating the folder: " + ex.getMessage();
				Logger.printError(message, ex);
				JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		OSHelper.openFileExplorerTo(artPacksPath.toFile());
	}

	private void handleAddArtPack()
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setDialogTitle("Select Art Pack (ZIP File)");
		fileChooser.setFileFilter(new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "Art Pack (ZIP File)";
			}

			@Override
			public boolean accept(File f)
			{
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".zip");
			}
		});

		int result = fileChooser.showOpenDialog(MainWindow.this);
		if (result == JFileChooser.APPROVE_OPTION)
		{
			File selectedFile = fileChooser.getSelectedFile();

			// Check for forbidden names. I'm adding 'all' to the list in case I someday decide I want an 'all' option for showing
			// art packs in IconsTool.
			List<String> subfolderNames;
			try
			{
				subfolderNames = FileHelper.getTopLevelSubFolders(selectedFile.toPath());
			}
			catch (IOException ex)
			{
				String message = "Error while reading zip file '" + selectedFile + "': " + ex.getMessage();
				Logger.printError(message, ex);
				JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			if (subfolderNames.isEmpty())
			{
				JOptionPane.showMessageDialog(this,
						"Invalid art pack. It's empty. It should have exactly one top-level folder, the name of which is the name of the art pack.",
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			if (subfolderNames.size() > 1)
			{
				JOptionPane.showMessageDialog(this,
						"Invalid art pack. It should have exactly one top-level folder, the name of which will be the name of the art pack. It has "
								+ subfolderNames.size() + " top-level folders.",
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			String artPackName = subfolderNames.get(0);

			if (Assets.reservedArtPacks.contains(artPackName.toLowerCase()))
			{
				JOptionPane.showMessageDialog(this, "The art pack name '" + artPackName + "' is not allowed.", "Invalid Art Pack Name",
						JOptionPane.ERROR_MESSAGE);
				return;
			}

			File artPackFolderAsFile = Assets.getArtPackPath(artPackName, null).toFile();
			if (artPackFolderAsFile.exists() && artPackFolderAsFile.isDirectory())
			{
				// Show the dialog
				int response = JOptionPane.showOptionDialog(this,
						"The art pack '" + artPackName + "' already exists. Do you wish to overwrite it?", "Overwrite Art Pack",
						JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, new Object[] { "Overwrite", "Cancel" }, "Cancel");

				if (response == 0)
				{
					// Overwrite
					try
					{
						FileUtils.deleteDirectory(artPackFolderAsFile);
					}
					catch (IOException e)
					{
						Logger.printError("Error while deleting folder '" + artPackFolderAsFile + "': " + e.getMessage(), e);
						return;
					}
				}
				else
				{
					// Cancel
					return;
				}
			}

			// Uncompress the zip file
			Path artPacksFolder = Assets.getArtPacksFolder();
			try
			{
				FileHelper.unzip(selectedFile, artPacksFolder, true);
				JOptionPane.showMessageDialog(MainWindow.this, "Art pack added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException ex)
			{
				JOptionPane.showMessageDialog(MainWindow.this, "Error uncompressing the zip file: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
			handleImagesRefresh();
		}
	}

	void handleImagesRefresh()
	{
		updater.setEnabled(false);
		undoer.setEnabled(false);
		ImageCache.clear();
		MapSettings settings = getSettingsFromGUI(false);
		themePanel.handleImagesRefresh(settings);
		// Tell Icons tool to refresh image previews
		toolsPanel.handleImagesRefresh(settings);
		updateArtPackHighlightOptions();
		updateArtPackHighlights();
		undoer.setEnabled(true);
		updater.setEnabled(true);
	}

	private void showAboutNortantisDialog()
	{
		AboutDialog dialog = new AboutDialog(this);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void createOrUpdateRecentMapMenuButtons()
	{
		recentSettingsMenuItem.removeAll();
		boolean hasRecents = false;

		for (String filePath : UserPreferences.getInstance().getRecentMapFilePaths())
		{
			String fileName = FilenameUtils.getName(filePath);
			JMenuItem item = new JMenuItem(fileName + "  (" + Paths.get(FilenameUtils.getPath(filePath)).toString() + ")");
			recentSettingsMenuItem.add(item);
			hasRecents = true;
			item.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					boolean cancelPressed = checkForUnsavedChanges();
					if (cancelPressed)
					{
						return;
					}

					openMap(filePath);
				}
			});
		}

		recentSettingsMenuItem.setEnabled(hasRecents);
	}

	private void openMap(String absolutePath)
	{
		if (!(new File(absolutePath).exists()))
		{
			JOptionPane.showMessageDialog(null, "The map '" + absolutePath + "' cannot be opened because it does not exist.",
					"Unable to Open Map", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try
		{
			openSettingsFilePath = Paths.get(absolutePath);
			if (!MapSettings.isOldPropertiesFile(absolutePath))
			{
				UserPreferences.getInstance().addRecentMapFilePath(absolutePath);
				createOrUpdateRecentMapMenuButtons();
			}
			MapSettings settings = new MapSettings(openSettingsFilePath.toString());
			convertCustomImagesFolderIfNeeded(settings);

			updater.cancel();
			updater.dowWhenMapIsNotDrawing(() ->
			{
				loadSettingsIntoGUI(settings);
			});

			updateFrameTitle(false, true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Error while opening '" + absolutePath + "': " + e.getMessage(), "Error While Opening Map",
					JOptionPane.ERROR_MESSAGE);
			Logger.printError("Unable to open '" + absolutePath + "' due to an error:", e);
		}
	}

	private void convertCustomImagesFolderIfNeeded(MapSettings settings)
	{
		if (settings.hasOldCustomImagesFolderStructure())
		{
			try
			{
				MapSettings.convertOldCustomImagesFolder(settings.customImagesPath);

				JOptionPane.showMessageDialog(null, "Your custom images folder has been automatically converted to the new structure.",
						"Custom Images Folder Converted", JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException ex)
			{
				String errorMessage = "Error while restructuring custom images folder for " + settings.customImagesPath + ": "
						+ ex.getMessage();
				Logger.printError(errorMessage, ex);
				JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void handleMouseWheelChangingZoom(MouseWheelEvent e)
	{
		if (toolsPanel.zoomComboBox.isEnabled())
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
				// The action listener on toolsPanel.zoomComboBox will update the map.
				toolsPanel.zoomComboBox.setSelectedIndex(newIndex);
			}
		}
	}

	public void updateDisplayedMapFromGeneratedMap(boolean updateScrollLocationIfZoomChanged, Rectangle incrementalChangeArea,
			boolean isOnlyZoomChange)
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
					// Zoom toward the mouse's position, keeping the point
					// currently under the mouse the same if possible.
					scrollTo = new java.awt.Rectangle((int) (mousePosition.x * scale) - mousePosition.x + visible.x,
							(int) (mousePosition.y * scale) - mousePosition.y + visible.y, visible.width, visible.height);
				}
				else
				{
					// Zoom toward or away from the current center of the
					// screen.
					java.awt.Point currentCentroid = new java.awt.Point(visible.x + (visible.width / 2), visible.y + (visible.height / 2));
					java.awt.Point targetCentroid = new java.awt.Point((int) (currentCentroid.x * scale),
							(int) (currentCentroid.y * scale));
					scrollTo = new java.awt.Rectangle(targetCentroid.x - visible.width / 2, targetCentroid.y - visible.height / 2,
							visible.width, visible.height);
				}
			}

			mapEditingPanel.setZoom(zoom);
			// Don't update the display quality when zoom is the only thing that changed because otherwise changing the zoom while the map
			// is redrawing at a new display quality can cause tool highlights to draw in the wrong position temporarily.
			if (!isOnlyZoomChange)
			{
				mapEditingPanel.setResolution(displayQualityScale);
			}
			Method method = zoom < 0.34 ? Method.QUALITY : Method.BALANCED;
			int zoomedWidth = (int) (mapEditingPanel.mapFromMapCreator.getWidth() * zoom);
			if (zoomedWidth <= 0)
			{
				// Prevents a crash if someone collapses the map editing panel.
				zoomedWidth = 600;
			}

			if (method == Method.QUALITY)
			{
				// Can't incrementally zoom. Zoom the whole thing.
				mapEditingPanel.setImage(AwtFactory
						.unwrap(ImageHelper.scaleByWidth(AwtFactory.wrap(mapEditingPanel.mapFromMapCreator), zoomedWidth, method)));
			}
			else
			{

				if (incrementalChangeArea == null)
				{
					// It's important that this image scaling is done using the
					// same method as the incremental case below
					// (when incrementalChangeArea != null), or at least close
					// enough that people can't tell the difference.
					// The reason is that the incremental case will update
					// pieces of the image created below.
					// I don't use ImageHelper.scaleInto for the full image case
					// because it's 5x slower than the below
					// method, which uses ImgScalr.
					mapEditingPanel.setImage(AwtFactory
							.unwrap(ImageHelper.scaleByWidth(AwtFactory.wrap(mapEditingPanel.mapFromMapCreator), zoomedWidth, method)));
				}
				else
				{
					// These two images will be the same if the zoom and display
					// quality are the same, in which case
					// ImageHelper.scaleByWidth called above returns the input
					// image.
					if (mapEditingPanel.mapFromMapCreator != mapEditingPanel.getImage())
					{
						ImageHelper.scaleInto(AwtFactory.wrap(mapEditingPanel.mapFromMapCreator),
								AwtFactory.wrap(mapEditingPanel.getImage()), incrementalChangeArea);
					}
				}
			}

			if (scrollTo != null)
			{
				// For some reason I have to do a whole bunch of revalidation or
				// else scrollRectToVisible doesn't realize the map has changed
				// size.
				mapEditingPanel.revalidate();
				mapEditingScrollPane.revalidate();
				this.revalidate();

				mapEditingPanel.scrollRectToVisible(scrollTo);
			}

			updater.doWhenMapIsReadyForInteractions(() -> 
			{
				toolsPanel.currentTool.onAfterShowMap();
			});
	
			mapEditingPanel.revalidate();
			mapEditingScrollPane.revalidate();
			mapEditingPanel.repaint();
			mapEditingScrollPane.repaint();
		}
	}

	private void updateZoomOptionsBasedOnWindowSize()
	{
		double minZoom = translateZoomLevel(ToolsPanel.fitToWindowZoomLevel);
		String selectedZoom = (String) toolsPanel.getZoomString();
		toolsPanel.zoomComboBox.removeAllItems();
		for (String level : toolsPanel.zoomLevels)
		{
			if (translateZoomLevel(level) >= minZoom || level.equals(ToolsPanel.fitToWindowZoomLevel))
			{
				toolsPanel.zoomComboBox.addItem(level);
			}
		}
		toolsPanel.zoomComboBox.setSelectedItem(selectedZoom);
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
				nortantis.geom.Dimension size = new nortantis.geom.Dimension(
						mapEditingScrollPane.getSize().width - additionalWidthToRemoveIDontKnowWhereItsCommingFrom,
						mapEditingScrollPane.getSize().height - additionalWidthToRemoveIDontKnowWhereItsCommingFrom);

				nortantis.geom.Dimension fitted = ImageHelper.fitDimensionsWithinBoundingBox(size,
						mapEditingPanel.mapFromMapCreator.getWidth(), mapEditingPanel.mapFromMapCreator.getHeight());
				return (fitted.width / mapEditingPanel.mapFromMapCreator.getWidth()) * mapEditingPanel.osScale;
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
				// Divide by the size of the generated map because the map's
				// displayed size should be the same
				// no matter the resolution it generated at.
				return (oneHundredPercentMapWidth * percentage) / mapEditingPanel.mapFromMapCreator.getWidth();
			}
			else
			{
				return 1.0;
			}
		}
	}

	public void showAsDrawing(boolean isDrawing)
	{
		clearEntireMapButton.setEnabled(!isDrawing);
		toolsPanel.showAsDrawing(isDrawing);
		if (textSearchDialog != null)
		{
			textSearchDialog.setAllowSearches(!isDrawing);
		}
	}

	private double parsePercentage(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		return zoomPercent / 100.0;
	}

	/**
	 * Handles when zoom level changes in the display.
	 */
	public void handleImageQualityChange(DisplayQuality quality)
	{
		updateImageQualityScale(quality);

		ImageCache.clear();
		updater.createAndShowMapFull();
	}

	public void updateImageQualityScale(DisplayQuality quality)
	{
		if (quality == DisplayQuality.Very_Low)
		{
			displayQualityScale = 0.5;
		}
		else if (quality == DisplayQuality.Low)
		{
			displayQualityScale = 0.75;
		}
		else if (quality == DisplayQuality.Medium)
		{
			displayQualityScale = 1.0;
		}
		else if (quality == DisplayQuality.High)
		{
			displayQualityScale = 1.25;
		}
		else if (quality == DisplayQuality.Ultra)
		{
			displayQualityScale = 1.5;
		}
	}

	public void clearEntireMap()
	{
		updater.doWhenMapIsReadyForInteractions(() ->
		{
			if (updater.mapParts == null || updater.mapParts.graph == null)
			{
				return;
			}

			toolsPanel.resetToolsForNewMap();

			// Erase text
			edits.text.clear();

			for (Center center : updater.mapParts.graph.centers)
			{
				// Change land to ocean and erase icons
				CenterEdit newValues = new CenterEdit(center.index, true, false, null, null, null);
				edits.centerEdits.put(center.index, newValues);
			}

			// Erase rivers
			for (Edge edge : updater.mapParts.graph.edges)
			{
				EdgeEdit eEdit = edits.edgeEdits.get(edge.index);
				eEdit.riverLevel = 0;
			}

			// Erase free icons
			edits.freeIcons.clear();

			// Erase roads.
			edits.roads.clear();

			undoer.setUndoPoint(UpdateType.Full, null);
			updater.createAndShowMapTerrainChange();
		});
	}

	private void handleExportAsImagePressed()
	{
		ImageExportDialog dialog = new ImageExportDialog(this, ImageExportType.Map);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void handleExportHeightmapPressed()
	{
		ImageExportDialog dialog = new ImageExportDialog(this, ImageExportType.Heightmap);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void handleCustomImagesPressed()
	{
		CustomImagesDialog dialog = new CustomImagesDialog(this, customImagesPath, (value) ->
		{
			customImagesPath = value;
			loadSettingsAndEditsIntoThemeAndToolsPanels(getSettingsFromGUI(false), false, true);
			toolsPanel.handleCustomImagesPathChanged(customImagesPath);
			undoer.setUndoPoint(UpdateType.Full, null, () -> handleImagesRefresh());
			updater.createAndShowMapFull(() -> handleImagesRefresh());
		});
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void handleNameGeneratorPressed()
	{
		MapSettings settings = getSettingsFromGUI(false);
		NameGeneratorDialog dialog = new NameGeneratorDialog(this, settings);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private void handleSearchTextPressed()
	{
		if (textSearchDialog == null || !(textSearchDialog.isVisible()))
		{
			textSearchDialog = new TextSearchDialog(this);
			textSearchDialog.setAllowSearches((!updater.isMapBeingDrawn()));

			java.awt.Point parentLocation = getLocation();
			Dimension parentSize = getSize();
			Dimension dialogSize = textSearchDialog.getSize();

			textSearchDialog.setLocation(parentLocation.x + parentSize.width / 2 - dialogSize.width / 2,
					parentLocation.y + parentSize.height - dialogSize.height - 18);

			textSearchDialog.setVisible(true);
		}
		else
		{
			textSearchDialog.requestFocusAndSelectAll();
		}
	}

	public boolean checkForUnsavedChanges()
	{
		if (lastSettingsLoadedOrSaved == null)
		{
			return false;
		}

		if (settingsHaveUnsavedChanges())
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

	private boolean settingsHaveUnsavedChanges()
	{
		if (lastSettingsLoadedOrSaved == null)
		{
			return true;
		}

		final MapSettings currentSettings = getSettingsFromGUI(false);

		if (DebugFlags.shouldWriteBeforeAndAfterJsonWhenSavePromptShows())
		{
			try
			{
				currentSettings.writeToFile("currentSettings.json");
				lastSettingsLoadedOrSaved.writeToFile("lastSettingsLoadedOrSaved.json");
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		if (hasDrawnCurrentMapAtLeastOnce)
		{
			return !currentSettings.equals(lastSettingsLoadedOrSaved);
		}
		else
		{
			// Ignore edits in this comparison because the first draw can create
			// or change edits, and the user cannot modify the
			// edits until the map has been drawn.
			return !currentSettings.equalsIgnoringEdits(lastSettingsLoadedOrSaved);
		}
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
			final MapSettings settings = getSettingsFromGUI(false);
			try
			{
				saveMap(settings, openSettingsFilePath.toString());
			}
			catch (IOException e)
			{
				e.printStackTrace();
				Logger.printError("Error while saving map.", e);
				JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to save settings.", JOptionPane.ERROR_MESSAGE);
			}
			updateFrameTitle(false, true);
		}
	}

	public void saveSettingsAs(Component parent)
	{
		Path curPath = openSettingsFilePath == null ? FileSystemView.getFileSystemView().getDefaultDirectory().toPath()
				: openSettingsFilePath;
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

		// This is necessary when we want to automatically select a file that
		// doesn't exist to save into, which is done
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

			if (!openSettingsFilePath.equals(curPath))
			{
				// Clear previous image export locations so that a new copy of a map doesn't export over the images from the older version.
				imageExportPath = null;
				heightmapExportPath = null;
			}

			final MapSettings settings = getSettingsFromGUI(false);
			try
			{
				saveMap(settings, openSettingsFilePath.toString());
			}
			catch (IOException e)
			{
				e.printStackTrace();
				Logger.printError("Erorr while saving settings to a new file:", e);
				JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to save settings.", JOptionPane.ERROR_MESSAGE);
			}

			updateFrameTitle(false, true);
		}
	}

	private void saveMap(MapSettings settings, String absolutePath) throws IOException
	{
		settings.writeToFile(absolutePath);
		Logger.println("Settings saved to " + openSettingsFilePath.toString());
		updateLastSettingsLoadedOrSaved(settings);
		UserPreferences.getInstance().addRecentMapFilePath(absolutePath);
		createOrUpdateRecentMapMenuButtons();
	}

	private boolean showUnsavedChangesSymbol = false;

	private void updateFrameTitle(boolean isTriggeredByChange, boolean clearUnsavedChangesSymbol)
	{
		if (isTriggeredByChange)
		{
			showUnsavedChangesSymbol = true;
		}
		if (clearUnsavedChangesSymbol)
		{
			showUnsavedChangesSymbol = false;
		}

		String title;
		if (openSettingsFilePath != null)
		{
			title = (showUnsavedChangesSymbol ? "✎ " : "") + FilenameUtils.getName(openSettingsFilePath.toString()) + " - "
					+ frameTitleBase;
		}
		else
		{
			title = frameTitleBase;
		}
		setTitle(title);
	}

	public void clearOpenSettingsFilePath()
	{
		openSettingsFilePath = null;
	}

	void loadSettingsIntoGUI(MapSettings settings)
	{
		hasDrawnCurrentMapAtLeastOnce = false;
		mapEditingPanel.clearAllSelectionsAndHighlights();

		updateLastSettingsLoadedOrSaved(settings);
		toolsPanel.resetToolsForNewMap();
		loadSettingsAndEditsIntoThemeAndToolsPanels(settings, false, true);

		exportResolution = settings.resolution;
		imageExportPath = settings.imageExportPath;
		heightmapExportResolution = settings.heightmapResolution;
		heightmapExportPath = settings.heightmapExportPath;

		setPlaceholderImage(new String[] { "Drawing map..." });

		undoer.reset();

		handleImagesRefresh();

		if (settings.edits != null && settings.edits.isInitialized())
		{
			undoer.initialize(settings);
			enableOrDisableFieldsThatRequireMap(true, settings);
		}
		else
		{
			// Note - this call needs to come after everything that calls into
			// loadSettingsAndEditsIntoThemeAndToolsPanels because the text
			// tool
			// might enable fields when when loading settings, which will cause
			// fields to be enabled before the map is ready.
			enableOrDisableFieldsThatRequireMap(false, settings);
		}

		toolsPanel.resetZoomToDefault();
		updater.createAndShowMapFull();
		updateFrameTitle(false, true);

		defaultMapExportAction = settings.defaultMapExportAction;
		defaultHeightmapExportAction = settings.defaultHeightmapExportAction;
	}

	void loadSettingsAndEditsIntoThemeAndToolsPanels(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean willDoImagesRefresh)
	{
		updater.setEnabled(false);
		undoer.setEnabled(false);
		customImagesPath = settings.customImagesPath;
		edits = settings.edits;
		boolean changeEffectsBackgroundImages = themePanel.loadSettingsIntoGUI(settings);
		toolsPanel.loadSettingsIntoGUI(settings, isUndoRedoOrAutomaticChange, changeEffectsBackgroundImages, willDoImagesRefresh);
		undoer.setEnabled(true);
		updater.setEnabled(true);
	}

	private void updateLastSettingsLoadedOrSaved(MapSettings settings)
	{
		lastSettingsLoadedOrSaved = settings.deepCopy();
	}

	MapSettings getSettingsFromGUI(boolean deepCopyEdits)
	{
		if (lastSettingsLoadedOrSaved == null)
		{
			// No settings are loaded.
			return null;
		}

		MapSettings settings = lastSettingsLoadedOrSaved.deepCopyExceptEdits();
		if (deepCopyEdits)
		{
			settings.edits = edits.deepCopy();
		}
		else
		{
			settings.edits = edits;
		}

		// Settings which have a UI in a popup.
		settings.resolution = exportResolution;
		settings.defaultMapExportAction = defaultMapExportAction;
		settings.defaultHeightmapExportAction = defaultHeightmapExportAction;
		settings.imageExportPath = imageExportPath;
		settings.heightmapResolution = heightmapExportResolution;
		settings.heightmapExportPath = heightmapExportPath;
		settings.customImagesPath = customImagesPath;

		themePanel.getSettingsFromGUI(settings);
		toolsPanel.getSettingsFromGUI(settings);

		if (lastSettingsLoadedOrSaved != null)
		{
			// Copy over any settings which do not have a UI element.
			settings.pointPrecision = lastSettingsLoadedOrSaved.pointPrecision;
			settings.textRandomSeed = lastSettingsLoadedOrSaved.textRandomSeed;
			settings.regionsRandomSeed = lastSettingsLoadedOrSaved.regionsRandomSeed;
			settings.randomSeed = lastSettingsLoadedOrSaved.randomSeed;

			// Copy over settings with a UI only in the new map dialog.
			settings.worldSize = lastSettingsLoadedOrSaved.worldSize;
			settings.randomSeed = lastSettingsLoadedOrSaved.randomSeed;
			settings.edgeLandToWaterProbability = lastSettingsLoadedOrSaved.edgeLandToWaterProbability;
			settings.centerLandToWaterProbability = lastSettingsLoadedOrSaved.centerLandToWaterProbability;
			settings.generatedWidth = lastSettingsLoadedOrSaved.generatedWidth;
			settings.generatedHeight = lastSettingsLoadedOrSaved.generatedHeight;
		}

		return settings;
	}

	public Color getLandColor()
	{
		return themePanel.getLandColor();
	}

	private void setPlaceholderImage(String[] message)
	{
		mapEditingPanel.setImage(AwtFactory
				.unwrap(ImageHelper.createPlaceholderImage(message, AwtFactory.wrap(SwingHelper.getTextColorForPlaceholderImages()))));

		// Clear out the map from map creator so that causing the window to
		// re-zoom while the placeholder image
		// is displayed doesn't show the previous map. This can happen when the
		// zoom is fit to window, you create
		// a new map, then resize the window while the new map is drawing for
		// the first time.
		mapEditingPanel.mapFromMapCreator = null;

		mapEditingPanel.repaint();

		// Prevent a single-pixel column on the right side of the map from remaining. Not sure why that happens.
		revalidate();
		repaint();
	}

	void handleThemeChange(boolean changeEffectsBackgroundImages)
	{
		// This check is to filter out automatic changes caused by
		// loadSettingsIntoGUI.
		if (undoer.isEnabled())
		{
			// Allow editor tools to update based on changes in the themes
			// panel.
			toolsPanel.loadSettingsIntoGUI(getSettingsFromGUI(false), true, changeEffectsBackgroundImages, false);
		}
	}

	@Override
	public void appendLoggerMessage(String message)
	{
		txtConsoleOutput.append(message);
		consoleOutputPane.revalidate();
		consoleOutputPane.repaint();
	}

	@Override
	public void clearLoggerMessages()
	{
		txtConsoleOutput.setText("");
		txtConsoleOutput.revalidate();
		txtConsoleOutput.repaint();
		consoleOutputPane.revalidate();
		consoleOutputPane.repaint();
	}

	@Override
	public boolean isReadyForLogging()
	{
		return txtConsoleOutput != null;
	}

	public Path getOpenSettingsFilePath()
	{
		return openSettingsFilePath;
	}

	String getFileMenuName()
	{
		return fileMenu.getText();
	}

	String getRefreshImagesMenuName()
	{
		return refreshMenuItem.getText();
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		// Tell drawing code to use AWT.
		PlatformFactory.setInstance(new AwtFactory());

		setLookAndFeel(UserPreferences.getInstance().lookAndFeel);

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
					System.out.println("Error while starting the program: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
	}
}
