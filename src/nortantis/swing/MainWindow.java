package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
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
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FilenameUtils;
import org.imgscalr.Scalr.Method;

import com.formdev.flatlaf.FlatDarkLaf;

import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.editor.EdgeEdit;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.AssetsPath;
import nortantis.util.ILoggerTarget;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class MainWindow extends JFrame implements ILoggerTarget
{
	private JTextArea txtConsoleOutput;
	private Path openSettingsFilePath;
	private boolean forceSaveAs;
	MapSettings lastSettingsLoadedOrSaved;
	boolean hasDrawnCurrentMapAtLeastOnce;
	static final String frameTitleBase = "Nortantis Fantasy Maps";
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
	private JMenu displayQualityMenu;
	private JRadioButtonMenuItem radioButton50Percent;
	private JRadioButtonMenuItem radioButton75Percent;
	private JRadioButtonMenuItem radioButton100Percent;
	private JRadioButtonMenuItem radioButton125Percent;
	private JRadioButtonMenuItem radioButton150Percent;
	ThemePanel themePanel;
	ToolsPanel toolsPanel;
	MapUpdater updater;
	private JCheckBoxMenuItem highlightLakesButton;
	private JCheckBoxMenuItem highlightRiversButton;
	private JScrollPane consoleOutputPane;
	double exportResolution;
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
	private JRadioButtonMenuItem[] displayQualityButtons;
	private JMenuItem nameGeneratorMenuItem;
	protected String customImagesPath;
	private JMenu fileMenu;
	private JMenuItem newMapWithSameThemeMenuItem;

	public MainWindow(String fileToOpen)
	{
		super(frameTitleBase);

		Logger.setLoggerTarget(this);

		createGUI();

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

		setIconImage(AwtFactory.unwrap(ImageHelper.read(Paths.get(AssetsPath.getInstallPath(), "internal/taskbar icon.png").toString())));
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
				// There's a bug in Windows where all of my swing components
				// disappear when you lock the screen and unlock it.
				// The is a fix that works most of the time.
				repaint();
			}
		});

		createMenuBar();

		undoer = new Undoer(this);

		themePanel = new ThemePanel(this);
		createMapEditingPanel();
		createMapUpdater();
		toolsPanel = new ToolsPanel(this, mapEditingPanel, updater);

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
				if (SwingUtilities.isLeftMouseButton(e))
				{
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMousePressedOnMap(e));
				}
				else if (SwingUtilities.isMiddleMouseButton(e))
				{
					mouseLocationForMiddleButtonDrag = e.getPoint();
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
				if (SwingUtilities.isLeftMouseButton(e))
				{
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseDraggedOnMap(e));
				}
				else if (SwingUtilities.isMiddleMouseButton(e))
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
				if (e.isControlDown())
				{
					MainWindow.this.handleMouseWheelChangingZoom(e);
				}
				else
				{
					e.getComponent().getParent().dispatchEvent(e);
				}
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
					updater.createAndShowMapIncrementalUsingCenters(null);
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
				if (settings.drawText)
				{
					if (toolsPanel.currentTool != null && !toolsPanel.currentTool.shouldShowTextWhenTextIsEnabled())
					{
						settings.drawText = false;
					}
				}
				return settings;
			}

			@Override
			protected void onFinishedDrawing(Image map, boolean anotherDrawIsQueued, int borderWidthAsDrawn,
					Rectangle incrementalChangeArea, List<String> warningMessages)
			{
				mapEditingPanel.mapFromMapCreator = AwtFactory.unwrap(map);
				mapEditingPanel.setBorderWidth(borderWidthAsDrawn);
				mapEditingPanel.setGraph(mapParts.graph);

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

				updateDisplayedMapFromGeneratedMap(false, incrementalChangeArea);

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
					JOptionPane.showMessageDialog(MainWindow.this, "<html>" + String.join("<br>", warningMessages) + "</html>",
							"Map Drew With Warnings", JOptionPane.WARNING_MESSAGE);
				}
			}

			@Override
			protected void onFailedToDraw()
			{
				showAsDrawing(false);
				mapEditingPanel.clearSelectedCenters();
				setPlaceholderImage(new String[] { "Map failed to draw due to an error.",
						"To retry, use " + fileMenu.getText() + " -> " + refreshMenuItem.getText() + "." });

				// In theory, enabling fields now could lead to the undoer not working quite right since edits might not have been created.
				// But leaving fields disabled makes the user unable to fix the error.
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

					if (MapSettings.isOldPropertiesFile(openSettingsFilePath.toString()))
					{
						JOptionPane.showMessageDialog(MainWindow.this, FilenameUtils.getName(openSettingsFilePath.toString())
								+ " is an older format '.properties' file. \nWhen you save, it will be converted to the newer format, a '"
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
					updater.doWhenMapIsReadyForInteractions(() ->
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
					updater.doWhenMapIsReadyForInteractions(() ->
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

		displayQualityMenu = new JMenu("Display Quality");
		viewMenu.add(displayQualityMenu);
		displayQualityMenu.setToolTipText(
				"Change the quality of the map displayed in the editor. Does not apply when exporting the map to an image. Higher values make the editor slower.");

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

		ActionListener resolutionListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				String text = ((JRadioButtonMenuItem) e.getSource()).getText();
				UserPreferences.getInstance().editorImageQuality = text;
				handleImageQualityChange(text);
			}
		};

		ButtonGroup resolutionButtonGroup = new ButtonGroup();

		radioButton50Percent = new JRadioButtonMenuItem("Very Low");
		radioButton50Percent.addActionListener(resolutionListener);
		displayQualityMenu.add(radioButton50Percent);
		resolutionButtonGroup.add(radioButton50Percent);

		radioButton75Percent = new JRadioButtonMenuItem("Low");
		radioButton75Percent.addActionListener(resolutionListener);
		displayQualityMenu.add(radioButton75Percent);
		resolutionButtonGroup.add(radioButton75Percent);

		radioButton100Percent = new JRadioButtonMenuItem("Medium");
		radioButton100Percent.addActionListener(resolutionListener);
		displayQualityMenu.add(radioButton100Percent);
		resolutionButtonGroup.add(radioButton100Percent);

		radioButton125Percent = new JRadioButtonMenuItem("High");
		radioButton125Percent.addActionListener(resolutionListener);
		displayQualityMenu.add(radioButton125Percent);
		resolutionButtonGroup.add(radioButton125Percent);

		radioButton150Percent = new JRadioButtonMenuItem("Very High");
		radioButton150Percent.addActionListener(resolutionListener);
		displayQualityMenu.add(radioButton150Percent);
		resolutionButtonGroup.add(radioButton150Percent);

		displayQualityButtons = new JRadioButtonMenuItem[] { radioButton50Percent, radioButton75Percent, radioButton100Percent,
				radioButton125Percent, radioButton150Percent };

		if (UserPreferences.getInstance().editorImageQuality != null && !UserPreferences.getInstance().editorImageQuality.equals(""))
		{
			boolean found = false;
			for (JRadioButtonMenuItem resolutionOption : displayQualityButtons)
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
		updateImageQualityScale(UserPreferences.getInstance().editorImageQuality);

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

		helpMenu = new JMenu("Help");
		menuBar.add(helpMenu);

		JMenuItem keyboardShortcutsItem = new JMenuItem("Keyboard Shortcuts");
		helpMenu.add(keyboardShortcutsItem);
		keyboardShortcutsItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				JOptionPane.showMessageDialog(MainWindow.this,
						"<html>Keyboard shortcuts for navigating the map:" + "<ul>" + "<li>Zoom: Ctrl + mouse wheel</li>"
								+ "<li>Pan: Hold mouse middle button and drag</li>" + "</ul>" + "</html>",
						"Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
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

	void handleImagesRefresh()
	{
		updater.setEnabled(false);
		undoer.setEnabled(false);
		ImageCache.clear();
		MapSettings settings = getSettingsFromGUI(false);
		themePanel.handleImagesRefresh(settings);
		// Tell Icons tool to refresh image previews
		toolsPanel.handleImagesRefresh(settings);
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

			updater.cancel();
			updater.dowWhenMapIsNotDrawing(() ->
			{
				loadSettingsIntoGUI(settings);
			});

			updateFrameTitle();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Error while opening '" + absolutePath + "': " + e.getMessage(), "Error While Opening Map",
					JOptionPane.ERROR_MESSAGE);
			Logger.printError("Unable to open '" + absolutePath + "' due to an error:", e);
		}
	}

	public void handleMouseWheelChangingZoom(MouseWheelEvent e)
	{
		updater.doIfMapIsReadyForInteractions(() ->
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
				updateDisplayedMapFromGeneratedMap(true, null);
			}
		});
	}

	public void updateDisplayedMapFromGeneratedMap(boolean updateScrollLocationIfZoomChanged, Rectangle incrementalChangeArea)
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

			toolsPanel.currentTool.onBeforeShowMap();
			mapEditingPanel.setZoom(zoom);
			mapEditingPanel.setResolution(displayQualityScale);
			Method method = zoom < 0.3 ? Method.QUALITY : Method.BALANCED;
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
					// It's important that this image scaling is done using the same method as the incremental case below
					// (when incrementalChangeArea != null), or at least close enough that people can't tell the difference.
					// The reason is that the incremental case will update pieces of the image created below.
					// I don't use ImageHelper.scaleInto for the full image case because it's 5x slower than the below
					// method, which uses ImgScalr.
					mapEditingPanel.setImage(AwtFactory
							.unwrap(ImageHelper.scaleByWidth(AwtFactory.wrap(mapEditingPanel.mapFromMapCreator), zoomedWidth, method)));
				}
				else
				{
					// These two images will be the same if the zoom and display quality are the same, in which case
					// ImageHelper.scaleByWidth called above returns the input image.
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
	}

	private double parsePercentage(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		return zoomPercent / 100.0;
	}

	/**
	 * Handles when zoom level changes in the display.
	 */
	public void handleImageQualityChange(String resolutionText)
	{
		updateImageQualityScale(resolutionText);

		ImageCache.clear();
		updater.createAndShowMapFull();
	}

	private void updateImageQualityScale(String imageQualityText)
	{
		if (imageQualityText.equals(radioButton50Percent.getText()))
		{
			displayQualityScale = 0.50;
		}
		else if (imageQualityText.equals(radioButton75Percent.getText()))
		{
			displayQualityScale = 0.75;
		}
		else if (imageQualityText.equals(radioButton100Percent.getText()))
		{
			displayQualityScale = 1.0;
		}
		else if (imageQualityText.equals(radioButton125Percent.getText()))
		{
			displayQualityScale = 1.25;
		}
		else if (imageQualityText.equals(radioButton150Percent.getText()))
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
			for (MapText text : edits.text)
			{
				text.value = "";
			}

			for (Center center : updater.mapParts.graph.centers)
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
		final MapSettings currentSettings = getSettingsFromGUI(false);

		// Debug code
		// try
		// {
		// currentSettings.writeToFile("currentSettings.json");
		// lastSettingsLoadedOrSaved.writeToFile("lastSettingsLoadedOrSaved.json");
		// }
		// catch (IOException e)
		// {
		// e.printStackTrace();
		// }

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
			updateFrameTitle();
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

			updateFrameTitle();
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

	private void updateFrameTitle()
	{
		String title = frameTitleBase;
		if (openSettingsFilePath != null)
		{
			title += " - " + FilenameUtils.getName(openSettingsFilePath.toString());
		}
		setTitle(title);
	}

	public void clearOpenSettingsFilePath()
	{
		openSettingsFilePath = null;
	}

	void loadSettingsIntoGUI(MapSettings settings)
	{
		boolean needsImagesRefresh = !Objects.equals(settings.customImagesPath, customImagesPath);
		hasDrawnCurrentMapAtLeastOnce = false;
		mapEditingPanel.clearAllSelectionsAndHighlights();

		updateLastSettingsLoadedOrSaved(settings);
		toolsPanel.resetToolsForNewMap();
		loadSettingsAndEditsIntoThemeAndToolsPanels(settings, false, needsImagesRefresh);

		updateFrameTitle();

		setPlaceholderImage(new String[] { "Drawing map..." });

		undoer.reset();

		if (needsImagesRefresh)
		{
			handleImagesRefresh();
		}

		if (settings.edits != null && settings.edits.isInitialized())
		{
			undoer.initialize(settings);
			enableOrDisableFieldsThatRequireMap(true, settings);
		}
		else
		{
			// Note - this call needs to come after everything that calls into loadSettingsAndEditsIntoThemeAndToolsPanels because the text
			// tool
			// might enable fields when when loading settings, which will cause fields to be enabled before the map is ready.
			enableOrDisableFieldsThatRequireMap(false, settings);
		}

		toolsPanel.resetZoomToDefault();
		updater.createAndShowMapFull();
	}

	void loadSettingsAndEditsIntoThemeAndToolsPanels(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean willDoImagesRefresh)
	{
		updater.setEnabled(false);
		undoer.setEnabled(false);
		exportResolution = settings.resolution;
		imageExportPath = settings.imageExportPath;
		heightmapExportResolution = settings.heightmapResolution;
		heightmapExportPath = settings.heightmapExportPath;
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
		mapEditingPanel.setImage(AwtFactory.unwrap(ImageHelper.createPlaceholderImage(message)));

		// Clear out the map from map creator so that causing the window to re-zoom while the placeholder image
		// is displayed doesn't show the previous map. This can happen when the zoom is fit to window, you create
		// a new map, then resize the window while the new map is drawing for the first time.
		mapEditingPanel.mapFromMapCreator = null;

		mapEditingPanel.repaint();
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
		try
		{
			// UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			UIManager.setLookAndFeel(new FlatDarkLaf());
		}
		catch (Exception e)
		{
			System.out.println("Error while setting look and feel: " + e.getMessage());
			e.printStackTrace();
		}

		// Tell drawing code to use AWT.
		PlatformFactory.setInstance(new AwtFactory());

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
