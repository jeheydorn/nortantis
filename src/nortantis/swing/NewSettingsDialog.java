package nortantis.swing;

import nortantis.GeneratedDimension;
import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.LandShape;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.ThemeCatalog;
import nortantis.editor.MapUpdater;
import nortantis.editor.MapEdits;
import nortantis.geom.IntRectangle;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.*;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.Collator;
import java.util.*;
import java.util.List;

@SuppressWarnings("serial")
public class NewSettingsDialog extends JDialog
{
	JSlider worldSizeSlider;
	private JSpinner customWidthSpinner;
	private JSpinner customHeightSpinner;
	private JLabel customDimPreviewLabel;
	private JPanel customSpinnersPanel;
	private RowHider customDimPreviewHider;
	private JComboBox<LandShape> landShapeComboBox;
	private JSlider regionCountSlider;
	private SliderWithDisplayedValue regionCountSliderWithDisplay;
	private JComboBox<GeneratedDimension> dimensionsComboBox;
	BooksWidget booksWidget;
	MapSettings settings;
	private JProgressBar progressBar;
	private MapUpdater updater;
	private MapEditingPanel mapEditingPanel;
	private Dimension defaultSize = new Dimension(955, 750);
	private int amountToSubtractFromLeftAndRightPanels = 40;
	private Timer progressBarTimer;
	public final double cityFrequencySliderScale = 100.0 / SettingsGenerator.maxCityProbability;
	private JSlider cityFrequencySlider;
	private JComboBox<String> cityIconsTypeComboBox;
	private JPanel mapEditingPanelContainer;
	MainWindow mainWindow;
	private JTextField pathDisplay;
	private JComboBox<String> artPackComboBox;
	/** The custom art pack folder row, shown only when the custom art pack is chosen. */
	private RowHider customArtPackFolderHider;
	private JLabel rotationWarningLabel;
	private RowHider rotationWarningHider;
	/**
	 * The map drawing area size the last preview draw was kicked off for, or null before the first draw. The resize handler uses it to skip
	 * a redraw when the size has not actually changed, so the preview is not drawn twice while the dialog is first shown and laid out.
	 */
	private nortantis.geom.Dimension lastDrawnMapAreaSize;
	/** True while the preview shows a message in place of the map. */
	private boolean isPreviewShowingMessage;
	private JComboBox<ThemeChoice> themeComboBox;
	/**
	 * True while a theme is being applied or the dialog's controls are being loaded, when the art pack and theme combo boxes changing must
	 * not choose and apply a theme.
	 */
	private boolean isApplyingTheme;
	/**
	 * Why the theme chosen in the theme combo box couldn't be loaded, or null if it was. While it's set, the preview fails to draw and the
	 * map can't be created.
	 */
	private ThemeLoadError themeLoadError;
	/**
	 * The map New Map With Same Theme was opened from, or null.
	 */
	private final MapSettings settingsToKeepThemeFrom;
	/**
	 * The theme chosen in the theme combo box, before it was varied, which Randomize Theme varies around so that randomizing repeatedly
	 * doesn't drift away from it.
	 */
	private MapSettings themeBase;
	/**
	 * What the look is chosen from: which theme an art pack gets when its themes don't include the one chosen, and how the theme is varied.
	 * Only Randomize Theme changes it, so choosing an art pack again brings back the look it had.
	 */
	private long themeSeed;
	/**
	 * Whether Randomize Theme has varied the theme of the map the dialog was opened from, which is otherwise kept as it is.
	 */
	private boolean isSameThemeRandomized;
	/**
	 * The theme last chosen in the theme combo box, or chosen for the art pack the dialog started with, which an art pack whose themes
	 * include it uses. Choosing an art pack that doesn't have it doesn't change it, so choosing one that does again brings it back.
	 */
	private ThemeChoice preferredThemeChoice;
	/**
	 * The city icon type last chosen in the city icon type combo box, or the one the dialog started with, which an art pack that has it
	 * uses. Choosing an art pack that doesn't have it doesn't change it, so choosing one that does again brings it back.
	 */
	private String preferredCityIconType;

	/**
	 * An entry in the theme combo box: the theme of the map the dialog was opened from, or a theme in an art pack. Choices for the same theme
	 * are equal, so that a choice still matches after the combo box's list is rebuilt.
	 */
	private static final class ThemeChoice
	{
		/** The theme in an art pack, or null for the theme of the map the dialog was opened from. */
		final ThemeCatalog.Entry entry;
		final String displayName;

		ThemeChoice(ThemeCatalog.Entry entry, String displayName)
		{
			this.entry = entry;
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}

		@Override
		public boolean equals(Object obj)
		{
			return obj instanceof ThemeChoice other && Objects.equals(entry, other.entry);
		}

		@Override
		public int hashCode()
		{
			return Objects.hashCode(entry);
		}
	}

	/** The theme of the map the dialog was opened from, or null when it wasn't opened from a map. */
	private ThemeChoice sameThemeChoice;

	/**
	 * Everything about the dialog that undo and redo restore.
	 */
	private static final class DialogState
	{
		final MapSettings settings;
		final ThemeChoice themeChoice;
		final MapSettings themeBase;
		final ThemeLoadError themeLoadError;
		final long themeSeed;
		final boolean isSameThemeRandomized;
		final ThemeChoice preferredThemeChoice;
		final String preferredCityIconType;

		DialogState(MapSettings settings, ThemeChoice themeChoice, MapSettings themeBase, ThemeLoadError themeLoadError, long themeSeed,
				boolean isSameThemeRandomized, ThemeChoice preferredThemeChoice, String preferredCityIconType)
		{
			this.preferredCityIconType = preferredCityIconType;
			this.settings = settings;
			this.themeChoice = themeChoice;
			this.themeBase = themeBase;
			this.themeLoadError = themeLoadError;
			this.themeSeed = themeSeed;
			this.isSameThemeRandomized = isSameThemeRandomized;
			this.preferredThemeChoice = preferredThemeChoice;
		}

		boolean matches(DialogState other)
		{
			return Objects.equals(themeChoice, other.themeChoice) && themeBase == other.themeBase && Objects.equals(themeLoadError, other.themeLoadError)
					&& themeSeed == other.themeSeed && isSameThemeRandomized == other.isSameThemeRandomized
					&& Objects.equals(preferredThemeChoice, other.preferredThemeChoice) && Objects.equals(preferredCityIconType, other.preferredCityIconType)
					&& settings.equalsIgnoringEdits(other.settings);
		}
	}

	private final Deque<DialogState> undoStack = new ArrayDeque<>();
	private final Deque<DialogState> redoStack = new ArrayDeque<>();
	/**
	 * The dialog's state as of the most recent undo step, or null before the dialog has finished loading its initial settings.
	 */
	private DialogState committedState;
	private boolean isUndoStepPending;
	private JButton undoButton;
	private JButton redoButton;

