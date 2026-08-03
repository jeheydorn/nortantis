package nortantis.swing;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import nortantis.CancelledException;
import nortantis.DebugFlags;
import nortantis.GeneratedDimension;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.editor.*;
import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import nortantis.graph.voronoi.Center;
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
import java.awt.datatransfer.StringSelection;
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
import java.util.concurrent.locks.Lock;

@SuppressWarnings("serial")
public class MainWindow extends JFrame implements ILoggerTarget
{
	private JTextArea txtConsoleOutput;
	private Path openSettingsFilePath;
	private boolean forceSaveAs;
	MapSettings lastSettingsLoadedOrSaved;
	boolean hasDrawnCurrentMapAtLeastOnce;
	/**
	 * False until the first full draw after a map is loaded has completed. While false, a full draw that removes cities for landing on water
	 * does not warn the user: opening a map (or creating a sub-map, which warns separately) can legitimately have cities sitting on water, and
	 * that is not something the user just caused. Once true, a later full draw that removes cities for water (e.g. from changing the shore line
	 * style or the display quality) warns. Set in loadSettingsIntoGUI: normally false, but true when the loaded map is from an older version of
	 * Nortantis, so that the first draw warns about cities that sank because of rendering differences between that version and the current one.
	 */
	private boolean hasEstablishedCityOnWaterBaseline;
	/**
	 * True when the currently loaded map was saved in an older version of Nortantis than the current one and has not been drawn yet in the
	 * current version. Used to show a warning message that explains cities sinking into the water may be caused by version differences in how
	 * shores are drawn or water collision is detected. Set in loadSettingsIntoGUI and cleared by the first full draw that follows, since after
	 * that draw the map has already been rendered in the current version and version differences can no longer sink cities.
	 */
	private boolean loadedMapIsFromOlderVersion;
	/**
	 * The version the currently loaded map was saved in, used in the warning message shown when cities sink because the map is from an older
	 * version. Set in loadSettingsIntoGUI. Only meaningful when {@link #loadedMapIsFromOlderVersion} is true.
	 */
	private String loadedMapVersion;
	static final String frameTitleBase = "Nortantis";
	/**
	 * The bounds of this window the last time it was seen in a non-maximized state, used to store where to open the window next time.
	 * Maximized bounds are not stored because restoring them would leave the window covering the whole screen but not actually maximized.
	 */
	private IntRectangle lastNormalWindowBounds;
	/**
	 * The bounds to give a window that was opened maximized the first time it is un-maximized. A window opened maximized is opened at the
	 * size of a maximized window, so the platform has only ever seen it at that size and would otherwise un-maximize it to the size of the
	 * screen. Null once it has been used, and for a window that was not opened maximized, which the platform sizes correctly on its own.
	 */
	private IntRectangle boundsToUnMaximizeTo;
	private javax.swing.Timer normalWindowBoundsTimer;
	/**
	 * How long the window must go without moving or resizing before the bounds it has count as a size the window is actually sitting at.
	 */
	private static final int windowResizeSettleDelay = 250;
	/**
	 * A floor on how small fitting the window to a monitor is allowed to make it. It exists only to keep a monitor that reports unusable
	 * dimensions from opening the window too small to use. It does not constrain sizes the user chose by resizing the window.
	 */
	private static final IntDimension minimumSizeWhenFittingToScreen = new IntDimension(400, 300);
	/**
	 * The size the content pane is given when the window has no stored size, and when the user resets the window size.
	 */
	private static final IntDimension defaultContentPaneSize = new IntDimension(1400, 780);
	private JSplitPane themePanelSplitPane;
	private JSplitPane toolsPanelSplitPane;
	private JSplitPane consoleOutputSplitPane;
	/**
	 * A divider location far below the bottom of the window, which hides the console output. The console output is hidden by default and is
	 * only seen when the user drags it open.
	 */
	private static final int consoleOutputHiddenDividerLocation = 9999999;
	/**
	 * The window created by main(), if it has been created yet. On macOS, a file opened via Finder (either at launch or while the app is
	 * already running) is delivered as an Apple Event through the OpenFilesHandler registered in main(), rather than as a command-line
	 * argument. That handler needs this to route the file into an already-open window; before the window exists, it stashes the file in
	 * pendingFileToOpenFromAppleEvent instead.
	 */
	private static volatile MainWindow instance;
	private static volatile String pendingFileToOpenFromAppleEvent;
	public MapEdits edits;

