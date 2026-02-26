package nortantis.swing;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import nortantis.CancelledException;
import nortantis.DebugFlags;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.editor.*;
import nortantis.geom.IntRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtBridge;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.translation.Translation;
import nortantis.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.imgscalr.Scalr.Method;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.*;
import java.util.List;

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
	private JMenuItem createSubMapMenuItem;
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
				JOptionPane.showMessageDialog(null, "Unable to create GUI because of error: " + ex.getMessage() + "\nVersion: " + MapSettings.currentVersion + "\nOS Name: "
						+ System.getProperty("os.name") + "\nStack trace: " + ExceptionUtils.getStackTrace(ex), "Error", JOptionPane.ERROR_MESSAGE);
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
			if (fileToOpen != null && !fileToOpen.isEmpty() && fileToOpen.endsWith(MapSettings.fileExtensionWithDot) && new File(fileToOpen).exists())
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
			setPlaceholderImage(new String[] { Translation.get("mainWindow.welcome"), Translation.get("mainWindow.welcome.line2") });
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

						if (MapSettings.isVersionGreaterThanCurrent(latestVersion) && (StringUtils.isEmpty(lastCheckedVersion) || MapSettings.isVersionGreaterThan(latestVersion, lastCheckedVersion)))
						{
							UserPreferences.getInstance().lastVersionFromCheck = latestVersion;
							UserPreferences.getInstance().lastVersionCheckTime = currentTime;

							String message = Translation.get("mainWindow.updateAvailableMessage", latestVersion);
							String url = "https://jandjheydorn.com/nortantis";

							JPanel messagePanel = new JPanel();
							messagePanel.setLayout(new FlowLayout());

							JLabel messageLabel = new JLabel(message);
							messagePanel.add(messageLabel);

							JLabel hyperlink = SwingHelper.createHyperlink(url, url);
							messagePanel.add(hyperlink);

							JOptionPane.showMessageDialog(MainWindow.this, messagePanel, Translation.get("mainWindow.updateAvailable"), JOptionPane.INFORMATION_MESSAGE);
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
		createSubMapMenuItem.setEnabled(enable);
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

		setIconImage(AwtBridge.toBufferedImage(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal/taskbar icon.png").toString())));
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
		toolsPanel = new ToolsPanel(this, updater);
		int toolsPanelWidth = UserPreferences.getInstance().toolsPanelWidth > SwingHelper.sidePanelMinimumWidth ? UserPreferences.getInstance().toolsPanelWidth : SwingHelper.sidePanelPreferredWidth;
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
				if (mapEditingPanel.isSelectionBoxActive() && SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
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
				if (mapEditingPanel.isSelectionBoxActive() && SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
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
				if (mapEditingPanel.isSelectionBoxActive())
				{
					return;
				}
				updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseMovedOnMap(e));
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (mapEditingPanel.isSelectionBoxActive() && SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
				if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e))
				{
					if (mouseLocationForMiddleButtonDrag != null)
					{
						int deltaX = mouseLocationForMiddleButtonDrag.x - e.getX();
						int deltaY = mouseLocationForMiddleButtonDrag.y - e.getY();
						mapEditingScrollPane.getVerticalScrollBar().setValue(mapEditingScrollPane.getVerticalScrollBar().getValue() + deltaY);
						mapEditingScrollPane.getHorizontalScrollBar().setValue(mapEditingScrollPane.getHorizontalScrollBar().getValue() + deltaX);
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
			protected void onFinishedDrawingFull(Image map, boolean anotherDrawIsQueued, int borderPaddingAsDrawn, List<String> warningMessages)
			{
				if (mapEditingPanel.mapFromMapCreator != null && mapEditingPanel.mapFromMapCreator != map)
				{
					mapEditingPanel.mapFromMapCreator.close();
				}
				mapEditingPanel.mapFromMapCreator = map;
				onFinishedDrawingCommon(anotherDrawIsQueued, borderPaddingAsDrawn, null, warningMessages);
			}

			@Override
			protected void onFinishedDrawingIncremental(boolean anotherDrawIsQueued, int borderPaddingAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
				// Map was already updated in-place by MapCreator on background thread.
				// Just update the zoomed display for the changed region.
				onFinishedDrawingCommon(anotherDrawIsQueued, borderPaddingAsDrawn, incrementalChangeArea, warningMessages);
			}

			private void onFinishedDrawingCommon(boolean anotherDrawIsQueued, int borderPaddingAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
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

					JOptionPane.showMessageDialog(MainWindow.this, scrollPane, Translation.get("mainWindow.mapDrewWithWarnings"), JOptionPane.WARNING_MESSAGE);

				}

				boolean isChange = settingsHaveUnsavedChanges();
				updateFrameTitle(isChange, !isChange);
			}

			@Override
			protected void onFailedToDraw()
			{
				showAsDrawing(false);
				mapEditingPanel.clearAllSelectionsAndHighlights();
				setPlaceholderImage(new String[] { Translation.get("mainWindow.mapFailedToDraw"), Translation.get("mainWindow.mapFailedRetry", fileMenu.getText(), refreshMenuItem.getText()) });

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
				return mapEditingPanel.mapFromMapCreator;
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

		fileMenu = new JMenu(Translation.get("menu.file"));
		menuBar.add(fileMenu);

		final JMenuItem newRandomMapMenuItem = new JMenuItem(Translation.get("menu.file.newRandomMap"));
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

		newMapWithSameThemeMenuItem = new JMenuItem(Translation.get("menu.file.newMapWithSameTheme"));
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

					if (settingsToKeepThemeFrom.drawRegionColors && !UserPreferences.getInstance().hideNewMapWithSameThemeRegionColorsMessage)
					{
						UserPreferences.getInstance().hideNewMapWithSameThemeRegionColorsMessage = SwingHelper.showDismissibleMessage(Translation.get("regionColors.title"),
								Translation.get("regionColors.message", LandWaterTool.getColorGeneratorSettingsName(), LandWaterTool.getToolbarNameStatic()), new Dimension(400, 133),
								JOptionPane.PLAIN_MESSAGE, MainWindow.this);
					}

					launchNewSettingsDialog(settingsToKeepThemeFrom);
				}
			}
		});

		createSubMapMenuItem = new JMenuItem("Create Sub-Map...");
		fileMenu.add(createSubMapMenuItem);
		createSubMapMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				handleCreateSubMap();
			}
		});

		final JMenuItem loadSettingsMenuItem = new JMenuItem(Translation.get("menu.file.open"));
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

				Path curPath = openSettingsFilePath == null ? FileSystemView.getFileSystemView().getDefaultDirectory().toPath() : openSettingsFilePath;
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
						return f.isDirectory() || f.getName().toLowerCase().endsWith(".properties") || f.getName().toLowerCase().endsWith(MapSettings.fileExtensionWithDot);
					}
				});
				int status = fileChooser.showOpenDialog(MainWindow.this);
				if (status == JFileChooser.APPROVE_OPTION)
				{
					openMap(fileChooser.getSelectedFile().getAbsolutePath());

					if (openSettingsFilePath != null && MapSettings.isOldPropertiesFile(openSettingsFilePath.toString()))
					{
						JOptionPane.showMessageDialog(MainWindow.this,
								Translation.get("mainWindow.fileConvertedMessage", FilenameUtils.getName(openSettingsFilePath.toString()), MapSettings.fileExtensionWithDot),
								Translation.get("mainWindow.fileConverted"), JOptionPane.INFORMATION_MESSAGE);
						openSettingsFilePath = Paths.get(FilenameUtils.getFullPath(openSettingsFilePath.toString()),
								FilenameUtils.getBaseName(openSettingsFilePath.toString()) + MapSettings.fileExtensionWithDot);
						forceSaveAs = true;
					}

				}

			}
		});

		recentSettingsMenuItem = new JMenu(Translation.get("menu.file.openRecent"));
		fileMenu.add(recentSettingsMenuItem);
		createOrUpdateRecentMapMenuButtons();

		saveMenuItem = new JMenuItem(Translation.get("menu.file.save"));
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

		saveAsMenItem = new JMenuItem(Translation.get("menu.file.saveAs"));
		fileMenu.add(saveAsMenItem);
		saveAsMenItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				saveSettingsAs(MainWindow.this);
			}
		});

		exportMapAsImageMenuItem = new JMenuItem(Translation.get("menu.file.exportAsImage"));
		fileMenu.add(exportMapAsImageMenuItem);
		exportMapAsImageMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				handleExportAsImagePressed();
			}
		});

		exportHeightmapMenuItem = new JMenuItem(Translation.get("menu.file.exportHeightmap"));
		fileMenu.add(exportHeightmapMenuItem);
		exportHeightmapMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				handleExportHeightmapPressed();
			}
		});

		refreshMenuItem = new JMenuItem(Translation.get("menu.file.refreshImagesAndRedraw"));
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

		editMenu = new JMenu(Translation.get("menu.edit"));
		menuBar.add(editMenu);

		undoButton = new JMenuItem(Translation.get("menu.edit.undo"));
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
					updater.doWhenMapIsNotDrawing(() ->
					{
						undoer.undo();
					});
				}
			}
		});

		redoButton = new JMenuItem(Translation.get("menu.edit.redo"));
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
					updater.doWhenMapIsNotDrawing(() ->
					{
						undoer.redo();
					});
				}
			}
		});

		clearEntireMapButton = new JMenuItem(Translation.get("menu.edit.clearEntireMap"));
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

		customImagesMenuItem = new JMenuItem(Translation.get("menu.edit.customImagesFolder"));
		editMenu.add(customImagesMenuItem);
		customImagesMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleCustomImagesPressed();
			}
		});

		viewMenu = new JMenu(Translation.get("menu.view"));
		menuBar.add(viewMenu);

		highlightLakesButton = new JCheckBoxMenuItem(Translation.get("menu.view.highlightLakes"));
		highlightLakesButton.setToolTipText(Translation.get("menu.view.highlightLakes.tooltip"));
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

		highlightRiversButton = new JCheckBoxMenuItem(Translation.get("menu.view.highlightRivers"));
		highlightRiversButton.setToolTipText(Translation.get("menu.view.highlightRivers.tooltip"));
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
			JMenu themeMenu = new JMenu(Translation.get("menu.view.theme"));

			JRadioButtonMenuItem darkTheme = new JRadioButtonMenuItem(Translation.enumDisplayName(LookAndFeel.Dark));
			JRadioButtonMenuItem lightTheme = new JRadioButtonMenuItem(Translation.enumDisplayName(LookAndFeel.Light));
			JRadioButtonMenuItem systemTheme = new JRadioButtonMenuItem(Translation.enumDisplayName(LookAndFeel.System));

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

		{
			JMenu languageMenu = new JMenu(Translation.get("menu.view.language"));
			ButtonGroup languageGroup = new ButtonGroup();

			String currentLanguage = UserPreferences.getInstance().language;

			JRadioButtonMenuItem systemDefaultItem = new JRadioButtonMenuItem(Translation.get("language.systemDefault"));
			systemDefaultItem.setSelected(currentLanguage == null || currentLanguage.isEmpty());
			languageGroup.add(systemDefaultItem);
			languageMenu.add(systemDefaultItem);
			systemDefaultItem.addActionListener(e -> handleLanguageChange(null));

			String[][] languages = { { "en", "English" }, { "de", "Deutsch" }, { "es", "Espa\u00F1ol" }, { "fr", "Fran\u00E7ais" }, { "pt", "Portugu\u00EAs" },
					{ "ru", "\u0420\u0443\u0441\u0441\u043A\u0438\u0439" }, { "zh", "\u4E2D\u6587" } };

			for (String[] lang : languages)
			{
				JRadioButtonMenuItem item = new JRadioButtonMenuItem(lang[1]);
				item.setSelected(lang[0].equals(currentLanguage));
				languageGroup.add(item);
				languageMenu.add(item);
				final String langCode = lang[0];
				item.addActionListener(e -> handleLanguageChange(langCode));
			}

			viewMenu.add(languageMenu);
		}

		toolsMenu = new JMenu(Translation.get("menu.tools"));
		menuBar.add(toolsMenu);

		nameGeneratorMenuItem = new JMenuItem(Translation.get("menu.tools.nameGenerator"));
		toolsMenu.add(nameGeneratorMenuItem);
		nameGeneratorMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleNameGeneratorPressed();
			}
		});

		searchTextMenuItem = new JMenuItem(Translation.get("menu.tools.searchText"));
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

		JMenu artPacksMenu = new JMenu(Translation.get("menu.tools.artPacks"));
		toolsMenu.add(artPacksMenu);

		JMenuItem addArtPackItem = new JMenuItem(Translation.get("menu.tools.addArtPack"));
		artPacksMenu.add(addArtPackItem);
		addArtPackItem.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleAddArtPack();
			}
		});

		JMenuItem openArtPacksFolderItem = new JMenuItem(Translation.get("menu.tools.openArtPacksFolder"));
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
		highlightIconsInArtPackMenu = new JMenu(Translation.get("menu.tools.highlightIconsInArtPacks"));
		artPacksMenu.add(highlightIconsInArtPackMenu);
		updateArtPackHighlightOptions();

		helpMenu = new JMenu(Translation.get("menu.help"));
		menuBar.add(helpMenu);

		JMenuItem keyboardShortcutsItem = new JMenuItem(Translation.get("menu.help.keyboardShortcuts"));
		helpMenu.add(keyboardShortcutsItem);
		keyboardShortcutsItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				JOptionPane.showMessageDialog(MainWindow.this, Translation.get("keyboardShortcuts.message"), Translation.get("keyboardShortcuts.title"), JOptionPane.INFORMATION_MESSAGE);
			}
		});

		JMenuItem aboutNortantisItem = new JMenuItem(Translation.get("menu.help.aboutNortantis"));
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

	private void handleLanguageChange(String languageCode)
	{
		UserPreferences.getInstance().language = languageCode;
		UserPreferences.getInstance().save();
		JOptionPane.showMessageDialog(this, Translation.get("language.changed"), Translation.get("language.changed.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void handleLookAndFeelChange(LookAndFeel lookAndFeel)
	{
		setLookAndFeel(lookAndFeel);
		UserPreferences.getInstance().lookAndFeel = lookAndFeel;
		SwingUtilities.updateComponentTreeUI(this);
		toolsPanel.handleLookAndFeelChange();
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
				String message = Translation.get("artPack.errorCreatingFolder", ex.getMessage());
				Logger.printError(message, ex);
				JOptionPane.showMessageDialog(this, message, Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		OSHelper.openFileExplorerTo(artPacksPath.toFile());
	}

	private void handleAddArtPack()
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setDialogTitle(Translation.get("artPack.selectZip"));
		fileChooser.setFileFilter(new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return Translation.get("artPack.zipFileFilter");
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
				JOptionPane.showMessageDialog(this, Translation.get("artPack.invalidEmpty"), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}

			if (subfolderNames.size() > 1)
			{
				JOptionPane.showMessageDialog(this, Translation.get("artPack.invalidMultipleFolders", subfolderNames.size()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}

			try
			{
				String settingsPath = subfolderNames.get(0) + "/" + "settings.txt";
				Properties settingsProps = FileHelper.readPropertiesFromZipFile(selectedFile.toPath(), settingsPath);
				final String requiredVersionKey = "requiredVersion";
				if (settingsProps.containsKey(requiredVersionKey))
				{
					String requiredVersion = settingsProps.getProperty(requiredVersionKey);
					if (!StringUtils.isBlank(requiredVersion))
					{
						try
						{
							if (MapSettings.isVersionGreaterThanCurrent(requiredVersion))
							{
								JOptionPane.showMessageDialog(this, Translation.get("artPack.requiresVersion", requiredVersion, MapSettings.currentVersion), Translation.get("common.error"),
										JOptionPane.ERROR_MESSAGE);
								return;
							}
						}
						catch (NumberFormatException e)
						{
							String message = "Number format error while reading " + requiredVersionKey + " from '" + settingsPath + "' in '" + selectedFile.toPath() + "': " + e.getMessage();
							Logger.printError(message, e);
							JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
					}
				}
			}
			catch (FileNotFoundException e)
			{
				// Do nothing. This means the art pack doesn't have a settings file. It's optional.
			}
			catch (IOException e)
			{
				final String message = "Error while trying to read art pack version file: " + e.getMessage();
				Logger.printError(message, e);
				JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
			}

			String artPackName = subfolderNames.get(0);

			if (Assets.reservedArtPacks.contains(artPackName.toLowerCase()))
			{
				JOptionPane.showMessageDialog(this, Translation.get("artPack.nameNotAllowed", artPackName), Translation.get("artPack.invalidName"), JOptionPane.ERROR_MESSAGE);
				return;
			}

			File artPackFolderAsFile = Assets.getArtPackPath(artPackName, null).toFile();
			if (artPackFolderAsFile.exists() && artPackFolderAsFile.isDirectory())
			{
				// Show the dialog
				int response = JOptionPane.showOptionDialog(this, Translation.get("artPack.alreadyExists", artPackName), Translation.get("artPack.overwriteTitle"), JOptionPane.DEFAULT_OPTION,
						JOptionPane.WARNING_MESSAGE, null, new Object[] { Translation.get("artPack.overwrite"), Translation.get("common.cancel") }, Translation.get("common.cancel"));

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
				JOptionPane.showMessageDialog(MainWindow.this, Translation.get("artPack.addedSuccessfully"), Translation.get("artPack.success"), JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException ex)
			{
				JOptionPane.showMessageDialog(MainWindow.this, Translation.get("artPack.errorUncompressing", ex.getMessage()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
			}
			handleImagesRefresh();
		}
	}

	void handleImagesRefresh()
	{
		updater.setEnabled(false);
		undoer.setEnabled(false);
		ImageCache.clear();
		ThemePanel.clearBackgroundImageCache();
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
			JOptionPane.showMessageDialog(null, Translation.get("mainWindow.mapDoesNotExist", absolutePath), Translation.get("mainWindow.unableToOpenMap"), JOptionPane.ERROR_MESSAGE);
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
			updater.doWhenMapIsNotDrawing(() ->
			{
				loadSettingsIntoGUI(settings);
			});

			updateFrameTitle(false, true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Error while opening '" + absolutePath + "': " + e.getMessage(), Translation.get("mainWindow.errorWhileOpeningMap"), JOptionPane.ERROR_MESSAGE);
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

				JOptionPane.showMessageDialog(null, Translation.get("customImages.folderConvertedMessage"), Translation.get("customImages.folderConverted"), JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException ex)
			{
				String errorMessage = "Error while restructuring custom images folder for " + settings.customImagesPath + ": " + ex.getMessage();
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

	public void updateDisplayedMapFromGeneratedMap(boolean updateScrollLocationIfZoomChanged, IntRectangle incrementalChangeArea, boolean isOnlyZoomChange)
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
					scrollTo = new java.awt.Rectangle((int) (mousePosition.x * scale) - mousePosition.x + visible.x, (int) (mousePosition.y * scale) - mousePosition.y + visible.y, visible.width,
							visible.height);
				}
				else
				{
					// Zoom toward or away from the current center of the
					// screen.
					java.awt.Point currentCentroid = new java.awt.Point(visible.x + (visible.width / 2), visible.y + (visible.height / 2));
					java.awt.Point targetCentroid = new java.awt.Point((int) (currentCentroid.x * scale), (int) (currentCentroid.y * scale));
					scrollTo = new java.awt.Rectangle(targetCentroid.x - visible.width / 2, targetCentroid.y - visible.height / 2, visible.width, visible.height);
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
				if (zoomedWidth > mapEditingPanel.mapFromMapCreator.getWidth())
				{
					// Zooming in: convert smaller source first, then scale up.
					BufferedImage sourceBI = AwtBridge.toBufferedImage(mapEditingPanel.mapFromMapCreator);
					try (Image source = AwtBridge.wrapBufferedImage(sourceBI); Image scaled = ImageHelper.getInstance().scaleByWidth(source, zoomedWidth, method))
					{
						mapEditingPanel.setImage(AwtBridge.toBufferedImage(scaled));
					}
				}
				else
				{
					// Zooming out (or 1:1): scale down first, then convert smaller result.
					try (Image scaled = ImageHelper.getInstance().scaleByWidth(mapEditingPanel.mapFromMapCreator, zoomedWidth, method))
					{
						mapEditingPanel.setImage(AwtBridge.toBufferedImage(scaled));
					}
				}
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
					if (zoomedWidth > mapEditingPanel.mapFromMapCreator.getWidth())
					{
						// Zooming in: convert smaller source first, then scale up.
						BufferedImage sourceBI = AwtBridge.toBufferedImage(mapEditingPanel.mapFromMapCreator);
						try (Image source = AwtBridge.wrapBufferedImage(sourceBI); Image scaled = ImageHelper.getInstance().scaleByWidth(source, zoomedWidth, method))
						{
							mapEditingPanel.setImage(AwtBridge.toBufferedImage(scaled));
						}
					}
					else
					{
						// Zooming out (or 1:1): scale down first, then convert smaller result.
						try (Image scaled = ImageHelper.getInstance().scaleByWidth(mapEditingPanel.mapFromMapCreator, zoomedWidth, method))
						{
							mapEditingPanel.setImage(AwtBridge.toBufferedImage(scaled));
						}
					}
				}
				else
				{
					if (mapEditingPanel.getImage() != null)
					{
						// Use wrapBufferedImage for the target so changes write back to the display BufferedImage.
						// fromBufferedImage would create a copy when using SkiaFactory, losing the changes.
						ImageHelper.getInstance().scaleInto(mapEditingPanel.mapFromMapCreator, AwtBridge.wrapBufferedImage(mapEditingPanel.getImage()), incrementalChangeArea);
					}
				}
			}

			if (scrollTo != null)
			{
				// For some reason I have to do a bunch of revalidation or
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
				final int additionalWidthToRemoveIDontKnowWhereItsComingFrom = 2;
				nortantis.geom.Dimension size = new nortantis.geom.Dimension(mapEditingScrollPane.getSize().width - additionalWidthToRemoveIDontKnowWhereItsComingFrom,
						mapEditingScrollPane.getSize().height - additionalWidthToRemoveIDontKnowWhereItsComingFrom);

				nortantis.geom.Dimension fitted = ImageHelper.getInstance().fitDimensionsWithinBoundingBox(size, mapEditingPanel.mapFromMapCreator.getWidth(),
						mapEditingPanel.mapFromMapCreator.getHeight());
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
		createSubMapMenuItem.setEnabled(!isDrawing);
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
				if (eEdit != null)
				{
					eEdit.riverLevel = 0;
				}
			}

			// Erase free icons
			edits.freeIcons.clear();

			// Erase roads.
			edits.roads.clear();

			undoer.setUndoPoint(UpdateType.Full, null);
			updater.createAndShowMapFull();
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
			loadSettingsAndEditsIntoThemeAndToolsPanels(getSettingsFromGUI(false), false, false);
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

			textSearchDialog.setLocation(parentLocation.x + parentSize.width / 2 - dialogSize.width / 2, parentLocation.y + parentSize.height - dialogSize.height - 18);

			textSearchDialog.setVisible(true);
		}
		else
		{
			textSearchDialog.requestFocusAndSelectAll();
		}
	}

	private void handleCreateSubMap()
	{
		updater.doIfMapIsReadyForInteractions(() -> new SubMapDialog(this).showStep1());
	}

	public boolean checkForUnsavedChanges()
	{
		if (lastSettingsLoadedOrSaved == null)
		{
			return false;
		}

		if (settingsHaveUnsavedChanges())
		{
			int n = JOptionPane.showConfirmDialog(this, Translation.get("mainWindow.settingsModified"), "", JOptionPane.YES_NO_CANCEL_OPTION);
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
				JOptionPane.showMessageDialog(null, e.getMessage(), Translation.get("mainWindow.unableToSaveSettings"), JOptionPane.ERROR_MESSAGE);
			}
			updateFrameTitle(false, true);
		}
	}

	public void saveSettingsAs(Component parent)
	{
		Path curPath = openSettingsFilePath == null ? FileSystemView.getFileSystemView().getDefaultDirectory().toPath() : openSettingsFilePath;
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
				Logger.printError("Error while saving settings to a new file:", e);
				JOptionPane.showMessageDialog(null, e.getMessage(), Translation.get("mainWindow.unableToSaveSettings"), JOptionPane.ERROR_MESSAGE);
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
			title = (showUnsavedChangesSymbol ? "✎ " : "") + FilenameUtils.getName(openSettingsFilePath.toString()) + " - " + frameTitleBase;
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
		loadSettingsAndEditsIntoThemeAndToolsPanels(settings, false, false);

		exportResolution = settings.resolution;
		imageExportPath = settings.imageExportPath;
		heightmapExportResolution = settings.heightmapResolution;
		heightmapExportPath = settings.heightmapExportPath;

		setPlaceholderImage(new String[] { Translation.get("mainWindow.drawingMap") });

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
			// might enable fields when loading settings, which will cause
			// fields to be enabled before the map is ready.
			enableOrDisableFieldsThatRequireMap(false, settings);
		}

		toolsPanel.resetZoomToDefault();

		defaultMapExportAction = settings.defaultMapExportAction;
		defaultHeightmapExportAction = settings.defaultHeightmapExportAction;

		updater.createAndShowMapFull();
		updateFrameTitle(false, true);
	}

	void loadSettingsAndEditsIntoThemeAndToolsPanels(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean refreshImagePreviews)
	{
		updater.setEnabled(false);
		undoer.setEnabled(false);
		customImagesPath = settings.customImagesPath;
		edits = settings.edits;
		themePanel.loadSettingsIntoGUI(settings, refreshImagePreviews);
		toolsPanel.loadSettingsIntoGUI(settings, isUndoRedoOrAutomaticChange, refreshImagePreviews);
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
		mapEditingPanel.setImage(AwtBridge.toBufferedImage(ImageHelper.getInstance().createPlaceholderImage(message, AwtBridge.fromAwtColor(SwingHelper.getTextColorForPlaceholderImages()))));

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

	void handleThemeChange(boolean refreshImagePreviews)
	{
		// This check is to filter out automatic changes caused by
		// loadSettingsIntoGUI.
		if (undoer.isEnabled())
		{
			// Allow editor tools to update based on changes in the themes
			// panel.
			toolsPanel.loadSettingsIntoGUI(getSettingsFromGUI(false), true, refreshImagePreviews);
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
		PlatformFactory.setInstance(new AwtFactory());

		Translation.initialize();

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