	public NewSettingsDialog(MainWindow mainWindow, MapSettings settingsToKeepThemeFrom)
	{
		super(mainWindow, Translation.get("newSettingsDialog.title"), Dialog.ModalityType.APPLICATION_MODAL);
		this.mainWindow = mainWindow;
		this.settingsToKeepThemeFrom = settingsToKeepThemeFrom;

		createGUI(mainWindow);

		if (settingsToKeepThemeFrom == null)
		{
			// New random maps don't use a custom art pack folder unless the custom art pack is chosen.
			String customArtPackPath = null;
			Random rand = new Random();
			String artPack = ProbabilityHelper.sampleUniform(rand, Assets.listArtPacksForNewRandomMaps(customArtPackPath));
			themeSeed = rand.nextLong();
			List<ThemeCatalog.Entry> entries = ThemeCatalog.listThemesToChooseFrom(artPack, customArtPackPath);
			initializeThemeOptions(entries);
			ThemeChoice choice = chooseThemeFromSeed(entries);
			preferredThemeChoice = choice;
			selectThemeChoice(choice);
			MapSettings theme = loadTheme(choice.entry);
			if (theme == null)
			{
				showThemeLoadError();
				// The controls still need settings to show, so they get the installed art pack's first theme. The map can't be created
				// until a theme that loads is chosen.
				ThemeCatalog.Entry installedTheme = ThemeCatalog.listThemesForArtPack(Assets.installedArtPack, customArtPackPath).get(0);
				settings = SettingsGenerator.generateFromTheme(rand, themeSeed, artPack, ThemeCatalog.load(installedTheme), true, customArtPackPath);
				themeBase = null;
			}
			else
			{
				themeBase = theme;
				settings = SettingsGenerator.generateFromTheme(rand, themeSeed, artPack, theme, ThemeCatalog.isFromInstalledArtPack(choice.entry), customArtPackPath);
			}
		}
		else
		{
			themeSeed = new Random().nextLong();
			settings = SettingsGenerator.newMapWithSameTheme(settingsToKeepThemeFrom, themeSeed);
			themeBase = settingsToKeepThemeFrom;
			sameThemeChoice = new ThemeChoice(null, Translation.get("newSettingsDialog.theme.sameAsMap"));
			preferredThemeChoice = sameThemeChoice;
			initializeThemeOptions(ThemeCatalog.listThemesToChooseFrom(settings.artPack, settings.customArtPackPath));
			selectThemeChoice(sameThemeChoice);
		}
		loadSettingsIntoGUI(settings);
		preferredCityIconType = settings.cityIconTypeName;

		updater.setEnabled(true);
		committedState = captureState();
	}