	JScrollPane mapEditingScrollPane;
	private MapCanvasOverlay mapCanvasOverlay;
	// Controls how large 100% zoom is, in pixels: the map's longer side is displayed at this many pixels at 100%. Using the longer side
	// (rather than always the width) keeps zoom well-behaved for extreme aspect ratios; otherwise a very tall, narrow map would render
	// enormously oversized at 100% because its height is many times its width.
	final double oneHundredPercentMapLongestSide = 4096;
	public MapEditingPanel mapEditingPanel;
	JMenuItem undoButton;
	JMenuItem redoButton;
	private JMenuItem clearEntireMapButton;
	public Undoer undoer;
	// The zoom level currently reflected by mapEditingPanel's displayed image. Only updated when a rescaled image is actually
	// committed to the panel (see commitBackgroundRescale), so it always matches what's on screen, even while a background rescale
	// for a newer zoom is still in flight.
	double zoom;
	double displayQualityScale;
	// The resolution the raw map image currently on screen (mapFromMapCreator) was actually rendered at. Updated only when a new full-draw
	// map is installed - NOT by zoom rescales, which reuse the existing raw map and would otherwise make the panel's resolution report the
	// target quality before the image at that quality exists. Overlays that scale RI geometry by displayQualityScale (the river/road
	// highlights) only line up with the on-screen image while this equals displayQualityScale; during a display-quality change it lags until
	// the new full draw completes.
	double displayedMapResolution;
	// Single background thread that produces full-map rescaled images for display (used whenever the display needs a full rescale
	// rather than a fast in-place patch of the existing displayed image), so that a slow QUALITY downscale never blocks the EDT.
	// Coalescing/superseding is handled with displayScaleGeneration: each request captures the generation counter at submit time, and
	// bails out (before and after the potentially slow scale) if a newer request has since been submitted, so bursts of requests don't
	// back up doing stale work, and a result is only committed to the display if it's still the latest request.
	private final java.util.concurrent.ExecutorService displayScaleExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable ->
	{
		Thread thread = new Thread(runnable, "display-scale");
		thread.setDaemon(true);
		return thread;
	});
	private final java.util.concurrent.atomic.AtomicLong displayScaleGeneration = new java.util.concurrent.atomic.AtomicLong();
	// True when the display update for the draw that just finished was handed off to a background rescale (rather than patched
	// synchronously), so its post-draw actions must wait until the rescale commits instead of running immediately.
	private boolean lastDisplayUpdateWasAsync;
	// Post-draw actions deferred until the next display commit (finishDisplayUpdate), because their draw's display update was
	// asynchronous. Runs the orange processing-area highlights' removal in sync with the map visibly updating.
	private final List<Runnable> actionsToRunOnNextDisplayUpdate = new ArrayList<>();
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
	// True while the current tool has an open press (mouse is down and the tool was given handleMousePressedOnMap but not yet a
	// matching release). Panning with Shift mid-drag suspends the interaction rather than finalizing it, so this stays true through
	// the pan and the tool's release is deferred to the real mouse-up.
	private boolean toolInteractionInProgress;
	// Fractional zoom-scroll accumulator so a touchpad's high-frequency, small-delta wheel events don't advance a full zoom step per
	// event. Whole units are consumed as zoom steps and the remainder is carried to the next event.
	private double accumulatedZoomScroll;
	private JMenu helpMenu;
	private JMenuItem mapInfoMenuItem;
	private JMenuItem refreshMenuItem;
	private JMenuItem customImagesMenuItem;
	private JMenu toolsMenu;
	private JMenuItem nameGeneratorMenuItem;
	protected String customImagesPath;
	private JMenu fileMenu;
	private JMenuItem newRandomMapMenuItem;
	private JMenuItem loadSettingsMenuItem;
	private JMenuItem newMapWithSameThemeMenuItem;
	private JMenuItem createSubMapMenuItem;
	private JMenuItem searchTextMenuItem;
	private TextSearchDialog textSearchDialog;
	private JMenu highlightIconsInArtPackMenu;
	private List<JCheckBoxMenuItem> artPacksToHighlight;

	public MainWindow(String fileToOpen) throws Exception
	{
		super(frameTitleBase);

		instance = this;
		Logger.setLoggerTarget(this);

		try
		{
			createGUI();
		}
		catch (Exception ex)
		{
			try
			{
				SwingHelper.showMessageDialog(null, "Unable to create GUI because of error: " + ex.getMessage() + "\nVersion: " + MapSettings.currentVersion + "\nOS Name: "
						+ System.getProperty("os.name") + "\nStack trace: " + ExceptionUtils.getStackTrace(ex), "Error", JOptionPane.ERROR_MESSAGE);
			}
			catch (Exception inner)
			{
				Logger.printError("Error while trying to log an error at startup: " + inner.getMessage(), inner);
			}
			throw ex;
		}

		// Start in the no-map state (fields locked). If a map was passed on the command line, open it after the window is shown, via
		// invokeLater, so a missing-art-pack prompt appears over the window instead of before it. The startup screen is shown while there is
		// no map, and is restored if opening the command-line map is cancelled or fails.
		enableOrDisableFieldsThatRequireMap(false, null, false);

		boolean hasCommandLineMap = fileToOpen != null && !fileToOpen.isEmpty() && fileToOpen.endsWith(MapSettings.fileExtensionWithDot) && new File(fileToOpen).exists();
		if (hasCommandLineMap)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (!openMap(new File(fileToOpen).getAbsolutePath()))
				{
					showStartupScreen();
				}
			});
		}
		else
		{
			showStartupScreen();
		}

		String preferencesLoadError = UserPreferences.getInstance().getLoadErrorMessage();
		if (preferencesLoadError != null)
		{
			SwingHelper.showMessageDialog(this, preferencesLoadError, "Error", JOptionPane.ERROR_MESSAGE);
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

							SwingHelper.showMessageDialog(MainWindow.this, messagePanel, Translation.get("mainWindow.updateAvailable"), JOptionPane.INFORMATION_MESSAGE);
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

	/**
	 * Snapshot of every menu item's enabled state captured when the menu bar was locked, used to restore each item exactly on unlock. Null
	 * when the menu bar is not locked.
	 */
	private Map<JMenuItem, Boolean> menuItemEnabledStatesBeforeLock;

	/**
	 * Locks or unlocks the entire menu bar (File, Edit, View, Tools, Help). Used to block all menu commands while the sub-map dialog is open,
	 * since its first step is non-modal and would otherwise leave commands like File -&gt; Open reachable.
	 * <p>
	 * Locking disables the individual menu items rather than the top-level {@link JMenu}s. Toggling a top-level menu's enabled state on the
	 * macOS screen menu bar and back leaves its items stuck disabled (a native peer refresh bug), so we avoid touching the top-level menus.
	 * Disabling the items also suppresses their keyboard accelerators, which fire from the focused-window input map regardless of the parent
	 * menu's state. The prior enabled state of every item is saved on lock and restored on unlock.
	 * <p>
	 * When unlocking, call this before any logic that recomputes item states (e.g. {@code enableOrDisableFieldsThatRequireMap}), so the
	 * restored snapshot doesn't overwrite the freshly computed states.
	 */
	void setMenuBarEnabled(boolean enabled)
	{
		if (!enabled)
		{
			// Locking while already locked would snapshot the current (all-disabled) states and later restore them as disabled, so treat a
			// repeat lock as a no-op and keep the original snapshot. This happens when going Back from step 2 to step 1 of the sub-map dialog,
			// which re-enters the lock.
			if (menuItemEnabledStatesBeforeLock != null)
			{
				return;
			}
			menuItemEnabledStatesBeforeLock = new LinkedHashMap<>();
			for (JMenu menu : Arrays.asList(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu))
			{
				disableMenuItemsRecursively(menu, menuItemEnabledStatesBeforeLock);
			}
		}
		else if (menuItemEnabledStatesBeforeLock != null)
		{
			for (Map.Entry<JMenuItem, Boolean> entry : menuItemEnabledStatesBeforeLock.entrySet())
			{
				entry.getKey().setEnabled(entry.getValue());
			}
			menuItemEnabledStatesBeforeLock = null;
		}
	}

	private static void disableMenuItemsRecursively(JMenu menu, Map<JMenuItem, Boolean> savedStates)
	{
		for (int i = 0; i < menu.getItemCount(); i++)
		{
			JMenuItem item = menu.getItem(i);
			if (item == null)
			{
				// Separators return null.
				continue;
			}
			savedStates.put(item, item.isEnabled());
			item.setEnabled(false);
			if (item instanceof JMenu)
			{
				disableMenuItemsRecursively((JMenu) item, savedStates);
			}
		}
	}

	void enableOrDisableFieldsThatRequireMap(boolean enable, MapSettings settings, boolean forceEnableZoom)
	{
		newMapWithSameThemeMenuItem.setEnabled(enable);
		createSubMapMenuItem.setEnabled(enable && hasDrawnCurrentMapAtLeastOnce);
		saveMenuItem.setEnabled(enable);
		saveAsMenItem.setEnabled(enable);
		exportMapAsImageMenuItem.setEnabled(enable);
		exportHeightmapMenuItem.setEnabled(enable);
		mapInfoMenuItem.setEnabled(enable);

		if (!enable || undoer == null)
		{
			undoButton.setEnabled(false);
			redoButton.setEnabled(false);
		}
		else
		{
			undoer.updateUndoRedoEnabled();
		}
		clearEntireMapButton.setEnabled(enable && hasDrawnCurrentMapAtLeastOnce);
		customImagesMenuItem.setEnabled(enable);

		nameGeneratorMenuItem.setEnabled(enable);
		searchTextMenuItem.setEnabled(enable);

		highlightLakesButton.setEnabled(enable);
		highlightRiversButton.setEnabled(enable);

		refreshMenuItem.setEnabled(enable);

		// Highlighting icons by art pack only makes sense once a map is loaded, and behaves strangely otherwise.
		highlightIconsInArtPackMenu.setEnabled(enable);

		themePanel.enableOrDisableEverything(enable);
		toolsPanel.enableOrDisableEverything(enable, settings, forceEnableZoom);
	}

	private void createGUI()
	{
		getContentPane().setPreferredSize(new Dimension(defaultContentPaneSize.width, defaultContentPaneSize.height));
		getContentPane().setLayout(new BorderLayout());

		String iconFileName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "taskbar icon.png" : "taskbar icon medium size.png";
		java.awt.image.BufferedImage appIcon = AwtBridge.toBufferedImage(Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal/" + iconFileName).toString()));
		setIconImage(appIcon);
		// Needed for Mac, at least when running from source.
		if (java.awt.Taskbar.isTaskbarSupported())
		{
			java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
			if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE))
			{
				taskbar.setIconImage(appIcon);
			}
		}

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
						storeWindowPlacementInPreferences();
						UserPreferences.getInstance().save();
						dispose();
						System.exit(0);
					}
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					SwingHelper.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					Logger.printError("Error while closing:", ex);
				}
			}

			@Override
			public void windowActivated(WindowEvent e)
			{
			}
		});

		addWindowStateListener(event -> restoreBoundsWhenUnMaximized(event));

		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				restartNormalWindowBoundsTimer();
			}

			@Override
			public void componentMoved(ComponentEvent e)
			{
				restartNormalWindowBoundsTimer();
			}
		});

		createMenuBar();

		undoer = new Undoer(this);

		themePanel = new ThemePanel(this);
		createMapEditingPanel();
		createMapUpdater();
		toolsPanel = new ToolsPanel(this, updater);
		int toolsPanelWidth = SwingHelper.clampSidePanelWidthToMinimum(UserPreferences.getInstance().toolsPanelWidth);
		toolsPanel.setPreferredSize(new Dimension(toolsPanelWidth, toolsPanel.getPreferredSize().height));
		toolsPanel.setMinimumSize(new Dimension(SwingHelper.sidePanelMinimumWidth, toolsPanel.getMinimumSize().height));

		createConsoleOutput();

		consoleOutputSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, themePanel, consoleOutputPane);
		consoleOutputSplitPane.setDividerLocation(consoleOutputHiddenDividerLocation);
		consoleOutputSplitPane.setResizeWeight(1.0);

		themePanelSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, consoleOutputSplitPane, mapCanvasOverlay);
		themePanelSplitPane.setOneTouchExpandable(true);
		toolsPanelSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, themePanelSplitPane, toolsPanel);
		toolsPanelSplitPane.setResizeWeight(1.0);
		toolsPanelSplitPane.setOneTouchExpandable(true);

		getContentPane().add(toolsPanelSplitPane, BorderLayout.CENTER);

		registerZoomKeyboardShortcuts();

		pack();

		restoreWindowPlacement();
	}

	/**
	 * Sizes and positions the window to where it was when it was last closed, and gives the side panels the widths they were last closed at.
	 * When there is no usable stored position, the operating system chooses the position. When the monitor the window was last on is no longer
	 * connected, the window is centered on the default monitor. Either way the window is kept entirely within the usable area of the monitor it
	 * lands on, so that it is never partly off screen even if the monitors have gotten smaller or fewer since the position was stored.
	 */
	private void restoreWindowPlacement()
	{
		IntRectangle normalBounds = null;
		IntRectangle savedBounds = UserPreferences.getInstance().windowBounds;
		if (savedBounds == null || savedBounds.isEmpty())
		{
			// Shrink the packed size if it is larger than the screen. Calling setSize rather than setBounds leaves the location for the OS to
			// choose, which cascades new windows rather than always using the upper left corner of the screen.
			IntDimension size = fitSizeToScreen(new IntDimension(getWidth(), getHeight()), getDefaultScreen());
			setSize(size.width, size.height);
			setLocationByPlatform(true);
		}
		else
		{
			GraphicsConfiguration screen = findScreenForBounds(savedBounds);
			normalBounds = screen == null ? centerBoundsOnDefaultScreen(savedBounds.size()) : fitBoundsToScreen(savedBounds, screen);
			setBounds(normalBounds.x, normalBounds.y, normalBounds.width, normalBounds.height);
			lastNormalWindowBounds = normalBounds;
		}

		if (UserPreferences.getInstance().isWindowMaximized)
		{
			if (normalBounds == null)
			{
				// There were no stored bounds, so the size the window packed to, centered, is the closest thing to a size it was last seen at.
				normalBounds = centerBoundsOnDefaultScreen(new IntDimension(getWidth(), getHeight()));
				lastNormalWindowBounds = normalBounds;
			}

			// The platform only ever sees this window at the size of a maximized window, so it has no size to un-maximize it to other than
			// the size of the screen.
			boundsToUnMaximizeTo = normalBounds;

			// Sized to the maximized area before being shown, so that the window is never drawn at another size while waiting for the window
			// manager to maximize it. The bounds are set before the state, since setting bounds afterwards takes the maximized state away.
			IntRectangle maximizedArea = getUsableScreenArea(getScreenWindowIsOn());
			setBounds(maximizedArea.x, maximizedArea.y, maximizedArea.width, maximizedArea.height);
			setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
		}

		// The window was laid out at the size it was packed at, which can be too narrow to hold both side panels at their stored widths. The
		// layout done at that size squeezes them, and giving the window its real size afterwards does not undo it, since a split pane gives
		// the width it gains to the side the map is on.
		validate();
		applyStoredSidePanelWidths();
	}

	/**
	 * The monitor this window is on, which is the one its bounds overlap the most. Falls back to the monitor the window reports being on, and
	 * then to the default monitor, when the window does not overlap any monitor enough to tell.
	 */
	private GraphicsConfiguration getScreenWindowIsOn()
	{
		GraphicsConfiguration screen = findScreenForBounds(new IntRectangle(getX(), getY(), getWidth(), getHeight()));
		if (screen != null)
		{
			return screen;
		}

		screen = getGraphicsConfiguration();
		return screen == null ? getDefaultScreen() : screen;
	}

	/**
	 * Gives a window that was opened maximized the bounds it was last un-maximized at, the first time it is un-maximized. Does nothing
	 * afterwards, leaving the platform to remember the bounds itself once it has seen the window at them.
	 */
	private void restoreBoundsWhenUnMaximized(WindowEvent event)
	{
		boolean wasMaximized = (event.getOldState() & Frame.MAXIMIZED_BOTH) != 0;
		boolean isNowMaximized = (event.getNewState() & Frame.MAXIMIZED_BOTH) != 0;
		if (boundsToUnMaximizeTo == null || !wasMaximized || isNowMaximized)
		{
			return;
		}

		IntRectangle bounds = boundsToUnMaximizeTo;
		boundsToUnMaximizeTo = null;
		setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
	}

	/**
	 * Gives the side panels the widths stored in preferences by moving the dividers, which keep whatever they were last laid out or dragged
	 * to and so do not follow the panels' preferred widths on their own.
	 */
	private void applyStoredSidePanelWidths()
	{
		// The tools panel divider is moved first because it decides the width the theme panel's split pane then divides. That layout is done
		// before the theme panel's divider is moved, since otherwise the theme panel's divider would be limited by the width its split pane
		// had while the window was still smaller. The split pane is marked invalid first because an already valid one lays out nothing.
		setToolsPanelWidth(SwingHelper.clampSidePanelWidthToMinimum(UserPreferences.getInstance().toolsPanelWidth));
		toolsPanelSplitPane.invalidate();
		toolsPanelSplitPane.validate();
		setThemePanelWidth(SwingHelper.clampSidePanelWidthToMinimum(UserPreferences.getInstance().themePanelWidth));
	}

	private void setThemePanelWidth(int width)
	{
		themePanelSplitPane.setDividerLocation(width);
	}

	private void setToolsPanelWidth(int width)
	{
		// The tools panel is the right side of its split pane, so where its divider goes is measured from the split pane's current width.
		toolsPanelSplitPane.setDividerLocation(toolsPanelSplitPane.getWidth() - width - toolsPanelSplitPane.getDividerSize());
	}

	/**
	 * Restores the window to its default size, the side panels to their default widths, which are the narrowest widths that fit their
	 * contents in the current language, and the console output to hidden. The window is left where it is unless part of it would then be off
	 * screen.
	 */
	private void resetWindowLayout()
	{
		// Dropped before un-maximizing, since this sets the bounds the window is being reset to and the un-maximize would otherwise put the
		// window back to the bounds it was opened at afterwards.
		boundsToUnMaximizeTo = null;
		setExtendedState(getExtendedState() & ~Frame.MAXIMIZED_BOTH);

		getContentPane().setPreferredSize(new Dimension(defaultContentPaneSize.width, defaultContentPaneSize.height));
		pack();

		IntRectangle bounds = new IntRectangle(getX(), getY(), getWidth(), getHeight());
		GraphicsConfiguration screen = findScreenForBounds(bounds);
		IntRectangle boundsToUse = screen == null ? centerBoundsOnDefaultScreen(bounds.size()) : fitBoundsToScreen(bounds, screen);
		setBounds(boundsToUse.x, boundsToUse.y, boundsToUse.width, boundsToUse.height);

		// Lay out at the new size before moving the dividers, since where the tools panel divider goes depends on the split pane's width.
		validate();

		resetSidePanelWidths();
		consoleOutputSplitPane.setDividerLocation(consoleOutputHiddenDividerLocation);

		getContentPane().revalidate();
		getContentPane().repaint();
	}

	private void resetSidePanelWidths()
	{
		themePanel.setPreferredSize(new Dimension(SwingHelper.sidePanelMinimumWidth, themePanel.getPreferredSize().height));
		toolsPanel.setPreferredSize(new Dimension(SwingHelper.sidePanelMinimumWidth, toolsPanel.getPreferredSize().height));

		// The dividers keep whatever the user last dragged them to, so they must be moved explicitly rather than only through preferred sizes.
		setThemePanelWidth(SwingHelper.sidePanelMinimumWidth);
		setToolsPanelWidth(SwingHelper.sidePanelMinimumWidth);
	}

	/**
	 * Returns the connected monitor that the given window bounds overlap the most, or null if they do not overlap any monitor enough for the
	 * window to be usable, meaning the user can see it and grab its title bar.
	 */
	private static GraphicsConfiguration findScreenForBounds(IntRectangle bounds)
	{
		final int minimumVisibleWidth = 200;
		final int minimumVisibleHeight = 60;

		GraphicsConfiguration result = null;
		long largestOverlapArea = 0;
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			GraphicsConfiguration screen = device.getDefaultConfiguration();
			IntRectangle intersection = bounds.findIntersection(toIntRectangle(screen.getBounds()));
			if (intersection == null || intersection.width < minimumVisibleWidth || intersection.height < minimumVisibleHeight)
			{
				continue;
			}

			long overlapArea = (long) intersection.width * intersection.height;
			if (overlapArea > largestOverlapArea)
			{
				largestOverlapArea = overlapArea;
				result = screen;
			}
		}
		return result;
	}

	/**
	 * Returns the part of the given monitor that windows can occupy, which excludes space reserved for things like the task bar and the Mac
	 * menu bar. Falls back to the monitor's full bounds when the reported insets leave less than half of the monitor usable, since some
	 * window managers report insets that cover most or all of a monitor.
	 */
	private static IntRectangle getUsableScreenArea(GraphicsConfiguration screen)
	{
		IntRectangle screenBounds = toIntRectangle(screen.getBounds());
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
		IntRectangle usableArea = new IntRectangle(screenBounds.x + insets.left, screenBounds.y + insets.top, screenBounds.width - insets.left - insets.right, screenBounds.height - insets.top - insets.bottom);

		// Intersecting with the monitor guards against negative insets, which would otherwise report area beyond the edge of the monitor as
		// usable.
		usableArea = usableArea.findIntersection(screenBounds);
		if (usableArea == null || usableArea.width < screenBounds.width / 2 || usableArea.height < screenBounds.height / 2)
		{
			return screenBounds;
		}
		return usableArea;
	}

	/**
	 * Shrinks the given window size as needed to make it fit within the usable area of the given monitor, leaving it alone when it already
	 * fits. The result is never smaller than {@link #minimumSizeWhenFittingToScreen} unless the given size already was, so that a monitor
	 * reporting unusable dimensions cannot shrink the window to nothing, while a small size the user deliberately chose is kept.
	 */
	private static IntDimension fitSizeToScreen(IntDimension size, GraphicsConfiguration screen)
	{
		IntRectangle usableArea = getUsableScreenArea(screen);
		return new IntDimension(Math.min(size.width, Math.max(usableArea.width, minimumSizeWhenFittingToScreen.width)), Math.min(size.height, Math.max(usableArea.height, minimumSizeWhenFittingToScreen.height)));
	}

	/**
	 * Shrinks and moves the given window bounds as needed to make them fit entirely within the usable area of the given monitor, leaving them
	 * alone when they already fit.
	 */
	private static IntRectangle fitBoundsToScreen(IntRectangle bounds, GraphicsConfiguration screen)
	{
		IntRectangle usableArea = getUsableScreenArea(screen);
		IntDimension size = fitSizeToScreen(bounds.size(), screen);
		int x = clamp(bounds.x, usableArea.x, usableArea.x + usableArea.width - size.width);
		int y = clamp(bounds.y, usableArea.y, usableArea.y + usableArea.height - size.height);
		return new IntRectangle(x, y, size.width, size.height);
	}

	/**
	 * Moves the given value into the range [minimum, maximum], returning the minimum when the range is empty.
	 */
	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
	}

	private static IntRectangle centerBoundsOnDefaultScreen(IntDimension sizeToCenter)
	{
		IntRectangle usableArea = getUsableScreenArea(getDefaultScreen());
		IntDimension size = fitSizeToScreen(sizeToCenter, getDefaultScreen());
		return new IntRectangle(usableArea.x + (usableArea.width - size.width) / 2, usableArea.y + (usableArea.height - size.height) / 2, size.width, size.height);
	}

	private static GraphicsConfiguration getDefaultScreen()
	{
		return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
	}

	private static IntRectangle toIntRectangle(java.awt.Rectangle rectangle)
	{
		return new IntRectangle(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
	}

	/**
	 * Waits for the window to stop changing before its bounds are recorded. The window's bounds change before the platform reports it as
	 * maximized, so bounds read while it is still becoming maximized are neither the maximized nor the normal size.
	 */
	private void restartNormalWindowBoundsTimer()
	{
		if (normalWindowBoundsTimer == null)
		{
			normalWindowBoundsTimer = new javax.swing.Timer(windowResizeSettleDelay, e -> updateLastNormalWindowBounds());
			normalWindowBoundsTimer.setRepeats(false);
		}
		normalWindowBoundsTimer.restart();
	}

	private void updateLastNormalWindowBounds()
	{
		if (!isMaximized())
		{
			java.awt.Rectangle bounds = getBounds();
			lastNormalWindowBounds = new IntRectangle(bounds.x, bounds.y, bounds.width, bounds.height);
		}
	}

	private boolean isMaximized()
	{
		return (getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
	}

	private void storeWindowPlacementInPreferences()
	{
		// Catch a size change made in the moment before closing, which the wait for the window to settle would otherwise miss.
		updateLastNormalWindowBounds();

		UserPreferences.getInstance().isWindowMaximized = isMaximized();
		if (lastNormalWindowBounds != null)
		{
			UserPreferences.getInstance().windowBounds = lastNormalWindowBounds;
		}
	}

	/**
	 * Registers the command-key zoom shortcuts (Ctrl on Windows/Linux, Cmd on Mac). Both the main-row and numpad plus/minus keys are
	 * bound, and '=' is treated as zoom-in since '+' is Shift+'=' on most keyboards.
	 */
	private void registerZoomKeyboardShortcuts()
	{
		JComponent rootPane = getRootPane();
		InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = rootPane.getActionMap();
		int commandMask = SwingHelper.getMenuShortcutKeyMask();

		Action zoomInAction = new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				changeZoomByOffset(1);
			}
		};
		Action zoomOutAction = new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				changeZoomByOffset(-1);
			}
		};

		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, commandMask), "zoomIn");
		// '+' is Shift+'=' on most layouts, so accept the shifted form too.
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, commandMask | InputEvent.SHIFT_DOWN_MASK), "zoomIn");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, commandMask), "zoomIn");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, commandMask), "zoomIn");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, commandMask), "zoomOut");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, commandMask), "zoomOut");
		actionMap.put("zoomIn", zoomInAction);
		actionMap.put("zoomOut", zoomOutAction);
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
		mapEditingPanel.setHoverHighlightsSuppressedSupplier(this::areHoverHighlightsSuppressed);

		mapEditingPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
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
					toolInteractionInProgress = true;
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMousePressedOnMap(e));
				}
				else if (SwingUtilities.isRightMouseButton(e))
				{
					// Right-press goes to a dedicated handler so drawing/erase modes don't accidentally
					// trigger on right-click. Tools opt in to right-click gestures (e.g. removing the last
					// freehand control point) or, eventually, context menus.
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseRightPressedOnMap(e));
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (mapEditingPanel.isSelectionBoxActive() && SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
				// End any pan in progress.
				mouseLocationForMiddleButtonDrag = null;
				if (toolInteractionInProgress && SwingUtilities.isLeftMouseButton(e))
				{
					// Always finalize an open tool interaction, even if Shift is still held from a mid-drag pan. Otherwise the tool
					// would never receive its release and would be left mid-operation (e.g. still dragging a control point).
					toolInteractionInProgress = false;
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
				// The drag switches live between panning and interacting with the tool based on the Shift key (or middle button), so the
				// user can pan mid-gesture without releasing the mouse. Pressing Shift SUSPENDS an in-progress tool interaction (it is
				// not finalized - the tool's release is deferred to the real mouse-up); releasing Shift RESUMES it. mouseLocationFor-
				// MiddleButtonDrag being non-null means a pan is currently in progress.
				boolean isPanning = mouseLocationForMiddleButtonDrag != null;
				boolean shouldPan = SwingUtilities.isMiddleMouseButton(e) || (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown());

				if (shouldPan)
				{
					if (isPanning)
					{
						int deltaX = mouseLocationForMiddleButtonDrag.x - e.getX();
						int deltaY = mouseLocationForMiddleButtonDrag.y - e.getY();
						mapEditingScrollPane.getVerticalScrollBar().setValue(mapEditingScrollPane.getVerticalScrollBar().getValue() + deltaY);
						mapEditingScrollPane.getHorizontalScrollBar().setValue(mapEditingScrollPane.getHorizontalScrollBar().getValue() + deltaX);
					}
					else
					{
						// Start panning. Any open tool interaction is left suspended, not finalized.
						mouseLocationForMiddleButtonDrag = e.getPoint();
					}
				}
				else if (SwingUtilities.isLeftMouseButton(e))
				{
					if (isPanning)
					{
						// Shift was released partway through a pan.
						mouseLocationForMiddleButtonDrag = null;
						if (toolInteractionInProgress)
						{
							// Resume the suspended tool interaction at the current point.
							updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseDraggedOnMap(e));
						}
						else
						{
							// The gesture began as a pan (Shift held at press), so there is no interaction to resume. Begin one now.
							toolInteractionInProgress = true;
							updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMousePressedOnMap(e));
						}
					}
					else
					{
						updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseDraggedOnMap(e));
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
				if (!mapEditingPanel.isSelectionBoxActive())
				{
					updater.doIfMapIsReadyForInteractions(() -> toolsPanel.currentTool.handleMouseExitedMap(e));
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

		mapEditingPanel.addMouseWheelListener(new MouseWheelListener()
		{
			@Override
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				if (e.isShiftDown())
				{
					// A sideways two-finger trackpad swipe arrives as a Shift-modified wheel event. Ignore it so the map doesn't
					// zoom on horizontal scrolling; the map zooms on vertical scrolling only.
					return;
				}
				MainWindow.this.handleMouseWheelChangingZoom(e);
			}

		});

		mapEditingScrollPane = new JScrollPane(mapEditingPanel);
		mapEditingScrollPane.setMinimumSize(new Dimension(500, themePanel.getMinimumSize().height));
		mapCanvasOverlay = new MapCanvasOverlay(mapEditingScrollPane);

		mapEditingScrollPane.addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent componentEvent)
			{
				updateZoomOptionsBasedOnWindowSize();
				if (ToolsPanel.fitToWindowZoomLevel.equals(toolsPanel.getZoomString()))
				{
					// A resize is a zoom-only change; the border padding is unchanged, so pass the current value through.
					updateDisplayedMapFromGeneratedMap(true, null, true, mapEditingPanel.getBorderPadding());
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
			protected void onFinishedDrawingFull(Image map, double mapResolution, boolean anotherDrawIsQueued, int borderPaddingAsDrawn, List<String> warningMessages,
					List<nortantis.IconDrawer.CityIconRemovedForWater> citiesRemovedForWater, boolean wasTriggeredByUndoRedo)
			{
				if (mapEditingPanel.mapFromMapCreator != null && mapEditingPanel.mapFromMapCreator != map)
				{
					mapEditingPanel.mapFromMapCreator.close();
				}
				mapEditingPanel.mapFromMapCreator = map;
				// Record the resolution THIS map was actually drawn at, not the live target display quality: when quality changes are queued or
				// coalesced, a finishing draw can be at an earlier resolution than the current target, and using the target here would tell the
				// panel the on-screen image is a resolution it isn't - misplacing every resolution-scaled overlay for the whole next redraw. The
				// mouse-hover highlights are suppressed while this differs from the target (see areHoverHighlightsSuppressed), read live.
				displayedMapResolution = mapResolution;
				onFinishedDrawingCommon(anotherDrawIsQueued, borderPaddingAsDrawn, null, warningMessages);
				warnIfCitiesWereRemovedForWater(citiesRemovedForWater, wasTriggeredByUndoRedo);
			}

			@Override
			protected void onFinishedDrawingIncremental(boolean anotherDrawIsQueued, int borderPaddingAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
				// Map was already updated in-place by MapCreator on background thread.
				// Just update the zoomed display for the changed region.
				onFinishedDrawingCommon(anotherDrawIsQueued, borderPaddingAsDrawn, incrementalChangeArea, warningMessages);
			}

			@Override
			protected void runPostDrawActions(List<Runnable> postRuns)
			{
				for (Runnable postRun : postRuns)
				{
					if (runsAfterMapDisplayed(postRun) && lastDisplayUpdateWasAsync)
					{
						// A display-tied action whose map is still being rescaled on a background thread. Defer it until the rescale
						// commits (finishDisplayUpdate), so overlays tied to this draw - like the orange processing-area highlights
						// shown while erasing an icon or text - aren't removed before the object visibly disappears.
						actionsToRunOnNextDisplayUpdate.add(postRun);
					}
					else
					{
						// Either a plain action, which keeps its original timing of running as soon as the draw finishes (before the
						// next queued draw, so e.g. a cache clear never races an in-progress draw), or a display-tied action whose
						// display was already patched synchronously (fast path), so the map on screen already reflects this draw.
						postRun.run();
					}
				}
			}

			private void onFinishedDrawingCommon(boolean anotherDrawIsQueued, int borderPaddingAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
				mapEditingPanel.setGraph(mapParts.graph);
				mapEditingPanel.setRivers(edits == null ? null : edits.rivers, getSettingsFromGUI().lineStyle);
				mapEditingPanel.setFreeIcons(edits == null ? null : edits.freeIcons);
				mapEditingPanel.setIconDrawer(mapParts.iconDrawer);

				if (!undoer.isInitialized())
				{
					// This has to be done after the map is drawn rather
					// than when the editor frame is first created because
					// the first time the map is drawn is when the edits are
					// created.
					undoer.initialize(MainWindow.this.getSettingsFromGUI(true));
					enableOrDisableFieldsThatRequireMap(true, MainWindow.this.getSettingsFromGUI(false), false);
				}

				if (!hasDrawnCurrentMapAtLeastOnce)
				{
					hasDrawnCurrentMapAtLeastOnce = true;
					// Drawing for the first time can create or modify the
					// edits, so update them in lastSettingsLoadedOrSaved.
					lastSettingsLoadedOrSaved.edits = edits.deepCopy();
				}

				updateDisplayedMapFromGeneratedMap(false, incrementalChangeArea, false, borderPaddingAsDrawn);

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

					SwingHelper.showMessageDialog(MainWindow.this, scrollPane, Translation.get("mainWindow.mapDrewWithWarnings"), JOptionPane.WARNING_MESSAGE);

				}

				boolean isChange = settingsHaveUnsavedChanges();
				updateFrameTitle(isChange, !isChange);
			}

			@Override
			protected void onFailedToDraw(Exception exception)
			{
				showAsDrawing(false);
				mapEditingPanel.clearAllSelectionsAndHighlights();
				showCanvasMessage(Translation.get("mainWindow.mapFailedToDraw"), Translation.get("mainWindow.mapFailedRetry", fileMenu.getText(), refreshMenuItem.getText()));

				// In theory, enabling fields now could lead to the undoer not
				// working quite right since edits might not have been created.
				// But leaving fields disabled makes the user unable to fix the
				// error.
				enableOrDisableFieldsThatRequireMap(true, MainWindow.this.getSettingsFromGUI(false), false);
				if (exception != null)
				{
					SwingHelper.handleException(exception, MainWindow.this, false);
				}
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

		newRandomMapMenuItem = new JMenuItem(Translation.get("menu.file.newRandomMap"));
		newRandomMapMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, SwingHelper.getMenuShortcutKeyMask()));
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

		createSubMapMenuItem = new JMenuItem(Translation.get("menu.file.createSubMap"));
		fileMenu.add(createSubMapMenuItem);
		createSubMapMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				handleCreateSubMap();
			}
		});

		fileMenu.addSeparator();

		loadSettingsMenuItem = new JMenuItem(Translation.get("menu.file.open"));
		loadSettingsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, SwingHelper.getMenuShortcutKeyMask()));
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
					boolean opened = openMap(fileChooser.getSelectedFile().getAbsolutePath());

					if (opened && openSettingsFilePath != null && MapSettings.isOldPropertiesFile(openSettingsFilePath.toString()))
					{
						SwingHelper.showMessageDialog(MainWindow.this,
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

		fileMenu.addSeparator();

		saveMenuItem = new JMenuItem(Translation.get("menu.file.save"));
		saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, SwingHelper.getMenuShortcutKeyMask()));
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

		fileMenu.addSeparator();

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

		fileMenu.addSeparator();

		refreshMenuItem = new JMenuItem(Translation.get("menu.file.refreshImagesAndRedraw"));
		refreshMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, SwingHelper.getMenuShortcutKeyMask()));
		fileMenu.add(refreshMenuItem);
		refreshMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				handleImagesRefresh();

				if (lastSettingsLoadedOrSaved == null)
				{
					// Nothing to redraw. This menu item is disabled while no map is open, so this is only a guard against the redraw below
					// being asked for settings that don't exist. A map that is open but failed to draw does have settings, and so is redrawn.
					return;
				}

				// Clear only tool-specific highlights (selected centers, edges, hover state, etc.) — not the
				// View-menu river/lake highlights, which should persist across a refresh like any normal full draw.
				updater.createAndShowMapFull(() -> mapEditingPanel.clearAllToolSpecificSelectionsAndHighlights());
			}
		});

		editMenu = new JMenu(Translation.get("menu.edit"));
		menuBar.add(editMenu);

		undoButton = new JMenuItem(Translation.get("menu.edit.undo"));
		undoButton.setEnabled(false);
		editMenu.add(undoButton);
		undoButton.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, SwingHelper.getMenuShortcutKeyMask()));
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
		redoButton.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, SwingHelper.getMenuShortcutKeyMask() | ActionEvent.SHIFT_MASK));
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

		editMenu.addSeparator();

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

		editMenu.addSeparator();

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

		viewMenu.addSeparator();

		JMenuItem resetWindowLayoutMenuItem = new JMenuItem(Translation.get("menu.view.resetWindowLayout"));
		resetWindowLayoutMenuItem.setToolTipText(Translation.get("menu.view.resetWindowLayout.tooltip"));
		resetWindowLayoutMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				resetWindowLayout();
			}
		});
		viewMenu.add(resetWindowLayoutMenuItem);

		viewMenu.addSeparator();

		{
			// Create the theme menu
			JMenu themeMenu = new JMenu(Translation.get("menu.view.theme"));

			JRadioButtonMenuItem darkTheme = new JRadioButtonMenuItem(LookAndFeel.Dark.toString());
			JRadioButtonMenuItem lightTheme = new JRadioButtonMenuItem(LookAndFeel.Light.toString());
			JRadioButtonMenuItem systemTheme = new JRadioButtonMenuItem(LookAndFeel.System.toString());

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
		searchTextMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, SwingHelper.getMenuShortcutKeyMask()));
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
				String zoomModifierKey = OSHelper.isMac() ? "Cmd" : "Ctrl";
				SwingHelper.showMessageDialog(MainWindow.this, Translation.get("keyboardShortcuts.message", zoomModifierKey), Translation.get("keyboardShortcuts.title"), JOptionPane.INFORMATION_MESSAGE);
			}
		});

		// Shows info about the current map (aspect ratio, world size, dimensions), plus sub-map provenance when the map is a sub-map.
		mapInfoMenuItem = new JMenuItem(Translation.get("menu.help.mapInfo"));
		helpMenu.add(mapInfoMenuItem);
		mapInfoMenuItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				showMapInfoDialog();
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
		SwingHelper.showMessageDialog(this, Translation.get("language.changed"), Translation.get("language.changed.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void handleLookAndFeelChange(LookAndFeel lookAndFeel)
	{
		setLookAndFeel(lookAndFeel);
		UserPreferences.getInstance().lookAndFeel = lookAndFeel;
		SwingUtilities.updateComponentTreeUI(this);
		toolsPanel.handleLookAndFeelChange();
		mapCanvasOverlay.handleLookAndFeelChange();
		if (textSearchDialog != null)
		{
			textSearchDialog.handleLookAndFeelChange();
		}
		if (!UserPreferences.getInstance().hideThemeChangedMessage)
		{
			UserPreferences.getInstance().hideThemeChangedMessage = SwingHelper.showDismissibleMessage(Translation.get("theme.changed.title"), Translation.get("theme.changed"),
					new Dimension(400, 100), JOptionPane.INFORMATION_MESSAGE, this);
			UserPreferences.getInstance().save();
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
			SwingHelper.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Keep mnemonic underlines visible without holding Alt. FlatLaf hides them until Alt is pressed on some platforms (Linux) but not
		// others (Windows), and the buttons that have a mnemonic rely on the underline to show which letter it is. This is a FlatLaf
		// property; the System look and feel ignores it and follows the platform's own convention. Set after installing the look and feel,
		// since installing one replaces the defaults table this reads from.
		UIManager.put("Component.hideMnemonics", false);

		// Give the Cancel button of Yes/No/Cancel dialogs a mnemonic. The JDK defines one for Yes and No but not for Cancel or OK, so
		// without this the third button is the only one in the dialog with no underlined letter. The value must be a string holding the key
		// code, which is how BasicOptionPaneUI reads it. Where the look and feel's own translation of "Cancel" has no C in it, the letter is
		// simply not underlined, matching how the rest of the app handles a mnemonic that its translated label doesn't contain.
		UIManager.put("OptionPane.cancelButtonMnemonic", String.valueOf(KeyEvent.VK_C));
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
			// Keep the menu open when toggling a checkbox so multiple art packs can be highlighted without reopening it.
			item.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick", Boolean.TRUE);
			highlightIconsInArtPackMenu.add(item);
			artPacksToHighlight.add(item);
			if (highlightedArtPacks.contains(artPack))
			{
				item.setSelected(true);
			}
			item.addActionListener(listener);
		}

		if (!artPacksToHighlight.isEmpty())
		{
			highlightIconsInArtPackMenu.addSeparator();
			// A checkbox item (never actually checked) so it can reuse the doNotCloseOnMouseClick behavior and leave the menu open.
			JCheckBoxMenuItem uncheckAllItem = new JCheckBoxMenuItem(Translation.get("menu.tools.uncheckAllArtPackHighlights"));
			uncheckAllItem.putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick", Boolean.TRUE);
			uncheckAllItem.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					for (JCheckBoxMenuItem item : artPacksToHighlight)
					{
						item.setSelected(false);
					}
					uncheckAllItem.setSelected(false);
					updateArtPackHighlights();
				}
			});
			highlightIconsInArtPackMenu.add(uncheckAllItem);
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
				SwingHelper.showMessageDialog(this, message, Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
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
				SwingHelper.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			if (subfolderNames.isEmpty())
			{
				SwingHelper.showMessageDialog(this, Translation.get("artPack.invalidEmpty"), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}

			if (subfolderNames.size() > 1)
			{
				SwingHelper.showMessageDialog(this, Translation.get("artPack.invalidMultipleFolders", subfolderNames.size()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
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
								SwingHelper.showMessageDialog(this, Translation.get("artPack.requiresVersion", requiredVersion, MapSettings.currentVersion), Translation.get("common.error"),
										JOptionPane.ERROR_MESSAGE);
								return;
							}
						}
						catch (NumberFormatException e)
						{
							String message = "Number format error while reading " + requiredVersionKey + " from '" + settingsPath + "' in '" + selectedFile.toPath() + "': " + e.getMessage();
							Logger.printError(message, e);
							SwingHelper.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
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
				SwingHelper.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
			}

			String artPackName = subfolderNames.get(0);

			if (Assets.reservedArtPacks.contains(artPackName.toLowerCase()))
			{
				SwingHelper.showMessageDialog(this, Translation.get("artPack.nameNotAllowed", artPackName), Translation.get("artPack.invalidName"), JOptionPane.ERROR_MESSAGE);
				return;
			}

			File artPackFolderAsFile = Assets.getArtPackPath(artPackName, null).toFile();
			if (artPackFolderAsFile.exists() && artPackFolderAsFile.isDirectory())
			{
				// Show the dialog
				int response = SwingHelper.showOptionDialog(this, Translation.get("artPack.alreadyExists", artPackName), Translation.get("artPack.overwriteTitle"), JOptionPane.DEFAULT_OPTION,
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
				SwingHelper.showMessageDialog(MainWindow.this, Translation.get("artPack.addedSuccessfully"), Translation.get("artPack.success"), JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException ex)
			{
				SwingHelper.showMessageDialog(MainWindow.this, Translation.get("artPack.errorUncompressing", ex.getMessage()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
			}
			handleImagesRefresh();
		}
	}

	void handleImagesRefresh()
	{
		updater.setEnabled(false);
		undoer.setEnabled(false);
		// The re-enabling is in a finally so that an error while refreshing doesn't leave the editor unable to draw or undo for the rest
		// of the session.
		try
		{
			ImageCache.clear();
			ThemePanel.clearBackgroundImageCache();
			MapSettings settings = getSettingsFromGUI(false);
			themePanel.handleImagesRefresh(settings);
			// Tell Icons tool to refresh image previews
			toolsPanel.handleImagesRefresh(settings);
			updateArtPackHighlightOptions();
			updateArtPackHighlights();
		}
		finally
		{
			undoer.setEnabled(true);
			updater.setEnabled(true);
		}
	}

	private void showAboutNortantisDialog()
	{
		AboutDialog dialog = new AboutDialog(this);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	/**
	 * Shows info about the current map: its dimensions, aspect ratio, and world size. When the map is a sub-map, the original map,
	 * selection box, detail, icon/river mode, and seed used to create it are also shown so the user can recreate it. The text is selectable
	 * and can be copied to the clipboard.
	 */
	private void showMapInfoDialog()
	{
		if (lastSettingsLoadedOrSaved == null)
		{
			return;
		}
		MapSettings settings = lastSettingsLoadedOrSaved;

		// Pass numbers as strings so MessageFormat does not apply locale grouping (which would, e.g., render the seed as "174,503,823" and
		// break copy-paste recreation).
		MapSettings.SubMapInfo info = settings.subMapInfo;
		StringBuilder text = new StringBuilder();
		Dimension scrollPanePreferredSize;
		if (info != null)
		{
			// Sub-map: show only the provenance needed to recreate it.
			boolean hasFileName = info.originalFileName != null && !info.originalFileName.isEmpty();
			String intro = hasFileName ? Translation.get("subMapInfo.createdFrom", info.originalFileName) : Translation.get("subMapInfo.createdFromUnsaved");
			String iconsRivers = info.redistributeIconsAndRivers ? Translation.get("subMapInfo.iconsRivers.redistributed") : Translation.get("subMapInfo.iconsRivers.matched");

			// Label the selection box with the aspect ratio it actually has (computed on the fly from its dimensions), which may differ from
			// the ratio requested in SubMapDialog due to integer truncation/rounding — in which case it shows as Custom.
			GeneratedDimension selectionDimension = GeneratedDimension.fromAspectRatio(info.selectionWidth, info.selectionHeight);
			String selectionAspectRatioName = selectionDimension.displayName();
			// fromAspectRatio matches a named ratio in either orientation, so a portrait selection (e.g. a 16-by-9 preset with Rotate 90°)
			// matches the same preset. Tag it as rotated so "16 by 9" reads correctly as 9 by 16. Square is orientation-agnostic.
			if (selectionDimension != GeneratedDimension.Custom && selectionDimension != GeneratedDimension.Square && info.selectionHeight > info.selectionWidth)
			{
				selectionAspectRatioName = Translation.get("mapInfo.aspectRatio.rotated", selectionAspectRatioName);
			}

			text.append(intro).append("\n\n");
			text.append(Translation.get("subMapInfo.field.selection", selectionAspectRatioName, Long.toString(Math.round(info.selectionX)),
					Long.toString(Math.round(info.selectionY)), Long.toString(Math.round(info.selectionWidth)), Long.toString(Math.round(info.selectionHeight)))).append("\n");
			text.append(Translation.get("subMapInfo.field.detail", Integer.toString(info.worldSize))).append("\n");
			text.append(Translation.get("subMapInfo.field.iconsRivers", iconsRivers)).append("\n");
			text.append(Translation.get("subMapInfo.field.seed", Long.toString(info.randomSeed)));
			scrollPanePreferredSize = new Dimension(440, 130);
		}
		else
		{
			// Regular map: show the aspect ratio using the same dimension labels as NewSettingsDialog (e.g. "16 by 9 (4096 x 2304)"), plus
			// the world size. Custom dimensions aren't a named preset, so spell out the actual size after the "Custom" label.
			GeneratedDimension dimension = GeneratedDimension.fromDimensions(settings.generatedWidth, settings.generatedHeight);
			String dimensionDisplay = dimension == GeneratedDimension.Custom ? dimension.displayName() + " (" + settings.generatedWidth + " × " + settings.generatedHeight + ")" : dimension.toString();
			// A 90°/270° rotation swaps the map's orientation (the generated dimensions stay landscape; rotation is applied at draw time), so
			// e.g. a 16-by-9 preset is shown rotated as 9 by 16. A 180° rotation preserves orientation, and a square looks the same rotated.
			if (dimension != GeneratedDimension.Square && (settings.rightRotationCount == 1 || settings.rightRotationCount == 3))
			{
				dimensionDisplay = Translation.get("mapInfo.aspectRatio.rotated", dimensionDisplay);
			}
			text.append(Translation.get("mapInfo.aspectRatio", dimensionDisplay)).append("\n");
			text.append(Translation.get("mapInfo.worldSize", Integer.toString(settings.worldSize)));
			scrollPanePreferredSize = new Dimension(300, 50);
		}

		JTextArea textArea = new JTextArea(text.toString());
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setOpaque(false);
		textArea.setBorder(null);
		textArea.setFont(UIManager.getFont("Label.font"));
		textArea.setCaretPosition(0);
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setBorder(null);
		scrollPane.setPreferredSize(scrollPanePreferredSize);

		Object[] options = { Translation.get("mapInfo.copyButton"), Translation.get("mapInfo.closeButton") };
		// Build the JOptionPane and its dialog manually (rather than JOptionPane.showOptionDialog) so the dialog can be made resizable.
		JOptionPane optionPane = new JOptionPane(scrollPane, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, options, options[1]);
		JDialog dialog = optionPane.createDialog(this, Translation.get("mapInfo.title"));
		dialog.setResizable(true);
		dialog.setVisible(true);
		dialog.dispose();
		if (options[0].equals(optionPane.getValue()))
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
		}
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

	/**
	 * Opens the map at the given path.
	 *
	 * @return True if a map was actually opened; false if opening was aborted (file missing, user cancelled the missing-art-pack prompt, or
	 *         an error occurred). Callers that do post-open work keyed off {@link #openSettingsFilePath} should only do it when this returns
	 *         true, since a false return leaves that field (and the rest of the window state) untouched.
	 */
	private boolean openMap(String absolutePath)
	{
		if (!(new File(absolutePath).exists()))
		{
			SwingHelper.showMessageDialog(null, Translation.get("mainWindow.mapDoesNotExist", absolutePath), Translation.get("mainWindow.unableToOpenMap"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		try
		{
			MapSettings settings = new MapSettings(absolutePath);

			// Refresh the art pack list from disk so a pack the user just added (e.g. the one this map needs) is seen and not reported as
			// missing.
			Assets.clearArtPackCache();

			// Before drawing anything, make sure every art pack the map's icons, border, and background texture depend on is installed. If
			// some are missing, let the user substitute an installed art pack or cancel opening the map, so the map isn't drawn with a large
			// number of silent substitutions.
			MapSettings.MissingArtPackInfo missingArtPacks = settings.findMissingArtPacks();
			if (!missingArtPacks.isEmpty())
			{
				String mapName = FilenameUtils.getBaseName(absolutePath);
				MissingArtPackDialog.Result response = MissingArtPackDialog.show(this, mapName, missingArtPacks, settings.customImagesPath);
				if (response.cancelled)
				{
					return false;
				}
				settings.applyMissingArtPackSubstitution(missingArtPacks.missingArtPacks, response.chosenArtPack);
			}

			openSettingsFilePath = Paths.get(absolutePath);
			if (!MapSettings.isOldPropertiesFile(absolutePath))
			{
				UserPreferences.getInstance().addRecentMapFilePath(absolutePath);
				createOrUpdateRecentMapMenuButtons();
			}
			convertCustomImagesFolderIfNeeded(settings);

			updater.cancel();
			updater.doWhenMapIsNotDrawing(() ->
			{
				loadSettingsIntoGUI(settings);
			});

			updateFrameTitle(false, true);
			return true;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			SwingHelper.showMessageDialog(null, "Error while opening '" + absolutePath + "': " + e.getMessage(), Translation.get("mainWindow.errorWhileOpeningMap"), JOptionPane.ERROR_MESSAGE);
			Logger.printError("Unable to open '" + absolutePath + "' due to an error:", e);
			return false;
		}
	}

	/**
	 * Opens a map file in this window. Used as a fallback for launchNewInstanceForFile when there's no packaged launcher to relaunch (e.g.
	 * running from an IDE or Gradle). Brings the window to the front first so it, and any confirmation dialog this triggers, are visible
	 * to the user.
	 */
	private void openMapFromOS(String absolutePath)
	{
		setState(Frame.NORMAL);
		toFront();
		requestFocus();

		boolean cancelPressed = checkForUnsavedChanges();
		if (cancelPressed)
		{
			return;
		}

		openMap(absolutePath);
	}

	/**
	 * Opens a map file in a brand new window by launching a second instance of the app. This mirrors how Windows and Linux already open
	 * a new window for every file double-clicked in the file explorer - each such double-click there starts a new OS process. On macOS,
	 * the app stays running as a single process and Finder delivers a double-clicked file to that same process as an Apple Event, so this
	 * relaunches the packaged launcher executable to get the same one-process-per-window behavior.
	 */
	private static void launchNewInstanceForFile(String absolutePath)
	{
		Optional<String> launcherCommand = ProcessHandle.current().info().command();
		if (launcherCommand.isEmpty())
		{
			// Not running from a packaged launcher (e.g. running from an IDE or Gradle), so there's nothing to relaunch.
			if (instance != null)
			{
				instance.openMapFromOS(absolutePath);
			}
			return;
		}

		try
		{
			new ProcessBuilder(launcherCommand.get(), absolutePath).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		}
		catch (IOException e)
		{
			Logger.printError("Unable to launch a new window for '" + absolutePath + "':", e);
		}
	}

	private void convertCustomImagesFolderIfNeeded(MapSettings settings)
	{
		if (settings.hasOldCustomImagesFolderStructure())
		{
			try
			{
				MapSettings.convertOldCustomImagesFolder(settings.customImagesPath);

				SwingHelper.showMessageDialog(null, Translation.get("customImages.folderConvertedMessage"), Translation.get("customImages.folderConverted"), JOptionPane.INFORMATION_MESSAGE);
			}
			catch (IOException ex)
			{
				String errorMessage = "Error while restructuring custom images folder for " + settings.customImagesPath + ": " + ex.getMessage();
				Logger.printError(errorMessage, ex);
				SwingHelper.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void handleMouseWheelChangingZoom(MouseWheelEvent e)
	{
		if (!toolsPanel.zoomComboBox.isEnabled())
		{
			return;
		}

		// Accumulate the precise (possibly fractional) rotation and only advance a zoom step once a whole unit is reached. A notched
		// mouse wheel reports ~1.0 per notch, so it still zooms one step per notch. A touchpad reports many small fractions, so it
		// zooms smoothly instead of racing through the levels. Reset the accumulator when the scroll direction reverses so a flick the
		// other way doesn't immediately step from a nearly-full accumulator.
		double rotation = e.getPreciseWheelRotation();
		if (Math.signum(rotation) != Math.signum(accumulatedZoomScroll))
		{
			accumulatedZoomScroll = 0;
		}
		accumulatedZoomScroll += rotation;
		int steps = (int) accumulatedZoomScroll;
		if (steps == 0)
		{
			return;
		}
		accumulatedZoomScroll -= steps;

		// Positive wheel rotation (scrolling toward the user) zooms out, which is a lower zoom-combo-box index.
		changeZoomByOffset(-steps);
	}

	/**
	 * Changes the current zoom level by offset positions in the zoom combo box (positive zooms in, negative zooms out), clamped to the
	 * available levels. Used by both wheel zooming and the keyboard zoom shortcuts.
	 */
	private void changeZoomByOffset(int offset)
	{
		if (!toolsPanel.zoomComboBox.isEnabled())
		{
			return;
		}
		int newIndex = toolsPanel.zoomComboBox.getSelectedIndex() + offset;
		newIndex = Math.max(0, Math.min(newIndex, toolsPanel.zoomComboBox.getItemCount() - 1));
		if (newIndex != toolsPanel.zoomComboBox.getSelectedIndex())
		{
			// The action listener on toolsPanel.zoomComboBox will update the map.
			toolsPanel.zoomComboBox.setSelectedIndex(newIndex);
		}
	}

	public void updateDisplayedMapFromGeneratedMap(boolean updateScrollLocationIfZoomChanged, IntRectangle incrementalChangeArea, boolean isOnlyZoomChange,
			int borderPadding)
	{
		// The zoom the current combo-box selection maps to, given the current map's size. Fit-to-window depends on the map's
		// dimensions, so this must be recomputed on every call - in particular on the first draw of a newly sized map, where the
		// stale zoom field wouldn't yet reflect the fit. translateZoomLevel returns 1.0 when there's no map.
		double targetZoom = translateZoomLevel((String) toolsPanel.zoomComboBox.getSelectedItem());

		// Assume a synchronous display update; the branches below flip this to true when they hand the rescale to a background thread.
		lastDisplayUpdateWasAsync = false;

		if (mapEditingPanel.mapFromMapCreator == null)
		{
			// No map to show yet. Keep the zoom field current (it's read by other code) but there's nothing to rescale.
			zoom = targetZoom;
			return;
		}

		// A real map is being shown; clear any leftover canvas message/support panel from before it was drawn.
		mapCanvasOverlay.setMessage();
		mapCanvasOverlay.setSupportPanel(false, 0, false);

		if (isOnlyZoomChange)
		{
			// A zoom change always needs a full rescale (there's no existing region to patch), and the target zoom can differ from
			// what's currently displayed, so do it in the background and only commit it once it's ready. This path rescales the raw map
			// currently on screen, which is not necessarily at the target display quality: a zoom change can arrive while a display-quality
			// change is still drawing. commitBackgroundRescale commits the source map's own resolution (captured at submit), so overlays
			// stay matched to the image actually shown rather than jumping to the in-flight target quality.
			lastDisplayUpdateWasAsync = true;
			fullRescale(targetZoom, updateScrollLocationIfZoomChanged, borderPadding, false);
			return;
		}

		// A draw just finished.
		Method method = targetZoom < 0.34 ? Method.QUALITY : Method.BALANCED;
		if (method == Method.BALANCED && incrementalChangeArea != null && targetZoom == zoom && mapEditingPanel.getImage() != null)
		{
			// Fast path: the displayed image is already at this zoom, so patch just the changed region directly into it, synchronously
			// on the EDT (a small-region scale is only a couple ms).
			// Use wrapBufferedImage for the target so changes write back to the display BufferedImage.
			// fromBufferedImage would create a copy when using SkiaFactory, losing the changes.
			// The raw map being patched is the one on screen, so commit its own resolution (matches displayQualityScale except when a
			// display-quality change is mid-flight).
			mapEditingPanel.setResolution(displayedMapResolution);
			mapEditingPanel.setBorderPadding(borderPadding);
			ImageHelper.getInstance().scaleInto(mapEditingPanel.mapFromMapCreator, AwtBridge.wrapBufferedImage(mapEditingPanel.getImage()), incrementalChangeArea);
			finishDisplayUpdate();
		}
		else if (incrementalChangeArea == null)
		{
			// A full draw with no region to patch. Rescale synchronously here on the EDT so the new image is committed in the SAME event as
			// the new graph/rivers/free-icons/icon-drawer that onFinishedDrawingCommon just set. If this ran on a background thread, there
			// would be a brief window where the panel holds the new-resolution graph over the still-displayed old image, and any repaint in
			// that window draws graph-derived overlays (Highlight Lakes/Rivers, etc.) misaligned. A full draw already spent significant time
			// generating on a background thread, so the added synchronous rescale is minor. The async path is kept for zoom changes (below)
			// and incremental updates, where responsiveness matters and the graph/image are already consistent.
			lastDisplayUpdateWasAsync = false;
			fullRescale(targetZoom, false, borderPadding, true);
		}
		else
		{
			// A QUALITY downscale of an incremental update, or the displayed zoom doesn't match the target yet (e.g. the first draw at
			// fit-to-window). Do the (possibly slow) rescale on a background thread so it never blocks the EDT.
			lastDisplayUpdateWasAsync = true;
			fullRescale(targetZoom, false, borderPadding, false);
		}
	}

	/**
	 * Produces a fresh full rescale of mapEditingPanel.mapFromMapCreator at targetZoom and commits it to the display. When {@code synchronous}
	 * is false the scale runs on a background thread so a slow QUALITY downscale never blocks the EDT; requests are then coalesced via
	 * displayScaleGeneration (a request bails - before scaling, and again right after acquiring the map read lock - if a newer request has
	 * since been submitted, and only commits if it's still the latest). When {@code synchronous} is true the scale and commit run inline on
	 * the caller's (EDT) thread, so the image is committed in the same event as any panel state the caller set just before - used for full
	 * draws so graph-derived overlays never draw against the new graph over the old image.
	 */
	private void fullRescale(double targetZoom, boolean updateScrollLocationIfZoomChanged, int borderPadding, boolean synchronous)
	{
		Image sourceMap = mapEditingPanel.mapFromMapCreator;
		// Capture the resolution the source raw map was rendered at, together with the map itself. commitBackgroundRescale must set the
		// panel to THIS resolution, not the live displayQualityScale: a zoom-only rescale that runs while a display-quality change is still
		// drawing rescales the previous (still-shown) raw map, yet displayQualityScale has already jumped to the new target. Committing the
		// target there would tell the panel the displayed image is the new resolution while it is still the old one, misplacing every
		// resolution-scaled overlay for the duration of the change.
		double committedResolution = displayedMapResolution;
		long generation = displayScaleGeneration.incrementAndGet();
		if (synchronous)
		{
			runScaleMapFull(generation, sourceMap, committedResolution, targetZoom, updateScrollLocationIfZoomChanged, borderPadding, true);
		}
		else
		{
			displayScaleExecutor.submit(() -> runScaleMapFull(generation, sourceMap, committedResolution, targetZoom, updateScrollLocationIfZoomChanged, borderPadding, false));
		}
	}

	private void runScaleMapFull(long generation, Image sourceMap, double committedResolution, double targetZoom, boolean updateScrollLocationIfZoomChanged, int borderPadding,
			boolean synchronous)
	{
		if (sourceMap == null || generation != displayScaleGeneration.get())
		{
			// Superseded by a newer request before we even started.
			return;
		}

		Method method = targetZoom < 0.34 ? Method.QUALITY : Method.BALANCED;
		int zoomedWidth = (int) (sourceMap.getWidth() * targetZoom);
		if (zoomedWidth <= 0)
		{
			// Prevents a crash if someone collapses the map editing panel.
			zoomedWidth = 600;
		}

		BufferedImage scaledImage;
		Lock mapReadLock = updater.getMapReadLock();
		mapReadLock.lock();
		try
		{
			if (generation != displayScaleGeneration.get())
			{
				// Superseded while waiting for an in-flight incremental update to finish mutating the map buffer.
				return;
			}
			scaledImage = scaleFullMap(sourceMap, zoomedWidth, method);
		}
		finally
		{
			mapReadLock.unlock();
		}

		if (synchronous)
		{
			// Already on the EDT (called inline for a full draw). Commit in this same event so the image lands together with the panel
			// state the caller set just before.
			commitBackgroundRescale(generation, scaledImage, committedResolution, targetZoom, updateScrollLocationIfZoomChanged, borderPadding);
		}
		else
		{
			SwingUtilities.invokeLater(() -> commitBackgroundRescale(generation, scaledImage, committedResolution, targetZoom, updateScrollLocationIfZoomChanged, borderPadding));
		}
		// Zoom changes create a huge amount of trash on the heap (many GBs can accumulate after just a couple of times zoom in and out), so run garbage collection. This is done in a background thread to avoid blocking the EDT.
		ThreadHelper.getInstance().submit(() -> System.gc());
	}

	/**
	 * Scales the full generated map to zoomedWidth using method. It's important that this produces the same result as the fast
	 * incremental patch path (ImageHelper.scaleInto, used above in updateDisplayedMapFromGeneratedMap), or at least close enough that
	 * people can't tell the difference, because that path updates pieces of an image created here.
	 */
	private BufferedImage scaleFullMap(Image sourceMap, int zoomedWidth, Method method)
	{
		if (zoomedWidth > sourceMap.getWidth())
		{
			// Zooming in: convert smaller source first, then scale up.
			BufferedImage sourceBI = AwtBridge.toBufferedImage(sourceMap);
			try (Image source = AwtBridge.wrapBufferedImage(sourceBI); Image scaled = ImageHelper.getInstance().scaleByWidth(source, zoomedWidth, method))
			{
				return AwtBridge.toBufferedImage(scaled);
			}
		}
		else
		{
			// Zooming out (or 1:1): scale down first, then convert smaller result.
			try (Image scaled = ImageHelper.getInstance().scaleByWidth(sourceMap, zoomedWidth, method))
			{
				return AwtBridge.toBufferedImage(scaled);
			}
		}
	}

	/**
	 * Runs on the EDT once a background rescale finishes. Commits the zoom and the rescaled image to mapEditingPanel together, in one
	 * frame, rather than applying the new zoom as soon as it's requested - otherwise overlays (which read the panel's zoom) would sit
	 * at the new zoom over a base image still at the old one for as long as the rescale takes, and visibly float off the map. While the
	 * rescale runs, the panel stays at the previously committed zoom, so overlays and the displayed image stay consistent; then
	 * everything snaps to the new zoom at once.
	 */
	private void commitBackgroundRescale(long generation, BufferedImage scaledImage, double committedResolution, double targetZoom, boolean updateScrollLocationIfZoomChanged, int borderPadding)
	{
		if (generation != displayScaleGeneration.get() || mapEditingPanel.mapFromMapCreator == null)
		{
			// Superseded by a newer request, or the canvas was cleared (e.g. to show a message) before this finished.
			return;
		}

		double oldZoom = zoom;
		zoom = targetZoom;

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

		// Commit the resolution and border padding together with the zoom and image, so overlays that read the panel's resolution, zoom,
		// and border padding (such as the sub-map selection box) match the newly displayed image in the same frame. Committing any of them
		// earlier, while the rescale is still in flight, would draw those overlays against a scale or border offset that doesn't match the
		// still-old zoom for a frame, making them visibly jump. committedResolution is the resolution the rescaled raw map was actually
		// rendered at (captured at submit), not the live target: for a zoom-only rescale that overlaps a display-quality change, the source
		// is the previous raw map, so this keeps the panel reporting the resolution that matches the image actually on screen.
		mapEditingPanel.setResolution(committedResolution);
		mapEditingPanel.setBorderPadding(borderPadding);
		mapEditingPanel.setZoom(zoom);
		mapEditingPanel.setImage(scaledImage);

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

		finishDisplayUpdate();
	}

	private void finishDisplayUpdate()
	{
		// The map on screen now reflects the finished draw, so run any post-draw actions that were deferred while its display
		// update was rescaling on a background thread (e.g. removing the orange processing-area highlights for erased icons/text).
		if (!actionsToRunOnNextDisplayUpdate.isEmpty())
		{
			List<Runnable> actions = new ArrayList<>(actionsToRunOnNextDisplayUpdate);
			actionsToRunOnNextDisplayUpdate.clear();
			for (Runnable action : actions)
			{
				action.run();
			}
		}

		updater.doWhenMapIsReadyForInteractions(() ->
		{
			if (!mapEditingPanel.isSelectionBoxActive())
			{
				toolsPanel.currentTool.onAfterShowMap();
			}
		});

		mapEditingPanel.revalidate();
		mapEditingScrollPane.revalidate();
		mapEditingPanel.repaint();
		mapEditingScrollPane.repaint();
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
				// Divide by the longer side of the generated map so the map's displayed size is the same no matter the resolution it
				// generated at, and so extreme aspect ratios stay bounded (100% always makes the longer side
				// oneHundredPercentMapLongestSide pixels, regardless of orientation).
				double longestSide = Math.max(mapEditingPanel.mapFromMapCreator.getWidth(), mapEditingPanel.mapFromMapCreator.getHeight());
				return (oneHundredPercentMapLongestSide * percentage) / longestSide;
			}
			else
			{
				return 1.0;
			}
		}
	}

	public void showAsDrawing(boolean isDrawing)
	{
		// While the sub-map workflow holds the menu bar locked, leave these two items alone: the lock's snapshot is authoritative and
		// restores their correct state on unlock. Re-enabling them here (as a background draw completes mid-workflow) would let the user
		// re-trigger Create Sub-Map or Clear Entire Map from inside the workflow.
		if (menuItemEnabledStatesBeforeLock == null)
		{
			clearEntireMapButton.setEnabled(hasDrawnCurrentMapAtLeastOnce);
			createSubMapMenuItem.setEnabled(hasDrawnCurrentMapAtLeastOnce);
		}
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
		// The hover-highlight suppression state (areHoverHighlightsSuppressed) is derived from displayQualityScale, so repaint to reflect the
		// change; the actual redraw that follows will keep it suppressed until the new image is shown.
		if (mapEditingPanel != null)
		{
			mapEditingPanel.repaint();
		}
	}

	/**
	 * Whether the mouse-hover highlights should be hidden: true while the raw map on screen was rendered at a different resolution than the
	 * current target display quality, i.e. while a display-quality change is redrawing and the image on screen is still the previous one. The
	 * panel reads this live each paint. A zoom-only change does not trigger it: it keeps the committed zoom until the rescaled image is ready,
	 * so overlays and the image stay consistent and the hover highlights remain visible throughout.
	 */
	public boolean areHoverHighlightsSuppressed()
	{
		return displayedMapResolution != displayQualityScale;
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
			edits.rivers.clear();

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
		boolean cancelPressed = checkForUnsavedChanges();
		if (!cancelPressed)
		{
			// Step 1 (region selection) needs no graph/resolution snapshot, so it can open even while the map is mid-draw. The snapshot is
			// captured later, when advancing to step 2, from the then-current map state.
			new SubMapDialog(this).showStep1();
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
			int n = SwingHelper.showConfirmDialog(this, Translation.get("mainWindow.settingsModified"), "", JOptionPane.YES_NO_CANCEL_OPTION);
			if (n == JOptionPane.YES_OPTION)
			{
				// A save that was cancelled or failed counts as a cancel so that the unsaved changes aren't discarded.
				return !saveSettings(this);
			}
			else if (n == JOptionPane.NO_OPTION)
			{
			}
			else if (n == JOptionPane.CANCEL_OPTION || n == JOptionPane.CLOSED_OPTION)
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

	/**
	 * Saves the currently open map, prompting for a file to save into if the map doesn't have one yet.
	 *
	 * @return True if the map was saved. False if the save was cancelled or failed.
	 */
	public boolean saveSettings(Component parent)
	{
		if (openSettingsFilePath == null || forceSaveAs)
		{
			boolean saved = saveSettingsAs(parent);
			if (saved)
			{
				forceSaveAs = false;
			}
			return saved;
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
				SwingHelper.showMessageDialog(null, e.getMessage(), Translation.get("mainWindow.unableToSaveSettings"), JOptionPane.ERROR_MESSAGE);
				return false;
			}
			updateFrameTitle(false, true);
			return true;
		}
	}

	/**
	 * Prompts for a file to save the currently open map into, then saves it there.
	 *
	 * @return True if the map was saved. False if the save was cancelled or failed.
	 */
	public boolean saveSettingsAs(Component parent)
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
		if (status != JFileChooser.APPROVE_OPTION)
		{
			return false;
		}

		Path savePath = Paths.get(fileChooser.getSelectedFile().getAbsolutePath());
		if (!savePath.getFileName().toString().endsWith(MapSettings.fileExtensionWithDot))
		{
			savePath = Paths.get(savePath.toString() + MapSettings.fileExtensionWithDot);
		}

		final MapSettings settings = getSettingsFromGUI(false);
		final boolean isSavingToDifferentFile = !savePath.equals(curPath);
		if (isSavingToDifferentFile)
		{
			// Clear previous image export locations so that a new copy of a map doesn't export over the images from the older version.
			settings.imageExportPath = null;
			settings.heightmapExportPath = null;
		}

		try
		{
			saveMap(settings, savePath.toString());
		}
		catch (IOException e)
		{
			e.printStackTrace();
			Logger.printError("Error while saving settings to a new file:", e);
			SwingHelper.showMessageDialog(null, e.getMessage(), Translation.get("mainWindow.unableToSaveSettings"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		// Only point this window at the new file once the save has succeeded, so that a failed save doesn't change where a later save
		// would write.
		openSettingsFilePath = savePath;
		if (isSavingToDifferentFile)
		{
			imageExportPath = null;
			heightmapExportPath = null;
		}

		updateFrameTitle(false, true);
		return true;
	}

	private void saveMap(MapSettings settings, String absolutePath) throws IOException
	{
		settings.writeToFile(absolutePath);
		Logger.println("Settings saved to " + absolutePath);
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

	/**
	 * Warns the user when a full draw removed coastal cities because they landed on water (IconDrawer drops such icons). This usually means a
	 * change reshaped the coastline (the shore line style) or changed the draw resolution (the display quality) just enough to put a city that
	 * was very close to the water over it. The cause is not pinned down on purpose, so any future change that can do this is covered. The
	 * removed cities come from the draw that dropped them, and removed cities are gone from the edits, so this fires once per change rather
	 * than nagging on later redraws. A city sinking because the user painted ocean over it is an incremental draw, which does not report
	 * removed cities here, so that expected case is not warned about. The first full draw after loading a current-version map only establishes
	 * the baseline, so opening such a map (or creating a sub-map, which warns separately) does not warn; but when the loaded map is from an
	 * older version of Nortantis, the first draw does warn (with a message explaining the version-difference cause), because the cities sank
	 * from rendering changes between versions. That version-difference message is only used for that first draw: after it, the map has already
	 * been drawn in the current version, so any later loss is caused by whatever the user just changed, not by the version it was saved in.
	 * Undo/redo draws are skipped: an undo is trying to put removed cities back, not make a forward change, so warning then would be backwards.
	 * The specific cities are not listed to keep the popup small; the user can undo to see what changed.
	 */
	private void warnIfCitiesWereRemovedForWater(List<nortantis.IconDrawer.CityIconRemovedForWater> citiesRemovedForWater, boolean wasTriggeredByUndoRedo)
	{
		if (hasEstablishedCityOnWaterBaseline && !wasTriggeredByUndoRedo && citiesRemovedForWater.size() > 0)
		{
			// For a map loaded from an older version, the cities most likely sank because of rendering differences between that version and the
			// current one, so use a message that explains that cause (and names the older version) instead of the usual line-style explanation.
			String message;
			if (loadedMapIsFromOlderVersion)
			{
				message = citiesRemovedForWater.size() == 1 ? Translation.get("mainWindow.cityRemovedForWater.olderVersion", loadedMapVersion)
						: Translation.get("mainWindow.citiesRemovedForWater.olderVersion", String.valueOf(citiesRemovedForWater.size()), loadedMapVersion);
			}
			else
			{
				String editMenuName = Translation.get("menu.edit");
				String undoName = Translation.get("menu.edit.undo");
				message = citiesRemovedForWater.size() == 1 ? Translation.get("mainWindow.cityRemovedForWater", editMenuName, undoName)
						: Translation.get("mainWindow.citiesRemovedForWater", String.valueOf(citiesRemovedForWater.size()), editMenuName, undoName);
			}
			SwingHelper.showMessageDialog(this, message, Translation.get("mainWindow.citiesRemovedForWater.title"), JOptionPane.WARNING_MESSAGE);
		}
		hasEstablishedCityOnWaterBaseline = true;
		// The map has now been drawn once in the current version, so cities cannot sink from version differences again. Any later loss is from
		// something the user changed, so stop using the version-difference message even though the map was loaded from an older version.
		loadedMapIsFromOlderVersion = false;
	}

	void loadSettingsIntoGUI(MapSettings settings)
	{
		hasDrawnCurrentMapAtLeastOnce = false;
		// A map saved in an older version of Nortantis can have cities that sink into the water when it is first drawn in the current version,
		// because the way shores are drawn or water collision is detected has changed between versions. In that case we want the first draw to
		// warn the user (with a message that explains the version-difference cause), so treat the loaded map as the baseline rather than
		// silently establishing it. A current-version map keeps the normal behavior: its first draw only establishes the baseline, and only a
		// later change the user makes (e.g. switching the shore line style) warns.
		loadedMapVersion = settings.version;
		// Only treat the map as being from an older version when it actually records a version, so the warning can always name that version.
		loadedMapIsFromOlderVersion = loadedMapVersion != null && !loadedMapVersion.isEmpty()
				&& MapSettings.isVersionGreaterThan(MapSettings.currentVersion, loadedMapVersion);
		hasEstablishedCityOnWaterBaseline = loadedMapIsFromOlderVersion;
		mapEditingPanel.clearAllSelectionsAndHighlights();

		updateLastSettingsLoadedOrSaved(settings);
		toolsPanel.resetToolsForNewMap();
		loadSettingsAndEditsIntoThemeAndToolsPanels(settings, false, false);

		exportResolution = settings.resolution;
		imageExportPath = settings.imageExportPath;
		heightmapExportResolution = settings.heightmapResolution;
		heightmapExportPath = settings.heightmapExportPath;

		showCanvasMessage(Translation.get("mainWindow.drawingMap"));

		undoer.reset();

		handleImagesRefresh();

		if (settings.edits != null && settings.edits.isInitialized())
		{
			undoer.initialize(settings);
			enableOrDisableFieldsThatRequireMap(true, settings, false);
		}
		else
		{
			// Note - this call needs to come after everything that calls into
			// loadSettingsAndEditsIntoThemeAndToolsPanels because the text
			// tool
			// might enable fields when loading settings, which will cause
			// fields to be enabled before the map is ready.
			enableOrDisableFieldsThatRequireMap(false, settings, false);
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
		// The re-enabling is in a finally so that an error while loading settings doesn't leave the editor unable to draw or undo for the
		// rest of the session.
		try
		{
			customImagesPath = settings.customImagesPath;
			edits = settings.edits;
			themePanel.loadSettingsIntoGUI(settings, refreshImagePreviews);
			toolsPanel.loadSettingsIntoGUI(settings, isUndoRedoOrAutomaticChange, refreshImagePreviews);
		}
		finally
		{
			undoer.setEnabled(true);
			updater.setEnabled(true);
		}
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

	/**
	 * Shows a message (e.g. "drawing map...", a draw-failure message) centered near the top of the map canvas instead of a map, clearing
	 * any startup support panel. Use {@link #showStartupScreen()} instead when there is no map open yet, so the support panel is included.
	 */
	private void showCanvasMessage(String... message)
	{
		mapEditingPanel.setImage(null);

		// Clear out the map from map creator so that causing the window to
		// re-zoom while no map is displayed doesn't show the previous map. This can happen when the
		// zoom is fit to window, you create
		// a new map, then resize the window while the new map is drawing for
		// the first time.
		mapEditingPanel.mapFromMapCreator = null;

		mapCanvasOverlay.setSupportPanel(false, 0, false);
		mapCanvasOverlay.setMessage(message);

		mapEditingPanel.repaint();

		// Prevent a single-pixel column on the right side of the map from remaining. Not sure why that happens.
		revalidate();
		repaint();
	}

	/**
	 * Shows the welcome message plus a support panel with the website/blog/source links and, unless the user has hidden it via its own
	 * checkbox, an ask for donations/book purchases. Shown once at launch when no map is passed in on the command line, and never shown
	 * again until the app is relaunched.
	 */
	private void showStartupScreen()
	{
		showCanvasMessage(Translation.get("mainWindow.welcome"));
		boolean showAskCard = !UserPreferences.getInstance().hideStartupSupportPanel;
		mapCanvasOverlay.setSupportPanel(true, SupportPanel.defaultContentWidth, showAskCard);
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
		System.setProperty("apple.awt.application.name", "Nortantis");
		System.setProperty("apple.laf.useScreenMenuBar", "true");

		PlatformFactory.setInstance(new AwtFactory());

		Translation.initialize();

		setLookAndFeel(UserPreferences.getInstance().lookAndFeel);

		// On macOS, a file opened via Finder is delivered as an Apple Event rather than a command-line argument, and can arrive before or
		// after the window is created depending on timing. Registering this before creating the window ensures an event that arrives
		// during startup isn't missed.
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE))
		{
			Desktop.getDesktop().setOpenFileHandler(event ->
			{
				if (event.getFiles().isEmpty())
				{
					return;
				}
				String filePath = event.getFiles().get(0).getAbsolutePath();
				if (instance != null)
				{
					launchNewInstanceForFile(filePath);
				}
				else
				{
					pendingFileToOpenFromAppleEvent = filePath;
				}
			});
		}

		String fileToOpen = args.length > 0 ? args[0] : "";
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					String fileFromAppleEvent = pendingFileToOpenFromAppleEvent;
					MainWindow mainWindow = new MainWindow(!fileToOpen.isEmpty() ? fileToOpen : fileFromAppleEvent != null ? fileFromAppleEvent : "");
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
