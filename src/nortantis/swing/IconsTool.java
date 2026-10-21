package nortantis.swing;

import nortantis.*;
import nortantis.editor.*;
import nortantis.geom.IntDimension;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.platform.*;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.*;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class IconsTool extends EditorTool
{
	private JToggleButton mountainsButton;
	private JToggleButton treesButton;
	private BrushSelector brushSelector;
	/**
	 * The press being held with rectangle selection in Edit mode, or null if there is none.
	 */
	private RectangleSelectDrag rectangleSelectDrag;
	/**
	 * The selection when the current rectangle selection began, which a rectangle drawn with the command key held adds to or removes from.
	 */
	private Set<FreeIcon> selectionBeforeRectangleSelect;
	/**
	 * Whether the command key was held during the latest press or drag of the current rectangle selection.
	 */
	private boolean isCommandKeyDownDuringRectangleSelect;
	/**
	 * Whether Alt was held during the latest press or drag of the current rectangle selection.
	 */
	private boolean isAltDownDuringRectangleSelect;
	/**
	 * A press with the command key held on an icon, which toggles or removes it when released, or null if there is none.
	 */
	private CommandClick<FreeIcon> commandClick;
	private final ToggledItemsUnderMouse<FreeIcon> toggledIconsUnderMouse = new ToggledItemsUnderMouse<>();
	/**
	 * Whether a press in Edit mode with a brush rather than rectangle selection is being held, so that dragging changes the selection by
	 * what the brush passes over.
	 */
	private boolean isSelectingByBrush;
	private SelectionDisplay selectionDisplay;
	/**
	 * Whether each icon checked since the map was last shown is in a valid position. That depends only on the icon and the map, and checking
	 * it is slow enough that redoing it for a large selection on every mouse event lags, so it is cached until the map is shown again.
	 *
	 * <p>
	 * It is cleared in {@link #onAfterShowMap()}, which runs after every draw while this tool is selected, and in {@link #onSwitchingTo()},
	 * for draws made while another tool was. This relies on every change to the map that can move an icon onto or off water or the map
	 * going through a draw. {@code MainWindow} skips {@code onAfterShowMap} while the sub-map selection box is showing. The only draw
	 * possible then is a display quality change, which doesn't change whether an icon is valid (see {@link IconDrawer#isValidPosition}).
	 * Only what is shown uses this cache. Anything that changes the map or its edits calls {@link IconDrawer#isValidPosition} directly.
	 */
	private final Map<FreeIcon, Boolean> isValidPositionCache = new HashMap<>();
	private JToggleButton hillsButton;
	private JToggleButton dunesButton;
	private IconTypeButtons mountainTypes;
	private IconTypeButtons hillTypes;
	private IconTypeButtons duneTypes;
	private IconTypeButtons treeTypes;
	private NamedIconSelector cityButtons;
	private NamedIconSelector decorationButtons;
	private JSlider densitySlider;
	private Random rand;
	private RowHider densityHider;
	private JToggleButton citiesButton;
	private JToggleButton decorationsButton;
	private SegmentedButtonWidget iconTypeWidget;
	private DrawModeWidget modeWidget;
	/**
	 * Center indices that were under the brush during the previous mouse event of the current drag stroke (Draw and Replace modes). A polygon
	 * is drawn or replaced when it first comes under the brush, then skipped while it stays under the brush, so dragging in place doesn't
	 * reshuffle its icon to a different random image on every mouse move. Once a polygon leaves the brush and comes back it is drawn again.
	 * Replaced with the current selection on each event, and cleared at the start of each press. Used in Draw mode, where whole polygons are
	 * drawn.
	 */
	private final Set<Integer> centerIndicesUnderBrushInPreviousDragEvent = new HashSet<>();
	/**
	 * Icons (by identity) that were under the brush during the previous mouse event of the current Replace-mode drag stroke. Replace mode
	 * tracks individual icons rather than whole polygons, because a single polygon can hold several tree icons that slide under the brush at
	 * different moments and must each be replaced once. Since replacing swaps the icon object, the new (replacement) icon is what gets
	 * recorded, so it is recognized and skipped on the next event while it stays under the brush. Cleared at the start of each press.
	 */
	private final Set<FreeIcon> iconsUnderBrushInPreviousDragEvent = Collections.newSetFromMap(new IdentityHashMap<>());
	private Set<FreeIcon> iconsToEdit;
	/**
	 * The location where the mouse was pressed to begin a move or scale, stored in graph coordinates rather than panel pixels so it stays
	 * correct if the zoom changes mid-drag. A panel-pixel press point would map to a different graph location under the new zoom, corrupting
	 * the move delta and the scale ratio.
	 */
	private Point editStart;
	private boolean isMoving;
	private boolean isScaling;
	private JComboBox<String> artPackComboBox;
	private RowHider iconTypeButtonsHider;
	private JToggleButton mountainsToggle;
	private JToggleButton hillsToggle;
	private JToggleButton dunesToggle;
	private JToggleButton treesToggle;
	private JToggleButton citiesToggle;
	private JToggleButton decorationsToggle;
	private RowHider iconTypeCheckboxesHider;
	private RowHider colorPickerHider;
	private JPanel fillColorDisplay;
	private Map<IconType, Color> fillColorsByType;
	private Map<IconType, HSBColor> iconFilterColorsByType;
	private Map<IconType, Boolean> maximizeOpacityByType;
	private Map<IconType, Boolean> fillWithColorByType;
	private Map<IconType, Boolean> fillShowsTextureByType;
	private RowHider artPackComboBoxHider;
	private boolean disableImageRefreshes;
	private RowHider modeOptionsAndBrushSeparatorHider;
	private RowHider deleteCopyPasteIconButtonsHider;
	private JLabel groupLabel;
	private JLabel nameLabel;
	private RowHider iconMetadataHider;
	private JLabel artPackLabel;
	private JLabel typeLabel;
	private Set<FreeIcon> copied;
	private JCheckBox maximizeOpacityCheckbox;
	private JSlider hueSlider;
	private JSlider saturationSlider;
	private JSlider brightnessSlider;
	private JSlider transparencySlider;
	private SliderWithDisplayedValue hueSliderWithDisplay;
	private SliderWithDisplayedValue saturationSliderWithDisplay;
	private SliderWithDisplayedValue brightnessSliderWithDisplay;
	private SliderWithDisplayedValue transparencySliderWithDisplay;
	private boolean disableColorChangeHandlers;
	private RowHider brushAndEditOptionsSeparatorHider;
	private JCheckBox fillWithColorCheckbox;
	private JCheckBox fillShowsTextureCheckbox;
	private JButton resetScaleButton;
	private RowHider fillColorHider;
	private volatile long previewUpdateGeneration;

	public IconsTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);
		rand = new Random();
		fillColorsByType = new TreeMap<>();
		iconFilterColorsByType = new TreeMap<>();
		maximizeOpacityByType = new TreeMap<>();
		fillWithColorByType = new TreeMap<>();
		fillShowsTextureByType = new TreeMap<>();
		iconsToEdit = new HashSet<>();
		copied = new HashSet<>();
	}

	@Override
	public String getToolbarName()
	{
		return Translation.get("iconsTool.name");
	}

	@Override
	public int getMnemonic()
	{
		return KeyEvent.VK_2;
	}

	@Override
	public Image getToolIcon()
	{
		Image icon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal/Icon tool.png").toString());
		try (nortantis.platform.Painter p = icon.createPainter(DrawQuality.High))
		{
			String text = Translation.get("iconsTool.toolIcon");
			p.setColor(Color.black);
			drawCenteredToolIconText(p, icon, toolIconLabelFontSize, text, 48);
		}
		return icon;
	}

	@Override
	public void onSwitchingAway()
	{
		endRectangleSelect();
		mapEditingPanel.clearAllToolSpecificSelectionsAndHighlights();
		unselectAnyIconsBeingEdited();
		mapEditingPanel.repaint();
		updateTypePanels();
	}

	@Override
	protected JPanel createToolOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		modeWidget = new DrawModeWidget(Translation.get("iconsTool.drawMode"), Translation.get("iconsTool.eraseMode"), true, Translation.get("iconsTool.replaceMode"), true,
				Translation.get("iconsTool.moveMode"), () -> handleModeChanged());
		modeWidget.addToOrganizer(organizer, "");

		// Icon type buttons
		{
			ActionListener typeListener = new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			};

			mountainsButton = new JToggleButton(Translation.get("IconType.mountains"));
			mountainsButton.setSelected(true);
			mountainsButton.addActionListener(typeListener);

			hillsButton = new JToggleButton(Translation.get("IconType.hills"));
			hillsButton.addActionListener(typeListener);

			dunesButton = new JToggleButton(Translation.get("IconType.sand"));
			dunesButton.addActionListener(typeListener);

			treesButton = new JToggleButton(Translation.get("IconType.trees"));
			treesButton.addActionListener(typeListener);

			citiesButton = new JToggleButton(Translation.get("IconType.cities"));
			citiesButton.addActionListener(typeListener);

			decorationsButton = new JToggleButton(Translation.get("IconType.decorations"));
			decorationsButton.addActionListener(typeListener);

			iconTypeWidget = new SegmentedButtonWidget(List.of(mountainsButton, hillsButton, dunesButton, treesButton, citiesButton, decorationsButton));
			iconTypeButtonsHider = iconTypeWidget.addToOrganizer(organizer, Translation.get("iconsTool.type.label"), Translation.get("iconsTool.type.help"));
		}

		// Icon type toggle buttons for edit/erase mode filtering
		{
			mountainsToggle = new JToggleButton(Translation.get("IconType.mountains"));
			hillsToggle = new JToggleButton(Translation.get("IconType.hills"));
			dunesToggle = new JToggleButton(Translation.get("IconType.sand"));
			treesToggle = new JToggleButton(Translation.get("IconType.trees"));
			citiesToggle = new JToggleButton(Translation.get("IconType.cities"));
			decorationsToggle = new JToggleButton(Translation.get("IconType.decorations"));

			List<JToggleButton> toggles = List.of(mountainsToggle, hillsToggle, dunesToggle, treesToggle, citiesToggle, decorationsToggle);
			toggles.forEach(button -> button.setSelected(true));

			SegmentedButtonWidget iconTypeCheckboxesWidget = new SegmentedButtonWidget(toggles, true);
			iconTypeCheckboxesHider = iconTypeCheckboxesWidget.addToOrganizer(organizer, Translation.get("iconsTool.types.label"), Translation.get("iconsTool.types.help"));

			JButton selectAll = new JButton(Translation.get("iconsTool.selectAll"));
			selectAll.addActionListener(e -> toggles.forEach(button -> button.setSelected(true)));

			JButton deselectAll = new JButton(Translation.get("iconsTool.deselectAll"));
			deselectAll.addActionListener(e -> toggles.forEach(button -> button.setSelected(false)));

			iconTypeCheckboxesHider.add(organizer.addLabelAndComponentsHorizontal("", "", Arrays.asList(selectAll, deselectAll)));
			iconTypeCheckboxesHider.setVisible(false);
		}

		brushSelector = organizer.addBrushSelector(brushSizes, Translation.get("gridBagOrganizer.brush.label"));

		modeOptionsAndBrushSeparatorHider = organizer.addSeparator();

		{
			densitySlider = new JSlider(1, 50);
			densitySlider.setValue(7);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(densitySlider);
			densityHider = sliderWithDisplay.addToOrganizer(organizer, Translation.get("iconsTool.density.label"), "");
		}

		{
			typeLabel = new JLabel();
			iconMetadataHider = organizer.addLabelAndComponent(Translation.get("iconsTool.metadata.type") + " ", "The type of this icon.", typeLabel);
			artPackLabel = new JLabel();
			iconMetadataHider.add(organizer.addLabelAndComponent(Translation.get("iconsTool.metadata.artPack") + " ", "The art pack this icon is from.", artPackLabel, 0));
			groupLabel = new JLabel();
			iconMetadataHider.add(organizer.addLabelAndComponent(Translation.get("iconsTool.metadata.group") + " ", "The icon group folder this icon is from.", groupLabel, 0));
			nameLabel = new JLabel();
			iconMetadataHider.add(organizer.addLabelAndComponent(Translation.get("iconsTool.metadata.name") + " ", "The icon's file name, not including modifiers or the extension.", nameLabel, 0));
		}

		GridBagOrganizer colorOrganizer = new GridBagOrganizer();

		{
			maximizeOpacityCheckbox = new JCheckBox(Translation.get("iconsTool.maximizeOpacity"));
			maximizeOpacityCheckbox.setToolTipText(Translation.get("iconsTool.maximizeOpacity.tooltip"));
			maximizeOpacityCheckbox.addActionListener(new ActionListener()
			{

				@Override
				public void actionPerformed(ActionEvent e)
				{
					SwingHelper.setCheckBoxMixed(maximizeOpacityCheckbox, false);
					handleColorChange(WhatHSBColorFieldChanged.maximizeOpacity);
				}
			});
			colorOrganizer.addLeftAlignedComponent(maximizeOpacityCheckbox);
		}

		{
			final Integer labelWidth = 35;

			{
				hueSlider = new JSlider();
				hueSlider.setPaintLabels(false);
				hueSlider.setMinimum(0);
				hueSlider.setMaximum(360);
				hueSliderWithDisplay = new SliderWithDisplayedValue(hueSlider, null, () ->
				{
					handleColorChange(WhatHSBColorFieldChanged.Hue);
				}, labelWidth);

				JButton clearButton = new JButton("x");
				clearButton.setToolTipText(Translation.get("iconsTool.clearHue.tooltip"));
				SwingHelper.addListener(clearButton, () -> clearFilterColorField(hueSliderWithDisplay, WhatHSBColorFieldChanged.Hue));

				hueSliderWithDisplay.addToOrganizer(colorOrganizer, Translation.get("iconsTool.hue.label"), Translation.get("iconsTool.hue.help"), clearButton, 0, 0);
			}

			{
				saturationSlider = new JSlider();
				saturationSlider.setPaintLabels(false);
				saturationSlider.setMinimum(-100);
				saturationSlider.setMaximum(100);
				saturationSliderWithDisplay = new SliderWithDisplayedValue(saturationSlider, null, () ->
				{
					handleColorChange(WhatHSBColorFieldChanged.Saturation);
				}, labelWidth);

				JButton clearButton = new JButton("x");
				clearButton.setToolTipText(Translation.get("iconsTool.clearSaturation.tooltip"));
				SwingHelper.addListener(clearButton, () -> clearFilterColorField(saturationSliderWithDisplay, WhatHSBColorFieldChanged.Saturation));

				saturationSliderWithDisplay.addToOrganizer(colorOrganizer, Translation.get("iconsTool.saturation.label"), Translation.get("iconsTool.saturation.help"), clearButton, 0, 0);
			}

			{
				brightnessSlider = new JSlider();
				brightnessSlider.setPaintLabels(false);
				brightnessSlider.setMinimum(-100);
				brightnessSlider.setMaximum(100);
				brightnessSliderWithDisplay = new SliderWithDisplayedValue(brightnessSlider, null, () ->
				{
					handleColorChange(WhatHSBColorFieldChanged.Brightness);
				}, labelWidth);

				JButton clearButton = new JButton("x");
				clearButton.setToolTipText(Translation.get("iconsTool.clearBrightness.tooltip"));
				SwingHelper.addListener(clearButton, () -> clearFilterColorField(brightnessSliderWithDisplay, WhatHSBColorFieldChanged.Brightness));

				brightnessSliderWithDisplay.addToOrganizer(colorOrganizer, Translation.get("iconsTool.brightness.label"), Translation.get("iconsTool.brightness.help"), clearButton, 0, 0);
			}

			{
				transparencySlider = new JSlider();
				transparencySlider.setPaintLabels(false);
				transparencySlider.setMinimum(0);
				transparencySlider.setMaximum(100);
				transparencySliderWithDisplay = new SliderWithDisplayedValue(transparencySlider, null, () ->
				{
					handleColorChange(WhatHSBColorFieldChanged.Transparency);
				}, labelWidth);

				JButton clearButton = new JButton("x");
				clearButton.setToolTipText(Translation.get("iconsTool.clearTransparency.tooltip"));
				SwingHelper.addListener(clearButton, () -> clearFilterColorField(transparencySliderWithDisplay, WhatHSBColorFieldChanged.Transparency));

				transparencySliderWithDisplay.addToOrganizer(colorOrganizer, Translation.get("iconsTool.transparency.label"), Translation.get("iconsTool.transparency.help"), clearButton, 0, 0);
			}
		}

		{
			fillWithColorCheckbox = new JCheckBox(Translation.get("iconsTool.fillWithColor"));
			fillWithColorCheckbox.addActionListener(e ->
			{
				SwingHelper.setCheckBoxMixed(fillWithColorCheckbox, false);
				fillColorHider.setVisible(fillWithColorCheckbox.isSelected());
				handleColorChange(WhatHSBColorFieldChanged.FillWithColorCheckbox);
			});
			colorOrganizer.addLeftAlignedComponent(fillWithColorCheckbox);
			colorOrganizer.addHorizontalSpacerRowToHelpComponentAlignment(0.66);

			fillColorDisplay = SwingHelper.createColorPickerPreviewPanel();
			JButton chooseColorButton = new JButton(Translation.get("common.choose"));
			chooseColorButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					showFillColorPicker();
				}
			});
			SwingHelper.addColorCopyAndPasteMenu(fillColorDisplay, fillColorDisplay::getBackground, color ->
			{
				fillColorDisplay.setBackground(color);
				SwingHelper.clearMixedColorsInColorPickerPreview(fillColorDisplay);
				handleColorChange(WhatHSBColorFieldChanged.FillColor);
			});
			fillColorHider = colorOrganizer.addLabelAndComponentsHorizontal(Translation.get("iconsTool.fillColor.label"), Translation.get("iconsTool.fillColor.help"),
					Arrays.asList(fillColorDisplay, chooseColorButton));

			fillShowsTextureCheckbox = new JCheckBox(Translation.get("iconsTool.fillShowsTexture"));
			fillShowsTextureCheckbox.setToolTipText(Translation.get("iconsTool.fillShowsTexture.tooltip"));
			fillShowsTextureCheckbox.addActionListener(e ->
			{
				SwingHelper.setCheckBoxMixed(fillShowsTextureCheckbox, false);
				handleColorChange(WhatHSBColorFieldChanged.FillShowsTexture);
			});
			fillColorHider.add(colorOrganizer.addLabelAndComponent("", "", fillShowsTextureCheckbox));
		}

		{
			JButton copyColorButton = new JButton(Translation.get("iconsTool.copyColorTo"));
			copyColorButton.setToolTipText(Translation.get("iconsTool.copyColorTo.tooltip"));
			copyColorButton.addActionListener(e -> showCopyColorDialog());
			colorOrganizer.addLeftAlignedComponents(Arrays.asList(copyColorButton));
		}

		CollapsiblePanel colorPanel = new CollapsiblePanel("color_options", "Color", Translation.get("iconsTool.colorOptions"), colorOrganizer.panel, true);
		colorPickerHider = organizer.addLeftAlignedComponent(colorPanel, GridBagOrganizer.rowVerticalInset, GridBagOrganizer.rowVerticalInset, false);

		brushAndEditOptionsSeparatorHider = organizer.addSeparator();

		{
			resetScaleButton = new JButton(Translation.get("iconsTool.resetScale"));
			resetScaleButton.setToolTipText(Translation.get("iconsTool.resetScale.tooltip"));
			resetScaleButton.addActionListener(ev -> clearScaleOnSelectedIcons());

			EditClipboardButtonsWidget editButtons = new EditClipboardButtonsWidget(this::copySelectedIcons, this::pasteSelectedIcons, this::deleteSelectedIcons,
					this::selectAllIcons, Translation.get("iconsTool.delete.tooltip"));
			deleteCopyPasteIconButtonsHider = editButtons.addToOrganizer(organizer, Arrays.asList(resetScaleButton));
		}

		{
			artPackComboBox = new ShrinkableComboBox<String>();
			updateArtPackOptions(Assets.installedArtPack, null);
			artPackComboBox.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					handleImagesRefresh(mainWindow.getSettingsFromGUI(false));
				}
			});
			artPackComboBoxHider = organizer.addLabelAndComponent(Translation.get("iconsTool.artPack.label"), Translation.get("iconsTool.artPack.help"), artPackComboBox);
		}

		mountainTypes =

				createOrUpdateRadioButtonsForIconType(organizer, IconType.mountains, mountainTypes, Assets.installedArtPack, null);
		hillTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.hills, hillTypes, Assets.installedArtPack, null);
		duneTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.sand, duneTypes, Assets.installedArtPack, null);
		treeTypes = createOrUpdateRadioButtonsForIconType(organizer, IconType.trees, treeTypes, Assets.installedArtPack, null);
		selectDefaultTreesButton(treeTypes);

		createOrUpdateButtonsForCities(organizer, Assets.installedArtPack, null);

		createOrUpdateDecorationButtons(organizer, Assets.installedArtPack, null);

		mountainsButton.doClick(0);

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.666);
		organizer.addVerticalFillerRow();

		SelectionCycling.addAltChangeListener(this::handleModifierKeyChange);
		selectionDisplay = new SelectionDisplay(this::isSelectingByDrag, () -> isMoving || isScaling, this::handleModifierKeyChange);

		return toolOptionsPanel;
	}

	/**
	 * Updates the selection's display, the hover preview, and any selection rectangle being dragged to match the command key and Alt.
	 */
	private void handleModifierKeyChange()
	{
		if (!isSelected())
		{
			return;
		}
		updateSelectionCycleCandidates(mapEditingPanel.getMousePosition());
		if (modeWidget.isEditMode() && !isMoving && !isScaling)
		{
			updater.doIfMapIsReadyForInteractions(() ->
			{
				if (rectangleSelectDrag != null && rectangleSelectDrag.isDragging())
				{
					isCommandKeyDownDuringRectangleSelect = selectionDisplay.isCommandKeyHeld();
					isAltDownDuringRectangleSelect = SelectionCycling.isAltDown();
					updateRectangleSelect(mapEditingPanel.getMousePosition());
				}
				else
				{
					showSelection();
					innerHandleMouseMovedOnMap(mapEditingPanel.getMousePosition());
				}
			});
		}
		mapEditingPanel.repaint();
	}

	/**
	 * Whether a selection rectangle is being dragged or a selecting brush stroke is in progress.
	 */
	private boolean isSelectingByDrag()
	{
		return isSelectingByBrush || (rectangleSelectDrag != null && rectangleSelectDrag.isDragging());
	}

	/**
	 * Shows the selection the way {@link SelectionDisplay} says to: the edit box with the move and scale handles, an outline around each
	 * selected icon, or both.
	 */
	private void showSelection()
	{
		SelectionDisplay.Shown shown = selectionDisplay.getShown(iconsToEdit.size());
		if (shown.showsBox())
		{
			boolean isValidPosition = iconsToEdit.stream().anyMatch(this::isValidPosition);
			mapEditingPanel.showIconEditToolsAt(iconsToEdit, isValidPosition);
		}
		else
		{
			mapEditingPanel.hideIconEditTools();
		}
		showSelectionOutlines(shown);
		mapEditingPanel.repaint();
	}

	/**
	 * Outlines each selected icon if the selection shows outlines. While the command key shows them, they preview what a click or the brush
	 * under the mouse would do, with the icons it would remove outlined in the color that previews removal.
	 */
	private void showSelectionOutlines(SelectionDisplay.Shown shown)
	{
		java.awt.Point mouseLocation = mapEditingPanel.getMousePosition();
		if (modeWidget.isEditMode() && selectionDisplay.isCommandKeyShowingOutlines() && !shown.showsBox() && !isSelectingByDrag() && mouseLocation != null)
		{
			boolean isAltDown = SelectionCycling.isAltDown();
			List<FreeIcon> after;
			if (getBrushDiameter() <= 1)
			{
				FreeIcon underMouse = getLowestIcon(toggledIconsUnderMouse.getItemsToPreview(getMultipleSelectedIcons(mouseLocation, null)));
				after = SelectionEdits.clicked(iconsToEdit, underMouse, true, isAltDown, HashSet::new);
			}
			else
			{
				after = SelectionEdits.area(iconsToEdit, getSelectedIcons(mouseLocation), true, isAltDown, HashSet::new);
			}
			mapEditingPanel.setHighlightedAreasFromIcons(after, this::isValidPosition);
			mapEditingPanel.setSelectionRemovePreviewFromIcons(SelectionEdits.change(iconsToEdit, after, HashSet::new).removed());
			return;
		}

		if (!shown.showsEachOutline())
		{
			mapEditingPanel.clearHighlightedAreas();
			return;
		}
		mapEditingPanel.setHighlightedAreasFromIcons(iconsToEdit, this::isValidPosition);
	}

	private boolean isValidPosition(FreeIcon icon)
	{
		return isValidPositionCache.computeIfAbsent(icon, updater.mapParts.iconDrawer::isValidPosition);
	}

	/**
	 * Replaces the selection, and shows it.
	 */
	private void setSelection(Collection<FreeIcon> icons)
	{
		Set<FreeIcon> newSelection = new HashSet<>(icons);
		iconsToEdit.clear();
		iconsToEdit.addAll(newSelection);
		handleIconSelectionChange();
	}

	private enum WhatHSBColorFieldChanged
	{
		maximizeOpacity, Hue, Saturation, Brightness, Transparency, FillColor, FillWithColorCheckbox, FillShowsTexture
	}

	private void deleteSelectedIcons()
	{
		if (modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			for (FreeIcon icon : iconsToEdit)
			{
				mainWindow.edits.freeIcons.remove(icon);
			}
			undoer.setUndoPoint(UpdateType.Incremental, this);
			updater.createAndShowMapIncrementalUsingIcons(new ArrayList<>(iconsToEdit));
			unselectAnyIconsBeingEdited();
		}
	}

	private void copySelectedIcons()
	{
		copied.clear();
		copied.addAll(iconsToEdit);
	}

	private void pasteSelectedIcons()
	{
		if (copied.isEmpty())
		{
			return;
		}

		List<FreeIcon> pasted = new ArrayList<>();
		double resolutionScale = mainWindow.displayQualityScale;
		Point temp = getPointOnGraph(mapEditingPanel.getMousePosition());
		Point graphPointMouseLocation = temp == null ? null : temp.mult(1.0 / resolutionScale);
		if (graphPointMouseLocation != null)
		{
			Rectangle bounds = mapEditingPanel.getIconEditBounds(copied);
			if (bounds == null)
			{
				return;
			}
			Point center = bounds.getCenter().mult(1.0 / resolutionScale);

			for (FreeIcon icon : copied)
			{
				Point newLocation = new Point(icon.locationResolutionInvariant.x - center.x + graphPointMouseLocation.x, icon.locationResolutionInvariant.y - center.y + graphPointMouseLocation.y);
				FreeIcon copied = icon.copyUnanchored().copyWithLocationResolutionInvariant(newLocation);
				mainWindow.edits.freeIcons.add(copied);
				pasted.add(copied);
			}

		}
		else
		{
			for (FreeIcon icon : copied)
			{
				final double offset = resolutionScale * 50;
				Point newLocation = new Point(icon.locationResolutionInvariant.x + offset, icon.locationResolutionInvariant.y + offset);
				FreeIcon copied = icon.copyUnanchored().copyWithLocationResolutionInvariant(newLocation);
				mainWindow.edits.freeIcons.add(copied);
				pasted.add(copied);

			}
		}

		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingIcons(pasted);
		endRectangleSelect();
		iconsToEdit.clear();
		iconsToEdit.addAll(pasted);
		selectionDisplay.onSelectionChangedByShortcut();
		handleIconSelectionChange();
		mapEditingPanel.repaint();
	}

	/**
	 * Selects every icon of the types the Types filter allows.
	 */
	private void selectAllIcons()
	{
		if (!modeWidget.isEditMode() || !updater.isMapReadyForInteractions() || isMoving || isScaling)
		{
			return;
		}

		endRectangleSelect();
		iconsToEdit.clear();
		mainWindow.edits.freeIcons.doWithLock(() ->
		{
			for (FreeIcon icon : mainWindow.edits.freeIcons)
			{
				if (isSelectedType(icon) && updater.mapParts.iconDrawer.toIconDrawTask(icon) != null)
				{
					iconsToEdit.add(icon);
				}
			}
		});
		selectionDisplay.onSelectionChangedByShortcut();
		handleIconSelectionChange();
		mapEditingPanel.repaint();
	}

	private void clearScaleOnSelectedIcons()
	{
		if (iconsToEdit == null || iconsToEdit.isEmpty())
		{
			return;
		}

		List<FreeIcon> updated = new ArrayList<>();
		Set<FreeIcon> unscaled = new HashSet<>();
		for (FreeIcon icon : iconsToEdit)
		{
			FreeIcon withoutScale = icon.copyWithScale(icon.originalScale);
			mainWindow.edits.freeIcons.replace(icon, withoutScale);
			updated.add(icon);
			updated.add(withoutScale);
			unscaled.add(withoutScale);
		}
		iconsToEdit.clear();
		iconsToEdit.addAll(unscaled);
		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingIcons(updated);
		handleIconSelectionChange();
	}

	private void handleIconSelectionChange()
	{
		if (!iconsToEdit.isEmpty())
		{
			if (iconsToEdit.size() == 1)
			{
				FreeIcon iconToEdit = iconsToEdit.iterator().next();
				typeLabel.setText(iconToEdit.type.getSingularNameForGUILowerCase());
				artPackLabel.setText(iconToEdit.artPack);
				groupLabel.setText(iconToEdit.groupId);
				if (!StringUtils.isEmpty(iconToEdit.iconName))
				{
					nameLabel.setText(iconToEdit.iconName);
				}
				else
				{
					List<String> fileNames = ImageCache.getInstance(iconToEdit.artPack, mainWindow.customArtPackPath).getIconGroupFileNamesWithoutWidthOrExtensionAsList(iconToEdit.type,
							iconToEdit.groupId);
					String iconName = fileNames.get(iconToEdit.iconIndex % fileNames.size());
					nameLabel.setText(iconName);
				}
				setColorFieldsWithoutRunningListeners(iconToEdit.fillColor, iconToEdit.filterColor, iconToEdit.maximizeOpacity, iconToEdit.fillWithColor, iconToEdit.fillShowsTexture);
			}
			else
			{
				typeLabel.setText(null);
				artPackLabel.setText(null);
				groupLabel.setText(null);
				nameLabel.setText(null);

				Color iconColorMode;
				{
					Counter<Color> counter = new HashCounter<Color>();
					iconsToEdit.stream().forEach(iconToEdit -> counter.incrementCount(iconToEdit.fillColor));
					iconColorMode = counter.argmax();
				}

				HSBColor filterColorMode;
				{
					Counter<HSBColor> counter = new ComparableCounter<>();
					iconsToEdit.stream().forEach(iconToEdit -> counter.incrementCount(iconToEdit.filterColor));
					filterColorMode = counter.argmax();
				}

				boolean maximizeOpacityMode;
				{
					Counter<Boolean> counter = new ComparableCounter<>();
					iconsToEdit.stream().forEach(iconToEdit -> counter.incrementCount(iconToEdit.maximizeOpacity));
					maximizeOpacityMode = counter.argmax();
				}

				boolean fillWithColorMode;
				{
					Counter<Boolean> counter = new ComparableCounter<>();
					iconsToEdit.stream().forEach(iconToEdit -> counter.incrementCount(iconToEdit.fillWithColor));
					fillWithColorMode = counter.argmax();
				}

				boolean fillShowsTextureMode;
				{
					Counter<Boolean> counter = new ComparableCounter<>();
					iconsToEdit.stream().forEach(iconToEdit -> counter.incrementCount(iconToEdit.fillShowsTexture));
					fillShowsTextureMode = counter.argmax();
				}

				setColorFieldsWithoutRunningListeners(iconColorMode, filterColorMode, maximizeOpacityMode, fillWithColorMode, fillShowsTextureMode);
				showWhichColorOptionsAreMixed();

				// The most common fill color comes first, so that it is the one the color picker starts on.
				List<java.awt.Color> fillColors = new ArrayList<>();
				fillColors.add(AwtBridge.toAwtColor(iconColorMode == null ? MapSettings.defaultIconFillColor : iconColorMode));
				for (FreeIcon iconToEdit : iconsToEdit)
				{
					fillColors.add(AwtBridge.toAwtColor(iconToEdit.fillColor == null ? MapSettings.defaultIconFillColor : iconToEdit.fillColor));
				}
				SwingHelper.showColorsInColorPickerPreview(fillColorDisplay, fillColors);

			}

			if (DebugFlags.printIconsBeingEdited())
			{
				for (FreeIcon iconToEdit : iconsToEdit)
				{
					System.out.println("Icon being edited: " + iconToEdit);
				}
			}
			fillColorDisplay.repaint();
			getToolOptionsPane().repaint();
		}
		else
		{
			editStart = null;

			typeLabel.setText(null);
			artPackLabel.setText(null);
			groupLabel.setText(null);
			nameLabel.setText(null);

			isMoving = false;
			isScaling = false;
			setColorFieldsWithoutRunningListeners(null, null, false, false, false);
		}

		showSelection();
		showOrHideEditComponents();
		updateResetScaleButtonEnabled();
	}

	/**
	 * Enables the Reset Scale button only when it would change one of the icons being edited.
	 */
	private void updateResetScaleButtonEnabled()
	{
		resetScaleButton.setEnabled(iconsToEdit != null && iconsToEdit.stream().anyMatch(icon -> icon.scale != icon.originalScale));
	}

	/**
	 * Shows which color options the icons being edited don't all share: a dash on the slider of each such part of the filter color, which
	 * keeps the value it was given, and a mixed state on each such checkbox.
	 */
	private void showWhichColorOptionsAreMixed()
	{
		SwingHelper.setCheckBoxMixed(maximizeOpacityCheckbox, iconsToEdit.stream().map(icon -> icon.maximizeOpacity).distinct().count() > 1);
		boolean isFillWithColorMixed = iconsToEdit.stream().map(icon -> icon.fillWithColor).distinct().count() > 1;
		SwingHelper.setCheckBoxMixed(fillWithColorCheckbox, isFillWithColorMixed);
		if (isFillWithColorMixed)
		{
			fillColorHider.setVisible(true);
		}
		SwingHelper.setCheckBoxMixed(fillShowsTextureCheckbox, iconsToEdit.stream().map(icon -> icon.fillShowsTexture).distinct().count() > 1);
		hueSliderWithDisplay.setMixed(iconsToEdit.stream().map(icon -> getFilterColorOrDefault(icon).hue).distinct().count() > 1);
		saturationSliderWithDisplay.setMixed(iconsToEdit.stream().map(icon -> getFilterColorOrDefault(icon).saturation).distinct().count() > 1);
		brightnessSliderWithDisplay.setMixed(iconsToEdit.stream().map(icon -> getFilterColorOrDefault(icon).brightness).distinct().count() > 1);
		transparencySliderWithDisplay.setMixed(iconsToEdit.stream().map(icon -> getFilterColorOrDefault(icon).transparency).distinct().count() > 1);
	}

	/**
	 * Whether the given icon being edited is to be filled with color: as the checkbox says, or as the icon already is while the checkbox
	 * stands for icons that differ.
	 */
	private boolean getFillWithColorToUse(FreeIcon icon)
	{
		return SwingHelper.isCheckBoxMixed(fillWithColorCheckbox) ? icon.fillWithColor : fillWithColorCheckbox.isSelected();
	}

	/**
	 * Whether the given icon being edited is to have a fill that shows the background texture: as the checkbox says, or as the icon already
	 * is while the checkbox stands for icons that differ.
	 */
	private boolean getFillShowsTextureToUse(FreeIcon icon)
	{
		return SwingHelper.isCheckBoxMixed(fillShowsTextureCheckbox) ? icon.fillShowsTexture : fillShowsTextureCheckbox.isSelected();
	}

	private static HSBColor getFilterColorOrDefault(FreeIcon icon)
	{
		return icon.filterColor == null ? MapSettings.defaultIconFilterColor : icon.filterColor;
	}

	/**
	 * Sets a part of the filter color to 0. A slider already at 0 while showing several values doesn't report a change, so the change is
	 * applied directly.
	 */
	private void clearFilterColorField(SliderWithDisplayedValue sliderWithDisplay, WhatHSBColorFieldChanged whatChanged)
	{
		if (sliderWithDisplay.slider.getValue() == 0 && sliderWithDisplay.isMixed())
		{
			sliderWithDisplay.setMixed(false);
			handleColorChange(whatChanged);
		}
		else
		{
			sliderWithDisplay.slider.setValue(0);
		}
	}

	private HSBColor getFilterColor()
	{
		return new HSBColor(hueSlider.getValue(), saturationSlider.getValue(), brightnessSlider.getValue(), transparencySlider.getValue());
	}

	/**
	 * Shows the fill color picker. In Edit mode with icons selected, the selected icons are redrawn with the color as it changes, and
	 * canceling restores each icon's original colors.
	 */
	private void showFillColorPicker()
	{
		if (!(modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty()))
		{
			SwingHelper.showColorPicker(getToolOptionsPane(), fillColorDisplay, Translation.get("iconsTool.fillColor.title"), () ->
			{
				SwingHelper.clearMixedColorsInColorPickerPreview(fillColorDisplay);
				handleColorChange(WhatHSBColorFieldChanged.FillColor);
			});
			return;
		}

		// An undo or edit that is waiting for the current draw to finish could otherwise run while the picker is open and change the
		// selected icons.
		updater.doAfterActionsWaitingForDrawing(() -> SwingUtilities.invokeLater(() ->
		{
			if (!(modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty()))
			{
				return;
			}
			List<FreeIcon> originalIcons = new ArrayList<>(iconsToEdit);
			// The version of each original icon that the map currently holds, in the same order as originalIcons.
			List<FreeIcon> shownIcons = new ArrayList<>(originalIcons);
			Consumer<List<FreeIcon>> showIcons = iconsToShow ->
			{
				for (int i = 0; i < shownIcons.size(); i++)
				{
					mainWindow.edits.freeIcons.replace(shownIcons.get(i), iconsToShow.get(i));
				}
				shownIcons.clear();
				shownIcons.addAll(iconsToShow);
				iconsToEdit.clear();
				iconsToEdit.addAll(iconsToShow);
				updater.createAndShowMapIncrementalUsingIcons(new ArrayList<>(iconsToShow), () -> ImageCache.clearColoredAndScaledImageCaches());
			};

			Runnable redrawAction = () ->
			{
				Color fillColor = AwtBridge.fromAwtColor(fillColorDisplay.getBackground());
				List<FreeIcon> recolored = new ArrayList<>();
				for (FreeIcon icon : originalIcons)
				{
					recolored.add(icon.copyWithColors(fillColor, icon.filterColor, icon.maximizeOpacity, getFillWithColorToUse(icon), icon.fillShowsTexture));
				}
				showIcons.accept(recolored);
			};
			SwingHelper.showColorPickerWithLiveMapPreview(fillColorDisplay, Translation.get("iconsTool.fillColor.title"), redrawAction,
					() -> undoer.setUndoPoint(UpdateType.Incremental, this), () -> showIcons.accept(originalIcons));
		}));
	}

	private void handleColorChange(WhatHSBColorFieldChanged whatChanged)
	{
		if (disableColorChangeHandlers)
		{
			return;
		}

		if (modeWidget.isDrawMode() || modeWidget.isReplaceMode())
		{
			IconType selectedType = getSelectedIconType();
			fillColorsByType.put(selectedType, AwtBridge.fromAwtColor(fillColorDisplay.getBackground()));
			iconFilterColorsByType.put(selectedType, getFilterColor());
			maximizeOpacityByType.put(selectedType, maximizeOpacityCheckbox.isSelected());
			fillWithColorByType.put(selectedType, fillWithColorCheckbox.isSelected());
			fillShowsTextureByType.put(selectedType, fillShowsTextureCheckbox.isSelected());
			ImageCache.clearColoredAndScaledImageCaches();

			updatePreviewImagesAfterColorChange();

			undoer.setUndoPoint(UpdateType.NoDraw, IconsTool.this, () ->
			{
				updateIconTypeButtonPreviewImages(mainWindow.getSettingsFromGUI(false));
			});
		}
		else if (modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			List<FreeIcon> updated = new ArrayList<>();
			for (FreeIcon iconToEdit : iconsToEdit)
			{
				HSBColor filterColorToUse;
				HSBColor filterColorFromUI = getFilterColor();
				switch (whatChanged)
				{
					case maximizeOpacity:
						filterColorToUse = iconToEdit.filterColor;
						break;
					case Hue:
						filterColorToUse = iconToEdit.filterColor.copyWithHue(filterColorFromUI.hue);
						break;
					case Saturation:
						filterColorToUse = iconToEdit.filterColor.copyWithSaturation(filterColorFromUI.saturation);
						break;
					case Brightness:
						filterColorToUse = iconToEdit.filterColor.copyWithBrightness(filterColorFromUI.brightness);
						break;
					case Transparency:
						filterColorToUse = iconToEdit.filterColor.copyWithTransparency(filterColorFromUI.transparency);
						break;
					case FillColor:
					case FillWithColorCheckbox:
					case FillShowsTexture:
						filterColorToUse = iconToEdit.filterColor;
						break;
					default:
						throw new UnsupportedOperationException("Unimplemented filter value change case " + whatChanged);
				}
				boolean maximizeOpacityToUse = whatChanged == WhatHSBColorFieldChanged.maximizeOpacity ? maximizeOpacityCheckbox.isSelected() : iconToEdit.maximizeOpacity;
				boolean fillWithColorToUse = (whatChanged == WhatHSBColorFieldChanged.FillColor || whatChanged == WhatHSBColorFieldChanged.FillWithColorCheckbox)
						? getFillWithColorToUse(iconToEdit)
						: iconToEdit.fillWithColor;
				boolean fillShowsTextureToUse = whatChanged == WhatHSBColorFieldChanged.FillShowsTexture ? getFillShowsTextureToUse(iconToEdit) : iconToEdit.fillShowsTexture;
				Color fillColorToUse = whatChanged == WhatHSBColorFieldChanged.FillColor ? AwtBridge.fromAwtColor(fillColorDisplay.getBackground()) : iconToEdit.fillColor;
				FreeIcon updatedIcon = iconToEdit.copyWithColors(fillColorToUse, filterColorToUse, maximizeOpacityToUse, fillWithColorToUse, fillShowsTextureToUse);
				mainWindow.edits.freeIcons.replace(iconToEdit, updatedIcon);
				updated.add(updatedIcon);
			}
			iconsToEdit.clear();
			iconsToEdit.addAll(updated);
			undoer.setUndoPoint(UpdateType.Incremental, this);
			updater.createAndShowMapIncrementalUsingIcons(new ArrayList<>(iconsToEdit), () -> ImageCache.clearColoredAndScaledImageCaches());
		}

	}

	/**
	 * Updates the preview images on the type buttons to show the colors chosen for each type.
	 */
	private void updatePreviewImagesAfterColorChange()
	{
		MapSettings settings = mainWindow.getSettingsFromGUI(false);
		// If the art pack drop down has changed since the last time we updated preview buttons, update them again so that
		// updateIconTypeButtonPreviewImages doesn't crash when it can't find the buttons for the preview images.
		if (!settings.artPack.equals(decorationButtons.artPack))
		{
			updateIconPreviewButtons(settings.artPack, settings.customArtPackPath);
		}
		updateIconTypeButtonPreviewImages(settings);
	}

	/**
	 * Shows the dialog that copies the colors shown in the Color card to the colors of other types of icons. It starts with the type being
	 * changed checked, or in Edit mode, the types of the icons being edited.
	 */
	private void showCopyColorDialog()
	{
		Set<IconType> initialTypes = EnumSet.noneOf(IconType.class);
		if (modeWidget.isEditMode())
		{
			for (FreeIcon icon : iconsToEdit)
			{
				initialTypes.add(icon.type);
			}
		}
		else
		{
			initialTypes.add(getSelectedIconType());
		}
		IconColors source = new IconColors(AwtBridge.fromAwtColor(fillColorDisplay.getBackground()), getFilterColor(), maximizeOpacityCheckbox.isSelected(),
				fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
		new CopyIconColorDialog(mainWindow, initialTypes, choice -> copyColor(source, choice)).setVisible(true);
	}

	/**
	 * Copies the chosen parts of the given colors to the colors of the chosen types, and when chosen, to the icons of those types on the
	 * map. Sets an undo point, and redraws the icons that changed.
	 */
	private void copyColor(IconColors source, CopyIconColorDialog.Choice choice)
	{
		boolean copiesFilterColor = choice.parts().contains(CopyIconColorDialog.Part.FilterColor);
		boolean copiesFillColor = choice.parts().contains(CopyIconColorDialog.Part.FillColor);
		for (IconType type : choice.types())
		{
			if (copiesFilterColor)
			{
				iconFilterColorsByType.put(type, source.filterColor);
				maximizeOpacityByType.put(type, source.maximizeOpacity);
			}
			if (copiesFillColor)
			{
				fillColorsByType.put(type, source.fillColor);
				fillWithColorByType.put(type, source.fillWithColor);
				fillShowsTextureByType.put(type, source.fillShowsTexture);
			}
		}

		List<FreeIcon> recoloredIcons = new ArrayList<>();
		if (choice.recolorsIconsOnMap())
		{
			List<FreeIcon> iconsOfChosenTypes = new ArrayList<>();
			mainWindow.edits.freeIcons.doWithLock(() ->
			{
				for (FreeIcon icon : mainWindow.edits.freeIcons)
				{
					if (choice.types().contains(icon.type))
					{
						iconsOfChosenTypes.add(icon);
					}
				}
			});
			for (FreeIcon icon : iconsOfChosenTypes)
			{
				FreeIcon recolored = icon.copyWithColors(copiesFillColor ? source.fillColor : icon.fillColor, copiesFilterColor ? source.filterColor : icon.filterColor,
						copiesFilterColor ? source.maximizeOpacity : icon.maximizeOpacity, copiesFillColor ? source.fillWithColor : icon.fillWithColor,
						copiesFillColor ? source.fillShowsTexture : icon.fillShowsTexture);
				if (!recolored.equals(icon))
				{
					mainWindow.edits.freeIcons.replace(icon, recolored);
					recoloredIcons.add(recolored);
					if (iconsToEdit.remove(icon))
					{
						iconsToEdit.add(recolored);
					}
				}
			}
		}

		ImageCache.clearColoredAndScaledImageCaches();
		updatePreviewImagesAfterColorChange();
		Runnable updatePreviewsOnUndoOrRedo = () -> updateIconTypeButtonPreviewImages(mainWindow.getSettingsFromGUI(false));
		if (recoloredIcons.isEmpty())
		{
			undoer.setUndoPoint(UpdateType.NoDraw, this, updatePreviewsOnUndoOrRedo);
		}
		else
		{
			undoer.setUndoPoint(UpdateType.Incremental, this, updatePreviewsOnUndoOrRedo);
			updater.createAndShowMapIncrementalUsingIcons(recoloredIcons, () -> ImageCache.clearColoredAndScaledImageCaches());
		}

		if (modeWidget.isEditMode())
		{
			handleIconSelectionChange();
		}
		else
		{
			setColorForSelectedType();
		}
	}

	private void setColorForSelectedType()
	{
		if (fillColorsByType == null || iconFilterColorsByType == null || maximizeOpacityByType == null || fillWithColorByType == null || fillShowsTextureByType == null)
		{
			return;
		}

		if (modeWidget.isDrawMode() || modeWidget.isReplaceMode())
		{
			IconType selectedType = getSelectedIconType();
			setColorFieldsWithoutRunningListeners(fillColorsByType.get(selectedType), iconFilterColorsByType.get(selectedType), Boolean.TRUE.equals(maximizeOpacityByType.get(selectedType)),
					Boolean.TRUE.equals(fillWithColorByType.get(selectedType)), getFillShowsTextureForType(selectedType));
		}
	}

	private boolean getFillShowsTextureForType(IconType type)
	{
		return fillShowsTextureByType.getOrDefault(type, MapSettings.defaultFillShowsTexture);
	}

	private void setColorFieldsWithoutRunningListeners(Color iconColor, HSBColor filterColor, boolean maximizeOpacity, boolean fillWithColor, boolean fillShowsTexture)
	{
		if (filterColor == null)
		{
			filterColor = MapSettings.defaultIconFilterColor;
		}

		if (iconColor == null)
		{
			iconColor = MapSettings.defaultIconFillColor;
		}

		try
		{
			disableColorChangeHandlers = true;

			fillColorDisplay.setBackground(AwtBridge.toAwtColor(iconColor));
			SwingHelper.clearMixedColorsInColorPickerPreview(fillColorDisplay);

			hueSlider.setValue(filterColor.hue);
			saturationSlider.setValue(filterColor.saturation);
			brightnessSlider.setValue(filterColor.brightness);
			transparencySlider.setValue(filterColor.transparency);
			// Setting a slider to the value it already has doesn't clear its mixed state.
			hueSliderWithDisplay.setMixed(false);
			saturationSliderWithDisplay.setMixed(false);
			brightnessSliderWithDisplay.setMixed(false);
			transparencySliderWithDisplay.setMixed(false);

			SwingHelper.setCheckBoxMixed(maximizeOpacityCheckbox, false);
			SwingHelper.setCheckBoxMixed(fillWithColorCheckbox, false);
			SwingHelper.setCheckBoxMixed(fillShowsTextureCheckbox, false);
			maximizeOpacityCheckbox.setSelected(maximizeOpacity);
			fillWithColorCheckbox.setSelected(fillWithColor);
			fillShowsTextureCheckbox.setSelected(fillShowsTexture);
			fillColorHider.setVisible(fillWithColor);
		}
		finally
		{
			disableColorChangeHandlers = false;
		}
	}

	/**
	 * Prevents cacti from being the default tree brush
	 */
	private void selectDefaultTreesButton(IconTypeButtons typeButtons)
	{
		if (typeButtons.buttons.size() > 1 && typeButtons.buttons.get(0).getText().equals("cacti"))
		{
			typeButtons.buttons.get(1).getRadioButton().setSelected(true);
		}
		else if (typeButtons.buttons.size() > 0)
		{
			typeButtons.buttons.get(0).getRadioButton().setSelected(true);
		}
	}

	private void handleModeChanged()
	{
		endRectangleSelect();
		unselectAnyIconsBeingEdited();
		// Update the panels first, since they choose the brush the hover highlight below uses.
		updateTypePanels();
		innerHandleMouseMovedOnMap(mapEditingPanel.getMousePosition());
		mapEditingPanel.repaint();
	}

	private void showOrHideEditComponents()
	{
		modeOptionsAndBrushSeparatorHider.setVisible((modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty()) || modeWidget.isDrawMode() || modeWidget.isReplaceMode());
		brushAndEditOptionsSeparatorHider.setVisible(modeWidget.isEditMode() || modeWidget.isDrawMode() || modeWidget.isReplaceMode());
		iconMetadataHider.setVisible(modeWidget.isEditMode() && iconsToEdit != null && iconsToEdit.size() == 1);
		colorPickerHider.setVisible(modeWidget.isDrawMode() || modeWidget.isReplaceMode() || modeWidget.isEditMode() && !iconsToEdit.isEmpty());
		deleteCopyPasteIconButtonsHider.setVisible(modeWidget.isEditMode());
	}

	private void showOrHideBrush(java.awt.Point mouseLocation)
	{
		int brushDiameter = getBrushDiameter();
		if (modeWidget.isDrawMode() || brushDiameter <= 1 || (modeWidget.isEditMode() && (mapEditingPanel.isInMoveTool(mouseLocation) || mapEditingPanel.isInScaleTool(mouseLocation))))
		{
			mapEditingPanel.hideBrush();
		}
		else
		{
			mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			mapEditingPanel.repaint();
		}

	}

	private void updateTypePanels()
	{
		mountainTypes.hider.setVisible(mountainsButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		hillTypes.hider.setVisible(hillsButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		duneTypes.hider.setVisible(dunesButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		treeTypes.hider.setVisible(treesButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		cityButtons.hider.setVisible(citiesButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		decorationButtons.hider.setVisible(decorationsButton.isSelected() && (modeWidget.isDrawMode() || modeWidget.isReplaceMode()));
		densityHider.setVisible(treesButton.isSelected() && (modeWidget.isDrawMode()));
		brushSelector.setOffersRectangleSelect(modeWidget.isEditMode());
		brushSelector.setVisible(
				(modeWidget.isDrawMode() && !citiesButton.isSelected() && !decorationsButton.isSelected()) || modeWidget.isReplaceMode() || modeWidget.isEraseMode() || modeWidget.isEditMode());
		// Disable color change handlers so they don't set undo points because of automatic changes.
		setColorForSelectedType();
		artPackComboBoxHider.setVisible(modeWidget.isDrawMode() || modeWidget.isReplaceMode());
		iconTypeButtonsHider.setVisible(modeWidget.isDrawMode() || modeWidget.isReplaceMode());
		iconTypeCheckboxesHider.setVisible(modeWidget.isEditMode() || modeWidget.isEraseMode());
		showOrHideEditComponents();

		toolsPanel.revalidate();
		toolsPanel.repaint();
	}

	private IconTypeButtons createOrUpdateRadioButtonsForIconType(GridBagOrganizer organizer, IconType iconType, IconTypeButtons existing, String artPack, String customArtPackPath)
	{
		String prevSelection = existing != null ? existing.getSelectedOption() : null;

		ButtonGroup group = new ButtonGroup();
		List<RadioButtonWithImage> radioButtons = new ArrayList<>();
		List<String> groupNames = new ArrayList<>(ImageCache.getInstance(artPack, customArtPackPath).getIconGroupNames(iconType));
		for (String groupName : groupNames)
		{
			RadioButtonWithImage button = new RadioButtonWithImage(groupName, null);
			group.add(button.getRadioButton());
			radioButtons.add(button);
		}

		List<? extends Component> listToUse = radioButtons.size() > 0 ? radioButtons
				: Arrays.asList(new JLabel("<html>" + Translation.get("iconsTool.artPackHasNoType", artPack, iconType.getNameForGUI()) + "</html>"));
		IconTypeButtons result;
		if (existing == null)
		{
			JPanel buttonsPanel = new JPanel();
			result = new IconTypeButtons(organizer.addLabelAndComponentsVerticalWithComponentPanel(iconType.getNameForGUI() + ":", "", listToUse, buttonsPanel), radioButtons, buttonsPanel);
		}
		else
		{
			result = existing;
			existing.buttons = radioButtons;
			GridBagOrganizer.updateComponentsPanelVertical(listToUse, existing.buttonsPanel);
		}

		if (prevSelection == null || !result.selectButtonIfPresent(prevSelection))
		{
			if (radioButtons.size() > 0)
			{
				if (iconType == IconType.trees)
				{
					selectDefaultTreesButton(result);
				}
				else
				{
					radioButtons.get(0).getRadioButton().setSelected(true);
				}
			}
		}

		return result;
	}

	@Override
	public void handleImagesRefresh(MapSettings settings)
	{
		if (disableImageRefreshes)
		{
			return;
		}

		String artPack = settings == null ? Assets.installedArtPack : settings.artPack;
		String customArtPackPath = settings == null ? null : settings.customArtPackPath;
		updateIconPreviewButtons(artPack, customArtPackPath);

		// When no map is open (e.g. a look-and-feel change on the startup screen) there are no settings to load into the GUI, and
		// loadSettingsIntoGUI dereferences settings unconditionally. Guarding here keeps the refresh from throwing and aborting the rest
		// of the caller's work - notably MainWindow.handleLookAndFeelChange, which rebuilds the support card right after this.
		if (settings != null)
		{
			// Trigger re-creation of image previews
			loadSettingsIntoGUI(settings, false, true);
		}
		unselectAnyIconsBeingEdited();
	}

	private void updateIconPreviewButtons(String artPack, String customArtPackPath)
	{
		mountainTypes = createOrUpdateRadioButtonsForIconType(null, IconType.mountains, mountainTypes, artPack, customArtPackPath);
		hillTypes = createOrUpdateRadioButtonsForIconType(null, IconType.hills, hillTypes, artPack, customArtPackPath);
		duneTypes = createOrUpdateRadioButtonsForIconType(null, IconType.sand, duneTypes, artPack, customArtPackPath);
		treeTypes = createOrUpdateRadioButtonsForIconType(null, IconType.trees, treeTypes, artPack, customArtPackPath);

		createOrUpdateButtonsForCities(null, artPack, customArtPackPath);
		createOrUpdateDecorationButtons(null, artPack, customArtPackPath);
	}

	private synchronized void updateIconTypeButtonPreviewImages(MapSettings settings)
	{
		if (settings == null)
		{
			return;
		}
		previewUpdateGeneration++;
		long generation = previewUpdateGeneration;
		updateOneIconTypeButtonPreviewImages(settings, IconType.mountains, mountainTypes, settings.customArtPackPath, generation);
		updateOneIconTypeButtonPreviewImages(settings, IconType.hills, hillTypes, settings.customArtPackPath, generation);
		updateOneIconTypeButtonPreviewImages(settings, IconType.sand, duneTypes, settings.customArtPackPath, generation);
		updateOneIconTypeButtonPreviewImages(settings, IconType.trees, treeTypes, settings.customArtPackPath, generation);

		updateNamedIconButtonPreviewImages(settings, cityButtons, generation);
		updateNamedIconButtonPreviewImages(settings, decorationButtons, generation);
	}

	private void updateOneIconTypeButtonPreviewImages(MapSettings settings, IconType iconType, IconTypeButtons buttons, String customArtPackPath, long generation)
	{
		for (RadioButtonWithImage button : buttons.buttons)
		{
			Color iconColor = fillColorsByType.get(iconType);
			HSBColor filterColor = iconFilterColorsByType.get(iconType);
			boolean maximizeOpacity = Boolean.TRUE.equals(maximizeOpacityByType.get(iconType));
			boolean fillWithColor = Boolean.TRUE.equals(fillWithColorByType.get(iconType));
			boolean fillShowsTexture = getFillShowsTextureForType(iconType);

			final String buttonText = button.getText();
			SwingWorker<Image, Void> worker = new SwingWorker<>()
			{
				@Override
				protected Image doInBackground() throws Exception
				{
					if (generation != previewUpdateGeneration)
					{
						return null;
					}
					return createIconPreviewForGroup(settings, iconType, buttonText, customArtPackPath, iconColor, filterColor, maximizeOpacity, fillWithColor, fillShowsTexture);
				}

				@Override
				public void done()
				{
					if (generation != previewUpdateGeneration)
					{
						return;
					}

					Image previewImage;
					try
					{
						previewImage = get();
					}
					catch (InterruptedException | ExecutionException e)
					{
						String message = "Error while creating preview images for buttons: " + e.getMessage();
						Logger.printError(message, e);
						e.printStackTrace();
						SwingHelper.showMessageDialog(IconsTool.this.mainWindow, message, "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}

					if (previewImage != null)
					{
						button.setImage(AwtBridge.toBufferedImage(previewImage));
					}
				}
			};

			worker.execute();
		}
	}

	private void updateNamedIconButtonPreviewImages(MapSettings settings, NamedIconSelector selector, long generation)
	{
		if (settings == null)
		{
			return;
		}

		if (!Objects.equals(settings.artPack, selector.artPack))
		{
			throw new IllegalArgumentException(

					"Cannot updated NamedIconsSelector because selector has buttons for different art pack than settings. Settings art pack: '" + settings.artPack + "', selector art pack: '"
							+ selector.artPack + "'");
		}

		for (String groupId : ImageCache.getInstance(settings.artPack, settings.customArtPackPath).getIconGroupNames(selector.type))
		{
			final List<Tuple2<String, UnscaledImageToggleButton>> namesAndButtons = selector.getIconNamesAndButtons(groupId);
			Color iconColor = fillColorsByType.get(selector.type);
			HSBColor filterColor = iconFilterColorsByType.get(selector.type);
			boolean maximizeOpacity = Boolean.TRUE.equals(maximizeOpacityByType.get(selector.type));
			boolean fillWithColor = Boolean.TRUE.equals(fillWithColorByType.get(selector.type));
			boolean fillShowsTexture = getFillShowsTextureForType(selector.type);

			if (namesAndButtons != null)
			{
				SwingWorker<List<Image>, Void> worker = new SwingWorker<>()
				{
					@Override
					protected List<Image> doInBackground() throws Exception
					{
						if (generation != previewUpdateGeneration)
						{
							return null;
						}

						List<Image> previewImages = new ArrayList<>();
						Map<String, ImageAndMasks> iconsInGroup = ImageCache.getInstance(settings.artPack, settings.customArtPackPath).getIconsByNameForGroup(selector.type, groupId);

						for (Tuple2<String, UnscaledImageToggleButton> nameAndButton : namesAndButtons)
						{
							String iconNameWithoutWidthOrExtension = nameAndButton.getFirst();
							if (!iconsInGroup.containsKey(iconNameWithoutWidthOrExtension))
							{
								throw new IllegalArgumentException("No '" + selector.type + "' icon exists for the button '" + iconNameWithoutWidthOrExtension + "'");
							}
							ImageAndMasks imageAndMasks = iconsInGroup.get(iconNameWithoutWidthOrExtension);
							Image preview = createIconPreview(settings, Collections.singletonList(imageAndMasks), 45, 0, selector.type, iconColor, filterColor, maximizeOpacity, fillWithColor,
									fillShowsTexture);

							previewImages.add(preview);
						}

						return previewImages;
					}

					@Override
					public void done()
					{
						if (generation != previewUpdateGeneration)
						{
							return;
						}

						List<Image> previewImages;
						try
						{
							previewImages = get();
						}
						catch (InterruptedException | ExecutionException e)
						{
							String message = "Error while creating preview images for buttons: " + e.getMessage();
							Logger.printError(message, e);
							e.printStackTrace();
							SwingHelper.showMessageDialog(IconsTool.this.mainWindow, message, "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}

						if (previewImages == null)
						{
							return;
						}

						for (int i : new Range(previewImages.size()))
						{
							try
							{
								selector.getIconNamesAndButtons(groupId).get(i).getSecond().setIcon(new ImageIcon(AwtBridge.toBufferedImage(previewImages.get(i))));
							}
							catch (NullPointerException ex)
							{
								Logger.println("While updating icon preview images, the image selectors did not contain group ID: " + groupId
										+ ". If icon previews don't update correctly, try refreshing the map (ctrl+R).");
							}
							catch (IndexOutOfBoundsException ex)
							{
								Logger.println("While updating icon preview images, the image selectors did not have the correct expected of buttons for: " + groupId
										+ ". If icon previews don't update correctly, try refreshing the map (ctrl+R).");
							}
						}
					}
				};

				worker.execute();
			}
		}
	}

	private void updateArtPackOptions(String selectedArtPack, String customArtPackPath)
	{
		SwingHelper.initializeComboBoxItems(artPackComboBox, Assets.listArtPacks(!StringUtils.isEmpty(customArtPackPath)), selectedArtPack, false);
	}

	private void createOrUpdateButtonsForCities(GridBagOrganizer organizer, String artPack, String customArtPackPath)
	{
		if (cityButtons == null)
		{
			// This is the first time to create the city buttons.
			cityButtons = new NamedIconSelector(IconType.cities);
			cityButtons.addToOrganizer(organizer, Translation.get("iconsTool.cities.label"));
		}

		cityButtons.updateButtonList(artPack, customArtPackPath);
	}

	private void createOrUpdateDecorationButtons(GridBagOrganizer organizer, String artPack, String customArtPackPath)
	{
		if (decorationButtons == null)
		{
			// This is the first time to create the city buttons.
			decorationButtons = new NamedIconSelector(IconType.decorations);
			decorationButtons.addToOrganizer(organizer, Translation.get("iconsTool.decorations.label"));
		}

		decorationButtons.updateButtonList(artPack, customArtPackPath);
	}

	private Image createIconPreviewForGroup(MapSettings settings, IconType iconType, String groupName, String customArtPackPath, Color iconColor, HSBColor filterColor, boolean maximizeOpacity,
			boolean fillWithColor, boolean fillShowsTexture)
	{
		List<ImageAndMasks> images = ImageCache.getInstance(settings.artPack, customArtPackPath).getIconsInGroup(iconType, groupName);
		int scaledHeight;
		switch (iconType)
		{
			case mountains:
				scaledHeight = 35;
				break;
			case hills:
			case sand:
				scaledHeight = 25;
				break;
			default:
				scaledHeight = 35;
				break;
		}
		return createIconPreview(settings, images, scaledHeight, 9, iconType, iconColor, filterColor, maximizeOpacity, fillWithColor, fillShowsTexture);
	}

	private static Image createIconPreview(MapSettings settings, List<ImageAndMasks> imagesAndMasks, int scaledHeight, int padding, IconType iconType, Color iconColor, HSBColor filterColor,
			boolean maximizeOpacity, boolean fillWithColor, boolean fillShowsTexture)
	{
		final double osScaling = SwingHelper.getOSScale();
		final int maxRowWidth = (int) (159 * osScaling);
		final int horizontalPaddingBetweenImages = (int) (2 * osScaling);

		padding = (int) (padding * osScaling);
		scaledHeight = (int) Math.round(scaledHeight * osScaling);

		// Pre-compute the scaled width for each icon using content bounds.
		// This uses the same formula as ImageHelper.getWidthWhenScaledByHeight.
		int[] scaledWidths = new int[imagesAndMasks.size()];
		for (int i : new Range(imagesAndMasks.size()))
		{
			IntDimension croppedSize = imagesAndMasks.get(i).getOrCreateContentBounds().size();
			int widthForHeight = Math.max(1, (int) Math.round(((double) croppedSize.width) / croppedSize.height * scaledHeight));
			scaledWidths[i] = Math.min(widthForHeight, maxRowWidth);
		}

		// Compute the layout positions for each icon. This determines both the
		// background size and where icons are drawn, ensuring they always match.
		int[] xPositions = new int[imagesAndMasks.size()];
		int[] yPositions = new int[imagesAndMasks.size()];
		int rowCount = 1;
		int largestRowWidth = 0;
		{
			int x = 0;
			int y = 0;
			for (int i : new Range(imagesAndMasks.size()))
			{
				if (x > 0 && x + scaledWidths[i] > maxRowWidth)
				{
					largestRowWidth = Math.max(largestRowWidth, x - horizontalPaddingBetweenImages);
					rowCount++;
					x = 0;
					y += scaledHeight;
				}
				xPositions[i] = x + padding;
				yPositions[i] = y + padding;
				x += scaledWidths[i] + horizontalPaddingBetweenImages;
			}
			largestRowWidth = Math.max(largestRowWidth, x > 0 ? x - horizontalPaddingBetweenImages : 0);
		}

		// Create the background image for the preview
		final int fadeWidth = Math.max(padding - 2, 0);

		IntDimension size = new IntDimension(Math.min(maxRowWidth, largestRowWidth) + (padding * 2), (rowCount * scaledHeight) + (padding * 2));

		Image previewImage;

		Path backgroundImagePath = settings.getBackgroundImagePath().getFirst();
		Tuple4<Image, ImageHelper.ColorizeAlgorithm, Image, ImageHelper.ColorizeAlgorithm> tuple = ThemePanel.createBackgroundImageDisplayImages(size, settings.backgroundRandomSeed,
				settings.colorizeOcean, settings.colorizeLand, settings.generateBackground, settings.generateBackgroundFromTexture, settings.solidColorBackground,
				backgroundImagePath == null ? null : backgroundImagePath.toString());
		if (iconType == IconType.decorations)
		{
			previewImage = tuple.getFirst();
			previewImage = ImageHelper.getInstance().colorize(previewImage, settings.oceanColor, tuple.getSecond());
		}
		else
		{
			previewImage = tuple.getThird();
			previewImage = ImageHelper.getInstance().colorize(previewImage, settings.landColor, tuple.getFourth());
		}

		previewImage = fadeEdges(previewImage, fadeWidth);

		// On the map, a fill that shows the texture is colored from the texture the ocean is colored from, and a solid color background has
		// no texture.
		Image paperTexture = settings.solidColorBackground ? null : tuple.getFirst();
		ImageHelper.ColorizeAlgorithm paperTextureColorizeAlgorithm = tuple.getSecond();
		boolean isFillFromTexture = fillWithColor && fillShowsTexture && iconColor.getAlpha() > 0;
		try (Painter p = previewImage.createPainter())
		{
			for (int i : new Range(imagesAndMasks.size()))
			{
				ImageAndMasks imageAndMasks = imagesAndMasks.get(i);
				Image coloredImage = ImageCache.getInstance(settings.artPack, settings.customArtPackPath).getColoredIcon(imageAndMasks, iconColor, filterColor, maximizeOpacity,
						fillWithColor && !fillShowsTexture);
				try (Image croppedImage = imageAndMasks.cropToContent(coloredImage))
				{
					int scaledWidth = scaledWidths[i];
					int yExtraForCentering = 0;
					if (scaledHeight > ImageHelper.getInstance().getHeightWhenScaledByWidth(croppedImage, scaledWidth))
					{
						yExtraForCentering = (scaledHeight - ImageHelper.getInstance().getHeightWhenScaledByWidth(croppedImage, scaledWidth)) / 2;
					}
					int x = xPositions[i];
					int y = yPositions[i] + yExtraForCentering;
					if (isFillFromTexture)
					{
						try (Image croppedFillMask = imageAndMasks.cropToContent(imageAndMasks.getOrCreateColorMask());
								Image scaledFillMask = ImageHelper.getInstance().scaleByWidth(croppedFillMask, scaledWidth, ScalingMethod.UltraQuality);
								Image fill = ImageHelper.getInstance().createTextureFill(scaledFillMask, paperTexture, x, y, iconColor, paperTextureColorizeAlgorithm))
						{
							p.drawImage(fill, x, y);
						}
					}
					try (Image scaled = ImageHelper.getInstance().scaleByWidth(croppedImage, scaledWidth, ScalingMethod.UltraQuality))
					{
						p.drawImage(scaled, x, y);
					}
				}
			}
		}

		return previewImage;
	}

	static Image fadeEdges(Image image, int fadeWidth)
	{
		try (Image box = Image.create(image.getWidth(), image.getHeight(), ImageType.Grayscale8Bit))
		{
			try (Painter p = box.createPainter())
			{
				p.setColor(Color.white);
				p.fillRect(fadeWidth, fadeWidth, image.getWidth() - fadeWidth * 2, image.getHeight() - fadeWidth * 2);
			}

			// Blur the box to get an alpha mask that fades to transparent at the edges. The blur must treat the outside of the
			// image as empty, or the edges would draw brightness from the opposite side and never reach transparent.
			try (Image hazyBox = ImageHelper.getInstance().blur(box, fadeWidth, true, true))
			{
				return ImageHelper.getInstance().setAlphaFromMask(image, hazyBox, false);
			}
		}
	}

	private void handleMousePressOrDrag(MouseEvent e, boolean isPress)
	{
		showOrHideBrush(e.getPoint());
		if (modeWidget.isDrawMode())
		{
			handleDrawIcons(e, isPress);
		}
		else if (modeWidget.isReplaceMode())
		{
			handleReplaceIcons(e);
		}
		else if (modeWidget.isEraseMode())
		{
			handleEraseIcons(e);
		}
		else if (modeWidget.isEditMode())
		{
			handleEditIcons(e, isPress);
		}
	}

	/**
	 * Given the centers currently under the brush, returns those that were not under the brush during the previous mouse event of this drag
	 * stroke (the ones to draw now), and records the current selection as the new previous-event set. So a polygon is drawn when it first
	 * comes under the brush and skipped while it stays under it, but drawn again if it leaves and returns. See
	 * {@link #centerIndicesUnderBrushInPreviousDragEvent}.
	 */
	private Set<Center> retainCentersNotUnderBrushLastEvent(Set<Center> currentlyUnderBrush)
	{
		Set<Center> result = new LinkedHashSet<>();
		for (Center center : currentlyUnderBrush)
		{
			if (!centerIndicesUnderBrushInPreviousDragEvent.contains(center.index))
			{
				result.add(center);
			}
		}

		centerIndicesUnderBrushInPreviousDragEvent.clear();
		for (Center center : currentlyUnderBrush)
		{
			centerIndicesUnderBrushInPreviousDragEvent.add(center.index);
		}

		return result;
	}

	private void handleDrawIcons(MouseEvent e, boolean isPress)
	{
		if (mountainsButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			String groupId = mountainTypes.getSelectedOption();
			if (!StringUtils.isEmpty(groupId))
			{
				selected = retainCentersNotUnderBrushLastEvent(selected);
				if (!selected.isEmpty())
				{
					for (Center center : selected)
					{
						CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
						CenterIcon newIcon = new CenterIcon(CenterIconType.Mountain, (String) artPackComboBox.getSelectedItem(), groupId, Helper.safeAbs(rand.nextInt()));
						mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(newIcon));
					}
					updater.createAndShowMapIncrementalUsingCenters(selected);
				}
			}
		}
		else if (hillsButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			String rangeId = hillTypes.getSelectedOption();
			if (!StringUtils.isEmpty(rangeId))
			{
				selected = retainCentersNotUnderBrushLastEvent(selected);
				if (!selected.isEmpty())
				{
					for (Center center : selected)
					{
						CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
						CenterIcon newIcon = new CenterIcon(CenterIconType.Hill, (String) artPackComboBox.getSelectedItem(), rangeId, Helper.safeAbs(rand.nextInt()));
						mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(newIcon));
					}
					updater.createAndShowMapIncrementalUsingCenters(selected);
				}
			}
		}
		else if (dunesButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			String groupId = duneTypes.getSelectedOption();
			if (!StringUtils.isEmpty(groupId))
			{
				selected = retainCentersNotUnderBrushLastEvent(selected);
				if (!selected.isEmpty())
				{
					for (Center center : selected)
					{
						CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
						CenterIcon newIcon = new CenterIcon(CenterIconType.Dune, (String) artPackComboBox.getSelectedItem(), groupId, Helper.safeAbs(rand.nextInt()));
						mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(newIcon));
					}
					updater.createAndShowMapIncrementalUsingCenters(selected);
				}
			}
		}
		else if (treesButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			String treeType = treeTypes.getSelectedOption();
			if (!StringUtils.isEmpty(treeType))
			{
				selected = retainCentersNotUnderBrushLastEvent(selected);
				if (!selected.isEmpty())
				{
					for (Center center : selected)
					{
						CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
						CenterTrees newTrees = new CenterTrees((String) artPackComboBox.getSelectedItem(), treeType, densitySlider.getValue() / 10.0, Helper.safeAbs(rand.nextLong()));
						mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithTrees(newTrees));
					}
					updater.createAndShowMapIncrementalUsingCenters(selected);
				}
			}
		}
		else if (citiesButton.isSelected())
		{
			Set<Center> selected = getSelectedLandCenters(e.getPoint());
			Tuple2<String, String> selectedCity = cityButtons.getSelectedButton();
			if (selectedCity == null)
			{
				return;
			}

			String cityType = selectedCity.getFirst();
			String cityName = selectedCity.getSecond();
			selected = retainCentersNotUnderBrushLastEvent(selected);
			if (!selected.isEmpty())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					CenterIcon cityIcon = new CenterIcon(CenterIconType.City, (String) artPackComboBox.getSelectedItem(), cityType, cityName);
					mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithIcon(cityIcon));
				}
				updater.createAndShowMapIncrementalUsingCenters(selected);
			}
		}
		else if (decorationsButton.isSelected())
		{
			if (isPress)
			{
				Tuple2<String, String> selectedButton = decorationButtons.getSelectedButton();
				if (selectedButton == null)
				{
					return;
				}

				String groupId = selectedButton.getFirst();
				String iconName = selectedButton.getSecond();
				nortantis.geom.Point point = getPointOnGraph(e.getPoint());
				FreeIcon icon = new FreeIcon(mainWindow.displayQualityScale, point, 1.0, IconType.decorations, (String) artPackComboBox.getSelectedItem(), groupId, iconName, null,
						getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(), maximizeOpacityCheckbox.isSelected(), fillWithColorCheckbox.isSelected(),
						fillShowsTextureCheckbox.isSelected());
				mainWindow.edits.freeIcons.add(icon);
				updater.createAndShowMapIncrementalUsingIcons(Arrays.asList(icon));
			}
		}
	}

	private Color getSelectedIconTypeColor()
	{
		IconType selectedType = getSelectedIconType();
		return fillColorsByType.get(selectedType);
	}

	private HSBColor getSelectedIconTypeFilterColor()
	{
		IconType selectedType = getSelectedIconType();
		return iconFilterColorsByType.get(selectedType);
	}

	private IconType getSelectedIconType()
	{
		if (mountainsButton.isSelected())
		{
			return IconType.mountains;
		}
		else if (hillsButton.isSelected())
		{
			return IconType.hills;
		}
		else if (dunesButton.isSelected())
		{
			return IconType.sand;
		}
		else if (treesButton.isSelected())
		{
			return IconType.trees;
		}
		else if (citiesButton.isSelected())
		{
			return IconType.cities;
		}
		else if (decorationsButton.isSelected())
		{
			return IconType.decorations;
		}
		else
		{
			throw new UnsupportedOperationException("Unrecognized icon type button.");
		}
	}

	private void handleReplaceIcons(MouseEvent e)
	{
		if (treesButton.isSelected())
		{
			replaceTreesThatFailedToDrawDueToLowDensity(e);
		}
		List<FreeIcon> iconsSelectedAfter = new ArrayList<>();

		List<FreeIcon> iconsBeforeAndAfterOuter = mainWindow.edits.freeIcons.doWithLockAndReturnResult(() ->
		{
			List<FreeIcon> icons = getSelectedIcons(e.getPoint());
			if (icons.isEmpty())
			{
				// Nothing under the brush, so anything re-entering it next event should be replaced again.
				iconsUnderBrushInPreviousDragEvent.clear();
				return icons;
			}

			List<FreeIcon> iconsBeforeAndAfter = new ArrayList<>();
			Set<FreeIcon> iconsUnderBrushThisEvent = Collections.newSetFromMap(new IdentityHashMap<>());

			for (FreeIcon before : icons)
			{
				// Skip replacing an icon that was already under the brush in the previous event, so dragging in place doesn't reshuffle it.
				// Still highlight the icon while it's under the brush, so it doesn't flash out as the mouse passes over it.
				if (iconsUnderBrushInPreviousDragEvent.contains(before))
				{
					iconsUnderBrushThisEvent.add(before);
					if (isSelected(e.getPoint(), before))
					{
						iconsSelectedAfter.add(before);
					}
					continue;
				}

				iconsBeforeAndAfter.add(before);
				// Remember this icon as under the brush this event. If it gets replaced below, the replacement is remembered instead, since
				// that is the object that will be under the brush next event.
				iconsUnderBrushThisEvent.add(before);

				FreeIcon after = null;
				if (mountainsButton.isSelected())
				{
					String groupId = mountainTypes.getSelectedOption();
					if (!StringUtils.isEmpty(groupId))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), groupId, Helper.safeAbs(rand.nextInt()), getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(),
								maximizeOpacityCheckbox.isSelected(), fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
					}
				}
				else if (hillsButton.isSelected())
				{
					String groupId = hillTypes.getSelectedOption();
					if (!StringUtils.isEmpty(groupId))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), groupId, Helper.safeAbs(rand.nextInt()), getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(),
								maximizeOpacityCheckbox.isSelected(), fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
					}
				}
				else if (dunesButton.isSelected())
				{
					String groupId = duneTypes.getSelectedOption();
					if (!StringUtils.isEmpty(groupId))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), groupId, Helper.safeAbs(rand.nextInt()), getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(),
								maximizeOpacityCheckbox.isSelected(), fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
					}
				}
				else if (treesButton.isSelected())
				{
					String treeType = treeTypes.getSelectedOption();
					if (!StringUtils.isEmpty(treeType))
					{
						after = before.copyWith((String) artPackComboBox.getSelectedItem(), treeType, Helper.safeAbs(rand.nextInt()), getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(),
								maximizeOpacityCheckbox.isSelected(), fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
					}
				}
				else if (citiesButton.isSelected())
				{
					Tuple2<String, String> selectedCity = cityButtons.getSelectedButton();
					if (selectedCity == null)
					{
						continue;
					}

					String cityType = selectedCity.getFirst();
					String cityName = selectedCity.getSecond();
					after = before.copyWith((String) artPackComboBox.getSelectedItem(), cityType, cityName, getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(),
							maximizeOpacityCheckbox.isSelected(), fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
				}
				else if (decorationsButton.isSelected())
				{
					Tuple2<String, String> selectedDecoration = decorationButtons.getSelectedButton();
					if (selectedDecoration == null)
					{
						continue;
					}

					String type = selectedDecoration.getFirst();
					String iconName = selectedDecoration.getSecond();
					after = before.copyWith((String) artPackComboBox.getSelectedItem(), type, iconName, getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(),
							maximizeOpacityCheckbox.isSelected(), fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
				}
				else
				{
					assert false;
					continue;
				}

				if (after != null)
				{
					mainWindow.edits.freeIcons.replace(before, after);
					iconsBeforeAndAfter.add(after);
					iconsUnderBrushThisEvent.remove(before);
					iconsUnderBrushThisEvent.add(after);
					if (isSelected(e.getPoint(), after))
					{
						iconsSelectedAfter.add(after);
					}
				}
			}

			// Record what was under the brush this event so those icons are skipped next event while they stay under it.
			iconsUnderBrushInPreviousDragEvent.clear();
			iconsUnderBrushInPreviousDragEvent.addAll(iconsUnderBrushThisEvent);

			return iconsBeforeAndAfter;
		});

		mapEditingPanel.setHighlightedAreasFromIcons(iconsSelectedAfter, updater.mapParts.iconDrawer, true);

		if (iconsBeforeAndAfterOuter != null && !iconsBeforeAndAfterOuter.isEmpty())
		{
			updater.createAndShowMapIncrementalUsingIcons(iconsBeforeAndAfterOuter);
		}
	}

	private void handleEraseIcons(MouseEvent e)
	{
		if (treesToggle.isSelected())
		{
			eraseTreesThatFailedToDrawDueToLowDensity(e);
		}

		List<FreeIcon> icons = mainWindow.edits.freeIcons.doWithLockAndReturnResult(() ->
		{
			List<FreeIcon> iconsInner = getSelectedIcons(e.getPoint());
			if (iconsInner.isEmpty())
			{
				return iconsInner;
			}

			mainWindow.edits.freeIcons.removeAll(iconsInner);
			return iconsInner;
		});

		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.repaint();

		if (icons != null && !icons.isEmpty())
		{
			Set<RotatedRectangle> processingAreas = icons.stream().map(icon -> updater.mapParts.iconDrawer.toIconDrawTask(icon)).filter(task -> task != null)
					.map(task -> new RotatedRectangle(task.getOrCreateContentBoundsPadded())).collect(Collectors.toSet());
			mapEditingPanel.addProcessingAreas(processingAreas);
			mapEditingPanel.repaint();
			updater.createAndShowMapIncrementalUsingIcons(icons, MapUpdater.afterMapDisplayed(() ->
			{
				mapEditingPanel.removeProcessingAreas(processingAreas);
				mapEditingPanel.repaint();
			}));
		}
	}

	private void handleEditIcons(MouseEvent e, boolean isPress)
	{
		if (isPress)
		{
			if (iconsToEdit != null && !iconsToEdit.isEmpty())
			{
				isMoving = mapEditingPanel.isInMoveTool(e.getPoint());
				isScaling = mapEditingPanel.isInScaleTool(e.getPoint());
				if (isMoving || isScaling)
				{
					editStart = getPointOnGraph(e.getPoint());
				}
				else
				{
					editStart = null;
				}
			}
			else
			{
				isMoving = false;
				isScaling = false;
				editStart = null;
			}
		}

		if (isMoving || isScaling)
		{
			updateMoveOrScalePreview(e.getPoint());
		}
		else if (isPress)
		{
			handleEditModeSelectingPress(e);
		}
		else if (rectangleSelectDrag != null)
		{
			isCommandKeyDownDuringRectangleSelect = SwingHelper.isCommandKeyDown(e);
			isAltDownDuringRectangleSelect = e.isAltDown();
			updateRectangleSelect(e.getPoint());
		}
		else if (commandClick != null)
		{
			if (commandClick.isDragAt(e.getPoint()))
			{
				// The press became a brush stroke, which begins with the icon pressed on.
				FreeIcon pressed = commandClick.getItem();
				commandClick = null;
				isSelectingByBrush = true;
				boolean isRemoving = SelectionEdits.isRemoving(SwingHelper.isCommandKeyDown(e), e.isAltDown());
				setSelection(SelectionEdits.area(iconsToEdit, Arrays.asList(pressed), true, isRemoving, HashSet::new));
				selectUnderBrushDuringStroke(e.getPoint(), e);
			}
		}
		else if (isSelectingByBrush)
		{
			selectUnderBrushDuringStroke(e.getPoint(), e);
		}

		mapEditingPanel.repaint();
	}

	/**
	 * Changes the selection for a press in Edit mode that isn't on one of the edit box's handles, following {@link SelectionEdits}. With the
	 * one-point brush, Alt without the command key selects the next of the icons under the mouse. A press with the command key on a single icon changes the
	 * selection only when released, in case the press becomes a drag.
	 */
	private void handleEditModeSelectingPress(MouseEvent e)
	{
		boolean isCommandKeyDown = SwingHelper.isCommandKeyDown(e);
		boolean isAltDown = e.isAltDown();
		if (isAltDown)
		{
			SelectionCycling.recordAltClick();
		}

		if (isAltDown && !isCommandKeyDown && getBrushDiameter() <= 1)
		{
			FreeIcon next = SelectionCycling.chooseNext(getSelectionCycleCandidates(e.getPoint()), iconsToEdit);
			setSelection(next == null ? new ArrayList<>() : Arrays.asList(next));
			updateSelectionCycleCandidates(e.getPoint());
			return;
		}

		if (getBrushDiameter() <= 1)
		{
			boolean isRectangleSelect = brushSelector.isRectangleSelect();
			if (isRectangleSelect)
			{
				rectangleSelectDrag = new RectangleSelectDrag(e.getPoint(), getPointRI(e.getPoint()));
				selectionBeforeRectangleSelect = new HashSet<>(iconsToEdit);
				isCommandKeyDownDuringRectangleSelect = isCommandKeyDown;
				isAltDownDuringRectangleSelect = isAltDown;
			}
			FreeIcon clicked = isCommandKeyDown ? getLowestIcon(toggledIconsUnderMouse.getItemsToClick(getMultipleSelectedIcons(e.getPoint(), null)))
					: getLowestSelectedIcon(e.getPoint(), null);
			if (isCommandKeyDown && clicked != null)
			{
				commandClick = new CommandClick<>(e.getPoint(), clicked, isAltDown);
				showSelection();
				return;
			}
			// A press with the one-point brush starts a brush stroke, which adds what it passes over.
			isSelectingByBrush = !isRectangleSelect;
			setSelection(SelectionEdits.clicked(iconsToEdit, clicked, isCommandKeyDown, isAltDown, HashSet::new));
			return;
		}

		isSelectingByBrush = true;
		setSelection(SelectionEdits.area(iconsToEdit, getSelectedIcons(e.getPoint()), isCommandKeyDown, isAltDown, HashSet::new));
	}

	/**
	 * Adds the icons under the brush at the given point to the selection during a brush stroke, or with the command key and Alt held,
	 * removes them.
	 */
	private void selectUnderBrushDuringStroke(java.awt.Point brushLocation, MouseEvent e)
	{
		boolean isRemoving = SelectionEdits.isRemoving(SwingHelper.isCommandKeyDown(e), e.isAltDown());
		setSelection(SelectionEdits.area(iconsToEdit, getSelectedIcons(brushLocation), true, isRemoving, HashSet::new));
	}

	/**
	 * Selects the icons the selection rectangle touches, once the mouse has moved far enough from the press for it to be a rectangle rather
	 * than a click, following {@link SelectionEdits#area}.
	 */
	private void updateRectangleSelect(java.awt.Point mouseLocation)
	{
		if (rectangleSelectDrag == null || mouseLocation == null)
		{
			return;
		}
		if (!rectangleSelectDrag.update(mouseLocation))
		{
			// Still a click.
			showSelection();
			return;
		}
		commandClick = null;

		Rectangle rectangleRI = rectangleSelectDrag.getRectangleRI(getPointRI(mouseLocation));
		mapEditingPanel.setRectangleSelection(rectangleRI);
		List<FreeIcon> touched = getIconsTouchingRectangle(rectangleRI.scaleAboutOrigin(mainWindow.displayQualityScale));
		setSelection(SelectionEdits.area(selectionBeforeRectangleSelect, touched, isCommandKeyDownDuringRectangleSelect, isAltDownDuringRectangleSelect, HashSet::new));
		mapEditingPanel.repaint();
	}

	/**
	 * Ends any selection rectangle, brush stroke, or press with the command key that is changing the selection.
	 */
	private void endRectangleSelect()
	{
		rectangleSelectDrag = null;
		selectionBeforeRectangleSelect = null;
		commandClick = null;
		isSelectingByBrush = false;
		mapEditingPanel.clearRectangleSelection();
	}

	/**
	 * The icons of the selected types whose bounds touch the given rectangle, which is in graph coordinates.
	 */
	private List<FreeIcon> getIconsTouchingRectangle(Rectangle rectangle)
	{
		List<FreeIcon> result = new ArrayList<>();
		mainWindow.edits.freeIcons.doWithLock(() ->
		{
			for (FreeIcon icon : mainWindow.edits.freeIcons)
			{
				if (!isSelectedType(icon))
				{
					continue;
				}
				IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
				if (task == null)
				{
					continue;
				}
				Rectangle bounds = task.getOrCreateContentBoundsPadded();
				// A direct bounds comparison rather than Rectangle.overlaps, which builds polygons for each test and is slow across
				// the thousands of icons a map can have, on every drag event.
				if (bounds.x <= rectangle.getRight() && bounds.getRight() >= rectangle.x && bounds.y <= rectangle.getBottom() && bounds.getBottom() >= rectangle.y)
				{
					result.add(icon);
				}
			}
		});
		return result;
	}


	/**
	 * Recomputes the in-progress move/scale preview (the highlighted icons and the edit box) from the current mouse location and the
	 * graph-coordinate press point. Called on each drag event, and also from {@link #onAfterShowMap()} so the preview follows a zoom change
	 * made mid-drag without requiring a mouse move.
	 */
	private void updateMoveOrScalePreview(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null || editStart == null || iconsToEdit == null || iconsToEdit.isEmpty())
		{
			return;
		}

		Point graphPointMouseLocation = getPointOnGraph(mouseLocation);
		Point graphPointMousePressedLocation = editStart;

		List<FreeIcon> updated = new ArrayList<>();

		if (isMoving)
		{
			double deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
			double deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);

			for (FreeIcon iconToEdit : iconsToEdit)
			{
				Point scaledOldLocation = iconToEdit.getScaledLocation(mainWindow.displayQualityScale);
				updated.add(iconToEdit.copyWithLocation(mainWindow.displayQualityScale, new Point(scaledOldLocation.x + deltaX, scaledOldLocation.y + deltaY)));
			}

		}
		else if (isScaling)
		{
			Rectangle iconEditBounds = mapEditingPanel.getIconEditBounds(iconsToEdit);
			double scale = calcScale(graphPointMouseLocation, graphPointMousePressedLocation, iconEditBounds);

			for (FreeIcon iconToEdit : iconsToEdit)
			{
				Rectangle imageBounds = updater.mapParts.iconDrawer.toIconDrawTask(iconToEdit).getOrCreateContentBoundsPadded();
				updated.add(iconToEdit.copyWithScale(iconToEdit.scale * floorWithMinScale(scale, imageBounds)));
			}
		}

		if (!updated.isEmpty())
		{
			if (selectionDisplay.getShown(updated.size()).showsEachOutline())
			{
				mapEditingPanel.setHighlightedAreasFromIcons(updated, updater.mapParts.iconDrawer, false);
			}
			else
			{
				mapEditingPanel.clearHighlightedAreas();
			}
			boolean isValidPosition = updated.stream().anyMatch(updater.mapParts.iconDrawer::isValidPosition);
			mapEditingPanel.showIconEditToolsAt(updated, isValidPosition);
		}

		showOrHideEditComponents();
	}

	private double calcScale(Point graphPointMouseLocation, Point graphPointMousePressedLocation, Rectangle iconEditBounds)
	{
		double scale = graphPointMouseLocation.distanceTo(iconEditBounds.getCenter()) / graphPointMousePressedLocation.distanceTo(iconEditBounds.getCenter());

		return floorWithMinScale(scale, iconEditBounds);
	}

	private double floorWithMinScale(double scale, Rectangle imageBounds)
	{
		final double minWidth = 8 * mainWindow.displayQualityScale;
		final double minHeight = 6 * mainWindow.displayQualityScale;
		final double minWidthByHeight = IconDrawer.getDimensionsWhenScaledByHeight(imageBounds.size().toIntDimension(), minHeight).width;

		double minScale = Math.min(1.0, Math.max(minWidth, minWidthByHeight) / imageBounds.width);
		return Math.max(scale, minScale);
	}

	private void handleFinishSelectingOrEditingIconsIfNeeded(MouseEvent e)
	{
		if (iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			if (isMoving || isScaling)
			{
				Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
				Point graphPointMousePressedLocation = editStart;
				List<FreeIcon> updated = new ArrayList<>();
				Rectangle iconEditBounds = mapEditingPanel.getIconEditBounds(iconsToEdit);

				for (FreeIcon iconToEdit : iconsToEdit)
				{
					if (isMoving)
					{
						double deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
						double deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);
						Point scaledOldLocation = iconToEdit.getScaledLocation(mainWindow.displayQualityScale);
						FreeIcon updatedIcon = iconToEdit.copyWithLocation(mainWindow.displayQualityScale, new Point(scaledOldLocation.x + deltaX, scaledOldLocation.y + deltaY)).copyUnanchored();
						updated.add(updatedIcon);
						mainWindow.edits.freeIcons.doWithLock(() ->
						{
							mainWindow.edits.freeIcons.replace(iconToEdit, updatedIcon);
						});

						if (iconToEdit.centerIndex != null && !mainWindow.edits.freeIcons.hasTrees(iconToEdit.centerIndex))
						{
							// The user moved the last tree out of the polygon it was anchored to. Remove the invisible CenterTree so that
							// if
							// someone resizes all trees later, trees don't appear out of nowhere on this Center.
							mainWindow.edits.centerEdits.put(iconToEdit.centerIndex, mainWindow.edits.centerEdits.get(iconToEdit.centerIndex).copyWithTrees(null));
						}
					}
					else if (isScaling)
					{
						double scale = calcScale(graphPointMouseLocation, graphPointMousePressedLocation, iconEditBounds);
						Rectangle imageBounds = updater.mapParts.iconDrawer.toIconDrawTask(iconToEdit).getOrCreateContentBoundsPadded();
						FreeIcon updatedIcon = iconToEdit.copyWithScale(iconToEdit.scale * floorWithMinScale(scale, imageBounds));
						updated.add(updatedIcon);
						mainWindow.edits.freeIcons.doWithLock(() ->
						{
							mainWindow.edits.freeIcons.replace(iconToEdit, updatedIcon);
						});
					}
				}

				if (updated != null)
				{
					undoer.setUndoPoint(UpdateType.Incremental, this);
					Set<FreeIcon> beforeAndAfter = new HashSet<>();
					beforeAndAfter.addAll(iconsToEdit);
					beforeAndAfter.addAll(updated);
					updater.createAndShowMapIncrementalUsingIcons(new ArrayList<>(beforeAndAfter));

					iconsToEdit.clear();
					iconsToEdit.addAll(updated);

					isMoving = false;
					isScaling = false;
					showSelection();
					updateResetScaleButtonEnabled();
				}
				mapEditingPanel.repaint();
			}
			else
			{
				showSelection();
				showOrHideEditComponents();
			}
		}
	}

	public void unselectAnyIconsBeingEdited()
	{
		endRectangleSelect();
		iconsToEdit.clear();
		isMoving = false;
		isScaling = false;
		editStart = null;
		mapEditingPanel.hideIconEditTools();
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.repaint();
		showOrHideEditComponents();
	}

	private void eraseTreesThatFailedToDrawDueToLowDensity(MouseEvent e)
	{
		Set<Center> selected = getSelectedLandCenters(e.getPoint());
		for (Center center : selected)
		{
			mainWindow.edits.centerEdits.put(center.index, mainWindow.edits.centerEdits.get(center.index).copyWithTrees(null));
		}
	}

	private void replaceTreesThatFailedToDrawDueToLowDensity(MouseEvent e)
	{
		String treeType = treeTypes.getSelectedOption();
		if (StringUtils.isEmpty(treeType))
		{
			return;
		}

		String artPack = (String) artPackComboBox.getSelectedItem();
		// Stamp the selected colors so these trees keep the colors they are being replaced with. Trees carrying no colors of their own
		// take on whatever the per-type tree colors are whenever they are next converted for drawing, which can be long after this edit.
		IconColors colors = new IconColors(getSelectedIconTypeColor(), getSelectedIconTypeFilterColor(), maximizeOpacityCheckbox.isSelected(),
				fillWithColorCheckbox.isSelected(), fillShowsTextureCheckbox.isSelected());
		Set<Center> selected = getSelectedLandCenters(e.getPoint());
		for (Center center : selected)
		{
			CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
			if (cEdit.trees != null)
			{
				CenterTrees newTrees = cEdit.trees.copyWithTreeType(artPack, treeType).copyWithColors(colors);
				mainWindow.edits.centerEdits.put(center.index, cEdit.copyWithTrees(newTrees));
			}
		}
	}

	private Set<Center> getSelectedLandCenters(java.awt.Point point)
	{
		Set<Center> selected = getSelectedCenters(point);
		return selected.stream().filter(c -> !c.isWater).collect(Collectors.toSet());
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		selectionDisplay.update(e);
		endRectangleSelect();
		// A new press starts a new stroke with no previous-event selection, so the pressed polygons/icons are drawn or replaced (reshuffled).
		centerIndicesUnderBrushInPreviousDragEvent.clear();
		iconsUnderBrushInPreviousDragEvent.clear();
		handleMousePressOrDrag(e, true);
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		selectionDisplay.update(e);
		if (modeWidget.isEditMode())
		{
			CommandClick<FreeIcon> click = commandClick;
			endRectangleSelect();
			if (click != null)
			{
				toggledIconsUnderMouse.add(click.getItem());
				setSelection(SelectionEdits.clicked(iconsToEdit, click.getItem(), true, click.isAltDown(), HashSet::new));
			}
			mapEditingPanel.repaint();
			handleFinishSelectingOrEditingIconsIfNeeded(e);
		}
		else
		{
			undoer.setUndoPoint(UpdateType.Incremental, this);
		}
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		selectionDisplay.update(e);
		innerHandleMouseMovedOnMap(e.getPoint());
	}

	private void innerHandleMouseMovedOnMap(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null)
		{
			return;
		}

		if (!updater.isMapReadyForInteractions())
		{
			return;
		}

		if (modeWidget.isDrawMode() && !decorationsButton.isSelected())
		{
			mapEditingPanel.hideBrush();
			highlightHoverCenters(mouseLocation);
		}
		else
		{
			highlightHoverIconsAndShowBrush(mouseLocation);
		}
		updateSelectionCycleCandidates(mouseLocation);
		mapEditingPanel.repaint();
	}

	private void highlightHoverCenters(java.awt.Point mouseLocation)
	{
		mapEditingPanel.clearHighlightedAreas();
		mapEditingPanel.clearHighlightedCenters();

		Set<Center> selected = getSelectedCenters(mouseLocation);
		mapEditingPanel.addHighlightedCenters(selected);
		mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
		mapEditingPanel.repaint();
	}

	private void highlightHoverIconsAndShowBrush(java.awt.Point mouseLocation)
	{
		mapEditingPanel.clearHighlightedCenters();

		showOrHideBrush(mouseLocation);

		if (modeWidget.isEditMode())
		{
			if (!toggledIconsUnderMouse.isEmpty())
			{
				toggledIconsUnderMouse.update(getMultipleSelectedIcons(mouseLocation, null));
			}
			if (!iconsToEdit.isEmpty() || selectionDisplay.isCommandKeyShowingOutlines())
			{
				showSelectionOutlines(selectionDisplay.getShown(iconsToEdit.size()));
			}
			else
			{
				mapEditingPanel.clearHighlightedAreas();
				List<FreeIcon> selected = getSelectedIcons(mouseLocation);
				// With the command key and Alt held, a click removes rather than selects, so there is nothing to preview.
				boolean isRemoving = SelectionEdits.isRemoving(selectionDisplay.isCommandKeyHeld(), SelectionCycling.isAltDown());
				if (selected != null && selected.size() > 0 && !isRemoving)
				{
					mapEditingPanel.setHighlightedAreasFromIcons(selected, updater.mapParts.iconDrawer, false);
				}
			}
		}
		else if (!(modeWidget.isDrawMode() && decorationsButton.isSelected()))
		{
			mapEditingPanel.clearHighlightedAreas();
			List<FreeIcon> icons = getSelectedIcons(mouseLocation);
			mapEditingPanel.setHighlightedAreasFromIcons(icons, updater.mapParts.iconDrawer, true);
		}
		mapEditingPanel.repaint();
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		selectionDisplay.update(e);
		if (modeWidget.isDrawMode() && !decorationsButton.isSelected())
		{
			highlightHoverCenters(e.getPoint());
		}
		else
		{
			highlightHoverIconsAndShowBrush(e.getPoint());
		}
		handleMousePressOrDrag(e, false);
	}

	@Override
	protected void handleMouseRightPressedOnMap(MouseEvent e)
	{
		if (!modeWidget.isEditMode())
		{
			return;
		}
		if (!updater.isMapReadyForInteractions())
		{
			return;
		}

		// If the right-click landed inside the visible multi-icon selection bounding box, always act on the existing selection rather than
		// switching to whatever icon happens to be under the cursor. The bounding box doesn't visually distinguish which icons inside it
		// are selected vs. not, so swapping the selection here would surprise the user.
		//
		// Exception: when the modifier key is held, the per-icon highlight overlay replaces the box visually and the user is in
		// "extend/modify selection" mode — they expect per-icon behavior, matching what Ctrl + left-click does.
		boolean insideSelectionBox = !SwingHelper.isCommandKeyDown(e) && mapEditingPanel.isPointInsideMultiIconSelectionBox(e.getPoint());

		// If the user right-clicks on an icon that isn't currently selected, treat the right-click
		// as a selecting click first so the context menu acts on the icon the user pointed at. Holding
		// the command key extends the existing selection (matches left-click extend semantics).
		if (!insideSelectionBox)
		{
			List<FreeIcon> underCursor = getSelectedIcons(e.getPoint());
			if (underCursor != null && !underCursor.isEmpty())
			{
				boolean alreadySelected = iconsToEdit != null && !iconsToEdit.isEmpty() && iconsToEdit.containsAll(underCursor);
				if (!alreadySelected)
				{
					if (iconsToEdit == null)
					{
						iconsToEdit = new HashSet<>();
					}
					if (!SwingHelper.isCommandKeyDown(e))
					{
						iconsToEdit.clear();
					}
					iconsToEdit.addAll(underCursor);
					handleIconSelectionChange();
					mapEditingPanel.repaint();
				}
			}
		}

		showEditModeContextMenu(e);
	}

	private void showEditModeContextMenu(MouseEvent e)
	{
		boolean hasSelection = iconsToEdit != null && !iconsToEdit.isEmpty();
		boolean hasClipboard = copied != null && !copied.isEmpty();
		if (!hasSelection && !hasClipboard)
		{
			return;
		}

		JPopupMenu menu = new JPopupMenu();

		JMenuItem copyItem = new JMenuItem(Translation.get("iconsTool.copy"));
		copyItem.setEnabled(hasSelection);
		copyItem.addActionListener(ev -> copySelectedIcons());
		menu.add(copyItem);

		JMenuItem pasteItem = new JMenuItem(Translation.get("iconsTool.paste"));
		pasteItem.setEnabled(hasClipboard);
		pasteItem.addActionListener(ev -> pasteSelectedIcons());
		menu.add(pasteItem);

		JMenuItem deleteItem = new JMenuItem(Translation.get("iconsTool.delete"));
		deleteItem.setEnabled(hasSelection);
		deleteItem.addActionListener(ev -> deleteSelectedIcons());
		menu.add(deleteItem);

		JMenuItem resetScaleItem = new JMenuItem(Translation.get("iconsTool.resetScale"));
		resetScaleItem.setEnabled(hasSelection);
		resetScaleItem.addActionListener(ev -> clearScaleOnSelectedIcons());
		menu.add(resetScaleItem);

		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		toggledIconsUnderMouse.clear();
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearSelectionCycleCandidateAreas();
		if (iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			if (SwingHelper.isCommandKeyDown(e) || e.getButton() == MouseEvent.BUTTON1)
			{
				mapEditingPanel.setHighlightedAreasFromIcons(iconsToEdit, this::isValidPosition);
			}
		}
		else
		{
			mapEditingPanel.clearHighlightedAreas();
		}
		mapEditingPanel.hideBrush();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onAfterShowMap()
	{
		isValidPositionCache.clear();
		if ((isMoving || isScaling) && editStart != null)
		{
			// A move or scale is in progress. Reposition the preview from the current mouse location so it follows a zoom change made
			// mid-drag, which repaints the map without generating a mouse-move event.
			updateMoveOrScalePreview(mapEditingPanel.getMousePosition());
			mapEditingPanel.repaint();
		}
		else if (rectangleSelectDrag != null && rectangleSelectDrag.isDragging())
		{
			// Keep the selection rectangle's moving corner under the mouse after a zoom change made mid-drag, which repaints the map without
			// generating a mouse-drag event.
			updateRectangleSelect(mapEditingPanel.getMousePosition());
		}
		else
		{
			updateHighlightsForMousePosition();
		}
	}

	@Override
	public void onSwitchingTo()
	{
		super.onSwitchingTo();
		// The map may have changed while another tool was selected.
		isValidPositionCache.clear();
		updater.doWhenMapIsReadyForInteractions(() ->
		{
			if (isSelected())
			{
				updateHighlightsForMousePosition();
			}
		});
	}

	private void updateHighlightsForMousePosition()
	{
		innerHandleMouseMovedOnMap(mapEditingPanel.getMousePosition());

		if (modeWidget.isEditMode() && iconsToEdit != null && !iconsToEdit.isEmpty())
		{
			handleIconSelectionChange();
		}
		mapEditingPanel.repaint();
	}

	@Override
	protected void onAfterUndoRedo()
	{
		endRectangleSelect();
		mapEditingPanel.clearHighlightedCenters();
		unselectAnyIconsBeingEdited();
		mapEditingPanel.repaint();
	}

	private Set<Center> getSelectedCenters(java.awt.Point mouseLocation)
	{
		return getSelectedCenters(mouseLocation, getBrushDiameter());
	}

	private List<FreeIcon> getSelectedIcons(java.awt.Point mouseLocation)
	{
		return getSelectedIcons(mouseLocation, null);
	}

	private List<FreeIcon> getSelectedIcons(java.awt.Point mouseLocation, Collection<FreeIcon> allowList)
	{
		int brushDiameter = getBrushDiameter();

		if (brushDiameter <= 1)
		{
			FreeIcon selected = getLowestSelectedIcon(mouseLocation, allowList);
			if (selected != null)
			{
				return Arrays.asList(selected);
			}
			return Collections.emptyList();
		}
		else
		{
			return getMultipleSelectedIcons(mouseLocation, allowList);
		}
	}

	private List<FreeIcon> getMultipleSelectedIcons(java.awt.Point mouseLocation, Collection<FreeIcon> allowList)
	{
		List<FreeIcon> selected = new ArrayList<>();
		mainWindow.edits.freeIcons.doWithLock(() ->
		{
			Iterable<FreeIcon> iterable = allowList == null ? mainWindow.edits.freeIcons : allowList;

			for (FreeIcon icon : iterable)
			{
				if (isSelected(mouseLocation, icon))
				{
					selected.add(icon);
				}
			}
		});

		return selected;
	}

	private boolean isSelected(java.awt.Point mouseLocation, FreeIcon icon)
	{
		int brushDiameter = getBrushDiameter();
		Point graphPoint = getPointOnGraph(mouseLocation);

		if (brushDiameter <= 1)
		{
			if (!isSelectedType(icon))
			{
				return false;
			}

			IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
			if (task == null)
			{
				return false;
			}

			return isWithinHighlightOutline(task.getOrCreateContentBoundsPadded(), graphPoint);
		}
		else
		{
			int brushRadius = (int) panelDistanceToGraphPixels(brushDiameter) / 2;
			if (!isSelectedType(icon))
			{
				return false;
			}
			IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
			if (task == null)
			{
				return false;
			}
			RotatedRectangle rect = new RotatedRectangle(task.getOrCreateContentBoundsPadded());
			return rect.overlapsCircle(graphPoint, brushRadius);
		}
	}

	/**
	 * The icons of the selected types under the given point, in the order Alt+click cycles through them: the one a plain click selects
	 * first, then by lowest top. Ties keep their draw order.
	 */
	private List<FreeIcon> getSelectionCycleCandidates(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null || updater.mapParts == null || updater.mapParts.iconDrawer == null)
		{
			return new ArrayList<>();
		}
		Point graphPoint = getPointOnGraph(mouseLocation);
		List<Tuple2<FreeIcon, Double>> iconsAndTops = new ArrayList<>();
		mainWindow.edits.freeIcons.doWithLock(() ->
		{
			for (FreeIcon icon : mainWindow.edits.freeIcons)
			{
				if (!isSelectedType(icon))
				{
					continue;
				}
				IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
				if (task == null)
				{
					continue;
				}
				Rectangle bounds = task.getOrCreateContentBoundsPadded();
				if (isWithinHighlightOutline(bounds, graphPoint))
				{
					iconsAndTops.add(new Tuple2<>(icon, bounds.getTop()));
				}
			}
		});
		iconsAndTops.sort((first, second) -> -Double.compare(first.getSecond(), second.getSecond()));
		return iconsAndTops.stream().map(Tuple2::getFirst).collect(Collectors.toList());
	}

	/**
	 * Outlines what Alt+click can select under the mouse while Alt is held in Edit mode.
	 */
	private void updateSelectionCycleCandidates(java.awt.Point mouseLocation)
	{
		if (!SelectionCycling.isAltDown() || selectionDisplay.isCommandKeyHeld() || !modeWidget.isEditMode() || getBrushDiameter() > 1
				|| mouseLocation == null || !updater.isMapReadyForInteractions())
		{
			mapEditingPanel.clearSelectionCycleCandidateAreas();
			return;
		}
		List<RotatedRectangle> areas = new ArrayList<>();
		for (FreeIcon icon : getSelectionCycleCandidates(mouseLocation))
		{
			IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
			if (task != null)
			{
				areas.add(new RotatedRectangle(task.getOrCreateContentBoundsPadded()));
			}
		}
		mapEditingPanel.setSelectionCycleCandidateAreas(areas);
	}

	/**
	 * Whether the given point is inside an icon's bounds or on the outline drawn around them when the icon is highlighted.
	 */
	private boolean isWithinHighlightOutline(Rectangle iconBounds, Point graphPoint)
	{
		return new RotatedRectangle(iconBounds).overlapsCircle(graphPoint, getHighlightOutlineReachInGraphPixels());
	}

	protected FreeIcon getLowestSelectedIcon(java.awt.Point mouseLocation, Collection<FreeIcon> allowList)
	{
		return getLowestIcon(getMultipleSelectedIcons(mouseLocation, allowList));
	}

	/**
	 * The icon whose top is lowest on the map, or null if there are none.
	 */
	private FreeIcon getLowestIcon(List<FreeIcon> icons)
	{
		FreeIcon lowest = null;
		double lowestTop = Double.NEGATIVE_INFINITY;

		for (FreeIcon icon : icons)
		{
			IconDrawTask task = updater.mapParts.iconDrawer.toIconDrawTask(icon);
			if (task != null)
			{
				double top = task.getOrCreateContentBoundsPadded().getTop();
				if (lowest == null || top > lowestTop)
				{
					lowest = icon;
					lowestTop = top;
				}
			}
		}
		return lowest;
	}

	private int getBrushDiameter()
	{
		if (brushSelector.isVisible())
		{
			return brushSelector.getBrushDiameter();
		}

		return brushSizes.get(0);
	}

	private boolean isSelectedType(FreeIcon icon)
	{
		if (modeWidget.isDrawMode() || modeWidget.isReplaceMode())
		{
			if (mountainsButton.isSelected() && icon.type == IconType.mountains)
			{
				return true;
			}

			if (hillsButton.isSelected() && icon.type == IconType.hills)
			{
				return true;
			}

			if (dunesButton.isSelected() && icon.type == IconType.sand)
			{
				return true;
			}

			if (treesButton.isSelected() && icon.type == IconType.trees)
			{
				return true;
			}

			if (decorationsButton.isSelected() && icon.type == IconType.decorations)
			{
				return true;
			}

			if (citiesButton.isSelected() && icon.type == IconType.cities)
			{
				return true;
			}
		}
		else
		{
			if (mountainsToggle.isSelected() && icon.type == IconType.mountains)
			{
				return true;
			}

			if (hillsToggle.isSelected() && icon.type == IconType.hills)
			{
				return true;
			}

			if (dunesToggle.isSelected() && icon.type == IconType.sand)
			{
				return true;
			}

			if (treesToggle.isSelected() && icon.type == IconType.trees)
			{
				return true;
			}

			if (decorationsToggle.isSelected() && icon.type == IconType.decorations)
			{
				return true;
			}

			if (citiesToggle.isSelected() && icon.type == IconType.cities)
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean refreshImagePreviews)
	{
		String customArtPackPath = settings.customArtPackPath;
		String artPack = settings.artPack;
		// The art pack field has special handling for undo/redo to avoid undo/redo changing the selected value, while still storing the
		// value in MapSettings.
		String artPackToSelect = isUndoRedoOrAutomaticChange && !StringUtils.isEmpty((String) artPackComboBox.getSelectedItem()) ? (String) artPackComboBox.getSelectedItem() : settings.artPack;
		try
		{
			disableImageRefreshes = !refreshImagePreviews;
			updateArtPackOptions(artPackToSelect, customArtPackPath);
		}
		finally
		{
			disableImageRefreshes = false;
		}

		if (!Objects.equals(artPackComboBox.getSelectedItem(), artPack))
		{
			if (isUndoRedoOrAutomaticChange)
			{
				// Set this so that the call to updateIconTypeButtonPreviewImages doesn't crash when settings.artPack doesn't match the art
				// pack of the selectors.
				settings.artPack = artPackToSelect;
			}
			else
			{
				if (Assets.artPackExists(artPack, customArtPackPath))
				{
					artPackComboBox.setSelectedItem(artPack);
				}
				else
				{
					artPackComboBox.setSelectedItem(Assets.installedArtPack);
					// Setting this fixes a bug where icon previews for shuffled icons don't show up right after an images refresh when the
					// selected art pack no longer exists. It seems to be because the call to updateIconTypeButtonPreviewImages below was
					// given
					// the wrong art pack when it changed here and wasn't updated.
					settings.artPack = Assets.installedArtPack;
				}
			}
		}

		fillColorsByType.clear();
		for (IconType iconType : IconType.values())
		{
			fillColorsByType.put(iconType, settings.getIconFillColorForType(iconType));
		}

		iconFilterColorsByType.clear();
		for (IconType iconType : IconType.values())
		{
			iconFilterColorsByType.put(iconType, settings.getIconFilterColorForType(iconType));
		}

		maximizeOpacityByType.clear();
		for (IconType iconType : IconType.values())
		{
			maximizeOpacityByType.put(iconType, settings.getMaximizeOpacityForType(iconType));
		}

		fillWithColorByType.clear();
		for (IconType iconType : IconType.values())
		{
			fillWithColorByType.put(iconType, settings.getFillWithColorForType(iconType));
		}

		fillShowsTextureByType.clear();
		for (IconType iconType : IconType.values())
		{
			fillShowsTextureByType.put(iconType, settings.getFillShowsTextureForType(iconType));
		}


		updateTypePanels();

		if (refreshImagePreviews)
		{
			updateIconTypeButtonPreviewImages(settings);
		}
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.artPack = (String) artPackComboBox.getSelectedItem();
		assert !StringUtils.isEmpty(settings.artPack);

		// Selected colors per icon type
		for (Map.Entry<IconType, Color> entry : fillColorsByType.entrySet())
		{
			settings.setIconFillColorForType(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<IconType, HSBColor> entry : iconFilterColorsByType.entrySet())
		{
			settings.setIconFilterColorForType(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<IconType, Boolean> entry : maximizeOpacityByType.entrySet())
		{
			settings.setMaximizeOpacityForType(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<IconType, Boolean> entry : fillWithColorByType.entrySet())
		{
			settings.setFillWithColorForType(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<IconType, Boolean> entry : fillShowsTextureByType.entrySet())
		{
			settings.setFillShowsTextureForType(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void handleEnablingAndDisabling(MapSettings settings)
	{
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
	}

	@Override
	protected void onBeforeUndoRedo()
	{
	}

	@Override
	public void handleCustomArtPackPathChanged(String customArtPackPath)
	{
		if (!StringUtils.isEmpty(customArtPackPath))
		{
			artPackComboBox.setSelectedItem(Assets.customArtPack);
		}
	}

}