	private void createGUI(MainWindow mainWindow)
	{
		setSize(defaultSize);
		setMinimumSize(defaultSize);

		GridBagOrganizer organizer = new GridBagOrganizer();
		JPanel container = organizer.panel;
		add(container);
		container.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		container.setPreferredSize(defaultSize);

		JPanel generatorSettingsPanel = new JPanel();
		generatorSettingsPanel.setLayout(new BoxLayout(generatorSettingsPanel, BoxLayout.X_AXIS));
		organizer.addLeftAlignedComponent(generatorSettingsPanel, 0, 0, false);

		createLeftPanel(generatorSettingsPanel);
		generatorSettingsPanel.add(Box.createHorizontalStrut(20));
		createRightPanel(generatorSettingsPanel);

		createMapEditingPanel();
		createMapUpdater();
		organizer.addLeftAlignedComponent(mapEditingPanelContainer, 0, 0, true);

		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SwingHelper.updateMapDrawingProgressBar(progressBar, updater);
			}
		};
		progressBarTimer = new Timer(50, listener);
		progressBarTimer.setInitialDelay(500);

		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

		{
			JButton randomizeThemeButton = new JButton(Translation.get("newSettingsDialog.randomizeTheme"));
			randomizeThemeButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					randomizeTheme();
				}
			});
			bottomPanel.add(randomizeThemeButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton randomizeLandButton = new JButton(Translation.get("newSettingsDialog.randomizeLand"));
			randomizeLandButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					randomizeLand();
				}
			});
			bottomPanel.add(randomizeLandButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			undoButton = new JButton("\u21A9");
			undoButton.setToolTipText(Translation.get("newSettingsDialog.undo.tooltip", SwingHelper.getCommandKeyName()));
			undoButton.addActionListener(e -> undo());
			SwingHelper.bindButtonShortcut(undoButton, KeyStroke.getKeyStroke(KeyEvent.VK_Z, SwingHelper.getMenuShortcutKeyMask()), "undoAction");
			bottomPanel.add(undoButton);
			bottomPanel.add(Box.createHorizontalStrut(5));

			redoButton = new JButton("\u21AA");
			redoButton.setToolTipText(Translation.get("newSettingsDialog.redo.tooltip", SwingHelper.getCommandKeyName()));
			redoButton.addActionListener(e -> redo());
			SwingHelper.bindButtonShortcut(redoButton, KeyStroke.getKeyStroke(KeyEvent.VK_Z, SwingHelper.getMenuShortcutKeyMask() | InputEvent.SHIFT_DOWN_MASK), "redoAction");
			bottomPanel.add(redoButton);
			bottomPanel.add(Box.createHorizontalStrut(40));
			updateUndoRedoButtons();
		}

		{
			JButton flipHorizontallyButton = new JButton(Translation.get("newSettingsDialog.flipHorizontal"));
			flipHorizontallyButton.setToolTipText(Translation.get("newSettingsDialog.flipHorizontal.tooltip"));
			flipHorizontallyButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					// If the map is rotated on it's side, then flip vertically instead of horizontally.
					if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
					{
						settings.flipVertically = !settings.flipVertically;
					}
					else
					{
						settings.flipHorizontally = !settings.flipHorizontally;
					}
					handleMapChange();
				}
			});
			bottomPanel.add(flipHorizontallyButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton flipVerticallyButton = new JButton(Translation.get("newSettingsDialog.flipVertical"));
			flipVerticallyButton.setToolTipText(Translation.get("newSettingsDialog.flipVertical.tooltip"));
			flipVerticallyButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					// If the map is rotated on it's side, then flip horizontally instead of vertically.
					if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
					{
						settings.flipHorizontally = !settings.flipHorizontally;
					}
					else
					{
						settings.flipVertically = !settings.flipVertically;
					}
					handleMapChange();
				}
			});
			bottomPanel.add(flipVerticallyButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton rotateButton = new JButton(Translation.get("newSettingsDialog.rotateLeft"));
			rotateButton.setToolTipText(Translation.get("newSettingsDialog.rotateLeft.tooltip"));

			rotateButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					if (settings.rightRotationCount == 0)
					{
						settings.rightRotationCount = 3;
					}
					else
					{
						settings.rightRotationCount--;
					}
					updateRotationWarning();
					handleMapChange();
				}
			});
			bottomPanel.add(rotateButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		{
			JButton rotateButton = new JButton(Translation.get("newSettingsDialog.rotateRight"));
			rotateButton.setToolTipText(Translation.get("newSettingsDialog.rotateRight.tooltip"));
			rotateButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					settings.rightRotationCount = (settings.rightRotationCount + 1) % 4;
					updateRotationWarning();
					handleMapChange();
				}
			});
			bottomPanel.add(rotateButton);
			bottomPanel.add(Box.createHorizontalStrut(5));
		}

		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString(Translation.get("newSettingsDialog.drawing"));
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		bottomPanel.add(Box.createHorizontalGlue());
		bottomPanel.add(progressBar);
		bottomPanel.add(Box.createHorizontalGlue());

		JPanel bottomButtonsPanel = new JPanel();
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton createMapButton = new JButton(Translation.get("newSettingsDialog.create"));
		// R rather than C so that Alt+C means Cancel in every dialog that has both buttons.
		SwingHelper.bindAltMnemonic(createMapButton, KeyEvent.VK_R);
		bottomButtonsPanel.add(createMapButton);
		createMapButton.addActionListener(new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				onCreateMap(mainWindow);
			}
		});

		bottomPanel.add(bottomButtonsPanel);
		organizer.addLeftAlignedComponent(bottomPanel, 0, 0, false);

		addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent componentEvent)
			{
				handleResize();
			}
		});

		pack();
	}

	private void onCreateMap(MainWindow mainWindow)
	{
		if (themeLoadError != null)
		{
			showThemeLoadError();
			return;
		}
		if (isCustomArtPackWithoutFolder())
		{
			SwingHelper.showMessageDialog(this, Translation.get("newSettingsDialog.chooseCustomArtPackFolder"), Translation.get("newSettingsDialog.title"),
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// Cancel and disable the dialog's own updater to stop it from submitting new GPU jobs
		updater.cancel();
		updater.setEnabled(false);

		// Get settings before disposing the dialog
		MapSettings settings = getSettingsFromGUI();

		// Dispose the dialog immediately
		dispose();

		// Cancel any current drawing in main window
		mainWindow.updater.cancel();

		// Load settings into main window - this will start a new draw and show the progress bar immediately.
		// GPU operations will be serialized through GPUExecutor.
		mainWindow.clearOpenSettingsFilePath();
		mainWindow.loadSettingsIntoGUI(settings);

		// After this modal dialog closes, Swing leaves the main JFrame as the active window but
		// without any of its components owning focus. WHEN_IN_FOCUSED_WINDOW keybindings (e.g.
		// Delete on selected river/road CPs, Ctrl+C / Ctrl+V on the edit clipboard, the Icons-tool
		// equivalents) won't fire in that state because KeyboardManager dispatches them through
		// the focus owner's ancestor window. MapEditingPanel intentionally doesn't grab focus on
		// click (see handleMousePressedOnMap), so without this nudge the user has to Tab, switch
		// tool modes, or right-click before keyboard shortcuts start working on the new map.
		SwingUtilities.invokeLater(() -> mainWindow.requestFocus());
	}

	private void createLeftPanel(JPanel generatorSettingsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel leftPanel = organizer.panel;
		generatorSettingsPanel.add(leftPanel);

		dimensionsComboBox = new JComboBox<GeneratedDimension>();
		for (GeneratedDimension dimension : GeneratedDimension.values())
		{
			dimensionsComboBox.addItem(dimension);
		}
		organizer.addLabelAndComponent(SwingHelper.createLabelWithTip(Translation.get("newSettingsDialog.dimensions.label"), Translation.get("newSettingsDialog.cannotBeChangedInEditor"),
				Translation.get("newSettingsDialog.dimensions.help")), dimensionsComboBox, GridBagOrganizer.rowVerticalInset);

		customWidthSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 32768, 1));
		customHeightSpinner = new JSpinner(new SpinnerNumberModel(9, 1, 32768, 1));
		Dimension spinnerDim = new Dimension(70, customWidthSpinner.getPreferredSize().height);
		customWidthSpinner.setPreferredSize(spinnerDim);
		customWidthSpinner.setMaximumSize(spinnerDim);
		customHeightSpinner.setPreferredSize(spinnerDim);
		customHeightSpinner.setMaximumSize(spinnerDim);

		customDimPreviewLabel = new JLabel();
		customWidthSpinner.addChangeListener(e -> clearMapPreview());
		customHeightSpinner.addChangeListener(e -> clearMapPreview());
		createMapChangeListener(customWidthSpinner);
		createMapChangeListener(customHeightSpinner);
		customWidthSpinner.addChangeListener(e -> updateCustomDimPreview());
		customHeightSpinner.addChangeListener(e -> updateCustomDimPreview());
		updateCustomDimPreview();

		// Spinners and preview label share a row that appears only when Custom is selected.
		customSpinnersPanel = new JPanel();
		customSpinnersPanel.setLayout(new BoxLayout(customSpinnersPanel, BoxLayout.X_AXIS));
		customSpinnersPanel.add(customWidthSpinner);
		customSpinnersPanel.add(new JLabel(" \u00d7 "));
		customSpinnersPanel.add(customHeightSpinner);
		customSpinnersPanel.add(Box.createHorizontalStrut(8));
		customSpinnersPanel.add(customDimPreviewLabel);

		customDimPreviewHider = organizer.addLabelAndComponent("", "", customSpinnersPanel, 2);
		customDimPreviewHider.setVisible(false);

		rotationWarningLabel = new JLabel();
		rotationWarningLabel.setForeground(new Color(160, 90, 0));
		rotationWarningHider = organizer.addLabelAndComponent("", "", rotationWarningLabel, 2);
		rotationWarningHider.setVisible(false);

		dimensionsComboBox.addActionListener(e ->
		{
			boolean isCustom = dimensionsComboBox.getSelectedItem() == GeneratedDimension.Custom;
			customDimPreviewHider.setVisible(isCustom);
			clearMapPreview();
			handleMapChange();
		});

		worldSizeSlider = new JSlider(SettingsGenerator.minWorldSize, SettingsGenerator.maxWorldSize);
		// The slider snaps to its tick spacing even though the ticks aren't drawn.
		worldSizeSlider.setSnapToTicks(true);
		worldSizeSlider.setMinorTickSpacing(SettingsGenerator.worldSizePrecision);
		worldSizeSlider.setPaintLabels(false);
		createMapChangeListener(worldSizeSlider);
		// Wide enough for the largest world size.
		new SliderWithDisplayedValue(worldSizeSlider, null, null, 44).addToOrganizer(organizer, SwingHelper.createLabelWithTip(
				Translation.get("newSettingsDialog.worldSize.label"), Translation.get("newSettingsDialog.cannotBeChangedInEditor"), Translation.get("newSettingsDialog.worldSize.help")));

		landShapeComboBox = new JComboBox<LandShape>();
		// Alphabetical by displayed name in the user's language.
		Collator collator = Collator.getInstance(Translation.getEffectiveLocale());
		List<LandShape> landShapesInDisplayOrder = new ArrayList<>(Arrays.asList(LandShape.values()));
		landShapesInDisplayOrder.sort((shape1, shape2) -> collator.compare(shape1.toString(), shape2.toString()));
		for (LandShape shape : landShapesInDisplayOrder)
		{
			landShapeComboBox.addItem(shape);
		}
		createMapChangeListener(landShapeComboBox);
		// The label's tooltip describes the control, and the combo box's tooltip describes the selected shape. This listener is separate
		// from the map change listener because that one ignores changes made while loading settings into the GUI.
		landShapeComboBox.addActionListener(e -> updateLandShapeTooltip());
		updateLandShapeTooltip();
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.landShape.label"), Translation.get("newSettingsDialog.landShape.help"), landShapeComboBox);

		regionCountSlider = new JSlider();
		regionCountSlider.setMinimum(SettingsGenerator.minRegionCount);
		regionCountSlider.setMaximum(SettingsGenerator.maxRegionCount);
		regionCountSlider.setValue(3);
		regionCountSlider.setSnapToTicks(true);
		regionCountSlider.setMajorTickSpacing(1);
		createMapChangeListener(regionCountSlider);
		regionCountSliderWithDisplay = new SliderWithDisplayedValue(regionCountSlider);
		regionCountSliderWithDisplay.addToOrganizer(organizer, Translation.get("newSettingsDialog.regionCount.label"), Translation.get("newSettingsDialog.regionCount.help"));

		cityFrequencySlider = new JSlider(0, 100);
		cityFrequencySlider.setPaintLabels(false);
		createMapChangeListener(cityFrequencySlider);
		// Wide enough for 100.
		new SliderWithDisplayedValue(cityFrequencySlider, null, null, 30).addToOrganizer(organizer, Translation.get("newSettingsDialog.cityFrequency.label"),
				Translation.get("newSettingsDialog.cityFrequency.help"));

		organizer.addLeftAlignedComponent(Box.createRigidArea(new Dimension((defaultSize.width / 2) - amountToSubtractFromLeftAndRightPanels, 0)));

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.66);
		organizer.addVerticalFillerRow();
	}

	/**
	 * Whether the custom art pack is chosen but has no folder yet, in which case there is nothing to draw it from.
	 */
	private boolean isCustomArtPackWithoutFolder()
	{
		return artPackComboBox != null && Assets.customArtPack.equals(artPackComboBox.getSelectedItem()) && (settings == null || StringUtils.isEmpty(settings.customArtPackPath));
	}

	/**
	 * Shows the custom art pack folder row only when the custom art pack is chosen. A folder the map has keeps its value while the row is
	 * hidden, so that a map whose theme uses custom images keeps them.
	 */
	private void updateCustomArtPackFolderVisibility()
	{
		customArtPackFolderHider.setVisible(Assets.customArtPack.equals(artPackComboBox.getSelectedItem()));
	}

	private void initializeCityTypeOptions()
	{
		// Filling the combo box isn't a choice of a city icon type.
		boolean wasApplyingTheme = isApplyingTheme;
		isApplyingTheme = true;
		try
		{
			SwingHelper.initializeComboBoxItems(cityIconsTypeComboBox, getCityIconTypesForArtPack(), (String) cityIconsTypeComboBox.getSelectedItem(), false);
		}
		finally
		{
			isApplyingTheme = wasApplyingTheme;
		}
	}

	/**
	 * The city icon types of the art pack chosen in the art pack combo box.
	 */
	private List<String> getCityIconTypesForArtPack()
	{
		return isCustomArtPackWithoutFolder() ? new ArrayList<>()
				: new ArrayList<>(ImageCache.getInstance((String) artPackComboBox.getSelectedItem(), (String) settings.customArtPackPath).getIconGroupNames(IconType.cities));
	}

	private void initializeArtPackOptionsAndCityTypeOptions()
	{
		// The custom art pack is always listed, and choosing it shows the row for choosing its folder.
		SwingHelper.initializeComboBoxItems(artPackComboBox, Assets.listArtPacks(true), settings.artPack, false);
		updateCustomArtPackFolderVisibility();
		initializeCityTypeOptions();
	}

	private void updatePathDisplay()
	{
		String newText;
		if (settings != null && settings.customArtPackPath != null && !settings.customArtPackPath.isEmpty())
		{
			newText = FileHelper.replaceHomeFolderPlaceholder(settings.customArtPackPath);
		}
		else
		{
			newText = "";
		}
		if (!newText.equals(pathDisplay.getText()))
		{
			pathDisplay.setText(newText);
		}
	}

	private void createRightPanel(JPanel generatorSettingsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel rightPanel = organizer.panel;
		generatorSettingsPanel.add(rightPanel);

		artPackComboBox = new ShrinkableComboBox<String>();
		artPackComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (isApplyingTheme)
				{
					return;
				}
				updateCustomArtPackFolderVisibility();
				if (Objects.equals(artPackComboBox.getSelectedItem(), settings.artPack))
				{
					return;
				}
				useThemeForArtPack();
			}
		});
		organizer.addLabelAndComponent(Translation.get("newSettingsDialog.artPack.label"), Translation.get("newSettingsDialog.artPack.help"), artPackComboBox);

		JButton changeButton = new JButton(Translation.get("newSettingsDialog.change"));
		pathDisplay = new JTextField();
		pathDisplay.setEditable(false);
		pathDisplay.setMinimumSize(new Dimension(0, pathDisplay.getMinimumSize().height));
		pathDisplay.setPreferredSize(new Dimension(0, pathDisplay.getPreferredSize().height));
		customArtPackFolderHider = organizer.addLabelAndComponentsHorizontal(Translation.get("newSettingsDialog.customArtPackFolder.label"),
				Translation.get("newSettingsDialog.customArtPackFolder.help"), Arrays.asList(pathDisplay, changeButton));

		changeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				CustomArtPackDialog dialog = new CustomArtPackDialog(mainWindow, settings.customArtPackPath, (value) ->
				{
					settings.customArtPackPath = value;
					updatePathDisplay();
					// The custom art pack's themes and images come from the new folder.
					useThemeForArtPack();

					redrawWithClearedImageCache();
					scheduleUndoStep();
				});
				dialog.setLocationRelativeTo(NewSettingsDialog.this);
				dialog.setVisible(true);
			}
		});

		themeComboBox = new ShrinkableComboBox<ThemeChoice>();
		themeComboBox.addActionListener(e ->
		{
			if (!isApplyingTheme)
			{
				applyThemeChoice();
			}
		});

		cityIconsTypeComboBox = new ShrinkableComboBox<String>();
		cityIconsTypeComboBox.addActionListener(e ->
		{
			// Only a choice the user makes, rather than the controls being loaded.
			if (!isApplyingTheme && updater.isEnabled())
			{
				preferredCityIconType = (String) cityIconsTypeComboBox.getSelectedItem();
			}
		});
		createMapChangeListener(cityIconsTypeComboBox);
		// The theme and the city icon type share a row, which leaves more room for the map preview. The row has its own labels rather than
		// using the label column, so that each label is beside its combo box and the combo boxes get the rest of the width.
		JLabel themeLabel = new JLabel(Translation.get("newSettingsDialog.theme.label"));
		themeLabel.setToolTipText(Translation.get("newSettingsDialog.theme.help"));
		JLabel cityIconTypeLabel = new JLabel(Translation.get("newSettingsDialog.cityIconType.label"));
		cityIconTypeLabel.setToolTipText(Translation.get("newSettingsDialog.cityIconType.help"));
		JPanel themeAndCityIconTypeRow = new JPanel();
		themeAndCityIconTypeRow.setLayout(new BoxLayout(themeAndCityIconTypeRow, BoxLayout.X_AXIS));
		themeAndCityIconTypeRow.add(themeLabel);
		themeAndCityIconTypeRow.add(Box.createHorizontalStrut(GridBagOrganizer.defaultHorizontalSpaceBetweenComponentsAddAsList));
		themeAndCityIconTypeRow.add(themeComboBox);
		themeAndCityIconTypeRow.add(Box.createHorizontalStrut(15));
		themeAndCityIconTypeRow.add(cityIconTypeLabel);
		themeAndCityIconTypeRow.add(Box.createHorizontalStrut(GridBagOrganizer.defaultHorizontalSpaceBetweenComponentsAddAsList));
		themeAndCityIconTypeRow.add(cityIconsTypeComboBox);
		organizer.addLeftAlignedComponent(themeAndCityIconTypeRow, false);

		// The Check All and Uncheck All buttons go beside the label rather than below the books, which saves a row.
		booksWidget = new BooksWidget(true, false, () -> handleMapChange());
		Dimension booksSize = new Dimension(360, 100);
		booksWidget.getContentPanel().setPreferredSize(booksSize);
		// Give the books widget a firm minimum height so that when vertical space is tight, the enclosing GridBagLayout doesn't shrink it to
		// its scroll pane's tiny minimum (collapsing it to about two rows). It keeps a usable height and its own scroll bar handles overflow,
		// while still growing to use extra space when it is available.
		booksWidget.getContentPanel().setMinimumSize(booksSize);
		JLabel booksLabel = new JLabel(Translation.get("newSettingsDialog.booksForText.label"));
		booksLabel.setToolTipText(Translation.get("newSettingsDialog.booksForText.help"));
		JPanel booksHeader = new JPanel(new BorderLayout());
		booksHeader.add(booksLabel, BorderLayout.WEST);
		booksHeader.add(booksWidget.getButtonsPanel(), BorderLayout.EAST);
		organizer.addLeftAlignedComponent(booksHeader, GridBagOrganizer.rowVerticalInset, 2, false);
		organizer.addLeftAlignedComponent(booksWidget.getContentPanel(), 0, GridBagOrganizer.rowVerticalInset, true, 1.0);

		organizer.addLeftAlignedComponent(Box.createRigidArea(new Dimension((defaultSize.width / 2) - amountToSubtractFromLeftAndRightPanels, 0)));

		// No vertical filler row, so that the books list takes all of the height the left column leaves spare.
		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.66);
	}

	/**
	 * Lists the given themes in the theme combo box, after the theme of the map the dialog was opened from, if there is one. Doesn't select
	 * one.
	 */
	private void initializeThemeOptions(List<ThemeCatalog.Entry> entries)
	{
		isApplyingTheme = true;
		try
		{
			themeComboBox.removeAllItems();
			if (sameThemeChoice != null)
			{
				themeComboBox.addItem(sameThemeChoice);
			}
			for (ThemeCatalog.Entry entry : entries)
			{
				themeComboBox.addItem(new ThemeChoice(entry, entry.name));
			}
		}
		finally
		{
			isApplyingTheme = false;
		}
	}

	/**
	 * Selects the given choice in the theme combo box without applying it.
	 */
	private void selectThemeChoice(ThemeChoice choice)
	{
		isApplyingTheme = true;
		try
		{
			themeComboBox.setSelectedItem(choice);
		}
		finally
		{
			isApplyingTheme = false;
		}
	}

	/**
	 * Chooses one of the given themes from the theme seed, so that the same themes give the same choice until Randomize Theme is pressed.
	 */
	private ThemeChoice chooseThemeFromSeed(List<ThemeCatalog.Entry> entries)
	{
		ThemeCatalog.Entry entry = ProbabilityHelper.sampleUniform(new Random(themeSeed), entries);
		return new ThemeChoice(entry, entry.name);
	}

	/**
	 * Why a theme couldn't be loaded.
	 *
	 * @param isInvalid
	 *            Whether the theme loaded but can't be used, rather than failing to load.
	 */
	private record ThemeLoadError(String message, boolean isInvalid)
	{
	}

	/**
	 * Loads a theme. If it can't be loaded, records why in {@link #themeLoadError} and returns null.
	 */
	private MapSettings loadTheme(ThemeCatalog.Entry entry)
	{
		try
		{
			MapSettings theme = ThemeCatalog.load(entry);
			themeLoadError = null;
			return theme;
		}
		catch (ThemeCatalog.InvalidThemeException e)
		{
			themeLoadError = new ThemeLoadError(e.getMessage(), true);
		}
		catch (Exception e)
		{
			Logger.printError("Unable to load the theme '" + entry.path + "'.", e);
			themeLoadError = new ThemeLoadError(Translation.get("theme.unableToLoad", e.getMessage()), false);
		}
		return null;
	}

	/**
	 * Tells the user why the chosen theme couldn't be loaded.
	 */
	private void showThemeLoadError()
	{
		String title = themeLoadError.isInvalid() ? Translation.get("theme.invalid.title") : Translation.get("theme.unableToLoad.title");
		SwingHelper.showMessageDialog(isVisible() ? this : mainWindow, themeLoadError.message(), title, JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * What the preview shows instead of the map when the map can't be drawn: that the chosen theme couldn't be loaded, or that the custom
	 * art pack needs a folder. Null when the map can be drawn.
	 */
	private String getReasonPreviewCannotBeDrawn()
	{
		if (themeLoadError != null)
		{
			return Translation.get("newSettingsDialog.previewFailedToDraw");
		}
		if (isCustomArtPackWithoutFolder())
		{
			return Translation.get("newSettingsDialog.chooseCustomArtPackFolder");
		}
		return null;
	}

	/**
	 * Stops drawing the preview and shows the given message in its place.
	 */
	private void showPreviewCannotBeDrawn(String message)
	{
		updater.cancel();
		enableOrDisableProgressBar(false);
		showMessageInPreview(message);
	}

	private void showMessageInPreview(String message)
	{
		mapEditingPanel.setImage(AwtBridge.toBufferedImage(ImageHelper.getInstance().createPlaceholderImage(new String[] { message },
				AwtBridge.fromAwtColor(SwingHelper.getTextColorForPlaceholderImages()))));
		isPreviewShowingMessage = true;
	}

	/**
	 * Clears a message the preview shows in place of the map, so that it isn't shown while a map that can be drawn is drawing.
	 */
	private void clearMessageFromPreview()
	{
		if (isPreviewShowingMessage)
		{
			mapEditingPanel.setImage(null);
			isPreviewShowingMessage = false;
		}
	}

	/**
	 * Lists the themes the chosen art pack can use, and makes the map with the chosen art pack from the preferred theme when the art pack
	 * can use it, which the theme of the map the dialog was opened from always can. Otherwise uses one of the art pack's themes chosen from
	 * the theme seed.
	 */
	private void useThemeForArtPack()
	{
		String artPack = (String) artPackComboBox.getSelectedItem();
		if (artPack == null)
		{
			return;
		}
		settings.artPack = artPack;
		initializeCityTypeOptions();
		List<ThemeCatalog.Entry> entries = ThemeCatalog.listThemesToChooseFrom(artPack, settings.customArtPackPath);
		initializeThemeOptions(entries);
		if (sameThemeChoice != null && sameThemeChoice.equals(preferredThemeChoice))
		{
			selectThemeChoice(sameThemeChoice);
			themeLoadError = null;
			applyTheme(sameThemeChoice, null);
			return;
		}
		ThemeChoice choice = preferredThemeChoice != null && entries.contains(preferredThemeChoice.entry) ? preferredThemeChoice : chooseThemeFromSeed(entries);
		selectThemeChoice(choice);
		useThemeChoice(choice);
	}

	/**
	 * Applies the theme chosen in the theme combo box.
	 */
	private void applyThemeChoice()
	{
		ThemeChoice choice = (ThemeChoice) themeComboBox.getSelectedItem();
		if (choice == null)
		{
			return;
		}
		preferredThemeChoice = choice;
		if (choice.equals(sameThemeChoice))
		{
			// Choosing the theme of the map the dialog was opened from shows that theme as it is.
			isSameThemeRandomized = false;
			themeLoadError = null;
			applyTheme(choice, null);
			return;
		}
		useThemeChoice(choice);
	}

	/**
	 * Loads the given theme and makes the map from it. If it can't be loaded, the preview fails to draw and the map can't be created until
	 * another theme is chosen.
	 */
	private void useThemeChoice(ThemeChoice choice)
	{
		MapSettings theme = loadTheme(choice.entry);
		if (theme == null)
		{
			showThemeLoadError();
			handleMapChange();
			return;
		}
		applyTheme(choice, theme);
	}

	/**
	 * Makes the map from the given theme with the chosen art pack, keeping the world the user set up.
	 *
	 * @param theme
	 *            The loaded theme, or null for the theme of the map the dialog was opened from.
	 */
	private void applyTheme(ThemeChoice choice, MapSettings theme)
	{
		MapSettings current = getSettingsFromGUI();
		String artPack = (String) artPackComboBox.getSelectedItem();
		MapSettings newSettings;
		MapSettings newThemeBase;
		try
		{
			if (choice.equals(sameThemeChoice))
			{
				newSettings = SettingsGenerator.newMapWithSameTheme(settingsToKeepThemeFrom, themeSeed);
				newThemeBase = settingsToKeepThemeFrom;
				if (!Objects.equals(newSettings.artPack, artPack))
				{
					newSettings.artPack = artPack;
					SettingsGenerator.chooseCityIconType(newSettings, themeSeed);
				}
				if (isSameThemeRandomized)
				{
					SettingsGenerator.randomizeTheme(newSettings, newThemeBase, artPack, artPack, themeSeed);
				}
			}
			else
			{
				// The world comes from the current settings below, so the random numbers it's generated from don't matter.
				newSettings = SettingsGenerator.generateFromTheme(new Random(), themeSeed, artPack, theme, ThemeCatalog.isFromInstalledArtPack(choice.entry),
						settings.customArtPackPath);
				newThemeBase = theme;
			}
		}
		catch (RuntimeException e)
		{
			SwingHelper.handleException(e, this, false);
			return;
		}

		if (preferredCityIconType != null && getCityIconTypesForArtPack().contains(preferredCityIconType))
		{
			newSettings.cityIconTypeName = preferredCityIconType;
		}
		// A theme is a look, so the world the user set up is kept.
		newSettings.customArtPackPath = current.customArtPackPath;
		newSettings.randomSeed = current.randomSeed;
		newSettings.textRandomSeed = current.textRandomSeed;
		newSettings.worldSize = current.worldSize;
		newSettings.landShape = current.landShape;
		newSettings.regionCount = current.regionCount;
		newSettings.generatedWidth = current.generatedWidth;
		newSettings.generatedHeight = current.generatedHeight;
		newSettings.rightRotationCount = current.rightRotationCount;
		newSettings.flipHorizontally = current.flipHorizontally;
		newSettings.flipVertically = current.flipVertically;
		newSettings.books = current.books;
		settings = newSettings;
		themeBase = newThemeBase;

		loadSettingsIntoGUIWithoutApplyingTheme();
		handleMapChange();
	}

	/**
	 * Loads {@link #settings} into the controls without the change listeners redrawing the map or applying the theme combo box's choice.
	 */
	private void loadSettingsIntoGUIWithoutApplyingTheme()
	{
		updater.setEnabled(false);
		isApplyingTheme = true;
		try
		{
			loadSettingsIntoGUI(settings);
		}
		finally
		{
			isApplyingTheme = false;
			updater.setEnabled(true);
		}
	}

	private DialogState captureState()
	{
		return new DialogState(getSettingsFromGUI(), (ThemeChoice) themeComboBox.getSelectedItem(), themeBase, themeLoadError, themeSeed, isSameThemeRandomized,
				preferredThemeChoice, preferredCityIconType);
	}

	/**
	 * Records an undo step for the changes made since the last one, once the current event has finished being handled. Deferring it
	 * combines the changes one user action makes, such as choosing an art pack, which also changes the city icon type, into one step.
	 */
	private void scheduleUndoStep()
	{
		if (isUndoStepPending)
		{
			return;
		}
		isUndoStepPending = true;
		SwingUtilities.invokeLater(() -> commitUndoStep());
	}

	private void commitUndoStep()
	{
		isUndoStepPending = false;
		if (committedState == null)
		{
			return;
		}
		DialogState current = captureState();
		if (!current.matches(committedState))
		{
			pushUndoStep(committedState);
			redoStack.clear();
			committedState = current;
			updateUndoRedoButtons();
		}
	}

	private void undo()
	{
		commitUndoStep();
		if (undoStack.isEmpty())
		{
			return;
		}
		redoStack.push(committedState);
		restoreState(undoStack.pop());
	}

	private void redo()
	{
		commitUndoStep();
		if (redoStack.isEmpty())
		{
			return;
		}
		pushUndoStep(committedState);
		restoreState(redoStack.pop());
	}

	private void pushUndoStep(DialogState state)
	{
		undoStack.push(state);
		while (undoStack.size() > Undoer.maxUndoLevels)
		{
			undoStack.removeLast();
		}
	}

	private void restoreState(DialogState state)
	{
		boolean dimensionsChanged = committedState.settings.generatedWidth != state.settings.generatedWidth
				|| committedState.settings.generatedHeight != state.settings.generatedHeight;
		committedState = state;
		boolean customArtPackPathChanged = !Objects.equals(settings.customArtPackPath, state.settings.customArtPackPath);
		MapSettings restored = state.settings.deepCopy();
		restored.edits = settings.edits;
		settings = restored;
		themeBase = state.themeBase;
		themeSeed = state.themeSeed;
		isSameThemeRandomized = state.isSameThemeRandomized;
		preferredThemeChoice = state.preferredThemeChoice;
		preferredCityIconType = state.preferredCityIconType;

		themeLoadError = state.themeLoadError;
		initializeThemeOptions(ThemeCatalog.listThemesToChooseFrom(state.settings.artPack, state.settings.customArtPackPath));
		selectThemeChoice(state.themeChoice);
		loadSettingsIntoGUIWithoutApplyingTheme();
		updateUndoRedoButtons();

		if (dimensionsChanged)
		{
			clearMapPreview();
		}
		if (customArtPackPathChanged)
		{
			redrawWithClearedImageCache();
		}
		else
		{
			handleMapChange();
		}
	}

	private void updateUndoRedoButtons()
	{
		undoButton.setEnabled(!undoStack.isEmpty());
		redoButton.setEnabled(!redoStack.isEmpty());
	}

	private void redrawWithClearedImageCache()
	{
		String reasonPreviewCannotBeDrawn = getReasonPreviewCannotBeDrawn();
		if (reasonPreviewCannotBeDrawn != null)
		{
			ImageCache.clear();
			showPreviewCannotBeDrawn(reasonPreviewCannotBeDrawn);
			return;
		}
		clearMessageFromPreview();
		enableOrDisableProgressBar(true);
		updater.createAndShowMapFull(() ->
		{
			ImageCache.clear();
		});
	}

	private void randomizeTheme()
	{
		if (themeLoadError != null)
		{
			showThemeLoadError();
			return;
		}
		settings.artPack = (String) artPackComboBox.getSelectedItem();
		// The controls at the top of the dialog are the user's, so the theme is varied for them, and they keep their values.
		settings.cityIconTypeName = (String) cityIconsTypeComboBox.getSelectedItem();
		long newThemeSeed = new Random().nextLong();
		try
		{
			SettingsGenerator.randomizeTheme(settings, themeBase, settings.artPack, settings.artPack, newThemeSeed);
		}
		catch (RuntimeException e)
		{
			SwingHelper.handleException(e, this, false);
			return;
		}
		themeSeed = newThemeSeed;
		if (sameThemeChoice != null && sameThemeChoice.equals(themeComboBox.getSelectedItem()))
		{
			isSameThemeRandomized = true;
		}
		handleMapChange();
	}

	private void randomizeLand()
	{
		SettingsGenerator.randomizeLand(settings);
		handleMapChange();
	}

	private void createMapEditingPanel()
	{
		BufferedImage placeHolder = AwtBridge.toBufferedImage(ImageHelper.getInstance().createPlaceholderImage(new String[] { Translation.get("newSettingsDialog.drawing") },
				AwtBridge.fromAwtColor(SwingHelper.getTextColorForPlaceholderImages())));
		mapEditingPanel = new MapEditingPanel(placeHolder);

		mapEditingPanelContainer = new JPanel();
		mapEditingPanelContainer.setLayout(new FlowLayout(FlowLayout.CENTER));
		mapEditingPanelContainer.add(mapEditingPanel);
	}

	private void createMapUpdater()
	{
		updater = new MapUpdater(false)
		{

			@Override
			protected void onBeginDraw()
			{
			}

			@Override
			public MapSettings getSettingsFromGUI()
			{
				MapSettings settings = NewSettingsDialog.this.getSettingsFromGUI();

				// This is only the maximum size because I'm passing in
				// maxDimensions to MapCreator.create.
				settings.resolution = MapSettings.defaultResolution;

				return settings;
			}

			@Override
			protected void onFinishedDrawingFull(Image map, double mapResolution, boolean anotherDrawIsQueued, int borderWidthAsDrawn, List<String> warningMessages,
					List<nortantis.IconDrawer.CityIconRemovedForWater> citiesRemovedForWater, boolean wasTriggeredByUndoRedo)
			{
				if (mapEditingPanel.mapFromMapCreator != null && mapEditingPanel.mapFromMapCreator != map)
				{
					mapEditingPanel.mapFromMapCreator.close();
				}
				mapEditingPanel.mapFromMapCreator = map;
				onFinishedDrawingCommon(anotherDrawIsQueued);
			}

			@Override
			protected void onFinishedDrawingIncremental(boolean anotherDrawIsQueued, int borderWidthAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
				onFinishedDrawingCommon(anotherDrawIsQueued);
			}

			private void onFinishedDrawingCommon(boolean anotherDrawIsQueued)
			{
				// A draw that was already running or queued when the map stopped being drawable drew settings that no longer apply.
				String reasonPreviewCannotBeDrawn = getReasonPreviewCannotBeDrawn();
				if (reasonPreviewCannotBeDrawn != null)
				{
					showMessageInPreview(reasonPreviewCannotBeDrawn);
				}
				else
				{
					mapEditingPanel.setImage(AwtBridge.toBufferedImage(mapEditingPanel.mapFromMapCreator));
					isPreviewShowingMessage = false;
				}

				if (!anotherDrawIsQueued)
				{
					enableOrDisableProgressBar(false);
				}

				NewSettingsDialog.this.revalidate();
				NewSettingsDialog.this.repaint();
			}

			@Override
			protected void onFailedToDraw(Exception exception)
			{
				enableOrDisableProgressBar(false);
				if (exception != null)
				{
					showMessageInPreview(Translation.get("newSettingsDialog.previewFailedToDraw"));
					SwingHelper.handleException(exception, NewSettingsDialog.this, false);
				}
			}

			@Override
			protected MapEdits getEdits()
			{
				return settings.edits;
			}

			@Override
			protected Image getCurrentMapForIncrementalUpdate()
			{
				return mapEditingPanel.mapFromMapCreator;
			}

		};
		updater.setEnabled(false);
	}

	private nortantis.geom.Dimension getMapDrawingAreaSize()
	{
		final int additionalWidthToRemoveIDontKnowWhereItsComingFrom = 10;
		return new nortantis.geom.Dimension((mapEditingPanelContainer.getSize().width - additionalWidthToRemoveIDontKnowWhereItsComingFrom) * mapEditingPanel.osScale,
				(mapEditingPanelContainer.getSize().height - additionalWidthToRemoveIDontKnowWhereItsComingFrom) * mapEditingPanel.osScale);

	}

	private void updateLandShapeTooltip()
	{
		LandShape selected = (LandShape) landShapeComboBox.getSelectedItem();
		landShapeComboBox.setToolTipText(selected == null ? null : selected.getDescription());
	}

	private void loadSettingsIntoGUI(MapSettings settings)
	{
		GeneratedDimension dim = GeneratedDimension.fromDimensions(settings.generatedWidth, settings.generatedHeight);
		dimensionsComboBox.setSelectedItem(dim);
		if (dim == GeneratedDimension.Custom)
		{
			customWidthSpinner.setValue(settings.generatedWidth);
			customHeightSpinner.setValue(settings.generatedHeight);
			updateCustomDimPreview();
		}
		customDimPreviewHider.setVisible(dim == GeneratedDimension.Custom);
		worldSizeSlider.setValue(settings.worldSize);
		if (settings.landShape != null)
		{
			landShapeComboBox.setSelectedItem(settings.landShape);
		}
		else
		{
			landShapeComboBox.setSelectedItem(LandShape.Continents);
		}
		regionCountSlider.setValue(settings.regionCount > 0 ? settings.regionCount : 3);
		cityFrequencySlider.setValue((int) (settings.cityProbability * cityFrequencySliderScale));
		initializeArtPackOptionsAndCityTypeOptions();
		cityIconsTypeComboBox.setSelectedItem(settings.cityIconTypeName);

		booksWidget.checkSelectedBooks(settings.books);

		updatePathDisplay();
		updateRotationWarning();
	}

	private MapSettings getSettingsFromGUI()
	{
		MapSettings resultSettings = settings.deepCopy();
		resultSettings.worldSize = worldSizeSlider.getValue();
		resultSettings.landShape = (LandShape) landShapeComboBox.getSelectedItem();
		resultSettings.regionCount = regionCountSlider.getValue();

		Dimension generatedDimensions = getGeneratedBackgroundDimensionsFromGUI();
		resultSettings.generatedWidth = (int) generatedDimensions.getWidth();
		resultSettings.generatedHeight = (int) generatedDimensions.getHeight();

		resultSettings.books = booksWidget.getSelectedBooks();

		resultSettings.cityProbability = cityFrequencySlider.getValue() / cityFrequencySliderScale;
		resultSettings.cityIconTypeName = (String) cityIconsTypeComboBox.getSelectedItem();
		resultSettings.artPack = (String) artPackComboBox.getSelectedItem();

		return resultSettings;
	}

	private Dimension getGeneratedBackgroundDimensionsFromGUI()
	{
		GeneratedDimension selected = (GeneratedDimension) dimensionsComboBox.getSelectedItem();
		if (selected == GeneratedDimension.Custom)
		{
			return normalizeCustomDimensions(((Number) customWidthSpinner.getValue()).intValue(), ((Number) customHeightSpinner.getValue()).intValue());
		}
		return new Dimension(selected.width, selected.height);
	}

	private void clearMapPreview()
	{
		// Loading settings into the controls fires the dimension controls' listeners even when the dimensions don't change.
		if (!updater.isEnabled())
		{
			return;
		}
		updater.cancel();
		mapEditingPanel.setImage(null);
		isPreviewShowingMessage = false;
	}

	private static Dimension normalizeCustomDimensions(int w, int h)
	{
		nortantis.geom.IntDimension normalized = GeneratedDimension.normalizeToPresetScale(w, h);
		return new Dimension(normalized.width, normalized.height);
	}

	private void updateCustomDimPreview()
	{
		int w = ((Number) customWidthSpinner.getValue()).intValue();
		int h = ((Number) customHeightSpinner.getValue()).intValue();
		Dimension norm = normalizeCustomDimensions(w, h);
		customDimPreviewLabel.setText("(" + (int) norm.getWidth() + " \u00d7 " + (int) norm.getHeight() + ")");
	}

	private void enableOrDisableProgressBar(boolean enable)
	{
		if (enable)
		{
			progressBarTimer.start();
		}
		else
		{
			progressBarTimer.stop();
			progressBar.setVisible(false);
		}
	}

	private void updateRotationWarning()
	{
		if (settings != null && settings.rightRotationCount != 0 && !dimensionsComboBox.getSelectedItem().equals(GeneratedDimension.Square))
		{
			int degrees = settings.rightRotationCount * 90;
			rotationWarningLabel.setText(Translation.get("newSettingsDialog.rotationWarning", String.valueOf(degrees)));
			rotationWarningHider.setVisible(true);
		}
		else
		{
			rotationWarningHider.setVisible(false);
		}
	}

	public void createMapChangeListener(Component component)
	{
		SwingHelper.addListener(component, () -> handleMapChange());
	}

	public void handleMapChange()
	{
		// Suppress redraws while the updater is disabled (e.g. during loadSettingsIntoGUI, which fires
		// many field-change listeners as it populates the controls). Because the redraw is deferred to a
		// later EDT cycle, checking enabled only inside the deferred body would be too late: the body runs
		// after the constructor re-enables the updater, so every one of those load-time changes would draw.
		// Checking synchronously here, the way MainWindow's synchronous listeners do, drops them.
		if (!updater.isEnabled())
		{
			return;
		}
		scheduleUndoStep();
		String reasonPreviewCannotBeDrawn = getReasonPreviewCannotBeDrawn();
		if (reasonPreviewCannotBeDrawn != null)
		{
			showPreviewCannotBeDrawn(reasonPreviewCannotBeDrawn);
			return;
		}
		clearMessageFromPreview();
		// Defer to the next EDT cycle so that any row visibility changes (e.g. custom dimension
		// spinners, rotation warning) have been laid out before we read the container size.
		// Without this, getMapDrawingAreaSize() returns the stale pre-layout dimensions, causing
		// the preview to render too large and be clipped at the bottom.
		SwingUtilities.invokeLater(() ->
		{
			nortantis.geom.Dimension size = getMapDrawingAreaSize();
			if (size != null && size.width > 0.0 && size.height > 0.0)
			{
				lastDrawnMapAreaSize = size;
				updater.setMaxMapSize(size);
				enableOrDisableProgressBar(true);
				updater.createAndShowMapFull();
			}
		});
	}

	/**
	 * Redraws the preview if the map drawing area has changed size since the last draw. Used by the resize handler, which fires several
	 * times (including redundantly with an unchanged size) while the dialog is first shown and would otherwise draw the preview twice.
	 */
	private void handleResize()
	{
		String reasonPreviewCannotBeDrawn = getReasonPreviewCannotBeDrawn();
		if (reasonPreviewCannotBeDrawn != null)
		{
			showPreviewCannotBeDrawn(reasonPreviewCannotBeDrawn);
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			nortantis.geom.Dimension size = getMapDrawingAreaSize();
			if (size != null && size.width > 0.0 && size.height > 0.0 && !size.equals(lastDrawnMapAreaSize))
			{
				lastDrawnMapAreaSize = size;
				updater.setMaxMapSize(size);
				enableOrDisableProgressBar(true);
				updater.createAndShowMapFull();
			}
		});
	}
}
