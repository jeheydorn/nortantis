package nortantis.swing;

import nortantis.*;
import nortantis.editor.*;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.GeometryHelper;
import nortantis.util.Helper;
import nortantis.util.OSHelper;
import nortantis.util.Tuple2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class LandWaterTool extends EditorTool
{

	private JPanel colorDisplay;
	private RowHider colorChooserHider;

	private JToggleButton landButton;
	private JToggleButton oceanButton;
	private JToggleButton lakesButton;
	private JToggleButton riversButton;
	private RowHider riverOptionHider;
	private JSlider riverWidthSlider;
	private SliderWithSpinner riverWidthSliderWithSpinner;
	private Corner riverStart;
	private Center roadStart;
	private RowHider modeHider;
	private JToggleButton fillRegionColorButton;
	private JToggleButton mergeRegionsButton;
	private Region selectedRegion;
	private JToggleButton selectColorFromMapButton;

	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private RowHider selectColorHider;
	private JCheckBox onlyUpdateLandCheckbox;

	private JSlider hueSlider;
	private JSlider saturationSlider;
	private JSlider brightnessSlider;
	private boolean areRegionColorsVisible;
	private boolean areRegionBoundariesVisible;
	private boolean areRoadsVisible;
	private RowHider onlyUpdateLandCheckboxHider;
	private RowHider generateColorButtonHider;
	private RowHider colorGeneratorSettingsHider;
	private JPanel baseColorPanel;
	private ActionListener brushActionListener;
	private DrawModeWidget modeWidget;
	private JToggleButton newRegionButton;
	private RowHider newRegionButtonHider;
	private JToggleButton roadsButton;
	private SegmentedButtonWidget brushTypeWidget;
	private JToggleButton polygonDrawStyleButton;
	private JToggleButton freeHandDrawStyleButton;
	private RowHider drawStyleHider;

	// Free-hand road drawing state (RI = resolution-invariant coordinates)
	private List<Point> freeHandRoadPathRI = null;
	private Point freeHandRoadSnapPoint = null;
	private Point polygonRoadSnapStart = null;

	// Free-hand river drawing state (RI = resolution-invariant coordinates)
	private List<Point> freeHandRiverPathRI = null;
	private Point freeHandRiverSnapPoint = null;
	private Point polygonRiverSnapStart = null;
	private JToggleButton riverPolygonDrawStyleButton;
	private JToggleButton riverFreeHandDrawStyleButton;
	private RowHider riverDrawStyleHider;

	// Multi-select edit state. The set of currently-selected control points, keyed by line. At most one of these maps is non-empty at a
	// time — selection is per-active-type, and switching between Rivers and Roads clears it. A segment is implicitly "selected" iff
	// both of its endpoint CPs are in the map; that drives the width-slider visibility and the highlighted-polyline visual.
	//
	// Note: the super constructor calls back into virtual methods on this subclass (e.g. createToolOptionsPanel → showOrHideBrushOptions
	// → showOrHideRoadAndRiverOptions) BEFORE our instance-field initializers run, so these references are null for a brief window.
	// Methods reachable during that window must tolerate that.
	// IdentityHashMap by design: River/Road override equals/hashCode based on their mutable `nodes` field, so a regular HashMap loses
	// entries the instant a line's nodes are reassigned (e.g. segment delete sets line.nodes = firstHalf, changing the hashCode and
	// orphaning the entry in its original bucket). Selection state must be keyed by identity, not content.
	//
	// INVARIANT: after undo/redo, edits.rivers/roads are replaced with deep-copied instances, so any entry left here would point at a
	// dead reference. {@link #onAfterUndoRedo} relies on this being the case and unconditionally calls {@link #clearSelection()} — if
	// that ever changes (e.g. someone tries to preserve selection across undos), this design breaks silently and the maps must be
	// rebuilt to point at the new instances.
	private Map<River, Set<Integer>> selectedRiverCPs = new java.util.IdentityHashMap<>();
	private Map<Road, Set<Integer>> selectedRoadCPs = new java.util.IdentityHashMap<>();

	// Hover state — what's under the cursor right now, refreshed on every mouse-moved. Drives the
	// hover ring on a control point and the right-click context menu's target. Cleared when the
	// mouse leaves the map or moves to a position where nothing is in range.
	private River hoveredRiver = null;
	private Road hoveredRoad = null;

	// In-progress drag state. Two flavors:
	// 1. Move-drag: started from a CP — translates every CP in {@link #dragMovingCPs} by the cursor delta.
	// 2. Paint-drag: started from a segment or empty area — accumulates CPs under the brush into the selection.
	private boolean dragInProgress = false;
	private boolean dragOccurred = false;
	private boolean dragIsPaint = false;
	private Point dragStartLocRI = null;
	private List<MovingCP> dragMovingCPs = null;
	// Before-drag snapshots of every line that has any moving CP. Used to compute redraw bounds covering the pre-drag curve shape.
	private Map<Object, List<Point>> dragBeforeSnapshots = null;

	/** A control point participating in a move-drag — line + index, plus its position at drag-start so we can apply the cursor delta. */
	private record MovingCP(Object line, int nodeIndex, Point startLocRI)
	{
	}

	// UI widget that exposes "Select" vs "Unselect" behavior for Ctrl-click. Shared pattern with IconsTool.
	private ControlClickBehaviorWidget controlClickBehavior;
	private RowHider controlClickBehaviorHider;
	private RowHider editButtonsSeparatorHider;
	private RowHider editButtonsHider;

	// Clipboard for copy/paste in edit mode: contiguous runs of CP locations from the active line type at the time copy was pressed.
	// For rivers, copiedRiverWidthLevels runs in parallel — same outer/inner index — and stores each node's widthLevelToNext so the
	// paste preserves the segment widths the user authored. Null for roads (RoadPathNode has no per-segment metadata).
	private LineType copiedLineType = null;
	private List<List<Point>> copiedCPRuns = null;
	private List<List<Integer>> copiedRiverWidthLevels = null;

	// Width-slider edit state. sliderEditWidthBeforeDrag holds the per-segment widths captured before the user grabbed the slider
	// thumb; on release we push an undo point if any width actually changed. Null means "no edit in progress".
	// IdentityHashMap when populated — see selectedRiverCPs note about River's mutable-content hashCode.
	private Map<River, Map<Integer, Integer>> sliderEditWidthsBeforeDrag = null;
	// True while we're applying the slider's value to the selected segments programmatically, so the change listener doesn't recurse
	// or attempt to re-tune the segments.
	private boolean syncingSliderToSelection = false;

	private enum LineType
	{
		RIVER, ROAD
	}

	/**
	 * Result of hit-testing the cursor against rivers (or roads): the closest control point or segment if one is in range, or null at the
	 * call site. Exactly one of {@code river} or {@code road} is non-null. Exactly one of {@code controlPointIndex} or {@code segmentIndex}
	 * is &gt;= 0.
	 */
	private record LineHit(River river, Road road, int controlPointIndex, int segmentIndex)
	{
		boolean isControlPoint()
		{
			return controlPointIndex >= 0;
		}

		boolean isSegment()
		{
			return segmentIndex >= 0;
		}
	}

	static String getToolbarNameStatic()
	{
		return Translation.get("landWaterTool.name");
	}

	static String getColorGeneratorSettingsName()
	{
		return Translation.get("landWaterTool.colorGeneratorSettings");
	}

	public LandWaterTool(MainWindow mainWindow, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(mainWindow, toolsPanel, mapUpdater);
	}

	@Override
	public String getToolbarName()
	{
		return getToolbarNameStatic();
	}

	@Override
	public int getMnemonic()
	{
		return KeyEvent.VK_Z;
	}

	@Override
	public String getKeyboardShortcutText()
	{
		return "(" + SwingHelper.getAltKeyName() + "+Z)";
	}

	@Override
	public Image getToolIcon()
	{
		Image icon = Assets.readImage(Paths.get(Assets.getAssetsPath(), "internal/Land Water tool.png").toString());
		try (nortantis.platform.Painter p = icon.createPainter(DrawQuality.High))
		{
			String land = Translation.get("landWaterTool.toolIcon.land");
			String water = Translation.get("landWaterTool.toolIcon.water");
			p.setFont(createToolIconFont(19, land + water));
			p.setColor(nortantis.platform.Color.black);
			p.drawString(land, 7, 15);
			p.drawString(water, 12 + getXOffSetBasedOnLanguage(), 48);
		}
		return icon;
	}

	private int getXOffSetBasedOnLanguage()
	{
		return switch (Translation.getEffectiveLocale().getLanguage())
		{
			case "de" -> OSHelper.isMac() ? 0 : -3;
			case "es" -> 3;
			case "fr" -> 4;
			case "pt" -> 4;
			case "ru" -> 4;
			default -> 0;
		};
	}


	@Override
	protected JPanel createToolOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		oceanButton = new JToggleButton(Translation.get("landWaterTool.ocean"));
		brushActionListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.clearSelectedCenters();

				updateColorControlVisibility();
				onlyUpdateLandCheckboxHider.setVisible(landButton.isSelected());

				showOrHideNewRegionButton();
				newRegionButton.setSelected(false);

				if (brushSizeComboBox != null)
				{
					boolean riversOrRoads = riversButton.isSelected() || roadsButton.isSelected();
					boolean inBrushFamily = oceanButton.isSelected() || lakesButton.isSelected() || landButton.isSelected();
					brushSizeHider.setVisible(inBrushFamily || (riversOrRoads && (modeWidget.isEraseMode() || modeWidget.isEditMode())));
				}

				showOrHideRoadAndRiverOptions();
			}
		};
		oceanButton.addActionListener(brushActionListener);

		lakesButton = new JToggleButton(Translation.get("landWaterTool.lakes"));
		lakesButton.setToolTipText(Translation.get("landWaterTool.lakes.tooltip"));
		lakesButton.addActionListener(brushActionListener);

		riversButton = new JToggleButton(Translation.get("landWaterTool.rivers"));
		riversButton.addActionListener(brushActionListener);

		landButton = new JToggleButton(Translation.get("landWaterTool.land"));
		landButton.addActionListener(brushActionListener);

		fillRegionColorButton = new JToggleButton(Translation.get("landWaterTool.fillRegionColor"));
		fillRegionColorButton.addActionListener(brushActionListener);

		mergeRegionsButton = new JToggleButton(Translation.get("landWaterTool.mergeRegions"));
		mergeRegionsButton.addActionListener(brushActionListener);

		roadsButton = new JToggleButton(Translation.get("landWaterTool.roads"));
		roadsButton.addActionListener(brushActionListener);

		oceanButton.setSelected(true); // Selected by default
		brushTypeWidget = new SegmentedButtonWidget(List.of(oceanButton, lakesButton, riversButton, landButton, fillRegionColorButton, mergeRegionsButton, roadsButton));
		brushTypeWidget.addToOrganizer(organizer, Translation.get("landWaterTool.brush.label"), "");

		// River options
		{
			modeWidget = new DrawModeWidget(Translation.get("landWaterTool.drawRivers"), Translation.get("landWaterTool.eraseRivers"), false, "", true,
					Translation.get("landWaterTool.editRiverOrRoad"), () -> brushActionListener.actionPerformed(null));
			modeHider = modeWidget.addToOrganizer(organizer, Translation.get("landWaterTool.riverMode.help"));

			int maxSliderValue = GraphRiver.MAX_RIVER_SLIDER_BASE + 1;
			riverWidthSlider = new JSlider(1, maxSliderValue);
			final int initialValue = 1;
			riverWidthSlider.setValue(initialValue);
			riverWidthSliderWithSpinner = new SliderWithSpinner(riverWidthSlider, () -> handleRiverWidthSliderChanged());
			riverOptionHider = riverWidthSliderWithSpinner.addToOrganizer(organizer, Translation.get("landWaterTool.riverWidth.label"), Translation.get("landWaterTool.riverWidth.help"));

			riverWidthSlider.addChangeListener(ev -> handleRiverWidthSliderChanged());
			riverWidthSlider.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mousePressed(java.awt.event.MouseEvent ev)
				{
					sliderEditWidthsBeforeDrag = captureSelectedRiverSegmentWidths();
				}

				@Override
				public void mouseReleased(java.awt.event.MouseEvent ev)
				{
					commitSliderEditIfChanged();
				}
			});
			// Each spinner click is an atomic edit — snapshot the prior widths before applying it, then commit an undo point after. The
			// slider's mouse listener handles the drag analogue; without this, spinner clicks change widths but never push undo points.
			riverWidthSliderWithSpinner.setSpinnerEditHooks(() -> sliderEditWidthsBeforeDrag = captureSelectedRiverSegmentWidths(), () -> commitSliderEditIfChanged());
		}

		// River draw style (graph vs. free-hand)
		{
			riverPolygonDrawStyleButton = new JToggleButton(Translation.get("landWaterTool.riverStyle.graph"));
			riverPolygonDrawStyleButton.setSelected(true);
			riverPolygonDrawStyleButton.setToolTipText(Translation.get("landWaterTool.riverStyle.graph.tooltip"));
			riverPolygonDrawStyleButton.addActionListener(e ->
			{
				cancelFreeHandDrawing(LineType.RIVER);
				brushActionListener.actionPerformed(null);
			});
			riverFreeHandDrawStyleButton = new JToggleButton(Translation.get("landWaterTool.riverStyle.freeHand"));
			riverFreeHandDrawStyleButton.setToolTipText(Translation.get("landWaterTool.riverStyle.freeHand.tooltip"));
			riverFreeHandDrawStyleButton.addActionListener(e ->
			{
				cancelFreeHandDrawing(LineType.RIVER);
				brushActionListener.actionPerformed(null);
			});
			SegmentedButtonWidget riverDrawStyleWidget = new SegmentedButtonWidget(List.of(riverPolygonDrawStyleButton, riverFreeHandDrawStyleButton));
			riverDrawStyleHider = riverDrawStyleWidget.addToOrganizer(organizer, Translation.get("landWaterTool.riverStyle.label"), Translation.get("landWaterTool.riverStyle.help"));
		}

		// Road draw style (polygon vs. free-hand)
		{
			polygonDrawStyleButton = new JToggleButton(Translation.get("landWaterTool.roadStyle.polygon"));
			polygonDrawStyleButton.setSelected(true);
			polygonDrawStyleButton.setToolTipText(Translation.get("landWaterTool.roadStyle.polygon.tooltip"));
			polygonDrawStyleButton.addActionListener(e ->
			{
				cancelFreeHandDrawing(LineType.ROAD);
				brushActionListener.actionPerformed(null);
			});
			freeHandDrawStyleButton = new JToggleButton(Translation.get("landWaterTool.roadStyle.freeHand"));
			freeHandDrawStyleButton.setToolTipText(Translation.get("landWaterTool.roadStyle.freeHand.tooltip"));
			freeHandDrawStyleButton.addActionListener(e ->
			{
				cancelFreeHandDrawing(LineType.ROAD);
				brushActionListener.actionPerformed(null);
			});
			SegmentedButtonWidget drawStyleWidget = new SegmentedButtonWidget(List.of(polygonDrawStyleButton, freeHandDrawStyleButton));
			drawStyleHider = drawStyleWidget.addToOrganizer(organizer, Translation.get("landWaterTool.roadStyle.label"), Translation.get("landWaterTool.roadStyle.help"));

			// Register Escape (cancel) and Enter (commit) key bindings for free-hand drawing
			mapEditingPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelFreeHandRoad");
			mapEditingPanel.getActionMap().put("cancelFreeHandRoad", new AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					cancelFreeHandDrawing(LineType.ROAD);
					cancelFreeHandDrawing(LineType.RIVER);
				}
			});
			mapEditingPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commitFreeHandRoad");
			mapEditingPanel.getActionMap().put("commitFreeHandRoad", new AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					if (riversButton.isSelected() && isFreeHandRiverDrawMode())
					{
						finalizeFreeHandLine(LineType.RIVER);
					}
					else
					{
						finalizeFreeHandLine(LineType.ROAD);
					}
				}
			});
		}

		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		// Edit-mode multi-select UI: Ctrl-click behavior toggle, then a separator, then Copy/Paste/Delete buttons. All visible only in
		// edit mode for rivers/roads (toggled together in showOrHideRoadAndRiverOptions).
		controlClickBehavior = new ControlClickBehaviorWidget();
		controlClickBehaviorHider = controlClickBehavior.addToOrganizer(organizer);
		editButtonsSeparatorHider = organizer.addSeparator();
		EditClipboardButtonsWidget editButtons = new EditClipboardButtonsWidget(this::copySelectedCPs, this::pasteCopiedCPs, this::deleteSelectedCPs,
				Translation.get("landWaterTool.edit.deleteSelection.tooltip"));
		editButtonsHider = editButtons.addToOrganizer(organizer);

		// Create new region button
		{
			newRegionButton = new JToggleButton(Translation.get("landWaterTool.createNewRegion"));
			newRegionButton.addActionListener(e -> updateColorControlVisibility());
			newRegionButton.setToolTipText(Translation.get("landWaterTool.createNewRegion.help"));
			newRegionButtonHider = organizer.addLabelAndComponent(new JLabel(""), newRegionButton, GridBagOrganizer.rowVerticalInset);
		}

		// Color chooser
		colorDisplay = SwingHelper.createColorPickerPreviewPanel();
		colorDisplay.setBackground(Color.black);

		JButton chooseButton = new JButton(Translation.get("common.choose"));
		chooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				cancelSelectColorFromMap();
				SwingHelper.showColorPickerWithPreviewPanel(toolOptionsPanel, colorDisplay, Translation.get("landWaterTool.regionColor.title"));
			}
		});
		colorChooserHider = organizer.addLabelAndComponentsHorizontal(Translation.get("landWaterTool.color.label"), "", Arrays.asList(colorDisplay, chooseButton), SwingHelper.colorPickerLeftPadding);

		selectColorFromMapButton = new JToggleButton(Translation.get("landWaterTool.selectColorFromMap"));
		selectColorFromMapButton.setToolTipText(Translation.get("landWaterTool.selectColorFromMap.tooltip"));
		selectColorHider = organizer.addLabelAndComponent("", "", selectColorFromMapButton, 0);

		JButton generateColorButton = new JButton(Translation.get("landWaterTool.generateColor"));
		generateColorButton.setToolTipText(Translation.get("landWaterTool.generateColor.tooltip"));
		generateColorButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				cancelSelectColorFromMap();
				Color newColor = AwtBridge.toAwtColor(MapCreator.generateColorFromBaseColor(new Random(), AwtBridge.fromAwtColor(baseColorPanel.getBackground()), hueSlider.getValue(),
						saturationSlider.getValue(), brightnessSlider.getValue()));
				colorDisplay.setBackground(newColor);
			}
		});
		generateColorButtonHider = organizer.addLabelAndComponent("", "", generateColorButton, 2);

		onlyUpdateLandCheckbox = new JCheckBox(Translation.get("landWaterTool.onlyUpdateExistingLand"));
		onlyUpdateLandCheckbox.setToolTipText(Translation.get("landWaterTool.onlyUpdateExistingLand.tooltip"));
		onlyUpdateLandCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateLandCheckbox);

		colorGeneratorSettingsHider = organizer.addLeftAlignedComponent(createColorGeneratorOptionsPanel(toolOptionsPanel));

		showOrHideBrushOptions();

		// Refresh the edit-mode hover preview when Ctrl is pressed or released without moving the mouse, so the highlighted CPs match what
		// a click would do at the moment the modifier changes (e.g. in Unselect mode, pressing Ctrl over a CP drops the add-highlight
		// immediately instead of waiting for the next mouse move). A KeyEventDispatcher catches the modifier even when focus is elsewhere.
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher()
		{
			@Override
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if (isSelected() && modeWidget.isEditMode() && (riversButton.isSelected() || roadsButton.isSelected()) && !dragInProgress
						&& SwingHelper.isCommandModifierKeyCode(e.getKeyCode()) && (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_RELEASED))
				{
					updater.doIfMapIsReadyForInteractions(() ->
					{
						boolean ctrlDown = e.getID() == KeyEvent.KEY_PRESSED;
						LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
						updateEditModeHoverDisplay(mapEditingPanel.getMousePosition(), activeType, ctrlDown);
						applySelectedSegmentsHighlight();
						mapEditingPanel.repaint();
					});
				}

				// Return false to allow other KeyEventDispatchers to process the event.
				return false;
			}
		});

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.66);
		organizer.addVerticalFillerRow();
		return toolOptionsPanel;
	}

	private void showOrHideRoadAndRiverOptions()
	{
		modeHider.setVisible(riversButton.isSelected() || roadsButton.isSelected());
		// Clear edit-mode selection and hover state when leaving edit mode, leaving the rivers/roads brush family entirely, or switching
		// between rivers and roads (a selection on one type would render stale highlights while editing the other). Do this before
		// computing slider visibility so the slider hides if the selection was just cleared. The selection maps may be null during the
		// initial super-constructor callback (before our field initializers run); treat null as empty.
		boolean leftEditMode = !modeWidget.isEditMode() || (!riversButton.isSelected() && !roadsButton.isSelected());
		boolean hasSelectedRivers = selectedRiverCPs != null && !selectedRiverCPs.isEmpty();
		boolean hasSelectedRoads = selectedRoadCPs != null && !selectedRoadCPs.isEmpty();
		boolean selectionTypeMismatch = (hasSelectedRivers && !riversButton.isSelected()) || (hasSelectedRoads && !roadsButton.isSelected());
		if (leftEditMode || selectionTypeMismatch)
		{
			boolean hadSelection = hasSelectedRivers || hasSelectedRoads;
			boolean hadHover = hoveredRiver != null || hoveredRoad != null;
			if (hadSelection || hadHover)
			{
				clearSelection();
				clearHoverState();
				mapEditingPanel.clearHighlightedPolylines();
				mapEditingPanel.setSelectedControlPointCircles(null);
				mapEditingPanel.repaint();
			}
		}
		// River width slider is shown in draw mode (sets the width of new rivers) and in edit mode only when at least one river segment
		// is implicitly selected (both endpoint CPs of a segment are in the selection). Hidden in edit mode without a selected segment
		// so the slider's value doesn't look meaningful when it has no effect.
		boolean showSliderInDraw = riversButton.isSelected() && modeWidget.isDrawMode();
		boolean showSliderInEdit = riversButton.isSelected() && modeWidget.isEditMode() && isAnyRiverSegmentSelected();
		riverOptionHider.setVisible(showSliderInDraw || showSliderInEdit);
		riverDrawStyleHider.setVisible(riversButton.isSelected() && modeWidget.isDrawMode());
		drawStyleHider.setVisible(roadsButton.isSelected() && modeWidget.isDrawMode());
		boolean inLineEditMode = modeWidget.isEditMode() && (riversButton.isSelected() || roadsButton.isSelected());
		if (controlClickBehaviorHider != null)
		{
			controlClickBehaviorHider.setVisible(inLineEditMode);
		}
		if (editButtonsSeparatorHider != null)
		{
			editButtonsSeparatorHider.setVisible(inLineEditMode);
		}
		if (editButtonsHider != null)
		{
			editButtonsHider.setVisible(inLineEditMode);
		}
		if (updater != null)
		{
			updater.doWhenMapIsReadyForInteractions(() ->
			{
				if (isSelected())
				{
					boolean riverDrawActive = riversButton.isSelected() && modeWidget.isDrawMode();
					boolean roadDrawActive = roadsButton.isSelected() && modeWidget.isDrawMode();
					if (!riverDrawActive && !roadDrawActive)
					{
						cancelFreeHandDrawing(LineType.ROAD);
						cancelFreeHandDrawing(LineType.RIVER);
						clearRoadControlPointDisplay();
						mapEditingPanel.repaint();
					}
					else if (roadDrawActive)
					{
						updateControlPointDisplay(null, LineType.ROAD);
						mapEditingPanel.repaint();
					}
					else
					{
						if (!isFreeHandRiverDrawMode())
						{
							freeHandRiverPathRI = null;
							freeHandRiverSnapPoint = null;
						}
						updateControlPointDisplay(null, LineType.RIVER);
						mapEditingPanel.repaint();
					}
				}
			});
		}
	}

	private boolean isFreeHandDrawMode()
	{
		return freeHandDrawStyleButton != null && freeHandDrawStyleButton.isSelected();
	}

	private boolean isFreeHandRiverDrawMode()
	{
		return riverFreeHandDrawStyleButton != null && riverFreeHandDrawStyleButton.isSelected();
	}

	private double getSnapRadiusRI()
	{
		// Match the outer edge of the drawn circle (including the stroke outline) so a click on the
		// visible circle line is considered "on" the control point, not just clicks inside it.
		return mapEditingPanel.getRoadControlPointHitRadiusInGraphPixels() / mainWindow.displayQualityScale;
	}

	// -------------------- Selection helpers --------------------

	/**
	 * True if any control point of the active line type is selected. Null-safe (the maps may not be initialized yet during construction).
	 */
	private boolean hasAnySelection()
	{
		return (selectedRiverCPs != null && !selectedRiverCPs.isEmpty()) || (selectedRoadCPs != null && !selectedRoadCPs.isEmpty());
	}

	private void clearSelection()
	{
		if (selectedRiverCPs != null)
		{
			selectedRiverCPs.clear();
		}
		if (selectedRoadCPs != null)
		{
			selectedRoadCPs.clear();
		}
	}

	private boolean isCPSelected(Object line, int index)
	{
		if (line instanceof River r)
		{
			Set<Integer> s = selectedRiverCPs.get(r);
			return s != null && s.contains(index);
		}
		if (line instanceof Road r)
		{
			Set<Integer> s = selectedRoadCPs.get(r);
			return s != null && s.contains(index);
		}
		return false;
	}

	private void addToSelection(Object line, int index)
	{
		forEachCoincidentCP(line, index, (l, i) ->
		{
			if (l instanceof River r)
			{
				selectedRiverCPs.computeIfAbsent(r, k -> new HashSet<>()).add(i);
			}
			else if (l instanceof Road r)
			{
				selectedRoadCPs.computeIfAbsent(r, k -> new HashSet<>()).add(i);
			}
		});
	}

	private void removeFromSelection(Object line, int index)
	{
		forEachCoincidentCP(line, index, (l, i) ->
		{
			if (l instanceof River r)
			{
				Set<Integer> s = selectedRiverCPs.get(r);
				if (s != null)
				{
					s.remove(i);
					if (s.isEmpty())
					{
						selectedRiverCPs.remove(r);
					}
				}
			}
			else if (l instanceof Road r)
			{
				Set<Integer> s = selectedRoadCPs.get(r);
				if (s != null)
				{
					s.remove(i);
					if (s.isEmpty())
					{
						selectedRoadCPs.remove(r);
					}
				}
			}
		});
	}

	/**
	 * Invokes {@code action} for ({@code originLine}, {@code originIdx}) and for every other CP of the same line type whose location
	 * matches the origin's. Used so selection operations on a junction CP (a shared point that lives in multiple River/Road objects)
	 * propagate to every line touching that point. Without this, two CPs at the same point but in different objects can't form an
	 * implicitly-selected segment, because consecutive-index detection in {@link #applySelectedSegmentsHighlight} runs per-line.
	 */
	private void forEachCoincidentCP(Object originLine, int originIdx, java.util.function.BiConsumer<Object, Integer> action)
	{
		List<? extends PathNode> originNodes = nodesOf(originLine);
		if (originIdx < 0 || originIdx >= originNodes.size())
		{
			return;
		}
		action.accept(originLine, originIdx);
		Point origLoc = originNodes.get(originIdx).getLoc();
		if (originLine instanceof River)
		{
			for (River other : mainWindow.edits.rivers)
			{
				if (other == originLine)
				{
					continue;
				}
				List<RiverPathNode> nodes = other.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					if (nodes.get(i).getLoc().isCloseEnough(origLoc))
					{
						action.accept(other, i);
					}
				}
			}
		}
		else if (originLine instanceof Road)
		{
			for (Road other : mainWindow.edits.roads)
			{
				if (other == originLine)
				{
					continue;
				}
				List<RoadPathNode> nodes = other.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					if (nodes.get(i).getLoc().isCloseEnough(origLoc))
					{
						action.accept(other, i);
					}
				}
			}
		}
	}

	/** Iterates every (line, index) pair in the active selection. */
	private void forEachSelectedCP(java.util.function.BiConsumer<Object, Integer> body)
	{
		for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
		{
			for (int idx : entry.getValue())
			{
				body.accept(entry.getKey(), idx);
			}
		}
		for (Map.Entry<Road, Set<Integer>> entry : selectedRoadCPs.entrySet())
		{
			for (int idx : entry.getValue())
			{
				body.accept(entry.getKey(), idx);
			}
		}
	}

	/** Returns the {@link PathNode} list for a line that is a River or Road. */
	private static List<? extends PathNode> nodesOf(Object line)
	{
		if (line instanceof River r)
		{
			return r.nodes;
		}
		if (line instanceof Road r)
		{
			return r.nodes;
		}
		throw new IllegalArgumentException("Not a line: " + line);
	}

	/** True if any river segment is implicitly selected (both endpoint CPs selected on the same river). */
	private boolean isAnyRiverSegmentSelected()
	{
		if (selectedRiverCPs == null)
		{
			return false;
		}
		for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
		{
			Set<Integer> idxs = entry.getValue();
			if (idxs.size() < 2)
			{
				continue;
			}
			River river = entry.getKey();
			int n = river.nodes.size();
			for (int i = 0; i < n - 1; i++)
			{
				if (idxs.contains(i) && idxs.contains(i + 1))
				{
					return true;
				}
			}
		}
		return false;
	}


	/**
	 * Applies the right-click delete semantics to every selected CP/segment as a single undo step:
	 * <ul>
	 * <li>Pairs of selected consecutive CPs are treated as a "selected segment" and dropped like right-click Delete Segment (the two
	 * endpoint CPs survive unless they become orphans, which the &lt;2-node fragment drop handles).</li>
	 * <li>A selected CP whose neighbors are <em>not</em> also selected is "isolated" and dropped like right-click Delete CP: the path is
	 * stitched between its previous and next surviving CPs (predecessor's edge-index metadata cleared because the bridge no longer follows
	 * a single Voronoi edge).</li>
	 * </ul>
	 * Pre-edit selection is cleared (mapping indices across multi-fragment splits would be brittle and the user can re-select what
	 * survives).
	 */
	private void deleteSelectedCPs()
	{
		if (!hasAnySelection())
		{
			return;
		}
		// Snapshot pre-edit node lists so we can compute redraw scope from where the deleted segments USED to be (the lines themselves
		// no longer hold those positions after the cut). Identity-keyed so a mutation to line.nodes mid-operation can't lose entries.
		Map<River, List<Point>> riverBeforePaths = new java.util.IdentityHashMap<>();
		for (River r : selectedRiverCPs.keySet())
		{
			riverBeforePaths.put(r, PathOperations.toLocationList(r.nodes));
		}
		Map<Road, List<Point>> roadBeforePaths = new java.util.IdentityHashMap<>();
		for (Road r : selectedRoadCPs.keySet())
		{
			roadBeforePaths.put(r, PathOperations.toLocationList(r.nodes));
		}

		List<River> changedRivers = new ArrayList<>();
		for (Map.Entry<River, Set<Integer>> entry : new ArrayList<>(selectedRiverCPs.entrySet()))
		{
			River river = entry.getKey();
			Set<Integer> sel = entry.getValue();
			if (sel == null || sel.isEmpty())
			{
				continue;
			}
			List<RiverPathNode> originalNodes = river.nodes;
			DeletePlan plan = computeDeletePlan(sel, originalNodes.size());
			if (plan.isEmpty())
			{
				continue;
			}
			List<List<RiverPathNode>> fragments = PathOperations.applySelectionDeletes(originalNodes, plan.isolatedNodes, plan.edges, RiverDrawer.RIVER_OPS);
			applyRiverFragments(river, fragments, changedRivers);
		}

		List<Road> changedRoads = new ArrayList<>();
		for (Map.Entry<Road, Set<Integer>> entry : new ArrayList<>(selectedRoadCPs.entrySet()))
		{
			Road road = entry.getKey();
			Set<Integer> sel = entry.getValue();
			if (sel == null || sel.isEmpty())
			{
				continue;
			}
			List<RoadPathNode> originalNodes = road.nodes;
			DeletePlan plan = computeDeletePlan(sel, originalNodes.size());
			if (plan.isEmpty())
			{
				continue;
			}
			List<List<RoadPathNode>> fragments = PathOperations.applySelectionDeletes(originalNodes, plan.isolatedNodes, plan.edges, RoadDrawer.ROAD_OPS);
			applyRoadFragments(road, fragments, changedRoads);
		}

		// Mapping indices across multi-fragment splits would be brittle; the user can re-select what remains.
		clearSelection();

		// Newly-exposed endpoints (split + stitch) may now match other lines' endpoints — merge so coincident-end splines stay continuous.
		List<River> extendedRivers = RiverDrawer.mergeAdjacentRivers(changedRivers, mainWindow.edits.rivers);
		List<Road> extendedRoads = RoadDrawer.mergeAdjacentRoads(changedRoads, mainWindow.edits.roads);

		Set<Center> centersToRedraw = new HashSet<>();
		List<List<Point>> riverRedrawPaths = new ArrayList<>(riverBeforePaths.values());
		for (River ext : extendedRivers)
		{
			riverRedrawPaths.add(PathOperations.toLocationList(ext.nodes));
		}
		centersToRedraw.addAll(getCentersTouchingPoints(riverRedrawPaths));

		List<List<Point>> roadRedrawPaths = new ArrayList<>(roadBeforePaths.values());
		for (Road ext : extendedRoads)
		{
			roadRedrawPaths.add(PathOperations.toLocationList(ext.nodes));
		}
		centersToRedraw.addAll(getCentersTouchingPoints(roadRedrawPaths));

		if (!changedRoads.isEmpty() || !extendedRoads.isEmpty())
		{
			List<Road> roadsToRedraw = new ArrayList<>(changedRoads);
			roadsToRedraw.removeIf(r -> !mainWindow.edits.roads.contains(r));
			for (Road ext : extendedRoads)
			{
				if (!roadsToRedraw.contains(ext))
				{
					roadsToRedraw.add(ext);
				}
			}
			if (!roadsToRedraw.isEmpty())
			{
				updater.addRoadsToRedrawLowPriority(roadsToRedraw, mainWindow.displayQualityScale);
			}
		}

		purgeOrphanedSelections();
		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingCenters(centersToRedraw);
		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));
		LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
		refreshSelectionVisuals(mapEditingPanel.getMousePosition(), activeType);
	}

	/**
	 * Classifies {@code selectedIndices} into (a) edges to drop — pairs of consecutive selected indices — and (b) isolated nodes to drop —
	 * selected indices with no selected neighbor. A selected index that touches a selected neighbor only has its segment dropped; the
	 * surviving CP rides along with its untouched neighbor.
	 */
	private static DeletePlan computeDeletePlan(Set<Integer> selectedIndices, int nodeCount)
	{
		Set<Integer> edges = new HashSet<>();
		Set<Integer> isolatedNodes = new HashSet<>();
		for (int idx : selectedIndices)
		{
			if (idx < 0 || idx >= nodeCount)
			{
				continue;
			}
			boolean prevSelected = selectedIndices.contains(idx - 1);
			boolean nextSelected = selectedIndices.contains(idx + 1);
			if (nextSelected && idx + 1 < nodeCount)
			{
				edges.add(idx);
			}
			if (!prevSelected && !nextSelected)
			{
				isolatedNodes.add(idx);
			}
		}
		return new DeletePlan(isolatedNodes, edges);
	}

	private record DeletePlan(Set<Integer> isolatedNodes, Set<Integer> edges)
	{
		boolean isEmpty()
		{
			return isolatedNodes.isEmpty() && edges.isEmpty();
		}
	}

	/**
	 * Replaces {@code river}'s nodes with the first surviving fragment (if any) and pushes the rest as new River objects. Removes
	 * {@code river} from edits when no fragments survive. Every River the caller still has a reference to (modified or freshly added) is
	 * appended to {@code changed} so the merge + redraw pass can find them.
	 */
	private void applyRiverFragments(River river, List<List<RiverPathNode>> fragments, List<River> changed)
	{
		List<List<RiverPathNode>> cleaned = new ArrayList<>(fragments.size());
		for (List<RiverPathNode> fragment : fragments)
		{
			List<RiverPathNode> normalized = PathOperations.normalizePath(fragment, RiverDrawer.RIVER_OPS);
			if (normalized.size() >= 2)
			{
				cleaned.add(normalized);
			}
		}
		if (cleaned.isEmpty())
		{
			mainWindow.edits.rivers.remove(river);
			return;
		}
		river.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(cleaned.get(0));
		changed.add(river);
		for (int i = 1; i < cleaned.size(); i++)
		{
			River newRiver = new River(cleaned.get(i));
			mainWindow.edits.rivers.add(newRiver);
			changed.add(newRiver);
		}
	}

	/** Road counterpart of {@link #applyRiverFragments}. */
	private void applyRoadFragments(Road road, List<List<RoadPathNode>> fragments, List<Road> changed)
	{
		List<List<RoadPathNode>> cleaned = new ArrayList<>(fragments.size());
		for (List<RoadPathNode> fragment : fragments)
		{
			List<RoadPathNode> normalized = PathOperations.normalizePath(fragment, RoadDrawer.ROAD_OPS);
			if (normalized.size() >= 2)
			{
				cleaned.add(normalized);
			}
		}
		if (cleaned.isEmpty())
		{
			mainWindow.edits.roads.remove(road);
			return;
		}
		road.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(cleaned.get(0));
		changed.add(road);
		for (int i = 1; i < cleaned.size(); i++)
		{
			Road newRoad = new Road(cleaned.get(i));
			mainWindow.edits.roads.add(newRoad);
			changed.add(newRoad);
		}
	}

	/**
	 * Captures contiguous runs of selected-CP locations from the active line type into the clipboard. Each run becomes one paste-able
	 * mini-line. Non-contiguous selections produce multiple runs.
	 */
	private void copySelectedCPs()
	{
		if (!hasAnySelection())
		{
			return;
		}
		List<List<Point>> runs = new ArrayList<>();
		List<List<Integer>> widthRuns = new ArrayList<>();
		LineType type = !selectedRiverCPs.isEmpty() ? LineType.RIVER : LineType.ROAD;
		if (type == LineType.RIVER)
		{
			for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
			{
				List<RiverPathNode> nodes = entry.getKey().nodes;
				Set<Integer> sel = entry.getValue();
				List<Point> run = new ArrayList<>();
				List<Integer> widths = new ArrayList<>();
				for (int i = 0; i < nodes.size(); i++)
				{
					if (sel.contains(i))
					{
						run.add(nodes.get(i).getLoc());
						widths.add(nodes.get(i).getWidthLevelToNext());
					}
					else if (!run.isEmpty())
					{
						if (run.size() >= 2)
						{
							runs.add(run);
							widthRuns.add(widths);
						}
						run = new ArrayList<>();
						widths = new ArrayList<>();
					}
				}
				if (run.size() >= 2)
				{
					runs.add(run);
					widthRuns.add(widths);
				}
			}
		}
		else
		{
			for (Map.Entry<Road, Set<Integer>> entry : selectedRoadCPs.entrySet())
			{
				List<RoadPathNode> nodes = entry.getKey().nodes;
				Set<Integer> sel = entry.getValue();
				List<Point> run = new ArrayList<>();
				for (int i = 0; i < nodes.size(); i++)
				{
					if (sel.contains(i))
					{
						run.add(nodes.get(i).getLoc());
					}
					else if (!run.isEmpty())
					{
						if (run.size() >= 2)
						{
							runs.add(run);
						}
						run = new ArrayList<>();
					}
				}
				if (run.size() >= 2)
				{
					runs.add(run);
				}
			}
		}
		if (runs.isEmpty())
		{
			return;
		}
		copiedCPRuns = runs;
		copiedRiverWidthLevels = type == LineType.RIVER ? widthRuns : null;
		copiedLineType = type;
	}

	/**
	 * Pastes the clipboard as new lines of the same type. When the mouse is over the map, the bounding box of the copied CPs is centered on
	 * the cursor. When the mouse isn't over the map (e.g. the user clicked the Paste button), the copies are translated by a small fixed
	 * offset so they don't land exactly on top of the originals — matches IconsTool's paste behavior.
	 */
	private void pasteCopiedCPs()
	{
		if (copiedCPRuns == null || copiedCPRuns.isEmpty() || copiedLineType == null)
		{
			return;
		}
		LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
		if (activeType != copiedLineType)
		{
			return;
		}
		java.awt.Point mouse = mapEditingPanel.getMousePosition();
		Point delta;
		if (mouse != null)
		{
			Point mouseRI = getPointOnGraph(mouse).mult(1.0 / mainWindow.displayQualityScale);
			// Centroid of the bounding box of every copied CP — gives the paste a natural "drop the group here" feel for multi-run pastes.
			double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
			for (List<Point> run : copiedCPRuns)
			{
				for (Point p : run)
				{
					if (p.x < minX)
						minX = p.x;
					if (p.x > maxX)
						maxX = p.x;
					if (p.y < minY)
						minY = p.y;
					if (p.y > maxY)
						maxY = p.y;
				}
			}
			Point center = new Point((minX + maxX) / 2.0, (minY + maxY) / 2.0);
			delta = new Point(mouseRI.x - center.x, mouseRI.y - center.y);
		}
		else
		{
			// Same fallback offset that IconsTool uses when its paste button is clicked while the mouse isn't over the map.
			final double offset = 50.0;
			delta = new Point(offset, offset);
		}

		clearSelection();
		List<List<Point>> centersTouched = new ArrayList<>();
		Set<Road> roadsToRephaseDashes = new HashSet<>();
		for (int runIdx = 0; runIdx < copiedCPRuns.size(); runIdx++)
		{
			List<Point> run = copiedCPRuns.get(runIdx);
			List<Point> translated = new ArrayList<>(run.size());
			for (Point p : run)
			{
				translated.add(new Point(p.x + delta.x, p.y + delta.y));
			}
			if (activeType == LineType.RIVER)
			{
				List<Integer> widthLevels = copiedRiverWidthLevels != null && runIdx < copiedRiverWidthLevels.size() ? copiedRiverWidthLevels.get(runIdx) : null;
				int defaultWidthLevel = GraphRiver.MIN_DRAWN_RIVER_LEVEL;
				List<RiverPathNode> newNodes = new ArrayList<>(translated.size());
				Random random = new Random();
				for (int i = 0; i < translated.size(); i++)
				{
					int widthLevel = widthLevels != null && i < widthLevels.size() ? widthLevels.get(i) : defaultWidthLevel;
					newNodes.add(new RiverPathNode(translated.get(i), widthLevel, random.nextLong(), RiverPathNode.EDGE_INDEX_NONE));
				}
				River newRiver = new River(newNodes);
				mainWindow.edits.rivers.add(newRiver);
				Set<Integer> sel = new HashSet<>();
				for (int i = 0; i < newNodes.size(); i++)
				{
					sel.add(i);
				}
				selectedRiverCPs.put(newRiver, sel);
				centersTouched.add(translated);
			}
			else
			{
				List<RoadPathNode> newNodes = new ArrayList<>(translated.size());
				for (Point p : translated)
				{
					newNodes.add(new RoadPathNode(p));
				}
				Road newRoad = new Road(newNodes);
				mainWindow.edits.roads.add(newRoad);
				Set<Integer> sel = new HashSet<>();
				for (int i = 0; i < newNodes.size(); i++)
				{
					sel.add(i);
				}
				selectedRoadCPs.put(newRoad, sel);
				centersTouched.add(translated);
				roadsToRephaseDashes.add(newRoad);
			}
		}
		if (!roadsToRephaseDashes.isEmpty())
		{
			updater.addRoadsToRedrawLowPriority(new ArrayList<>(roadsToRephaseDashes), mainWindow.displayQualityScale);
		}
		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingPoints(centersTouched));
		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));
		refreshSelectionVisuals(mouse, activeType);
	}

	/**
	 * Distance threshold (in graph pixels) for showing the hover highlight on a river/road. The right-click context-menu hit-test uses the
	 * same threshold so the menu becomes available exactly when the line is visually highlighted.
	 */
	private double getHoverHighlightThresholdInGraphPixels()
	{
		return updater.mapParts.graph.getMeanCenterWidth();
	}

	private double getHoverHighlightThresholdInRI()
	{
		return getHoverHighlightThresholdInGraphPixels() / mainWindow.displayQualityScale;
	}

	/**
	 * Distance threshold (in graph pixels) for resolving a segment click in edit mode. Mirrors Erase mode: for brush&nbsp;=&nbsp;1 the
	 * threshold is the same small "single-point" radius Erase mode uses (so clicks feel the same in both modes); for brush&nbsp;&gt;&nbsp;1
	 * it's the brush radius (so the user explicitly chose how wide the hit is). Used by both click resolution and the segment
	 * hover-highlight so the visualization always matches what a click would actually pick.
	 */
	private double getEditSegmentHitThresholdInGraphPixels()
	{
		int brushDiameter = getEditBrushDiameter();
		if (brushDiameter <= 1)
		{
			// Erase mode's brush=1 radius is in RI; convert to graph pixels here.
			return ((double) singlePointRoadSelectionRadiusBeforeZoomAndScale / mainWindow.zoom) * mapEditingPanel.osScale * mainWindow.displayQualityScale;
		}
		return ((double) brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale / 2.0;
	}

	private Point computeSnapPointForType(java.awt.Point mouseLocation, LineType type)
	{
		if (updater.mapParts == null)
		{
			return null;
		}
		return findNearestPointInPaths(mouseLocation, getAllLinePaths(type), getFreeHandPath(type));
	}

	private List<List<Point>> getAllLinePaths(LineType type)
	{
		return type == LineType.RIVER ? mainWindow.edits.rivers.stream().map(r -> PathOperations.toLocationList(r.nodes)).toList()
				: mainWindow.edits.roads.stream().map(r -> PathOperations.toLocationList(r.nodes)).toList();
	}

	private List<Point> getFreeHandPath(LineType type)
	{
		return type == LineType.RIVER ? freeHandRiverPathRI : freeHandRoadPathRI;
	}

	private void setFreeHandPath(LineType type, List<Point> path)
	{
		if (type == LineType.RIVER)
		{
			freeHandRiverPathRI = path;
		}
		else
		{
			freeHandRoadPathRI = path;
		}
	}

	private void setSnapPoint(LineType type, Point snapPoint)
	{
		if (type == LineType.RIVER)
		{
			freeHandRiverSnapPoint = snapPoint;
		}
		else
		{
			freeHandRoadSnapPoint = snapPoint;
		}
	}

	private void updateControlPointDisplay(java.awt.Point mouseLocation, LineType type)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null)
		{
			return;
		}

		Point snapPoint = mouseLocation != null ? computeSnapPointForType(mouseLocation, type) : null;
		setSnapPoint(type, snapPoint);

		List<Point> circlesGraphPixels = new ArrayList<>();
		Point mouseRI = mouseLocation != null ? getPointOnGraph(mouseLocation).mult(1.0 / mainWindow.displayQualityScale) : null;
		double highlightThresholdRI = getHoverHighlightThresholdInRI();
		for (List<Point> path : getAllLinePaths(type))
		{
			if (mouseRI != null && isPathNearPoint(path, mouseRI, highlightThresholdRI))
			{
				for (Point riPoint : path)
				{
					circlesGraphPixels.add(riPoint.mult(mainWindow.displayQualityScale));
				}
			}
		}
		// Show the start of the line being drawn so the user can snap to it and close the loop.
		List<Point> freeHandPath = getFreeHandPath(type);
		if (freeHandPath != null && freeHandPath.size() >= 2)
		{
			circlesGraphPixels.add(freeHandPath.get(0).mult(mainWindow.displayQualityScale));
		}
		mapEditingPanel.setControlPointCircles(circlesGraphPixels);

		if (snapPoint != null)
		{
			// Draw mode hover = filled yellow disk — the snap point is a single decisive click target.
			mapEditingPanel.setHoveredRoadControlPoint(snapPoint.mult(mainWindow.displayQualityScale), true);
		}
		else
		{
			mapEditingPanel.clearHoveredControlPoint();
		}

		if (mouseLocation != null && freeHandPath != null && (type == LineType.RIVER || isFreeHandDrawMode()))
		{
			Point currentRI = snapPoint != null ? snapPoint : getPointOnGraph(mouseLocation).mult(1.0 / mainWindow.displayQualityScale);
			List<Point> previewGraphPixels = new ArrayList<>();
			for (Point riPoint : freeHandPath)
			{
				previewGraphPixels.add(riPoint.mult(mainWindow.displayQualityScale));
			}
			previewGraphPixels.add(currentRI.mult(mainWindow.displayQualityScale));
			mapEditingPanel.setFreeHandPreviewPath(previewGraphPixels);
		}
	}

	private void clearRoadControlPointDisplay()
	{
		freeHandRoadSnapPoint = null;
		mapEditingPanel.clearRoadControlPointCircles();
		mapEditingPanel.clearHoveredControlPoint();
		mapEditingPanel.clearFreeHandRoadPreviewPath();
	}

	private void cancelFreeHandDrawing(LineType type)
	{
		if (type == LineType.ROAD)
		{
			if (freeHandRoadPathRI == null)
			{
				return;
			}
			freeHandRoadPathRI = null;
			polygonRoadSnapStart = null;
			freeHandRoadSnapPoint = null;
		}
		else
		{
			freeHandRiverPathRI = null;
			freeHandRiverSnapPoint = null;
			polygonRiverSnapStart = null;
		}
		// Clear only the in-progress preview path. Leave the nearby control point circles and the snap
		// indicator in place so the highlight persists after escape until the user moves the mouse again.
		mapEditingPanel.clearFreeHandRoadPreviewPath();
		mapEditingPanel.repaint();
	}

	private void finalizeFreeHandLine(LineType type)
	{
		List<Point> freeHandPath = getFreeHandPath(type);
		if (freeHandPath == null || freeHandPath.size() < 2)
		{
			setFreeHandPath(type, null);
			clearRoadControlPointDisplay();
			return;
		}

		List<Point> pathToCommit = freeHandPath;
		setFreeHandPath(type, null);
		clearRoadControlPointDisplay();
		mapEditingPanel.repaint();

		List<List<Point>> pathsForCenters;
		if (type == LineType.RIVER)
		{
			int riverLevel = sliderValueToRiverLevel(riverWidthSlider.getValue());
			List<River> newRivers = RiverDrawer.addFreeHandRiverFromPoints(pathToCommit, riverLevel, mainWindow.edits.rivers, updater.mapParts.graph, mainWindow.displayQualityScale);
			if (newRivers.isEmpty())
			{
				return;
			}
			pathsForCenters = newRivers.stream().map(r -> PathOperations.toLocationList(r.nodes)).collect(Collectors.toList());
		}
		else
		{
			List<Road> changedList = RoadDrawer.addFreeHandRoadFromPoints(pathToCommit, mainWindow.edits.roads);
			RoadDrawer.removeEmptyOrSinglePointRoads(mainWindow.edits.roads);
			updater.addRoadsToRedrawLowPriority(changedList, mainWindow.displayQualityScale);
			pathsForCenters = changedList.stream().map(r -> PathOperations.toLocationList(r.nodes)).collect(Collectors.toList());
		}

		updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingPoints(pathsForCenters));
		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));
		undoer.setUndoPoint(UpdateType.Incremental, this);
	}

	private JPanel createColorGeneratorOptionsPanel(JPanel toolOptionsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();
		organizer.panel.setBorder(BorderFactory.createTitledBorder(new DynamicLineBorder("controlShadow", 1), getColorGeneratorSettingsName()));

		baseColorPanel = SwingHelper.createColorPickerPreviewPanel();
		final JButton baseColorChooseButton = new JButton(Translation.get("common.choose"));
		baseColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				SwingHelper.showColorPicker(toolOptionsPanel, baseColorPanel, Translation.get("landWaterTool.baseColor.title"), () ->
				{
				});
			}
		});
		organizer.addLabelAndComponentsHorizontal(Translation.get("landWaterTool.baseColor.label"), Translation.get("landWaterTool.baseColor.help"),
				Arrays.asList(baseColorPanel, baseColorChooseButton), SwingHelper.borderWidthBetweenComponents);

		final int labelWidth = 30;

		hueSlider = new JSlider();
		hueSlider.setMaximum(360);
		SliderWithDisplayedValue hueSliderWithDisplay = new SliderWithDisplayedValue(hueSlider, null, null, labelWidth);
		hueSliderWithDisplay.addToOrganizer(organizer, Translation.get("landWaterTool.hueRange.label"), Translation.get("landWaterTool.hueRange.help"));

		saturationSlider = new JSlider();
		saturationSlider.setMaximum(100);
		SliderWithDisplayedValue saturationSliderWithDisplay = new SliderWithDisplayedValue(saturationSlider, null, null, labelWidth);
		saturationSliderWithDisplay.addToOrganizer(organizer, Translation.get("landWaterTool.saturationRange.label"), Translation.get("landWaterTool.saturationRange.help"));

		brightnessSlider = new JSlider();
		brightnessSlider.setMaximum(100);
		SliderWithDisplayedValue brightnessSliderWithDisplay = new SliderWithDisplayedValue(brightnessSlider, null, null, labelWidth);
		brightnessSliderWithDisplay.addToOrganizer(organizer, Translation.get("landWaterTool.brightnessRange.label"), Translation.get("landWaterTool.brightnessRange.help"));

		return organizer.panel;
	}

	private void showOrHideBrushOptions()
	{
		fillRegionColorButton.setVisible(areRegionColorsVisible);
		mergeRegionsButton.setVisible(areRegionBoundariesVisible || areRegionColorsVisible);
		roadsButton.setVisible(areRoadsVisible);

		if (mergeRegionsButton.isSelected() && !mergeRegionsButton.isVisible())
		{
			landButton.setSelected(true);
		}
		else if (fillRegionColorButton.isSelected() && !fillRegionColorButton.isVisible())
		{
			landButton.setSelected(true);
		}
		else if (roadsButton.isSelected() && !roadsButton.isVisible())
		{
			oceanButton.setSelected(true);
		}

		brushActionListener.actionPerformed(null);

		showOrHideNewRegionButton();
	}

	private void showOrHideNewRegionButton()
	{
		newRegionButtonHider.setVisible((areRegionBoundariesVisible || areRegionColorsVisible) && landButton.isSelected());
		String labelKey = areRegionColorsVisible ? "landWaterTool.createNewRegion" : "landWaterTool.createNewRegion.singleColor";
		newRegionButton.setText(Translation.get(labelKey));
		String helpKey = areRegionColorsVisible ? "landWaterTool.createNewRegion.help" : "landWaterTool.createNewRegion.singleColor.help";
		newRegionButton.setToolTipText(Translation.get(helpKey));
	}

	private void updateColorControlVisibility()
	{
		boolean showColorControls = (newRegionButton.isSelected() && areRegionColorsVisible) || fillRegionColorButton.isSelected();
		colorChooserHider.setVisible(showColorControls);
		selectColorHider.setVisible(showColorControls);
		generateColorButtonHider.setVisible(showColorControls);
		colorGeneratorSettingsHider.setVisible(showColorControls);
	}

	private LineHit editModeHitTest(java.awt.Point panelPoint, LineType activeType, River scopeRiver, Road scopeRoad)
	{
		return editModeHitTest(panelPoint, activeType, scopeRiver, scopeRoad, -1);
	}

	/**
	 * @param customSegmentThresholdGraphPixels
	 *            When non-negative, overrides the default {@code controlPointRadius * 2.0} segment hit threshold. Used by the right-click
	 *            context-menu hit-test, which wants to match the on-screen hover highlight range so the menu is available whenever the line
	 *            is visually highlighted (avoiding the confusing case where the highlight is shown but right-click does nothing because the
	 *            cursor isn't close enough to the centerline). Pass {@code -1} for the default threshold.
	 */
	private LineHit editModeHitTest(java.awt.Point panelPoint, LineType activeType, River scopeRiver, Road scopeRoad, double customSegmentThresholdGraphPixels)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null || panelPoint == null)
		{
			return null;
		}
		Point clickGraph = getPointOnGraph(panelPoint);
		double controlPointRadius = mapEditingPanel.getRoadControlPointHitRadiusInGraphPixels();
		// Default segment threshold mirrors Erase mode (see getEditSegmentHitThresholdInGraphPixels). Callers can pass a custom override
		// (e.g. when they want to ignore brush size).
		double segmentThreshold = customSegmentThresholdGraphPixels >= 0 ? customSegmentThresholdGraphPixels : getEditSegmentHitThresholdInGraphPixels();
		double scale = mainWindow.displayQualityScale;

		River bestCpRiver = null;
		Road bestCpRoad = null;
		int bestCpIdx = -1;
		double bestCpDist = controlPointRadius;
		River bestSegRiver = null;
		Road bestSegRoad = null;
		int bestSegIdx = -1;
		double bestSegDist = segmentThreshold;

		if (activeType == LineType.RIVER)
		{
			Iterable<River> riversToScan = scopeRiver != null ? Collections.singletonList(scopeRiver) : mainWindow.edits.rivers;
			for (River river : riversToScan)
			{
				List<RiverPathNode> nodes = river.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					Point pGraph = nodes.get(i).getLoc().mult(scale);
					double d = clickGraph.distanceTo(pGraph);
					if (d < bestCpDist)
					{
						bestCpDist = d;
						bestCpRiver = river;
						bestCpIdx = i;
					}
				}
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					double d = GeometryHelper.distanceFromPointToSegment(clickGraph, a, b);
					if (d < bestSegDist)
					{
						bestSegDist = d;
						bestSegRiver = river;
						bestSegIdx = i;
					}
				}
			}
		}
		else
		{
			Iterable<Road> roadsToScan = scopeRoad != null ? Collections.singletonList(scopeRoad) : mainWindow.edits.roads;
			for (Road road : roadsToScan)
			{
				List<RoadPathNode> nodes = road.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					Point pGraph = nodes.get(i).getLoc().mult(scale);
					double d = clickGraph.distanceTo(pGraph);
					if (d < bestCpDist)
					{
						bestCpDist = d;
						bestCpRoad = road;
						bestCpIdx = i;
					}
				}
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					double d = GeometryHelper.distanceFromPointToSegment(clickGraph, a, b);
					if (d < bestSegDist)
					{
						bestSegDist = d;
						bestSegRoad = road;
						bestSegIdx = i;
					}
				}
			}
		}

		if (bestCpRiver != null)
		{
			return new LineHit(bestCpRiver, null, bestCpIdx, -1);
		}
		if (bestCpRoad != null)
		{
			return new LineHit(null, bestCpRoad, bestCpIdx, -1);
		}
		if (bestSegRiver != null)
		{
			return new LineHit(bestSegRiver, null, -1, bestSegIdx);
		}
		if (bestSegRoad != null)
		{
			return new LineHit(null, bestSegRoad, -1, bestSegIdx);
		}
		return null;
	}

	private void clearHoverState()
	{
		hoveredRiver = null;
		hoveredRoad = null;
	}

	/**
	 * Refreshes the river-width slider's visibility based on the current mode and selection state. Should be called after any change to the
	 * selection or mode.
	 */
	private void refreshRiverWidthSliderVisibility()
	{
		if (riverOptionHider == null)
		{
			return;
		}
		boolean showSliderInDraw = riversButton.isSelected() && modeWidget.isDrawMode();
		boolean showSliderInEdit = riversButton.isSelected() && modeWidget.isEditMode() && isAnyRiverSegmentSelected();
		riverOptionHider.setVisible(showSliderInDraw || showSliderInEdit);
	}

	/**
	 * Refreshes the edit-mode hover preview. The highlighted CPs and segments are exactly what a press at the current cursor position would
	 * select, so the preview is never out-of-sync with the press behavior (both call {@link #computePressOutcome}).
	 */
	private void updateEditModeHoverDisplay(java.awt.Point mouseLocation, LineType type, boolean ctrlDown)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null)
		{
			return;
		}
		double scale = mainWindow.displayQualityScale;
		int brushDiameter = getEditBrushDiameter();

		// Filled circles: the live selection (independent of where the cursor is). Outlines, yellow rings, and hover polylines all
		// come from the press outcome — they preview exactly what would change if the press fires right now, so the visuals always
		// match the actual click behavior.
		List<Point> selectedCirclesGraphPixels = collectCPGraphLocations(selectedRiverCPs, selectedRoadCPs, type, scale);
		List<Point> outlinedCirclesGraphPixels = new ArrayList<>();
		List<Point> hoverRingsGraphPixels = new ArrayList<>();
		mapEditingPanel.clearHoverPolylines();

		if (mouseLocation != null)
		{
			boolean deselectMode = controlClickBehavior != null && controlClickBehavior.isUnselectMode();
			PressOutcome preview = computePressOutcome(mouseLocation, ctrlDown, deselectMode, brushDiameter, type);
			List<Point> previewLocations = collectCPGraphLocations(preview.riverCPsAfter(), preview.roadCPsAfter(), type, scale);
			Set<Point> selectedLocations = new HashSet<>(selectedCirclesGraphPixels);
			// CPs that the press would ADD to the selection get the orange outline AND the yellow hover ring — they're the
			// click targets, so we make them visually obvious. The yellow ring matching the orange outline keeps the hover-on-
			// segment case visually consistent with the hover-on-CP case (in both, the would-be-selected CP gets both rings).
			for (Point p : previewLocations)
			{
				if (!selectedLocations.contains(p))
				{
					outlinedCirclesGraphPixels.add(p);
					hoverRingsGraphPixels.add(p);
				}
			}
			// Segments that would be implicitly selected post-press (both endpoints in preview) but aren't currently — show as hover
			// polylines. Implicitly-selected segments that are already selected stay yellow via applySelectedSegmentsHighlight.
			addHoverPolylinesForNewImplicitSegments(type, preview, scale);
		}

		mapEditingPanel.setControlPointCircles(outlinedCirclesGraphPixels);
		mapEditingPanel.setSelectedControlPointCircles(selectedCirclesGraphPixels);
		if (!hoverRingsGraphPixels.isEmpty())
		{
			// Edit mode hover = stroked yellow ring — kept visually distinct from the filled-yellow selected CP.
			mapEditingPanel.setHoveredRoadControlPoints(hoverRingsGraphPixels, false);
		}
		else
		{
			mapEditingPanel.clearHoveredControlPoint();
		}
	}

	/** Returns the graph-pixel locations of every CP in {@code riverCPs}/{@code roadCPs} of the active type. */
	private static List<Point> collectCPGraphLocations(Map<River, Set<Integer>> riverCPs, Map<Road, Set<Integer>> roadCPs, LineType activeType, double scale)
	{
		List<Point> result = new ArrayList<>();
		if (activeType == LineType.RIVER && riverCPs != null)
		{
			for (Map.Entry<River, Set<Integer>> entry : riverCPs.entrySet())
			{
				List<RiverPathNode> nodes = entry.getKey().nodes;
				for (int idx : entry.getValue())
				{
					if (idx >= 0 && idx < nodes.size())
					{
						result.add(nodes.get(idx).getLoc().mult(scale));
					}
				}
			}
		}
		else if (activeType == LineType.ROAD && roadCPs != null)
		{
			for (Map.Entry<Road, Set<Integer>> entry : roadCPs.entrySet())
			{
				List<RoadPathNode> nodes = entry.getKey().nodes;
				for (int idx : entry.getValue())
				{
					if (idx >= 0 && idx < nodes.size())
					{
						result.add(nodes.get(idx).getLoc().mult(scale));
					}
				}
			}
		}
		return result;
	}

	/**
	 * Adds a hover polyline for every segment that would be implicitly selected by the press (both endpoints in {@code preview}) but isn't
	 * already (so {@link #applySelectedSegmentsHighlight} would draw it yellow). Keeps preview-orange polylines distinct from
	 * selection-yellow polylines.
	 */
	private void addHoverPolylinesForNewImplicitSegments(LineType activeType, PressOutcome preview, double scale)
	{
		if (activeType == LineType.RIVER)
		{
			for (Map.Entry<River, Set<Integer>> entry : preview.riverCPsAfter().entrySet())
			{
				List<RiverPathNode> nodes = entry.getKey().nodes;
				Set<Integer> sel = entry.getValue();
				Set<Integer> currentSel = selectedRiverCPs.getOrDefault(entry.getKey(), Collections.emptySet());
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					if (!sel.contains(i) || !sel.contains(i + 1))
					{
						continue;
					}
					boolean wasImplicitlySelected = currentSel.contains(i) && currentSel.contains(i + 1);
					if (wasImplicitlySelected)
					{
						continue;
					}
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					mapEditingPanel.addHoverPolyline(List.of(a, b));
				}
			}
		}
		else
		{
			for (Map.Entry<Road, Set<Integer>> entry : preview.roadCPsAfter().entrySet())
			{
				List<RoadPathNode> nodes = entry.getKey().nodes;
				Set<Integer> sel = entry.getValue();
				Set<Integer> currentSel = selectedRoadCPs.getOrDefault(entry.getKey(), Collections.emptySet());
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					if (!sel.contains(i) || !sel.contains(i + 1))
					{
						continue;
					}
					boolean wasImplicitlySelected = currentSel.contains(i) && currentSel.contains(i + 1);
					if (wasImplicitlySelected)
					{
						continue;
					}
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					mapEditingPanel.addHoverPolyline(List.of(a, b));
				}
			}
		}
	}

	/**
	 * Adds every implicitly-selected segment (both endpoint CPs in the selection) to the panel's highlighted-polyline list. Callers must
	 * clear highlighted polylines beforehand if they want only the selection highlight to remain. Safe to call when nothing is selected.
	 */
	private void applySelectedSegmentsHighlight()
	{
		double scale = mainWindow.displayQualityScale;
		for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
		{
			List<RiverPathNode> nodes = entry.getKey().nodes;
			Set<Integer> sel = entry.getValue();
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				if (sel.contains(i) && sel.contains(i + 1))
				{
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					mapEditingPanel.addPolylinesToHighlight(List.of(a, b));
				}
			}
		}
		for (Map.Entry<Road, Set<Integer>> entry : selectedRoadCPs.entrySet())
		{
			List<RoadPathNode> nodes = entry.getKey().nodes;
			Set<Integer> sel = entry.getValue();
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				if (sel.contains(i) && sel.contains(i + 1))
				{
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					mapEditingPanel.addPolylinesToHighlight(List.of(a, b));
				}
			}
		}
	}

	/** Returns the river level for {@code sliderValue}. The slider value is one greater than the river level "base" index. */
	private int sliderValueToRiverLevel(int sliderValue)
	{
		return GraphRiver.sliderBaseToRiverLevel(sliderValue - 1);
	}

	/** Inverse of {@link #sliderValueToRiverLevel(int)}; clamped to the slider's bounds. */
	private int riverLevelToSliderValue(int riverLevel)
	{
		int value = GraphRiver.riverLevelToSliderBase(riverLevel) + 1;
		if (value < riverWidthSlider.getMinimum())
		{
			value = riverWidthSlider.getMinimum();
		}
		if (value > riverWidthSlider.getMaximum())
		{
			value = riverWidthSlider.getMaximum();
		}
		return value;
	}

	/**
	 * Returns the mode (most common value) of width levels across all currently-selected river segments. Returns -1 when no segment is
	 * selected — the slider is hidden in that case.
	 */
	private int currentEditSelectedSegmentWidthLevel()
	{
		Map<Integer, Integer> counts = new HashMap<>();
		int bestLevel = -1;
		int bestCount = 0;
		for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
		{
			List<RiverPathNode> nodes = entry.getKey().nodes;
			Set<Integer> sel = entry.getValue();
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				if (sel.contains(i) && sel.contains(i + 1))
				{
					int lvl = nodes.get(i).getWidthLevelToNext();
					int c = counts.merge(lvl, 1, Integer::sum);
					if (c > bestCount)
					{
						bestCount = c;
						bestLevel = lvl;
					}
				}
			}
		}
		return bestLevel;
	}

	/**
	 * Snapshot the current width level of every selected river segment, keyed by (river, segmentIndex). Used to push an undo point only
	 * when at least one selected segment's width actually changed during a slider drag.
	 */
	private Map<River, Map<Integer, Integer>> captureSelectedRiverSegmentWidths()
	{
		Map<River, Map<Integer, Integer>> result = new java.util.IdentityHashMap<>();
		for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
		{
			List<RiverPathNode> nodes = entry.getKey().nodes;
			Set<Integer> sel = entry.getValue();
			Map<Integer, Integer> perSeg = new HashMap<>();
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				if (sel.contains(i) && sel.contains(i + 1))
				{
					perSeg.put(i, nodes.get(i).getWidthLevelToNext());
				}
			}
			if (!perSeg.isEmpty())
			{
				result.put(entry.getKey(), perSeg);
			}
		}
		return result;
	}

	/**
	 * Whenever the slider value changes AND we're in edit mode with a river segment selected, rewrite every selected segment's width level
	 * live. The change listener also fires when the slider is synced programmatically from selection; {@link #syncingSliderToSelection}
	 * suppresses the rewrite in that case.
	 */
	private void handleRiverWidthSliderChanged()
	{
		if (syncingSliderToSelection)
		{
			return;
		}
		if (!modeWidget.isEditMode() || selectedRiverCPs.isEmpty())
		{
			return;
		}
		int newLevel = sliderValueToRiverLevel(riverWidthSlider.getValue());
		List<List<Point>> centersTouched = new ArrayList<>();
		boolean anyChanged = false;
		for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
		{
			River river = entry.getKey();
			Set<Integer> sel = entry.getValue();
			if (sel.size() < 2)
			{
				continue;
			}
			List<RiverPathNode> nodes = new ArrayList<>(river.nodes);
			boolean changedThisRiver = false;
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				if (sel.contains(i) && sel.contains(i + 1))
				{
					RiverPathNode old = nodes.get(i);
					if (old.getWidthLevelToNext() != newLevel)
					{
						nodes.set(i, new RiverPathNode(old.getLoc(), newLevel, old.getSeedToNext(), old.getEdgeIndexToNext()));
						changedThisRiver = true;
					}
				}
			}
			if (changedThisRiver)
			{
				river.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(nodes);
				centersTouched.add(PathOperations.toLocationList(nodes));
				anyChanged = true;
			}
		}
		if (anyChanged)
		{
			updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingPoints(centersTouched));
		}
	}

	private void commitSliderEditIfChanged()
	{
		if (sliderEditWidthsBeforeDrag == null)
		{
			return;
		}
		boolean changed = false;
		for (Map.Entry<River, Map<Integer, Integer>> entry : sliderEditWidthsBeforeDrag.entrySet())
		{
			River river = entry.getKey();
			List<RiverPathNode> nodes = river.nodes;
			for (Map.Entry<Integer, Integer> seg : entry.getValue().entrySet())
			{
				int segIdx = seg.getKey();
				int beforeLevel = seg.getValue();
				if (segIdx < nodes.size() - 1 && nodes.get(segIdx).getWidthLevelToNext() != beforeLevel)
				{
					changed = true;
					break;
				}
			}
			if (changed)
			{
				break;
			}
		}
		if (changed)
		{
			undoer.setUndoPoint(UpdateType.Incremental, this);
		}
		sliderEditWidthsBeforeDrag = null;
	}

	/** Reflects the mode of selected segment widths in the slider, suppressing the change-listener side effect. */
	private void syncSliderToSelectedSegment()
	{
		int level = currentEditSelectedSegmentWidthLevel();
		if (level < 0)
		{
			return;
		}
		int sliderValue = riverLevelToSliderValue(level);
		if (sliderValue == riverWidthSlider.getValue())
		{
			return;
		}
		syncingSliderToSelection = true;
		try
		{
			riverWidthSlider.setValue(sliderValue);
		}
		finally
		{
			syncingSliderToSelection = false;
		}
	}

	private Integer regionIdToExpand;

	private void handleSharedPressAndDragActions(MouseEvent e, boolean isMouseDrag)
	{
		if (!SwingUtilities.isLeftMouseButton(e))
		{
			return;
		}

		if (mergeRegionsButton.isSelected() && isMouseDrag)
		{
			return;
		}

		highlightHoverCentersOrEdgesAndBrush(e.getPoint());

		if (oceanButton.isSelected() || lakesButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			boolean hasChange = false;
			for (Center center : selected)
			{
				CenterEdit edit = mainWindow.edits.centerEdits.get(center.index);
				hasChange |= !edit.isWater;
				hasChange |= edit.isLake != lakesButton.isSelected();
				// Note that I'm nulling out trees in the assignment below because any trees that failed to draw previously should be
				// cleared out when the Center becomes water.
				mainWindow.edits.centerEdits.put(edit.index, new CenterEdit(edit.index, true, lakesButton.isSelected(), null, edit.icon, null));
			}
			if (hasChange)
			{
				removeRiversCoveredByPaintedWater(selected);
				handleMapChange(selected);
			}
		}
		else if (landButton.isSelected())
		{
			if (selectColorFromMapButton.isVisible() && selectColorFromMapButton.isSelected())
			{
				selectColorFromMap(e);
				return;
			}

			Set<Center> selected = getSelectedCenters(e.getPoint());

			if (!isMouseDrag)
			{
				// Set the id of the region that will be expanded as the user drags the mouse.

				if (newRegionButton.isSelected())
				{
					regionIdToExpand = createNewRegion(getCurrentRegionColor());
					newRegionButton.setSelected(false);
					updateColorControlVisibility();
				}
				else
				{
					Set<Center> mouseDownCenters = getSelectedCenters(e.getPoint(), 1);
					if (mouseDownCenters == null || mouseDownCenters.isEmpty())
					{
						// The mouse press was not on the map
						return;
					}
					assert mouseDownCenters.size() == 1;
					Center mouseDownCenter = mouseDownCenters.iterator().next();
					CenterEdit centerEdit = mainWindow.edits.centerEdits.get(mouseDownCenter.index);
					if (centerEdit.regionId == null)
					{
						// Find the nearest political region when drawing in water.
						nortantis.geom.Point graphPoint = getPointOnGraph(e.getPoint());
						Optional<CenterEdit> nearest = mainWindow.edits.centerEdits.values().stream().filter(cEdit -> cEdit.regionId != null).min((c1, c2) -> Double
								.compare(updater.mapParts.graph.centers.get(c1.index).loc.distanceTo(graphPoint), updater.mapParts.graph.centers.get(c2.index).loc.distanceTo(graphPoint)));
						regionIdToExpand = nearest.map(edit -> edit.regionId).orElse(null);
					}
					else
					{
						regionIdToExpand = centerEdit.regionId;
					}
				}
			}

			boolean hasChange = false;
			for (Center center : selected)
			{
				CenterEdit edit = mainWindow.edits.centerEdits.get(center.index);
				if (onlyUpdateLandCheckbox.isSelected() && edit.isWater)
				{
					continue;
				}
				// Always add region IDs to edits even if regions aren't displayed because the user might show them later.
				Integer newRegionId = getOrCreateRegionIdForEdit(center, getCurrentRegionColor());
				hasChange |= (edit.regionId == null) || newRegionId != edit.regionId;
				hasChange |= edit.isWater;
				mainWindow.edits.centerEdits.put(edit.index, new CenterEdit(edit.index, false, false, newRegionId, edit.icon, edit.trees));
			}
			if (hasChange)
			{
				handleMapChange(selected);
			}

		}
		else if (fillRegionColorButton.isSelected())
		{
			if (selectColorFromMapButton.isSelected())
			{
				selectColorFromMap(e);
			}
			else
			{
				Center center = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
				if (center != null)
				{
					Region region = center.region;
					if (region != null)
					{
						RegionEdit edit = mainWindow.edits.regionEdits.get(region.id);
						edit.color = AwtBridge.fromAwtColor(colorDisplay.getBackground());
						Set<Center> regionCenters = region.getCenters();
						handleMapChange(regionCenters);
					}
				}
			}
		}
		else if (mergeRegionsButton.isSelected())
		{
			Center center = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
			if (center != null)
			{
				Region region = center.region;
				if (region != null)
				{
					if (selectedRegion == null)
					{
						selectedRegion = region;
						mapEditingPanel.addSelectedCenters(selectedRegion.getCenters());
					}
					else
					{
						if (region == selectedRegion)
						{
							// Cancel the selection
							selectedRegion = null;
							mapEditingPanel.clearSelectedCenters();
						}
						else
						{
							// Loop over edits instead of region.getCenters() because the region assigned to centers is changed by map
							// drawing, but edits is thread safe.
							for (CenterEdit c : mainWindow.edits.centerEdits.values())
							{
								assert c != null;
								if (c.regionId != null && c.regionId == region.id)
								{
									CenterEdit newValues = new CenterEdit(c.index, c.isWater, c.isLake, selectedRegion.id, c.icon, c.trees);
									mainWindow.edits.centerEdits.put(c.index, newValues);
								}

							}
							mainWindow.edits.regionEdits.remove(region.id);
							selectedRegion = null;
							mapEditingPanel.clearSelectedCenters();
							// Take the region-boundary highlights down before the redraw recomputes the noisy edges. The
							// highlighted/selected outlines are drawn from the graph's noisy edges; once the merge dissolves the
							// shared boundary, that edge stops being a region boundary and the rebuild re-curves it to a straight
							// segment, which the stale highlight would briefly draw as a jagged line. Clearing and repainting now
							// makes the highlight simply disappear instead of flashing jagged.
							mapEditingPanel.clearHighlightedCenters();
							mapEditingPanel.repaint();
							handleMapChange(region.getCenters());
						}
					}
				}
			}
		}
		else if (riversButton.isSelected())
		{
			if (modeWidget.isEraseMode())
			{
				List<List<Point>> riverSegmentsToRemove = getSelectedRiverSegments(e.getPoint());
				List<River> changed = RiverDrawer.removeSegmentsAndSplitRivers(mainWindow.edits.rivers, riverSegmentsToRemove);
				RiverDrawer.removeEmptyOrShortRivers(mainWindow.edits.rivers);
				List<River> extended = RiverDrawer.mergeAdjacentRivers(changed, mainWindow.edits.rivers);
				mapEditingPanel.clearHighlightedEdges();

				if (!riverSegmentsToRemove.isEmpty())
				{
					List<List<Point>> centerPaths = new ArrayList<>(pointsToCoverInRedrawAfterPathCut(mainWindow.edits.rivers, r -> r.nodes, riverSegmentsToRemove));
					for (River ext : extended)
					{
						centerPaths.add(PathOperations.toLocationList(ext.nodes));
					}
					Set<Center> centersToRedraw = getCentersTouchingPoints(centerPaths);
					if (!centersToRedraw.isEmpty())
					{
						updater.createAndShowMapIncrementalUsingCenters(centersToRedraw);
					}
					updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));
					undoer.setUndoPoint(UpdateType.Incremental, this);
				}
			}
		}
		else if (roadsButton.isSelected())
		{
			if (modeWidget.isEraseMode())
			{
				List<List<Point>> roadSegmentsToRemove = getSelectedRoadSegments(e.getPoint());
				List<Road> changed = RoadDrawer.removeSegmentsAndSplitRoads(mainWindow.edits.roads, roadSegmentsToRemove);
				RoadDrawer.removeEmptyOrSinglePointRoads(mainWindow.edits.roads);
				List<Road> extended = RoadDrawer.mergeAdjacentRoads(changed, mainWindow.edits.roads);
				mapEditingPanel.clearHighlightedPolylines();
				List<List<Point>> centerPaths = new ArrayList<>(pointsToCoverInRedrawAfterPathCut(mainWindow.edits.roads, r -> r.nodes, roadSegmentsToRemove));
				for (Road ext : extended)
				{
					centerPaths.add(PathOperations.toLocationList(ext.nodes));
				}
				updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingPoints(centerPaths));
				List<Road> roadsToRedraw = new ArrayList<>(changed);
				roadsToRedraw.removeIf(r -> !mainWindow.edits.roads.contains(r));
				for (Road ext : extended)
				{
					if (!roadsToRedraw.contains(ext))
					{
						roadsToRedraw.add(ext);
					}
				}
				updater.addRoadsToRedrawLowPriority(roadsToRedraw, mainWindow.displayQualityScale);
			}
		}
	}

	private Set<Center> getCentersTouchingPoints(List<List<Point>> pointLists)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null)
		{
			assert false;
			return Collections.emptySet();
		}

		WorldGraph graph = updater.mapParts.graph;
		Set<Center> result = new HashSet<>();
		for (List<Point> points : pointLists)
		{
			for (Point point : points)
			{
				// Clamp the point into the map bounds before the lookup. A path (e.g. a freehand river)
				// can have a node that lies beyond the map border; the segment leading to that node still
				// needs to draw up to the edge, where it will be covered by the border. Dropping off-map
				// nodes would leave the redraw bounds short, so the on-map part of the boundary-crossing
				// segment would be clipped during an incremental update (a full draw is unaffected because
				// it ignores these bounds). Clamping maps the off-map node to the border center where the
				// segment exits the map, which extends the redraw bounds to the edge.
				Point pixel = point.mult(mainWindow.displayQualityScale);
				double clampedX = Math.min(Math.max(pixel.x, 0), graph.getWidth() - 1);
				double clampedY = Math.min(Math.max(pixel.y, 0), graph.getHeight() - 1);
				Center c = graph.findClosestCenter(new Point(clampedX, clampedY), true);
				if (c != null)
				{
					result.add(c);
				}
			}
		}
		return result;
	}

	/**
	 * Appends a {@link PathOperations#CATMULL_ROM_PROPAGATION_RADIUS}-clipped slice of node locations to {@code sink}, one slice per
	 * supplied {@code changedIndex}. Used to scope incremental redraws to "the segments whose Catmull-Rom curve shape can change as a
	 * result of a control-point edit", instead of redrawing every center under the entire path.
	 */
	private static void appendControlPointEditScope(List<List<Point>> sink, List<? extends PathNode> path, int... changedIndices)
	{
		if (path == null || path.isEmpty())
		{
			return;
		}
		for (int idx : changedIndices)
		{
			List<Point> slice = PathOperations.nodeLocationsAround(path, idx, PathOperations.CATMULL_ROM_PROPAGATION_RADIUS);
			if (!slice.isEmpty())
			{
				sink.add(slice);
			}
		}
	}

	/** Same as {@link #appendControlPointEditScope} but takes a pre-extracted snapshot of point locations (e.g. a drag's before-path). */
	private static void appendControlPointEditScopeFromSnapshot(List<List<Point>> sink, List<Point> snapshot, int... changedIndices)
	{
		if (snapshot == null || snapshot.isEmpty())
		{
			return;
		}
		for (int idx : changedIndices)
		{
			List<Point> slice = PathOperations.pointsAround(snapshot, idx, PathOperations.CATMULL_ROM_PROPAGATION_RADIUS);
			if (!slice.isEmpty())
			{
				sink.add(slice);
			}
		}
	}

	/**
	 * Bundles the removed-segment endpoint points together with the "inner neighbor" points needed to fully cover any new end segments
	 * created by the cut. See {@link PathOperations#findInnerNeighborsOfCutEndpoints} for why the extra points are required to prevent
	 * tearing at the incremental update boundary. Works for both rivers and roads via the {@code nodeAccessor} projection.
	 */
	private static <T> List<List<Point>> pointsToCoverInRedrawAfterPathCut(List<T> pathsAfterCut, java.util.function.Function<T, ? extends List<? extends PathNode>> nodeAccessor,
			List<List<Point>> removedSegments)
	{
		List<List<? extends PathNode>> nodeLists = new ArrayList<>(pathsAfterCut.size());
		for (T path : pathsAfterCut)
		{
			nodeLists.add(nodeAccessor.apply(path));
		}
		List<Point> innerNeighbors = PathOperations.findInnerNeighborsOfCutEndpoints(nodeLists, removedSegments);
		if (innerNeighbors.isEmpty())
		{
			return removedSegments;
		}
		List<List<Point>> combined = new ArrayList<>(removedSegments.size() + 1);
		combined.addAll(removedSegments);
		combined.add(innerNeighbors);
		return combined;
	}

	private final int singlePointRoadSelectionRadiusBeforeZoomAndScale = 10;

	private List<List<Point>> getSelectedRoadSegments(java.awt.Point pointFromMouse)
	{
		nortantis.geom.Point graphPointResolutionInvariant = getPointOnGraph(pointFromMouse).mult(1.0 / mainWindow.displayQualityScale);
		int brushDiameter = brushSizes.get(brushSizeComboBox.getSelectedIndex());
		if (brushDiameter <= 1)
		{
			// Find the closest road point within a certain diameter.
			int radius = (int) ((double) ((singlePointRoadSelectionRadiusBeforeZoomAndScale / mainWindow.zoom) * mapEditingPanel.osScale));
			List<Point> closest = findClosestRoadSegmentWithinRadius(graphPointResolutionInvariant, radius);

			List<List<Point>> result = new ArrayList<>(1);
			if (closest != null && !closest.isEmpty())
			{
				result.add(closest);
			}
			return result;
		}
		else
		{
			double brushRadiusResolutionInvariant = (double) ((brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale) / (2 * mainWindow.displayQualityScale);
			return findRoadSegmentsWithinRadius(graphPointResolutionInvariant, brushRadiusResolutionInvariant);
		}
	}

	private List<Point> findClosestRoadSegmentWithinRadius(Point targetPoint, double radius)
	{
		return findClosestSegmentWithinRadius(targetPoint, radius, mainWindow.edits.roads.stream().map(r -> PathOperations.toLocationList(r.nodes)).toList());
	}

	private List<Point> findClosestSegmentWithinRadius(Point targetPoint, double radius, List<List<Point>> paths)
	{
		Point closestStart = null;
		Point closestEnd = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		// Each list in withinRadius is a concatenation of consecutive overlapping segment pairs:
		// e.g. for path A-B-C-D with all three segments overlapping, the list is [A,B,B,C,C,D].
		// Iterate by pairs (i += 2) to get each underlying segment and use line distance, so a
		// click near a shared corner returns a real segment (not a degenerate point-to-itself).
		List<List<Point>> withinRadius = findSegmentsWithinRadius(paths, targetPoint, radius);
		for (List<Point> segment : withinRadius)
		{
			for (int i = 0; i + 1 < segment.size(); i += 2)
			{
				Point p1 = segment.get(i);
				Point p2 = segment.get(i + 1);
				double d = distanceToSegment(targetPoint, p1, p2);
				if (d < closestDistance)
				{
					closestDistance = d;
					closestStart = p1;
					closestEnd = p2;
				}
			}
		}

		if (closestStart == null)
		{
			return Collections.emptyList();
		}

		return Arrays.asList(closestStart, closestEnd);
	}

	private List<List<Point>> findRoadSegmentsWithinRadius(Point targetPoint, double radius)
	{
		return findSegmentsWithinRadius(mainWindow.edits.roads.stream().map(r -> PathOperations.toLocationList(r.nodes)).toList(), targetPoint, radius);
	}

	private List<List<Point>> findSegmentsWithinRadius(List<List<Point>> paths, Point targetPoint, double radius)
	{
		List<List<Point>> result = new ArrayList<>();
		for (List<Point> path : paths)
		{
			if (path.size() < 2)
			{
				continue;
			}

			List<Point> soFar = new ArrayList<>();
			for (int i = 0; i < path.size() - 1; i++)
			{
				Point p1 = path.get(i);
				Point p2 = path.get(i + 1);
				if (GeometryHelper.doesLineOverlapCircle(p1, p2, targetPoint, radius))
				{
					soFar.add(p1);
					soFar.add(p2);
				}
				else
				{
					if (!soFar.isEmpty())
					{
						result.add(soFar);
						soFar = new ArrayList<>();
					}
				}
			}

			if (!soFar.isEmpty())
			{
				result.add(soFar);
			}
		}

		return result;
	}

	private double distanceToSegment(Point p, Point a, Point b)
	{
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double lengthSquared = dx * dx + dy * dy;
		if (lengthSquared == 0)
		{
			return p.distanceTo(a);
		}
		double t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared));
		double closestX = a.x + t * dx;
		double closestY = a.y + t * dy;
		return Math.sqrt((p.x - closestX) * (p.x - closestX) + (p.y - closestY) * (p.y - closestY));
	}

	private boolean isPathNearPoint(List<Point> path, Point target, double threshold)
	{
		for (int i = 0; i < path.size() - 1; i++)
		{
			if (distanceToSegment(target, path.get(i), path.get(i + 1)) <= threshold)
			{
				return true;
			}
		}
		return path.size() == 1 && path.get(0).distanceTo(target) <= threshold;
	}

	private Point findNearestPointInPaths(java.awt.Point mouseLocation, List<List<Point>> paths, List<Point> inProgressPath)
	{
		Point mouseRI = getPointOnGraph(mouseLocation).mult(1.0 / mainWindow.displayQualityScale);
		double snapRadius = getSnapRadiusRI();

		List<Point> candidates = new ArrayList<>();
		for (List<Point> path : paths)
			candidates.addAll(path);
		if (inProgressPath != null && inProgressPath.size() >= 2)
			candidates.add(inProgressPath.get(0));

		Point nearest = Helper.minItem(candidates, Comparator.comparingDouble(p -> p.distanceTo(mouseRI)));
		return nearest != null && nearest.distanceTo(mouseRI) < snapRadius ? nearest : null;
	}

	private List<List<Point>> getSelectedRiverSegments(java.awt.Point pointFromMouse)
	{
		Point targetPoint = getPointOnGraph(pointFromMouse).mult(1.0 / mainWindow.displayQualityScale);
		int brushDiameter = brushSizes.get(brushSizeComboBox.getSelectedIndex());
		List<List<Point>> riverPaths = mainWindow.edits.rivers.stream().map(r -> PathOperations.toLocationList(r.nodes)).toList();
		if (brushDiameter <= 1)
		{
			int radius = (int) ((double) ((singlePointRoadSelectionRadiusBeforeZoomAndScale / mainWindow.zoom) * mapEditingPanel.osScale));
			List<Point> closest = findClosestSegmentWithinRadius(targetPoint, radius, riverPaths);
			List<List<Point>> result = new ArrayList<>(1);
			if (closest != null && !closest.isEmpty())
			{
				result.add(closest);
			}
			return result;
		}
		else
		{
			double brushRadiusResolutionInvariant = (double) ((brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale) / (2 * mainWindow.displayQualityScale);
			return findSegmentsWithinRadius(riverPaths, targetPoint, brushRadiusResolutionInvariant);
		}
	}

	/**
	 * After the user has painted ocean or lake on {@code paintedCenters} (centerEdits already updated), removes river segments that are
	 * entirely in water or that follow exactly along a Voronoi edge which has just become a coast or lakeshore. The actual water test
	 * consults {@code centerEdits} because {@link Center#isWater} is not updated until the next redraw runs. Triggers an incremental redraw
	 * of the centers along any removed segments so stale river pixels on the land side of a new coast get cleaned up.
	 */
	private void removeRiversCoveredByPaintedWater(Set<Center> paintedCenters)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null || mainWindow.edits.rivers.isEmpty())
		{
			return;
		}

		java.util.function.Predicate<Center> isWaterAfterEdit = center ->
		{
			CenterEdit edit = mainWindow.edits.centerEdits.get(center.index);
			return edit != null ? edit.isWater : center.isWater;
		};

		List<List<Point>> segmentsToRemove = RiverDrawer.findRiverSegmentsToRemoveForWaterPaint(mainWindow.edits.rivers, updater.mapParts.graph, mainWindow.displayQualityScale, paintedCenters,
				isWaterAfterEdit);
		if (segmentsToRemove.isEmpty())
		{
			return;
		}

		RiverDrawer.removeSegmentsAndSplitRivers(mainWindow.edits.rivers, segmentsToRemove);
		RiverDrawer.removeEmptyOrShortRivers(mainWindow.edits.rivers);

		// Redraw centers on the land side of removed segments too, so the river pixels there get cleared.
		Set<Center> centersToRedraw = getCentersTouchingPoints(pointsToCoverInRedrawAfterPathCut(mainWindow.edits.rivers, r -> r.nodes, segmentsToRemove));
		if (!centersToRedraw.isEmpty())
		{
			updater.createAndShowMapIncrementalUsingCenters(centersToRedraw);
		}
		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));
	}

	/**
	 * Bridges the river's natural endpoint(s) to {@code snapStart}/{@code snapEnd} by appending (or prepending) a new node, rather than
	 * replacing the natural endpoint. This is what the drag preview shows — a polygon path that walks Voronoi corners then bridges from the
	 * last/first corner to the snap point. Replacing the natural endpoint (the old behavior) made the polygon river end short of the snap
	 * target when the target was a middle control point of another river (no merge possible, no bridge drawn).
	 *
	 * <p>
	 * The bridge segment inherits the polygon path's width and gets a fresh seed; its edge index is cleared because the bridge doesn't
	 * follow a single Voronoi edge.
	 */
	private static void applyRiverSnapPoints(River river, Point snapStart, Point naturalStartRI, Point snapEnd, Point naturalEndRI)
	{
		List<RiverPathNode> updated = new ArrayList<>(river.nodes);
		if (updated.isEmpty())
		{
			return;
		}
		Random random = new Random();
		if (snapEnd != null && !snapEnd.isCloseEnough(naturalEndRI))
		{
			appendRiverBridge(updated, snapEnd, naturalEndRI, random);
		}
		if (snapStart != null && !snapStart.isCloseEnough(naturalStartRI))
		{
			prependRiverBridge(updated, snapStart, naturalStartRI, random);
		}
		river.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(updated);
	}

	private static void appendRiverBridge(List<RiverPathNode> nodes, Point snap, Point natural, Random random)
	{
		if (nodes.isEmpty())
		{
			return;
		}
		RiverPathNode last = nodes.get(nodes.size() - 1);
		if (last.getLoc().isCloseEnough(natural))
		{
			// For a normal polygon-derived river (size >= 2), the bridge inherits width from the previous segment. For the synthetic
			// single-node river built when the polygon path is empty (Corner-to-snap bridge with no polygon edges), there is no
			// previous segment, so we fall back to the lone node's own widthLevelToNext — the caller seeds it with the slider's level.
			int width = nodes.size() >= 2 ? nodes.get(nodes.size() - 2).getWidthLevelToNext() : last.getWidthLevelToNext();
			long seed = random.nextLong();
			// The old last node now has an outgoing segment (the bridge); set its to-next metadata
			// so the bridge inherits the polygon path's width and gets a fresh seed.
			nodes.set(nodes.size() - 1, new RiverPathNode(last.getLoc(), width, seed, RiverPathNode.EDGE_INDEX_NONE));
			// New last node with cleared to-next (no outgoing segment).
			nodes.add(new RiverPathNode(snap, 0, 0L, RiverPathNode.EDGE_INDEX_NONE));
			return;
		}
		RiverPathNode first = nodes.get(0);
		if (first.getLoc().isCloseEnough(natural))
		{
			// Path's "natural" location is actually the start — fall through to prepend semantics.
			prependRiverBridge(nodes, snap, natural, random);
		}
	}

	private static void prependRiverBridge(List<RiverPathNode> nodes, Point snap, Point natural, Random random)
	{
		if (nodes.isEmpty())
		{
			return;
		}
		RiverPathNode first = nodes.get(0);
		if (first.getLoc().isCloseEnough(natural))
		{
			int width = first.getWidthLevelToNext() != 0 ? first.getWidthLevelToNext() : (nodes.size() >= 2 ? nodes.get(1).getWidthLevelToNext() : 0);
			long seed = random.nextLong();
			// New first node carries the bridge segment's to-next metadata so the bridge has consistent
			// width with the rest of the polygon river.
			nodes.add(0, new RiverPathNode(snap, width, seed, RiverPathNode.EDGE_INDEX_NONE));
			return;
		}
		RiverPathNode last = nodes.get(nodes.size() - 1);
		if (last.getLoc().isCloseEnough(natural))
		{
			// Path's "natural" location is actually the end — fall through to append semantics.
			appendRiverBridge(nodes, snap, natural, random);
		}
	}

	private static void applyRoadSnapPoints(Road road, Point snapStart, Point naturalStartRI, Point snapEnd, Point naturalEndRI)
	{
		List<RoadPathNode> updated = new ArrayList<>(road.nodes);
		if (updated.isEmpty())
		{
			return;
		}
		if (snapEnd != null && !snapEnd.isCloseEnough(naturalEndRI))
		{
			appendRoadBridge(updated, snapEnd, naturalEndRI);
		}
		if (snapStart != null && !snapStart.isCloseEnough(naturalStartRI))
		{
			prependRoadBridge(updated, snapStart, naturalStartRI);
		}
		road.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(updated);
	}

	private static void appendRoadBridge(List<RoadPathNode> nodes, Point snap, Point natural)
	{
		if (nodes.isEmpty())
		{
			return;
		}
		if (nodes.get(nodes.size() - 1).getLoc().isCloseEnough(natural))
		{
			nodes.add(new RoadPathNode(snap));
		}
		else if (nodes.get(0).getLoc().isCloseEnough(natural))
		{
			prependRoadBridge(nodes, snap, natural);
		}
	}

	private static void prependRoadBridge(List<RoadPathNode> nodes, Point snap, Point natural)
	{
		if (nodes.isEmpty())
		{
			return;
		}
		if (nodes.get(0).getLoc().isCloseEnough(natural))
		{
			nodes.add(0, new RoadPathNode(snap));
		}
		else if (nodes.get(nodes.size() - 1).getLoc().isCloseEnough(natural))
		{
			appendRoadBridge(nodes, snap, natural);
		}
	}

	private void selectColorFromMap(MouseEvent e)
	{
		Center center = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
		if (center != null)
		{
			if (center != null && center.region != null)
			{
				colorDisplay.setBackground(AwtBridge.toAwtColor(center.region.backgroundColor));
				selectColorFromMapButton.setSelected(false);
			}
		}
	}

	private void cancelSelectColorFromMap()
	{
		if (selectColorFromMapButton.isSelected())
		{
			selectColorFromMapButton.setSelected(false);
			selectedRegion = null;
			mapEditingPanel.clearSelectedCenters();
		}
	}

	private Set<Center> getSelectedCenters(java.awt.Point point)
	{
		return getSelectedCenters(point, brushSizes.get(brushSizeComboBox.getSelectedIndex()));
	}

	/**
	 * The color to use when creating a new political region. When region colors are shown, this is the color currently selected in the
	 * tool (which survives undo/redo); otherwise regions are invisible, so the theme's single land color is used. Used both by the
	 * explicit "New Color / Political Region" action and by the fallback that creates a region when there's no existing land nearby to
	 * extend.
	 */
	private Color getCurrentRegionColor()
	{
		return areRegionColorsVisible ? colorDisplay.getBackground() : mainWindow.getLandColor();
	}

	private int getOrCreateRegionIdForEdit(Center center, Color color)
	{
		// When the user clicked down on centers that had a region id, use that one.
		if (regionIdToExpand != null)
		{
			return regionIdToExpand;
		}

		// If a neighboring center has a region, use that region.
		for (Center neighbor : center.neighbors)
		{
			CenterEdit neighborEdit = mainWindow.edits.centerEdits.get(neighbor.index);
			if (neighborEdit.regionId != null)
			{
				return neighborEdit.regionId;
			}
		}

		// Find the closest center with a region.
		Optional<CenterEdit> opt = mainWindow.edits.centerEdits.values().stream().filter(cEdit1 -> cEdit1.regionId != null).min((cEdit1, cEdit2) -> Double
				.compare(updater.mapParts.graph.centers.get(cEdit1.index).loc.distanceTo(center.loc), updater.mapParts.graph.centers.get(cEdit2.index).loc.distanceTo(center.loc)));
		if (opt.isPresent())
		{
			return opt.get().regionId;
		}
		else
		{
			int newRegionId = createNewRegion(color);
			return newRegionId;
		}
	}

	private int createNewRegion(Color color)
	{
		int largestRegionId;
		if (mainWindow.edits.regionEdits.isEmpty())
		{
			largestRegionId = -1;
		}
		else
		{
			largestRegionId = mainWindow.edits.regionEdits.values().stream().max((r1, r2) -> Integer.compare(r1.regionId, r2.regionId)).get().regionId;
		}

		int newRegionId = largestRegionId + 1;

		RegionEdit regionEdit = new RegionEdit(newRegionId, AwtBridge.fromAwtColor(color));
		mainWindow.edits.regionEdits.put(newRegionId, regionEdit);
		return newRegionId;
	}

	private void handleMapChange(Set<Center> centers)
	{
		updater.createAndShowMapIncrementalUsingCenters(centers);
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		// MapEditingPanel doesn't grab focus on click, so a JSpinner text field with an uncommitted typed value won't get a focus-lost
		// event from this press. Commit any pending edit up front so its change listener fires (and applies the typed value to the
		// currently-selected segments) BEFORE this press mutates the selection.
		if (riverWidthSliderWithSpinner != null && riverOptionHider.isVisible())
		{
			riverWidthSliderWithSpinner.commitEdit();
		}

		handleSharedPressAndDragActions(e, false);

		if (riversButton.isSelected() && modeWidget.isDrawMode() && !isFreeHandRiverDrawMode())
		{
			polygonRiverSnapStart = computeSnapPointForType(e.getPoint(), LineType.RIVER);
			riverStart = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
		}
		else if (riversButton.isSelected() && modeWidget.isDrawMode() && isFreeHandRiverDrawMode() && SwingUtilities.isLeftMouseButton(e) && updater.mapParts != null && updater.mapParts.graph != null)
		{
			Point riPoint = freeHandRiverSnapPoint != null ? freeHandRiverSnapPoint : getPointOnGraph(e.getPoint()).mult(1.0 / mainWindow.displayQualityScale);
			if (e.getClickCount() == 1)
			{
				if (freeHandRiverPathRI == null)
				{
					freeHandRiverPathRI = new ArrayList<>();
				}
				freeHandRiverPathRI.add(riPoint);
				updateControlPointDisplay(e.getPoint(), LineType.RIVER);
				mapEditingPanel.repaint();
			}
			else if (e.getClickCount() == 2)
			{
				finalizeFreeHandLine(LineType.RIVER);
			}
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && isFreeHandDrawMode() && SwingUtilities.isLeftMouseButton(e) && updater.mapParts != null && updater.mapParts.graph != null)
		{
			// Use press (not click) so moving the mouse between press and release still registers the control point.
			Point riPoint = freeHandRoadSnapPoint != null ? freeHandRoadSnapPoint : getPointOnGraph(e.getPoint()).mult(1.0 / mainWindow.displayQualityScale);
			if (e.getClickCount() == 1)
			{
				if (freeHandRoadPathRI == null)
				{
					freeHandRoadPathRI = new ArrayList<>();
				}
				freeHandRoadPathRI.add(riPoint);
				updateControlPointDisplay(e.getPoint(), LineType.ROAD);
				mapEditingPanel.repaint();
			}
			else if (e.getClickCount() == 2)
			{
				// Double-click: the first press already added the final point; finalize now.
				finalizeFreeHandLine(LineType.ROAD);
			}
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && !isFreeHandDrawMode())
		{
			polygonRoadSnapStart = computeSnapPointForType(e.getPoint(), LineType.ROAD);
			roadStart = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
		}
		else if (modeWidget.isEditMode() && (riversButton.isSelected() || roadsButton.isSelected()) && SwingUtilities.isLeftMouseButton(e))
		{
			handleEditModeMousePressed(e);
		}
	}

	/**
	 * Edit-mode press handler. Dispatches to one of three modes based on where the click landed and whether the modifier key is held:
	 * <ul>
	 * <li><b>Move-drag</b> — press on a CP (with brush 1) or in the brush radius of a CP — replaces the selection with that CP (or extends
	 * it when Ctrl is held), then arms a move-drag so any subsequent cursor motion translates every selected CP.</li>
	 * <li><b>Paint-drag (from segment)</b> — press on a segment's centerline — selects both endpoint CPs of the segment, then arms a
	 * paint-drag so subsequent motion extends the selection with the brush. Does not move the line on accidental drag.</li>
	 * <li><b>Paint-drag (from empty)</b> — press in empty space — clears the selection (unless Ctrl is held), then arms a paint-drag.</li>
	 * </ul>
	 */
	private void handleEditModeMousePressed(MouseEvent e)
	{
		LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
		boolean ctrlDown = SwingHelper.isCommandKeyDown(e);
		boolean deselectMode = controlClickBehavior != null && controlClickBehavior.isUnselectMode();
		int brushDiameter = getEditBrushDiameter();

		// Reset transient drag state. We'll re-arm below if the press starts a drag.
		dragInProgress = false;
		dragOccurred = false;
		dragIsPaint = false;
		dragMovingCPs = null;
		dragBeforeSnapshots = null;
		dragStartLocRI = getPointOnGraph(e.getPoint()).mult(1.0 / mainWindow.displayQualityScale);

		PressOutcome outcome = computePressOutcome(e.getPoint(), ctrlDown, deselectMode, brushDiameter, activeType);
		applyPressOutcome(outcome);
		refreshSelectionVisuals(e.getPoint(), activeType, ctrlDown);
	}

	/** Applies a press outcome to the live state: swaps the selection maps and arms either move-drag or paint-drag. */
	private void applyPressOutcome(PressOutcome outcome)
	{
		selectedRiverCPs.clear();
		selectedRiverCPs.putAll(outcome.riverCPsAfter());
		selectedRoadCPs.clear();
		selectedRoadCPs.putAll(outcome.roadCPsAfter());

		if (outcome.isMoveDrag())
		{
			armMoveDragIfApplicable(outcome.moveAnchorLine(), outcome.moveAnchorIdx());
		}
		else
		{
			dragInProgress = true;
			dragIsPaint = true;
		}
	}

	private record SelectedCPRef(Object line, int index, double distance)
	{
	}

	/**
	 * Describes the effect a press at a given point would have on the selection and drag state. Returned by {@link #computePressOutcome}
	 * and consumed both by the press handler (which applies it) and the hover-display handler (which renders it as a preview, so the
	 * highlighted CPs/segments are always exactly the things the press would actually select).
	 *
	 * <p>
	 * {@code riverCPsAfter} / {@code roadCPsAfter} are independent copies — applying the outcome means clearing the field maps and putting
	 * these in. {@code isMoveDrag} distinguishes the move-drag arm path from the paint-drag arm path. {@code moveAnchorLine} +
	 * {@code moveAnchorIdx} are the CP to drag from (one of the selected CPs); they are unset for non-move outcomes.
	 */
	private record PressOutcome(Map<River, Set<Integer>> riverCPsAfter, Map<Road, Set<Integer>> roadCPsAfter, boolean isMoveDrag, Object moveAnchorLine, int moveAnchorIdx)
	{
	}

	/**
	 * Computes what a press at {@code point} would do, without mutating the live selection state. The press handler applies the result; the
	 * hover-display handler uses it to render a preview that exactly matches what a press would actually do. Keeping a single source of
	 * truth here is the whole point — otherwise the preview and the press can diverge (which previously led to "I see the segment
	 * highlighted but the click selects the CP instead").
	 */
	private PressOutcome computePressOutcome(java.awt.Point point, boolean ctrlDown, boolean deselectMode, int brushDiameter, LineType activeType)
	{
		// Identity-keyed to match selectedRiverCPs/selectedRoadCPs (Road/River hashCode is mutable; see field declarations).
		Map<River, Set<Integer>> riverAfter = new java.util.IdentityHashMap<>();
		if (selectedRiverCPs != null)
		{
			for (Map.Entry<River, Set<Integer>> e : selectedRiverCPs.entrySet())
			{
				riverAfter.put(e.getKey(), new HashSet<>(e.getValue()));
			}
		}
		Map<Road, Set<Integer>> roadAfter = new java.util.IdentityHashMap<>();
		if (selectedRoadCPs != null)
		{
			for (Map.Entry<Road, Set<Integer>> e : selectedRoadCPs.entrySet())
			{
				roadAfter.put(e.getKey(), new HashSet<>(e.getValue()));
			}
		}

		// Local helpers that mutate the post-press maps (not the live state).
		java.util.function.BiConsumer<Object, Integer> add = (line, idx) ->
		{
			if (line instanceof River r)
			{
				riverAfter.computeIfAbsent(r, k -> new HashSet<>()).add(idx);
			}
			else if (line instanceof Road r)
			{
				roadAfter.computeIfAbsent(r, k -> new HashSet<>()).add(idx);
			}
		};
		java.util.function.BiConsumer<Object, Integer> remove = (line, idx) ->
		{
			if (line instanceof River r)
			{
				Set<Integer> s = riverAfter.get(r);
				if (s != null)
				{
					s.remove(idx);
					if (s.isEmpty())
					{
						riverAfter.remove(r);
					}
				}
			}
			else if (line instanceof Road r)
			{
				Set<Integer> s = roadAfter.get(r);
				if (s != null)
				{
					s.remove(idx);
					if (s.isEmpty())
					{
						roadAfter.remove(r);
					}
				}
			}
		};
		Runnable clear = () ->
		{
			riverAfter.clear();
			roadAfter.clear();
		};
		// Expanding wrappers: include CPs at the same location in other lines so junction points behave as one selectable unit.
		java.util.function.BiConsumer<Object, Integer> addExpanded = (line, idx) -> forEachCoincidentCP(line, idx, add);
		java.util.function.BiConsumer<Object, Integer> removeExpanded = (line, idx) -> forEachCoincidentCP(line, idx, remove);

		boolean brushIsSinglePoint = brushDiameter <= 1;
		double brushRadiusGraphPixels = brushIsSinglePoint ? 0 : ((double) brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale / 2.0;

		// PRIORITY: a plain (no-Ctrl) press that lands on a selected CP/segment preserves the existing selection and arms a move-drag.
		// The CP grab radius is at least the segment hit threshold (the same Erase-mode 10-RI radius); without that, a click slightly
		// off a selected CP but still within typical mouse-aiming tolerance would resolve to the segment-hit branch and wipe the
		// selection — making the user have to re-select before they could drag again.
		if (!ctrlDown)
		{
			double cpGrabRadius = Math.max(Math.max(mapEditingPanel.getRoadControlPointHitRadiusInGraphPixels(), getEditSegmentHitThresholdInGraphPixels()), brushRadiusGraphPixels);
			double segGrabRadius = Math.max(getEditSegmentHitThresholdInGraphPixels(), brushRadiusGraphPixels);
			SelectedCPRef grab = findNearestSelectedCP(point, activeType, cpGrabRadius);
			if (grab == null)
			{
				grab = findNearestSelectedSegmentEndpoint(point, activeType, segGrabRadius);
			}
			if (grab != null)
			{
				// Grabbing the existing selection preserves it as-is — unselected CPs under a wide brush are NOT folded in. The
				// brush is a hit-test zone for finding the grab anchor, not a selection-extension on a grab+drag.
				return new PressOutcome(riverAfter, roadAfter, true, grab.line, grab.index);
			}
		}

		// Brush=1: single-click resolution using editModeHitTest. Brush>1 always falls through to the paint branch below.
		LineHit hit = brushIsSinglePoint ? editModeHitTest(point, activeType, null, null) : null;

		if (brushIsSinglePoint && hit != null && hit.isControlPoint())
		{
			Object line = hit.river() != null ? hit.river() : hit.road();
			int idx = hit.controlPointIndex();
			if (ctrlDown)
			{
				if (deselectMode)
				{
					removeExpanded.accept(line, idx);
				}
				else
				{
					addExpanded.accept(line, idx);
				}
				return new PressOutcome(riverAfter, roadAfter, false, null, -1);
			}
			// Replace selection with the clicked CP, arm move-drag.
			clear.run();
			addExpanded.accept(line, idx);
			return new PressOutcome(riverAfter, roadAfter, true, line, idx);
		}
		if (brushIsSinglePoint && hit != null && hit.isSegment())
		{
			Object line = hit.river() != null ? hit.river() : hit.road();
			int segIdx = hit.segmentIndex();
			if (!ctrlDown)
			{
				clear.run();
			}
			if (deselectMode && ctrlDown)
			{
				removeExpanded.accept(line, segIdx);
				removeExpanded.accept(line, segIdx + 1);
			}
			else
			{
				addExpanded.accept(line, segIdx);
				addExpanded.accept(line, segIdx + 1);
			}
			return new PressOutcome(riverAfter, roadAfter, false, null, -1);
		}

		// Paint path (brush > 1 always lands here unless priority fired above; brush = 1 falls here only when the click landed on empty).
		List<LineHit> brushHits = brushIsSinglePoint ? Collections.emptyList() : multiBrushHitTest(point, activeType, brushDiameter);
		if (!ctrlDown)
		{
			clear.run();
		}
		for (LineHit bh : brushHits)
		{
			Object line = bh.river() != null ? bh.river() : bh.road();
			boolean removing = ctrlDown && deselectMode;
			if (bh.isControlPoint())
			{
				if (removing)
				{
					removeExpanded.accept(line, bh.controlPointIndex());
				}
				else
				{
					addExpanded.accept(line, bh.controlPointIndex());
				}
			}
			else if (bh.isSegment())
			{
				if (removing)
				{
					removeExpanded.accept(line, bh.segmentIndex());
					removeExpanded.accept(line, bh.segmentIndex() + 1);
				}
				else
				{
					addExpanded.accept(line, bh.segmentIndex());
					addExpanded.accept(line, bh.segmentIndex() + 1);
				}
			}
		}
		return new PressOutcome(riverAfter, roadAfter, false, null, -1);
	}

	/**
	 * Returns the currently-selected CP closest to {@code panelPoint} (of the active type) within {@code maxDistanceGraphPixels}, or null
	 * if none is within range. Used by the press handler to decide whether a click should grab the existing multi-selection.
	 */
	private SelectedCPRef findNearestSelectedCP(java.awt.Point panelPoint, LineType activeType, double maxDistanceGraphPixels)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null || panelPoint == null)
		{
			return null;
		}
		Point clickGraph = getPointOnGraph(panelPoint);
		double scale = mainWindow.displayQualityScale;
		SelectedCPRef best = null;
		if (activeType == LineType.RIVER && selectedRiverCPs != null)
		{
			for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
			{
				List<RiverPathNode> nodes = entry.getKey().nodes;
				for (int idx : entry.getValue())
				{
					if (idx < 0 || idx >= nodes.size())
					{
						continue;
					}
					double d = clickGraph.distanceTo(nodes.get(idx).getLoc().mult(scale));
					if (d <= maxDistanceGraphPixels && (best == null || d < best.distance))
					{
						best = new SelectedCPRef(entry.getKey(), idx, d);
					}
				}
			}
		}
		else if (activeType == LineType.ROAD && selectedRoadCPs != null)
		{
			for (Map.Entry<Road, Set<Integer>> entry : selectedRoadCPs.entrySet())
			{
				List<RoadPathNode> nodes = entry.getKey().nodes;
				for (int idx : entry.getValue())
				{
					if (idx < 0 || idx >= nodes.size())
					{
						continue;
					}
					double d = clickGraph.distanceTo(nodes.get(idx).getLoc().mult(scale));
					if (d <= maxDistanceGraphPixels && (best == null || d < best.distance))
					{
						best = new SelectedCPRef(entry.getKey(), idx, d);
					}
				}
			}
		}
		return best;
	}

	/**
	 * Returns a reference to either endpoint of the implicitly-selected segment (both endpoint CPs in the selection) closest to
	 * {@code panelPoint} within {@code maxDistanceGraphPixels}, or null if none qualify. Used as a secondary press-priority check so
	 * clicking on a selected segment (not just a selected CP) grabs the existing multi-selection rather than wiping it.
	 */
	private SelectedCPRef findNearestSelectedSegmentEndpoint(java.awt.Point panelPoint, LineType activeType, double maxDistanceGraphPixels)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null || panelPoint == null)
		{
			return null;
		}
		Point clickGraph = getPointOnGraph(panelPoint);
		double scale = mainWindow.displayQualityScale;
		SelectedCPRef best = null;
		if (activeType == LineType.RIVER && selectedRiverCPs != null)
		{
			for (Map.Entry<River, Set<Integer>> entry : selectedRiverCPs.entrySet())
			{
				Set<Integer> sel = entry.getValue();
				if (sel.size() < 2)
				{
					continue;
				}
				List<RiverPathNode> nodes = entry.getKey().nodes;
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					if (!sel.contains(i) || !sel.contains(i + 1))
					{
						continue;
					}
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					double d = GeometryHelper.distanceFromPointToSegment(clickGraph, a, b);
					if (d <= maxDistanceGraphPixels && (best == null || d < best.distance))
					{
						best = new SelectedCPRef(entry.getKey(), i, d);
					}
				}
			}
		}
		else if (activeType == LineType.ROAD && selectedRoadCPs != null)
		{
			for (Map.Entry<Road, Set<Integer>> entry : selectedRoadCPs.entrySet())
			{
				Set<Integer> sel = entry.getValue();
				if (sel.size() < 2)
				{
					continue;
				}
				List<RoadPathNode> nodes = entry.getKey().nodes;
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					if (!sel.contains(i) || !sel.contains(i + 1))
					{
						continue;
					}
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					double d = GeometryHelper.distanceFromPointToSegment(clickGraph, a, b);
					if (d <= maxDistanceGraphPixels && (best == null || d < best.distance))
					{
						best = new SelectedCPRef(entry.getKey(), i, d);
					}
				}
			}
		}
		return best;
	}

	/** Adds or removes (depending on {@code isDeselect}) the given CP from the selection. */
	private void applySelectionDelta(Object line, int idx, boolean isDeselect)
	{
		if (isDeselect)
		{
			removeFromSelection(line, idx);
		}
		else
		{
			addToSelection(line, idx);
		}
	}

	/**
	 * If the CP at (line, idx) is in the current selection, captures the locations of every selected CP and arms a move-drag. Y-junction
	 * shared CPs (CPs of OTHER lines at the same locations as selected CPs) are also captured so they move in lockstep. If the CP isn't in
	 * the selection (e.g. user clicked-then-Ctrl-removed it), no drag is armed.
	 */
	private void armMoveDragIfApplicable(Object line, int idx)
	{
		if (!isCPSelected(line, idx))
		{
			return;
		}
		List<MovingCP> moving = new ArrayList<>();
		Set<Point> selectedLocations = new HashSet<>();
		forEachSelectedCP((selLine, selIdx) ->
		{
			List<? extends PathNode> nodes = nodesOf(selLine);
			if (selIdx >= 0 && selIdx < nodes.size())
			{
				Point loc = nodes.get(selIdx).getLoc();
				moving.add(new MovingCP(selLine, selIdx, loc));
				selectedLocations.add(loc);
			}
		});
		// Find Y-junction shared CPs: other lines of the active type whose CPs sit at any selected location.
		LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
		if (activeType == LineType.RIVER)
		{
			for (River other : mainWindow.edits.rivers)
			{
				List<RiverPathNode> nodes = other.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					if (isCPSelected(other, i))
					{
						continue;
					}
					Point loc = nodes.get(i).getLoc();
					for (Point sel : selectedLocations)
					{
						if (loc.isCloseEnough(sel))
						{
							moving.add(new MovingCP(other, i, loc));
							break;
						}
					}
				}
			}
		}
		else
		{
			for (Road other : mainWindow.edits.roads)
			{
				List<RoadPathNode> nodes = other.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					if (isCPSelected(other, i))
					{
						continue;
					}
					Point loc = nodes.get(i).getLoc();
					for (Point sel : selectedLocations)
					{
						if (loc.isCloseEnough(sel))
						{
							moving.add(new MovingCP(other, i, loc));
							break;
						}
					}
				}
			}
		}
		dragMovingCPs = moving;
		// Snapshot the full path of every line participating in the drag (anchor + selected + shares) for redraw-bound computation at
		// drag-end. Use a map so duplicates are coalesced.
		dragBeforeSnapshots = new HashMap<>();
		for (MovingCP m : moving)
		{
			dragBeforeSnapshots.computeIfAbsent(m.line(), l -> PathOperations.toLocationList(nodesOf(l)));
		}
		dragInProgress = true;
		dragIsPaint = false;
	}

	/** Hit-tests every line of the active type whose CPs or segments are within {@code brushDiameter / 2} of {@code panelPoint}. */
	private List<LineHit> multiBrushHitTest(java.awt.Point panelPoint, LineType activeType, int brushDiameter)
	{
		List<LineHit> hits = new ArrayList<>();
		if (updater.mapParts == null || updater.mapParts.graph == null || panelPoint == null)
		{
			return hits;
		}
		Point clickGraph = getPointOnGraph(panelPoint);
		double scale = mainWindow.displayQualityScale;
		double controlPointRadius = mapEditingPanel.getRoadControlPointHitRadiusInGraphPixels();
		// Segment hit threshold matches Erase mode (see getEditSegmentHitThresholdInGraphPixels): small for brush=1, exact brush radius
		// for brush>1. CP hits stay at the precise radius (brush=1) or widen to the brush (brush>1) so CPs can be grabbed within the
		// painted area.
		double segmentThreshold = getEditSegmentHitThresholdInGraphPixels();
		double cpThreshold;
		if (brushDiameter <= 1)
		{
			cpThreshold = controlPointRadius;
		}
		else
		{
			double brushRadius = ((double) brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale / 2.0;
			cpThreshold = Math.max(controlPointRadius, brushRadius);
		}

		if (activeType == LineType.RIVER)
		{
			for (River river : mainWindow.edits.rivers)
			{
				List<RiverPathNode> nodes = river.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					if (clickGraph.distanceTo(nodes.get(i).getLoc().mult(scale)) <= cpThreshold)
					{
						hits.add(new LineHit(river, null, i, -1));
					}
				}
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					if (GeometryHelper.distanceFromPointToSegment(clickGraph, a, b) <= segmentThreshold)
					{
						hits.add(new LineHit(river, null, -1, i));
					}
				}
			}
		}
		else
		{
			for (Road road : mainWindow.edits.roads)
			{
				List<RoadPathNode> nodes = road.nodes;
				for (int i = 0; i < nodes.size(); i++)
				{
					if (clickGraph.distanceTo(nodes.get(i).getLoc().mult(scale)) <= cpThreshold)
					{
						hits.add(new LineHit(null, road, i, -1));
					}
				}
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					Point a = nodes.get(i).getLoc().mult(scale);
					Point b = nodes.get(i + 1).getLoc().mult(scale);
					if (GeometryHelper.distanceFromPointToSegment(clickGraph, a, b) <= segmentThreshold)
					{
						hits.add(new LineHit(null, road, -1, i));
					}
				}
			}
		}
		return hits;
	}

	/** Returns the edit-mode brush diameter (in panel pixels). Defaults to 1 (single-point) when the dropdown isn't available. */
	private int getEditBrushDiameter()
	{
		if (brushSizeComboBox == null)
		{
			return 1;
		}
		int idx = brushSizeComboBox.getSelectedIndex();
		if (idx < 0 || idx >= brushSizes.size())
		{
			return 1;
		}
		return brushSizes.get(idx);
	}

	/** Refreshes the selection-related visuals: highlighted segments, control-point circles, and the slider value+visibility. */
	private void refreshSelectionVisuals(java.awt.Point mouseLocation, LineType activeType)
	{
		refreshSelectionVisuals(mouseLocation, activeType, false);
	}

	/**
	 * Refreshes the selection-related visuals: highlighted segments, control-point circles, and the slider value+visibility.
	 * {@code ctrlDown} is the live modifier state so the hover preview reflects what a press would actually do (e.g. in Unselect mode a
	 * held Ctrl over a CP shows no add-highlight, since the click would remove rather than select).
	 */
	private void refreshSelectionVisuals(java.awt.Point mouseLocation, LineType activeType, boolean ctrlDown)
	{
		mapEditingPanel.clearHighlightedPolylines();
		applySelectedSegmentsHighlight();
		updateEditModeHoverDisplay(mouseLocation, activeType, ctrlDown);
		refreshRiverWidthSliderVisibility();
		syncSliderToSelectedSegment();
		mapEditingPanel.repaint();
	}

	/**
	 * Drag tick. Two flavors:
	 * <ul>
	 * <li><b>Move-drag</b> ({@code dragIsPaint == false}) — translates every CP in {@link #dragMovingCPs} by the cursor delta and redraws
	 * the affected map regions. The undo point is set only on release.</li>
	 * <li><b>Paint-drag</b> ({@code dragIsPaint == true}) — accumulates CPs/segments under the brush into the selection (without Ctrl) or
	 * into a deselection (with Ctrl in deselect mode). Does not modify any line geometry.</li>
	 * </ul>
	 */
	private void handleEditModeControlPointDrag(MouseEvent e)
	{
		dragOccurred = true;
		LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
		// Keep the brush circle following the cursor during a drag — handleMouseDraggedOnMap fires on drag, but the brush position is
		// normally updated by the mouse-MOVED handler which doesn't fire during a drag.
		int brushDiameter = getEditBrushDiameter();
		if (brushDiameter > 1)
		{
			mapEditingPanel.showBrush(e.getPoint(), brushDiameter);
		}
		if (dragIsPaint)
		{
			handleEditModePaintDragTick(e, activeType);
			return;
		}
		if (dragMovingCPs == null || dragMovingCPs.isEmpty() || dragStartLocRI == null)
		{
			return;
		}
		Point currentLocRI = getPointOnGraph(e.getPoint()).mult(1.0 / mainWindow.displayQualityScale);
		Point delta = new Point(currentLocRI.x - dragStartLocRI.x, currentLocRI.y - dragStartLocRI.y);

		// Only the segments adjacent to each moved CP (within Catmull-Rom propagation range) need redrawing. The unaffected portions
		// of any road's dashed pattern are caught later by the low-priority redraw queued in handleEditModeControlPointDragEnd.
		List<List<Point>> centersTouched = new ArrayList<>();

		// Group by line so we mutate each line once with all its moves applied.
		Map<Object, Map<Integer, Point>> movesByLine = new HashMap<>();
		for (MovingCP m : dragMovingCPs)
		{
			Point newLoc = new Point(m.startLocRI().x + delta.x, m.startLocRI().y + delta.y);
			movesByLine.computeIfAbsent(m.line(), k -> new HashMap<>()).put(m.nodeIndex(), newLoc);
		}

		for (Map.Entry<Object, Map<Integer, Point>> entry : movesByLine.entrySet())
		{
			Object line = entry.getKey();
			Map<Integer, Point> moves = entry.getValue();
			if (line instanceof River river)
			{
				List<RiverPathNode> nodes = new ArrayList<>(river.nodes);
				for (Map.Entry<Integer, Point> move : moves.entrySet())
				{
					int idx = move.getKey();
					if (idx < 0 || idx >= nodes.size())
					{
						continue;
					}
					appendControlPointEditScope(centersTouched, nodes, idx);
					moveRiverControlPointTo(nodes, idx, move.getValue());
				}
				river.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(nodes);
				for (int idx : moves.keySet())
				{
					appendControlPointEditScope(centersTouched, nodes, idx);
				}
			}
			else if (line instanceof Road road)
			{
				List<RoadPathNode> nodes = new ArrayList<>(road.nodes);
				for (Map.Entry<Integer, Point> move : moves.entrySet())
				{
					int idx = move.getKey();
					if (idx < 0 || idx >= nodes.size())
					{
						continue;
					}
					appendControlPointEditScope(centersTouched, nodes, idx);
					nodes.set(idx, new RoadPathNode(move.getValue()));
				}
				road.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(nodes);
				for (int idx : moves.keySet())
				{
					appendControlPointEditScope(centersTouched, nodes, idx);
				}
			}
		}

		// Refresh visuals at the new cursor position. During a move-drag we deliberately skip the hover preview (orange CP outlines,
		// yellow rings, hover-color segment polylines) since nothing under the cursor is selectable while the drag is in flight —
		// showing them would falsely suggest the user could pick something up.
		mapEditingPanel.clearHighlightedPolylines();
		renderSelectionOnlyVisuals(activeType);
		updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingPoints(centersTouched));
		mapEditingPanel.repaint();
	}

	/**
	 * Refreshes the live-selection visuals (filled CP circles + selected-segment polylines) and clears every hover-preview visual (orange
	 * CP outlines, yellow hover rings, hover-color segment polylines). Used during a move-drag, where the hover preview is misleading
	 * because the user can't switch the selection mid-drag.
	 */
	private void renderSelectionOnlyVisuals(LineType activeType)
	{
		double scale = mainWindow.displayQualityScale;
		List<Point> selectedCirclesGraphPixels = collectCPGraphLocations(selectedRiverCPs, selectedRoadCPs, activeType, scale);
		mapEditingPanel.setSelectedControlPointCircles(selectedCirclesGraphPixels);
		mapEditingPanel.setControlPointCircles(new ArrayList<>());
		mapEditingPanel.clearHoveredControlPoint();
		mapEditingPanel.clearHoverPolylines();
		applySelectedSegmentsHighlight();
	}

	/**
	 * Paint-drag tick. Accumulates CPs/segments under the brush into (or out of) the selection. Geometry is not modified.
	 */
	private void handleEditModePaintDragTick(MouseEvent e, LineType activeType)
	{
		boolean ctrlDown = SwingHelper.isCommandKeyDown(e);
		boolean deselectMode = controlClickBehavior != null && controlClickBehavior.isUnselectMode();
		int brushDiameter = getEditBrushDiameter();
		List<LineHit> brushHits = multiBrushHitTest(e.getPoint(), activeType, brushDiameter);
		boolean removing = ctrlDown && deselectMode;
		for (LineHit bh : brushHits)
		{
			Object line = bh.river() != null ? bh.river() : bh.road();
			if (bh.isControlPoint())
			{
				applySelectionDelta(line, bh.controlPointIndex(), removing);
			}
			else if (bh.isSegment())
			{
				applySelectionDelta(line, bh.segmentIndex(), removing);
				applySelectionDelta(line, bh.segmentIndex() + 1, removing);
			}
		}
		// Always refresh, even when the brush is over empty space (no hits). The selection is unchanged in that case, but the brush
		// circle's position was updated via showBrush in the caller and needs a repaint to keep following the cursor. Erase mode gets
		// this for free because its drag path always runs highlightHoverCentersOrEdgesAndBrush, which repaints unconditionally; bailing
		// out here without repainting was what froze the brush the moment it left a river/road.
		refreshSelectionVisuals(e.getPoint(), activeType, ctrlDown);
	}

	/**
	 * Moves the river control point at {@code idx} in {@code nodes} to {@code newLocRI}, clearing the edge index on the moved node and on
	 * its predecessor (both adjacent segments no longer follow a single Voronoi edge). Mutates {@code nodes} in place; does not assign to
	 * any River's {@code .nodes} field.
	 */
	private void moveRiverControlPointTo(List<RiverPathNode> nodes, int idx, Point newLocRI)
	{
		RiverPathNode old = nodes.get(idx);
		nodes.set(idx, new RiverPathNode(newLocRI, old.getWidthLevelToNext(), old.getSeedToNext(), RiverPathNode.EDGE_INDEX_NONE));
		if (idx > 0)
		{
			RiverPathNode prev = nodes.get(idx - 1);
			nodes.set(idx - 1, new RiverPathNode(prev.getLoc(), prev.getWidthLevelToNext(), prev.getSeedToNext(), RiverPathNode.EDGE_INDEX_NONE));
		}
	}

	/**
	 * Commits a move-drag: records the undo point and triggers a normal-priority redraw scoped to each moved CP's neighborhood, plus a
	 * low-priority full-line redraw for any roads that had CPs moved (so the dashed pattern is rephased across the whole road). No-op for
	 * paint-drags (they don't modify geometry).
	 */
	private void handleEditModeControlPointDragEnd()
	{
		if (dragIsPaint || dragMovingCPs == null || dragMovingCPs.isEmpty() || dragBeforeSnapshots == null)
		{
			return;
		}
		List<List<Point>> centerPaths = new ArrayList<>();
		// Group moved indices by line so we can call the per-line scope helper once with all changed indices.
		Map<Object, Set<Integer>> indicesByLine = new HashMap<>();
		for (MovingCP m : dragMovingCPs)
		{
			indicesByLine.computeIfAbsent(m.line(), k -> new HashSet<>()).add(m.nodeIndex());
		}
		Set<Road> roadsToRephaseDashes = new HashSet<>();
		for (Map.Entry<Object, Set<Integer>> entry : indicesByLine.entrySet())
		{
			Object line = entry.getKey();
			List<Point> beforeSnapshot = dragBeforeSnapshots.get(line);
			for (int idx : entry.getValue())
			{
				appendControlPointEditScopeFromSnapshot(centerPaths, beforeSnapshot, idx);
				appendControlPointEditScope(centerPaths, nodesOf(line), idx);
			}
			if (line instanceof Road r)
			{
				roadsToRephaseDashes.add(r);
			}
		}
		if (!roadsToRephaseDashes.isEmpty())
		{
			updater.addRoadsToRedrawLowPriority(new ArrayList<>(roadsToRephaseDashes), mainWindow.displayQualityScale);
		}
		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingPoints(centerPaths));
		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));
		dragMovingCPs = null;
		dragBeforeSnapshots = null;
	}

	/**
	 * Right-click handling:
	 * <ul>
	 * <li>When a free-hand line is in progress, removes the last control point Krita-style.</li>
	 * <li>Otherwise, in draw or edit mode for rivers/roads, opens a context menu targeted at whatever is under the cursor.</li>
	 * </ul>
	 */
	@Override
	protected void handleMouseRightPressedOnMap(MouseEvent e)
	{
		if (riversButton.isSelected() && modeWidget.isDrawMode() && isFreeHandRiverDrawMode() && freeHandRiverPathRI != null && !freeHandRiverPathRI.isEmpty())
		{
			if (freeHandRiverPathRI.size() > 1)
			{
				freeHandRiverPathRI.remove(freeHandRiverPathRI.size() - 1);
			}
			updateControlPointDisplay(e.getPoint(), LineType.RIVER);
			mapEditingPanel.repaint();
			return;
		}
		if (roadsButton.isSelected() && modeWidget.isDrawMode() && isFreeHandDrawMode() && freeHandRoadPathRI != null && !freeHandRoadPathRI.isEmpty())
		{
			if (freeHandRoadPathRI.size() > 1)
			{
				freeHandRoadPathRI.remove(freeHandRoadPathRI.size() - 1);
			}
			updateControlPointDisplay(e.getPoint(), LineType.ROAD);
			mapEditingPanel.repaint();
			return;
		}
		if ((riversButton.isSelected() || roadsButton.isSelected()) && (modeWidget.isDrawMode() || modeWidget.isEditMode()))
		{
			showRiverRoadContextMenu(e);
		}
	}

	/**
	 * Re-runs the cursor hit-test and shows a context menu targeted at whatever is under the cursor. Items are context-dependent: Delete
	 * Control Point when on a control point; Delete Segment, Insert Control Point, and (in edit mode only) Select Segment when on a
	 * segment. Returns without showing a menu when nothing is in range.
	 */
	private void showRiverRoadContextMenu(MouseEvent e)
	{
		LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
		// Right-click is a single-point gesture, so it uses the default segment hit threshold — same one a single left-click would. The
		// segment hover-highlight visualizes that same range, so what the user sees lit up is what right-click can act on.
		final LineHit hit = editModeHitTest(e.getPoint(), activeType, null, null);
		boolean haveSelectionMenuItems = modeWidget.isEditMode() && hasAnySelection();
		if (hit == null && !haveSelectionMenuItems)
		{
			return;
		}
		JPopupMenu menu = new JPopupMenu();
		if (hit != null && hit.isControlPoint())
		{
			JMenuItem deleteCp = new JMenuItem(Translation.get("landWaterTool.edit.deleteControlPoint"));
			deleteCp.addActionListener(ev -> deleteControlPoint(hit));
			menu.add(deleteCp);
		}
		else if (hit != null && hit.isSegment())
		{
			JMenuItem deleteSeg = new JMenuItem(Translation.get("landWaterTool.edit.deleteSegment"));
			deleteSeg.addActionListener(ev -> deleteSegment(hit));
			menu.add(deleteSeg);

			JMenuItem insertCp = new JMenuItem(Translation.get("landWaterTool.edit.insertControlPoint"));
			final java.awt.Point clickPanelPoint = e.getPoint();
			insertCp.addActionListener(ev -> insertControlPointIntoSegment(hit, clickPanelPoint));
			menu.add(insertCp);
		}
		if (haveSelectionMenuItems)
		{
			if (menu.getComponentCount() > 0)
			{
				menu.addSeparator();
			}
			JMenuItem deleteSelection = new JMenuItem(Translation.get("landWaterTool.edit.deleteSelection"));
			deleteSelection.addActionListener(ev -> deleteSelectedCPs());
			menu.add(deleteSelection);
		}
		if (menu.getComponentCount() > 0)
		{
			menu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	/**
	 * Deletes the control point identified by {@code hit} (must be a control-point hit). The sticky selection is preserved across the edit:
	 * if the deleted CP belongs to the sticky river/road, the selection is adjusted so the user's other selected CPs on the line keep
	 * tracking the same geometric points after the shift. If the line drops below 2 control points, the line is removed and any sticky
	 * selection on it is cleared.
	 */
	private void deleteControlPoint(LineHit hit)
	{
		int idx = hit.controlPointIndex();
		if (hit.river() != null)
		{
			River line = hit.river();
			List<RiverPathNode> nodes = new ArrayList<>(line.nodes);
			if (idx < 0 || idx >= nodes.size())
			{
				return;
			}
			List<Point> beforePath = PathOperations.toLocationList(nodes);
			if (idx > 0 && idx < nodes.size() - 1)
			{
				// Middle: the previous node's outgoing segment now spans to nodes[idx+1]. Clear its
				// edge index because the new bridging segment doesn't follow a single Voronoi edge.
				RiverPathNode prev = nodes.get(idx - 1);
				nodes.set(idx - 1, new RiverPathNode(prev.getLoc(), prev.getWidthLevelToNext(), prev.getSeedToNext(), RiverPathNode.EDGE_INDEX_NONE));
			}
			nodes.remove(idx);
			adjustSelectionAfterControlPointDelete(line, idx);
			// After delete: the post-delete index closest to where the change happened is min(idx, lastIndex). The redraw slice around
			// it (radius = CATMULL_ROM_PROPAGATION_RADIUS) covers the new bridging segment plus its tangent-reference neighbors.
			int afterIdx = Math.max(0, Math.min(idx, nodes.size() - 1));
			commitRiverEdit(line, beforePath, nodes, idx, afterIdx);
		}
		else if (hit.road() != null)
		{
			Road line = hit.road();
			List<RoadPathNode> nodes = new ArrayList<>(line.nodes);
			if (idx < 0 || idx >= nodes.size())
			{
				return;
			}
			List<Point> beforePath = PathOperations.toLocationList(nodes);
			nodes.remove(idx);
			adjustSelectionAfterControlPointDelete(line, idx);
			int afterIdx = Math.max(0, Math.min(idx, nodes.size() - 1));
			commitRoadEdit(line, beforePath, nodes, idx, afterIdx);
		}
	}

	/**
	 * Deletes the segment identified by {@code hit} (must be a segment hit). Splits the line at that segment into a "first half" and a
	 * "second half". The first half mutates the existing line in place; the second half (if non-degenerate) becomes a new line. The sticky
	 * selection follows the segment when possible: if the sticky segment was before the deleted segment it stays in the first half; if it
	 * was after it moves to the second half. The deleted segment itself can't be sticky after the operation.
	 */
	private void deleteSegment(LineHit hit)
	{
		int idx = hit.segmentIndex();
		if (hit.river() != null)
		{
			River line = hit.river();
			List<RiverPathNode> nodes = new ArrayList<>(line.nodes);
			if (idx < 0 || idx >= nodes.size() - 1)
			{
				return;
			}
			List<RiverPathNode> firstHalf = new ArrayList<>(nodes.subList(0, idx + 1));
			List<RiverPathNode> secondHalf = new ArrayList<>(nodes.subList(idx + 1, nodes.size()));
			// The last node of the first half no longer has an outgoing segment; clear its "to-next"
			// metadata so we don't leave dangling width/seed/edgeIndex referencing the removed segment.
			if (!firstHalf.isEmpty())
			{
				RiverPathNode last = firstHalf.get(firstHalf.size() - 1);
				firstHalf.set(firstHalf.size() - 1, new RiverPathNode(last.getLoc(), 0, 0L, RiverPathNode.EDGE_INDEX_NONE));
			}
			List<River> changed = new ArrayList<>();
			if (firstHalf.size() >= 2)
			{
				line.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(firstHalf);
				changed.add(line);
			}
			else
			{
				mainWindow.edits.rivers.remove(line);
			}
			River newRiver = null;
			if (secondHalf.size() >= 2)
			{
				newRiver = new River(secondHalf);
				mainWindow.edits.rivers.add(newRiver);
				changed.add(newRiver);
			}
			adjustSelectionAfterSegmentDelete(line, newRiver, idx);
			List<List<Point>> removedSegments = Collections.singletonList(Arrays.asList(nodes.get(idx).getLoc(), nodes.get(idx + 1).getLoc()));
			commitRiverCut(removedSegments, changed);
		}
		else if (hit.road() != null)
		{
			Road line = hit.road();
			List<RoadPathNode> nodes = new ArrayList<>(line.nodes);
			if (idx < 0 || idx >= nodes.size() - 1)
			{
				return;
			}
			List<RoadPathNode> firstHalf = new ArrayList<>(nodes.subList(0, idx + 1));
			List<RoadPathNode> secondHalf = new ArrayList<>(nodes.subList(idx + 1, nodes.size()));
			List<Road> changed = new ArrayList<>();
			if (firstHalf.size() >= 2)
			{
				line.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(firstHalf);
				changed.add(line);
			}
			else
			{
				mainWindow.edits.roads.remove(line);
			}
			Road newRoad = null;
			if (secondHalf.size() >= 2)
			{
				newRoad = new Road(secondHalf);
				mainWindow.edits.roads.add(newRoad);
				changed.add(newRoad);
			}
			adjustSelectionAfterSegmentDelete(line, newRoad, idx);
			List<List<Point>> removedSegments = Collections.singletonList(Arrays.asList(nodes.get(idx).getLoc(), nodes.get(idx + 1).getLoc()));
			commitRoadCut(removedSegments, changed);
		}
	}

	/**
	 * Inserts a new control point at {@code panelPoint} into the segment identified by {@code hit} (must be a segment hit). The sticky
	 * selection is preserved: if the sticky segment was the one being inserted into, it stays as the "first half" of the split; sticky
	 * segments after the insert point shift by 1 to remain on the same geometric segment.
	 */
	private void insertControlPointIntoSegment(LineHit hit, java.awt.Point panelPoint)
	{
		Point clickRI = getPointOnGraph(panelPoint).mult(1.0 / mainWindow.displayQualityScale);
		int idx = hit.segmentIndex();
		if (hit.river() != null)
		{
			River line = hit.river();
			List<RiverPathNode> nodes = new ArrayList<>(line.nodes);
			if (idx < 0 || idx >= nodes.size() - 1)
			{
				return;
			}
			List<Point> beforePath = PathOperations.toLocationList(nodes);
			RiverPathNode boundary = nodes.get(idx);
			int width = boundary.getWidthLevelToNext();
			Random random = new Random();
			// Existing node[idx] now goes to the new node — keep its width but generate a fresh seed
			// and clear its edge index since the split segment no longer follows a single Voronoi edge.
			nodes.set(idx, new RiverPathNode(boundary.getLoc(), width, random.nextLong(), RiverPathNode.EDGE_INDEX_NONE));
			// New node carries its outgoing segment's width with a fresh seed; no edge index either.
			RiverPathNode newNode = new RiverPathNode(clickRI, width, random.nextLong(), RiverPathNode.EDGE_INDEX_NONE);
			nodes.add(idx + 1, newNode);
			adjustSelectionAfterControlPointInsert(line, idx);
			// The split segment ran from idx to idx+1 in beforePath; the new node lands at idx+1 in the post-insert path. Slicing
			// around those indices (radius = CATMULL_ROM_PROPAGATION_RADIUS) covers every segment whose tangent-reference position
			// shifted because of the insert.
			commitRiverEdit(line, beforePath, nodes, idx, idx + 1);
		}
		else if (hit.road() != null)
		{
			Road line = hit.road();
			List<RoadPathNode> nodes = new ArrayList<>(line.nodes);
			if (idx < 0 || idx >= nodes.size() - 1)
			{
				return;
			}
			List<Point> beforePath = PathOperations.toLocationList(nodes);
			nodes.add(idx + 1, new RoadPathNode(clickRI));
			adjustSelectionAfterControlPointInsert(line, idx);
			commitRoadEdit(line, beforePath, nodes, idx, idx + 1);
		}
	}

	/**
	 * Shifts indices in the selection set of {@code line} after the control point at {@code deletedIdx} was removed. The deleted index
	 * itself is dropped; indices > deletedIdx shift down by 1. Selections on other lines are untouched.
	 */
	private void adjustSelectionAfterControlPointDelete(Object line, int deletedIdx)
	{
		Set<Integer> oldSet = (line instanceof River r) ? selectedRiverCPs.get(r) : selectedRoadCPs.get((Road) line);
		if (oldSet == null || oldSet.isEmpty())
		{
			return;
		}
		Set<Integer> newSet = new HashSet<>();
		for (int idx : oldSet)
		{
			if (idx < deletedIdx)
			{
				newSet.add(idx);
			}
			else if (idx > deletedIdx)
			{
				newSet.add(idx - 1);
			}
			// idx == deletedIdx: dropped.
		}
		if (line instanceof River r)
		{
			if (newSet.isEmpty())
			{
				selectedRiverCPs.remove(r);
			}
			else
			{
				selectedRiverCPs.put(r, newSet);
			}
		}
		else if (line instanceof Road r)
		{
			if (newSet.isEmpty())
			{
				selectedRoadCPs.remove(r);
			}
			else
			{
				selectedRoadCPs.put(r, newSet);
			}
		}
	}

	/**
	 * Shifts indices in the selection set of {@code line} after a control point was inserted at index {@code splitSegmentIdx + 1}. Indices
	 * > splitSegmentIdx shift up by 1.
	 */
	private void adjustSelectionAfterControlPointInsert(Object line, int splitSegmentIdx)
	{
		Set<Integer> oldSet = (line instanceof River r) ? selectedRiverCPs.get(r) : selectedRoadCPs.get((Road) line);
		if (oldSet == null || oldSet.isEmpty())
		{
			return;
		}
		Set<Integer> newSet = new HashSet<>();
		for (int idx : oldSet)
		{
			if (idx > splitSegmentIdx)
			{
				newSet.add(idx + 1);
			}
			else
			{
				newSet.add(idx);
			}
		}
		if (line instanceof River r)
		{
			selectedRiverCPs.put(r, newSet);
		}
		else if (line instanceof Road r)
		{
			selectedRoadCPs.put(r, newSet);
		}
	}

	/**
	 * Splits the selection of {@code modifiedLine} after a segment-cut. Indices &le; deletedSegIdx stay; indices &gt; deletedSegIdx move to
	 * {@code newLine} (with shifted index). Indices at exactly the cut endpoints (deletedSegIdx, deletedSegIdx+1) stay on whichever
	 * surviving piece they belong to.
	 */
	private void adjustSelectionAfterSegmentDelete(Object modifiedLine, Object newLine, int deletedSegIdx)
	{
		Set<Integer> oldSet;
		if (modifiedLine instanceof River r)
		{
			oldSet = selectedRiverCPs.get(r);
		}
		else if (modifiedLine instanceof Road r)
		{
			oldSet = selectedRoadCPs.get(r);
		}
		else
		{
			return;
		}
		if (oldSet == null || oldSet.isEmpty())
		{
			return;
		}
		Set<Integer> firstHalfIdxs = new HashSet<>();
		Set<Integer> secondHalfIdxs = new HashSet<>();
		for (int idx : oldSet)
		{
			if (idx <= deletedSegIdx)
			{
				firstHalfIdxs.add(idx);
			}
			else
			{
				secondHalfIdxs.add(idx - (deletedSegIdx + 1));
			}
		}
		if (modifiedLine instanceof River r)
		{
			if (firstHalfIdxs.isEmpty())
			{
				selectedRiverCPs.remove(r);
			}
			else
			{
				selectedRiverCPs.put(r, firstHalfIdxs);
			}
			if (newLine instanceof River newRiver && !secondHalfIdxs.isEmpty())
			{
				selectedRiverCPs.put(newRiver, secondHalfIdxs);
			}
		}
		else if (modifiedLine instanceof Road r)
		{
			if (firstHalfIdxs.isEmpty())
			{
				selectedRoadCPs.remove(r);
			}
			else
			{
				selectedRoadCPs.put(r, firstHalfIdxs);
			}
			if (newLine instanceof Road newRoad && !secondHalfIdxs.isEmpty())
			{
				selectedRoadCPs.put(newRoad, secondHalfIdxs);
			}
		}
	}

	/**
	 * Commits a modified river's node list in place and triggers a redraw scoped to the segments affected by changing a single control
	 * point. Pass the deleted/inserted/moved index in {@code beforeChangedIndex} (relative to {@code beforePath}) and the corresponding
	 * index in the new node list in {@code afterChangedIndex}. The slice radius is {@link PathOperations#CATMULL_ROM_PROPAGATION_RADIUS} so
	 * the visible curve-shape change is fully covered. Preserves sticky selection.
	 */
	private void commitRiverEdit(River line, List<Point> beforePath, List<RiverPathNode> newNodes, int beforeChangedIndex, int afterChangedIndex)
	{
		boolean removed = newNodes.size() < 2;
		if (removed)
		{
			mainWindow.edits.rivers.remove(line);
			selectedRiverCPs.remove(line);
		}
		else
		{
			line.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(newNodes);
		}
		List<List<Point>> centerPaths = new ArrayList<>();
		appendControlPointEditScopeFromSnapshot(centerPaths, beforePath, beforeChangedIndex);
		if (!removed)
		{
			appendControlPointEditScope(centerPaths, line.nodes, afterChangedIndex);
		}
		// If the edit changed an endpoint (e.g. deleting a terminal CP), the line's new endpoint may now match an existing river's
		// endpoint. Merge so the result is a single continuous spline instead of two coincident-end segments meeting at a sharp angle.
		mergeRiverWithNeighborsAndExpandRedrawScope(removed ? null : line, centerPaths);
		finishRiverPostEdit(getCentersTouchingPoints(centerPaths));
	}

	/**
	 * Attempts to merge {@code modifiedLine} (if non-null) with any existing river whose endpoint now matches and, when a merge happens,
	 * appends the extended river's full node list to {@code centerPaths} so the incremental redraw covers the join point. The newly merged
	 * spline curves around the join in a way the candidate's local pre-edit scope would not have covered.
	 */
	private void mergeRiverWithNeighborsAndExpandRedrawScope(River modifiedLine, List<List<Point>> centerPaths)
	{
		if (modifiedLine == null || modifiedLine.nodes.size() < 2)
		{
			return;
		}
		List<River> extended = RiverDrawer.mergeAdjacentRivers(Collections.singletonList(modifiedLine), mainWindow.edits.rivers);
		for (River ext : extended)
		{
			centerPaths.add(PathOperations.toLocationList(ext.nodes));
		}
	}

	/**
	 * Used by the segment-delete (cut) path: redraws the removed-segment area plus the inner neighbors of the new end segments. See
	 * {@link PathOperations#findInnerNeighborsOfCutEndpoints} for why the inner neighbors are required (Catmull-Rom end segments use a
	 * synthetic reflection control point, so the curve shape on the new end segments changes and the redraw bounds must cover them).
	 */
	private void commitRiverCut(List<List<Point>> removedSegments, List<River> changed)
	{
		List<List<Point>> centerPaths = new ArrayList<>(pointsToCoverInRedrawAfterPathCut(mainWindow.edits.rivers, r -> r.nodes, removedSegments));
		// The cut may have exposed new endpoints that match an existing river's endpoint — merge to keep splines continuous.
		List<River> extended = RiverDrawer.mergeAdjacentRivers(changed, mainWindow.edits.rivers);
		for (River ext : extended)
		{
			centerPaths.add(PathOperations.toLocationList(ext.nodes));
		}
		finishRiverPostEdit(getCentersTouchingPoints(centerPaths));
	}

	private void finishRiverPostEdit(Set<Center> centersToRedraw)
	{
		purgeOrphanedSelections();
		undoer.setUndoPoint(UpdateType.Incremental, this);
		updater.createAndShowMapIncrementalUsingCenters(centersToRedraw);
		mapEditingPanel.clearHighlightedPolylines();
		applySelectedSegmentsHighlight();
		mapEditingPanel.repaint();
	}

	/** Road counterpart of {@link #commitRiverEdit}. */
	private void commitRoadEdit(Road line, List<Point> beforePath, List<RoadPathNode> newNodes, int beforeChangedIndex, int afterChangedIndex)
	{
		boolean removed = newNodes.size() < 2;
		if (removed)
		{
			mainWindow.edits.roads.remove(line);
			selectedRoadCPs.remove(line);
		}
		else
		{
			line.nodes = new java.util.concurrent.CopyOnWriteArrayList<>(newNodes);
		}
		List<List<Point>> centerPaths = new ArrayList<>();
		appendControlPointEditScopeFromSnapshot(centerPaths, beforePath, beforeChangedIndex);
		List<Road> changed = removed ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(line));
		if (!removed)
		{
			appendControlPointEditScope(centerPaths, line.nodes, afterChangedIndex);
		}
		// If the edit changed an endpoint, the line's new endpoint may now match an existing road's endpoint — merge so the spline
		// continues smoothly across the join. Replace the redraw target with the surviving (extended) road in that case.
		List<Road> extended = removed ? Collections.emptyList() : RoadDrawer.mergeAdjacentRoads(Collections.singletonList(line), mainWindow.edits.roads);
		if (!extended.isEmpty())
		{
			changed = new ArrayList<>(extended);
			for (Road ext : extended)
			{
				centerPaths.add(PathOperations.toLocationList(ext.nodes));
			}
		}
		finishRoadPostEdit(getCentersTouchingPoints(centerPaths), changed);
	}

	/** Road counterpart of {@link #commitRiverCut}. */
	private void commitRoadCut(List<List<Point>> removedSegments, List<Road> changed)
	{
		List<List<Point>> centerPaths = new ArrayList<>(pointsToCoverInRedrawAfterPathCut(mainWindow.edits.roads, r -> r.nodes, removedSegments));
		List<Road> extended = RoadDrawer.mergeAdjacentRoads(changed, mainWindow.edits.roads);
		if (!extended.isEmpty())
		{
			// Drop any candidates that got absorbed (no longer in edits.roads) and add the surviving extended roads.
			changed = new ArrayList<>(changed);
			changed.removeIf(r -> !mainWindow.edits.roads.contains(r));
			for (Road ext : extended)
			{
				if (!changed.contains(ext))
				{
					changed.add(ext);
				}
				centerPaths.add(PathOperations.toLocationList(ext.nodes));
			}
		}
		finishRoadPostEdit(getCentersTouchingPoints(centerPaths), changed);
	}

	private void finishRoadPostEdit(Set<Center> centersToRedraw, List<Road> changed)
	{
		purgeOrphanedSelections();
		undoer.setUndoPoint(UpdateType.Incremental, this);
		if (!changed.isEmpty())
		{
			updater.addRoadsToRedrawLowPriority(changed, mainWindow.displayQualityScale);
		}
		updater.createAndShowMapIncrementalUsingCenters(centersToRedraw);
		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));
		mapEditingPanel.clearHighlightedPolylines();
		applySelectedSegmentsHighlight();
		mapEditingPanel.repaint();
	}

	/**
	 * Drops selection entries whose River/Road has been removed from the live edits collection (segment cut leaving sub-2-node pieces,
	 * post-edit merge absorbing the candidate, etc.). Without this, the highlight renderer happily redraws a CP using the dead object's
	 * stale node list — leaving "orphan" highlights at locations whose underlying line no longer exists.
	 */
	private void purgeOrphanedSelections()
	{
		selectedRiverCPs.keySet().removeIf(r -> !mainWindow.edits.rivers.contains(r));
		selectedRoadCPs.keySet().removeIf(r -> !mainWindow.edits.roads.contains(r));
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		regionIdToExpand = null;

		if (dragInProgress)
		{
			boolean wasDrag = dragOccurred;
			dragInProgress = false;
			dragOccurred = false;
			// Only commit when the user actually moved the cursor; a click-without-drag is just a select and shouldn't push an undo point.
			// Paint-drags don't modify geometry, so they have no commit step either way.
			if (wasDrag && !dragIsPaint)
			{
				handleEditModeControlPointDragEnd();
			}
			dragIsPaint = false;
			dragMovingCPs = null;
			dragBeforeSnapshots = null;
			dragStartLocRI = null;
			return;
		}

		if (riversButton.isSelected() && modeWidget.isDrawMode() && !isFreeHandRiverDrawMode() && riverStart != null)
		{
			Corner end = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
			Point polygonRiverSnapEnd = computeSnapPointForType(e.getPoint(), LineType.RIVER);
			Set<Edge> river = filterOutOceanAndCoastEdges(updater.mapParts.graph.findPathGreedy(riverStart, end));
			Point snapEndGraphPixels = polygonRiverSnapEnd == null ? null : polygonRiverSnapEnd.mult(mainWindow.displayQualityScale);
			Point snapStartGraphPixels = polygonRiverSnapStart == null ? null : polygonRiverSnapStart.mult(mainWindow.displayQualityScale);
			TrimmedRiverPath trimmed = trimRiverPathIfPathOvershootsMouse(river, riverStart, end, snapStartGraphPixels, snapEndGraphPixels);
			river = trimmed.path();
			Corner start = trimmed.start();
			end = trimmed.end();
			if (DebugFlags.printRiverEdgeIndexes())
			{
				System.out.println("River edges (polygon mode):");
				for (Edge edge : river)
				{
					System.out.println("  index=" + edge.index);
				}
			}
			int riverLevel = sliderValueToRiverLevel(riverWidthSlider.getValue());
			List<River> newRivers = RiverDrawer.addRiversFromEdgesInEditor(river, start, riverLevel, mainWindow.displayQualityScale, mainWindow.edits.rivers);

			// Empty-path case: the user pressed and released at the same Corner (or so close that findPathGreedy returned no edges),
			// but a snap is active because the cursor is near a freehand control point. The intended result is a bridge-only river
			// from the Corner to the snap point — synthesize a 1-node river so applyRiverSnapPoints has something to extend.
			if (newRivers.isEmpty() && (polygonRiverSnapStart != null || polygonRiverSnapEnd != null))
			{
				Point startLocRI = start.loc.mult(1.0 / mainWindow.displayQualityScale);
				List<RiverPathNode> syntheticNodes = new ArrayList<>();
				syntheticNodes.add(new RiverPathNode(startLocRI, riverLevel, new Random().nextLong(), RiverPathNode.EDGE_INDEX_NONE));
				River syntheticRiver = new River(syntheticNodes);
				mainWindow.edits.rivers.add(syntheticRiver);
				newRivers.add(syntheticRiver);
			}

			if (polygonRiverSnapStart != null || polygonRiverSnapEnd != null)
			{
				Point riverStartRI = start.loc.mult(1.0 / mainWindow.displayQualityScale);
				Point endRI = end.loc.mult(1.0 / mainWindow.displayQualityScale);
				for (River r : newRivers)
				{
					if (r == null || r.nodes.isEmpty())
					{
						continue;
					}
					applyRiverSnapPoints(r, polygonRiverSnapStart, riverStartRI, polygonRiverSnapEnd, endRI);
				}
			}

			// After snap points are prepended/appended, try to merge each changed river with any
			// existing river whose endpoint now matches the snap point. This handles continuing a
			// freehand river using polygon mode: the snap prepend puts the freehand river's endpoint
			// at the start of the new river, but addRiversFromEdgesInEditor ran before that prepend
			// and could not see the connection.
			if (polygonRiverSnapStart != null || polygonRiverSnapEnd != null)
			{
				for (int i = 0; i < newRivers.size(); i++)
				{
					River r = newRivers.get(i);
					if (r == null || r.nodes.isEmpty())
					{
						continue;
					}
					River joined = RiverDrawer.tryConnectingRiverToExistingRiver(r, mainWindow.edits.rivers);
					if (joined != null)
					{
						mainWindow.edits.rivers.remove(r);
						newRivers.set(i, joined);
					}
				}
			}

			riverStart = null;
			polygonRiverSnapStart = null;
			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.clearHighlightedPolylines();
			mapEditingPanel.repaint();

			if (!newRivers.isEmpty())
			{
				List<List<Point>> pathsForCenters = newRivers.stream().map(r -> PathOperations.toLocationList(r.nodes)).collect(Collectors.toList());
				updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingPoints(pathsForCenters));
			}
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && !isFreeHandDrawMode() && roadStart != null)
		{
			Point polygonSnapEnd = computeSnapPointForType(e.getPoint(), LineType.ROAD);
			Center end = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
			List<Edge> edges = updater.mapParts.graph.findShortestPath(roadStart, end, (ignored1, ignored2, distance) -> distance);
			Point snapEndGraphPixels = polygonSnapEnd == null ? null : polygonSnapEnd.mult(mainWindow.displayQualityScale);
			Point snapStartGraphPixels = polygonRoadSnapStart == null ? null : polygonRoadSnapStart.mult(mainWindow.displayQualityScale);
			TrimmedRoadPath trimmed = trimRoadPathIfPathOvershootsMouse(edges, roadStart, end, snapStartGraphPixels, snapEndGraphPixels);
			edges = trimmed.path();
			Center start = trimmed.start();
			end = trimmed.end();

			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.clearHighlightedPolylines();
			clearRoadControlPointDisplay();
			mapEditingPanel.repaint();

			List<Road> changed = RoadDrawer.addRoadsFromEdgesInEditor(edges, updater.mapParts.graph, mainWindow.edits.roads, mainWindow.displayQualityScale);

			// Empty-path case: same as the river branch — pressed and released at the same Center but a snap is active, so the
			// intended result is a bridge-only road from the Center to the snap point. Synthesize a 1-node road for the snap logic
			// to extend.
			if (changed.isEmpty() && (polygonRoadSnapStart != null || polygonSnapEnd != null))
			{
				Point roadStartLocRI = start.loc.mult(1.0 / mainWindow.displayQualityScale);
				List<RoadPathNode> syntheticNodes = new ArrayList<>();
				syntheticNodes.add(new RoadPathNode(roadStartLocRI));
				Road syntheticRoad = new Road(syntheticNodes);
				mainWindow.edits.roads.add(syntheticRoad);
				changed.add(syntheticRoad);
			}

			// If snap points are set, extend the road's endpoints to connect to those exact control-point locations.
			// Skip if the snap point already equals the center location (already the natural start/end of the Delaunay road).
			if (polygonRoadSnapStart != null || polygonSnapEnd != null)
			{
				Point roadStartRI = start.loc.mult(1.0 / mainWindow.displayQualityScale);
				Point endRI = end.loc.mult(1.0 / mainWindow.displayQualityScale);
				for (Road road : changed)
				{
					if (road == null || road.nodes.isEmpty())
					{
						continue;
					}
					applyRoadSnapPoints(road, polygonRoadSnapStart, roadStartRI, polygonSnapEnd, endRI);
				}
			}

			// After snap points are prepended/appended, try to merge each changed road with any existing
			// road whose endpoint now matches the snap point. This handles continuing a freehand road
			// using polygon mode: the snap prepend puts the freehand road's endpoint at the start of
			// the new road, but addRoadsFromEdgesInEditor ran before that prepend and could not see
			// the connection.
			if (polygonRoadSnapStart != null || polygonSnapEnd != null)
			{
				for (int i = 0; i < changed.size(); i++)
				{
					Road road = changed.get(i);
					if (road == null || road.nodes.isEmpty())
					{
						continue;
					}
					Road joined = RoadDrawer.tryConnectingRoadToExistingRoad(road, mainWindow.edits.roads);
					if (joined != null)
					{
						mainWindow.edits.roads.remove(road);
						changed.set(i, joined);
					}
				}
			}

			updater.addRoadsToRedrawLowPriority(changed, mainWindow.displayQualityScale);
			updater.createAndShowMapIncrementalUsingEdges(new HashSet<Edge>(edges));

			polygonRoadSnapStart = null;
		}

		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges(false));

		undoer.setUndoPoint(UpdateType.Incremental, this);
	}

	private Set<Edge> filterOutOceanAndCoastEdges(Set<Edge> edges)
	{
		return edges.stream().filter(e -> (e.d0 == null || !e.d0.isWater) && (e.d1 == null || !e.d1.isWater)).collect(Collectors.toSet());
	}

	/**
	 * Returns the river polygon path, trimmed by one Corner at the start and/or end if the snap-bridge there would backtrack the path.
	 * Backtracking is detected with a dot product: at the end, when the bridge direction ({@code snap - end}) points opposite to the
	 * path-approach direction ({@code end - secondToLast}), the bridge forms a V — going past {@code end} and curving back to the snap
	 * target. The start mirrors this: when ({@code snap - start}) points opposite to ({@code start - secondFromStart}), the bridge into
	 * {@code start} reverses the path's departure direction. In both cases the offending terminal Corner is dropped so the bridge continues
	 * straight from the next interior Corner.
	 *
	 * <p>
	 * Each side's trim only fires when its snap is present (no snap → no bridge → nothing to overshoot). It can reduce the path all the way
	 * to empty — the caller's empty-path synthesis (1-node river bridged to snap) takes over from there.
	 */
	private TrimmedRiverPath trimRiverPathIfPathOvershootsMouse(Set<Edge> path, Corner start, Corner end, Point snapStartGraphPixels, Point snapEndGraphPixels)
	{
		if (path == null || path.isEmpty())
		{
			return new TrimmedRiverPath(path, start, end);
		}

		Set<Edge> result = path;
		boolean copied = false;

		if (end != null && snapEndGraphPixels != null)
		{
			Edge edgeAtEnd = findRiverEdgeTouching(result, end);
			if (edgeAtEnd != null)
			{
				Corner secondToLast = (edgeAtEnd.v0 == end) ? edgeAtEnd.v1 : edgeAtEnd.v0;
				if (secondToLast != null && bridgeReversesPath(secondToLast.loc, end.loc, snapEndGraphPixels))
				{
					if (!copied)
					{
						result = new HashSet<>(result);
						copied = true;
					}
					result.remove(edgeAtEnd);
					end = secondToLast;
				}
			}
		}

		if (start != null && snapStartGraphPixels != null && !result.isEmpty())
		{
			Edge edgeAtStart = findRiverEdgeTouching(result, start);
			if (edgeAtStart != null)
			{
				Corner secondFromStart = (edgeAtStart.v0 == start) ? edgeAtStart.v1 : edgeAtStart.v0;
				if (secondFromStart != null && bridgeReversesPath(secondFromStart.loc, start.loc, snapStartGraphPixels))
				{
					if (!copied)
					{
						result = new HashSet<>(result);
						copied = true;
					}
					result.remove(edgeAtStart);
					start = secondFromStart;
				}
			}
		}

		return new TrimmedRiverPath(result, start, end);
	}

	private static Edge findRiverEdgeTouching(Set<Edge> path, Corner corner)
	{
		for (Edge edge : path)
		{
			if (edge.v0 == corner || edge.v1 == corner)
			{
				return edge;
			}
		}
		return null;
	}

	private record TrimmedRiverPath(Set<Edge> path, Corner start, Corner end)
	{
	}

	/** Road counterpart of {@link #trimRiverPathIfPathOvershootsMouse}. See that method for the rationale. */
	private TrimmedRoadPath trimRoadPathIfPathOvershootsMouse(List<Edge> path, Center start, Center end, Point snapStartGraphPixels, Point snapEndGraphPixels)
	{
		if (path == null || path.isEmpty())
		{
			return new TrimmedRoadPath(path, start, end);
		}

		List<Edge> result = path;
		boolean copied = false;

		// findShortestPath builds the list by walking back from end, so result.get(0) is the edge touching end
		// and result.get(size - 1) is the edge touching start.
		if (end != null && snapEndGraphPixels != null)
		{
			Edge edgeAtEnd = result.get(0);
			if (edgeAtEnd.d0 == end || edgeAtEnd.d1 == end)
			{
				Center secondToLast = (edgeAtEnd.d0 == end) ? edgeAtEnd.d1 : edgeAtEnd.d0;
				if (secondToLast != null && bridgeReversesPath(secondToLast.loc, end.loc, snapEndGraphPixels))
				{
					if (!copied)
					{
						result = new ArrayList<>(result);
						copied = true;
					}
					result.remove(0);
					end = secondToLast;
				}
			}
		}

		if (start != null && snapStartGraphPixels != null && !result.isEmpty())
		{
			Edge edgeAtStart = result.get(result.size() - 1);
			if (edgeAtStart.d0 == start || edgeAtStart.d1 == start)
			{
				Center secondFromStart = (edgeAtStart.d0 == start) ? edgeAtStart.d1 : edgeAtStart.d0;
				if (secondFromStart != null && bridgeReversesPath(secondFromStart.loc, start.loc, snapStartGraphPixels))
				{
					if (!copied)
					{
						result = new ArrayList<>(result);
						copied = true;
					}
					result.remove(result.size() - 1);
					start = secondFromStart;
				}
			}
		}

		return new TrimmedRoadPath(result, start, end);
	}

	private record TrimmedRoadPath(List<Edge> path, Center start, Center end)
	{
	}

	/**
	 * Dot product check for whether a snap-bridge at a terminal Corner reverses the path. {@code interior} is the path's neighbor of the
	 * terminal, {@code terminal} is the path's start or end Corner, and {@code snap} is the snap-bridge target. Returns true when
	 * {@code (terminal - interior) · (snap - terminal) < 0}, i.e. the bridge points back along the path.
	 */
	private static boolean bridgeReversesPath(Point interior, Point terminal, Point snap)
	{
		double approachX = terminal.x - interior.x;
		double approachY = terminal.y - interior.y;
		double bridgeX = snap.x - terminal.x;
		double bridgeY = snap.y - terminal.y;
		return approachX * bridgeX + approachY * bridgeY < 0;
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		highlightHoverCentersOrEdgesAndBrush(e.getPoint(), SwingHelper.isCommandKeyDown(e));
	}

	protected void highlightHoverCentersOrEdgesAndBrush(java.awt.Point mouseLocation)
	{
		highlightHoverCentersOrEdgesAndBrush(mouseLocation, false);
	}

	protected void highlightHoverCentersOrEdgesAndBrush(java.awt.Point mouseLocation, boolean ctrlDown)
	{
		if (mouseLocation == null)
		{
			return;
		}

		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.clearHighlightedPolylines();
		mapEditingPanel.clearHoverPolylines();
		mapEditingPanel.hideBrush();

		boolean isSelectingColorFromMap = selectColorFromMapButton.isVisible() && selectColorFromMapButton.isSelected();
		if (isSelectingColorFromMap || mergeRegionsButton.isSelected() || fillRegionColorButton.isSelected())
		{
			if (updater.mapParts == null || updater.mapParts.graph == null)
			{
				assert false;
				return;
			}
			Center center = updater.mapParts.graph.findClosestCenter(getPointOnGraph(mouseLocation), true);
			if (center != null)
			{
				if (center.region != null)
				{
					mapEditingPanel.addHighlightedCenters(center.region.getCenters());
				}
				mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineGroup);
			}
		}
		else if (oceanButton.isSelected() || lakesButton.isSelected() || landButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(mouseLocation);

			if (DebugFlags.printCenterIndexes())
			{
				System.out.println("Highlighted center indexes:");
				for (Center center : selected)
				{
					System.out.println(center.index);
				}
			}

			mapEditingPanel.addHighlightedCenters(selected);
			mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
		}
		else if (riversButton.isSelected() && modeWidget.isEraseMode())
		{
			int brushDiameter = brushSizes.get(brushSizeComboBox.getSelectedIndex());
			if (brushDiameter > 1)
			{
				mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			}
			List<List<Point>> riverSegments = getSelectedRiverSegments(mouseLocation);
			for (List<Point> segment : scalePoints(riverSegments, mainWindow.displayQualityScale))
			{
				mapEditingPanel.addPolylinesToHighlight(segment);
			}
		}
		else if (riversButton.isSelected() && modeWidget.isDrawMode())
		{
			updateControlPointDisplay(mouseLocation, LineType.RIVER);
		}
		else if ((riversButton.isSelected() || roadsButton.isSelected()) && modeWidget.isEditMode())
		{
			LineType activeType = riversButton.isSelected() ? LineType.RIVER : LineType.ROAD;
			int brushDiameter = getEditBrushDiameter();
			if (brushDiameter > 1)
			{
				mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			}
			updateEditModeHoverDisplay(mouseLocation, activeType, ctrlDown);
			// Re-apply the selected-segments polyline highlight after the polyline clear above so it persists across mouse moves.
			applySelectedSegmentsHighlight();
		}
		else if (roadsButton.isSelected() && modeWidget.isEraseMode())
		{
			int brushDiameter = brushSizes.get(brushSizeComboBox.getSelectedIndex());
			if (brushDiameter > 1)
			{
				mapEditingPanel.showBrush(mouseLocation, brushDiameter);
			}

			List<List<Point>> roadSegments = getSelectedRoadSegments(mouseLocation);
			for (List<Point> list : scalePoints(roadSegments, mainWindow.displayQualityScale))
			{
				mapEditingPanel.addPolylinesToHighlight(list);
			}
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode())
		{
			updateControlPointDisplay(mouseLocation, LineType.ROAD);
		}

		mapEditingPanel.repaint();
	}

	private List<List<Point>> scalePoints(List<List<Point>> points, double scale)
	{
		List<List<Point>> scaledPoints = new ArrayList<>();

		for (List<Point> pointList : points)
		{
			List<Point> scaledPointList = new ArrayList<>();
			for (Point point : pointList)
			{
				Point scaledPoint = new Point(point.x * scale, point.y * scale);
				scaledPointList.add(scaledPoint);
			}
			scaledPoints.add(scaledPointList);
		}

		return scaledPoints;
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (dragInProgress && modeWidget.isEditMode() && (riversButton.isSelected() || roadsButton.isSelected()))
		{
			handleEditModeControlPointDrag(e);
		}
		else if (riversButton.isSelected() && modeWidget.isDrawMode() && !isFreeHandRiverDrawMode())
		{
			previewPolygonRiverPath(e);
		}
		else if (riversButton.isSelected() && modeWidget.isDrawMode() && isFreeHandRiverDrawMode())
		{
			previewFreeHandControlPoints(e, LineType.RIVER);
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && isFreeHandDrawMode())
		{
			previewFreeHandControlPoints(e, LineType.ROAD);
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode())
		{
			previewPolygonRoadPath(e);
		}
		else
		{
			handleSharedPressAndDragActions(e, true);
		}
	}

	/**
	 * Previews the Voronoi-edge river path from {@link #riverStart} to the cursor while the user drags in polygon (non-freehand) river draw
	 * mode, including any snap-back bridges that will be added when the mouse is released.
	 */
	private void previewPolygonRiverPath(MouseEvent e)
	{
		if (riverStart == null)
		{
			return;
		}
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.clearHighlightedPolylines();
		Corner end = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
		Set<Edge> river = filterOutOceanAndCoastEdges(updater.mapParts.graph.findPathGreedy(riverStart, end));
		// Preview the snap-back connection that will be added when the mouse is released, so the
		// user isn't surprised by the gap between the polygon path and the freehand control point
		// they clicked on (or the one they're hovering near at the other end).
		Point currentEndSnapPoint = computeSnapPointForType(e.getPoint(), LineType.RIVER);
		Point snapEndGraphPixels = currentEndSnapPoint == null ? null : currentEndSnapPoint.mult(mainWindow.displayQualityScale);
		Point snapStartGraphPixels = polygonRiverSnapStart == null ? null : polygonRiverSnapStart.mult(mainWindow.displayQualityScale);
		TrimmedRiverPath trimmed = trimRiverPathIfPathOvershootsMouse(river, riverStart, end, snapStartGraphPixels, snapEndGraphPixels);
		river = trimmed.path();
		Corner start = trimmed.start();
		end = trimmed.end();
		mapEditingPanel.addHighlightedEdges(river, EdgeType.Voronoi);
		Point riverStartRI = start == null ? null : start.loc.mult(1.0 / mainWindow.displayQualityScale);
		Point endRI = end == null ? null : end.loc.mult(1.0 / mainWindow.displayQualityScale);
		if (polygonRiverSnapStart != null && start != null && !polygonRiverSnapStart.isCloseEnough(riverStartRI))
		{
			mapEditingPanel.addPolylinesToHighlight(List.of(polygonRiverSnapStart.mult(mainWindow.displayQualityScale), start.loc));
		}
		if (currentEndSnapPoint != null && endRI != null && !currentEndSnapPoint.isCloseEnough(endRI))
		{
			mapEditingPanel.addPolylinesToHighlight(List.of(end.loc, currentEndSnapPoint.mult(mainWindow.displayQualityScale)));
		}
		updateControlPointDisplay(e.getPoint(), LineType.RIVER);
		mapEditingPanel.repaint();
	}

	/**
	 * Updates the control-point snap display and in-progress preview while the user drags in freehand draw mode for the given line type.
	 */
	private void previewFreeHandControlPoints(MouseEvent e, LineType type)
	{
		updateControlPointDisplay(e.getPoint(), type);
		mapEditingPanel.repaint();
	}

	/**
	 * Previews the Delaunay road path from {@link #roadStart} to the cursor while the user drags in polygon (non-freehand) road draw mode,
	 * including any snap-back bridges that will be added when the mouse is released.
	 */
	private void previewPolygonRoadPath(MouseEvent e)
	{
		if (roadStart == null)
		{
			return;
		}
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.clearHighlightedPolylines();
		Center end = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
		List<Edge> edges = updater.mapParts.graph.findShortestPath(roadStart, end, (ignored1, ignored2, distance) -> distance);
		Point currentEndSnapPoint = computeSnapPointForType(e.getPoint(), LineType.ROAD);
		Point snapEndGraphPixels = currentEndSnapPoint == null ? null : currentEndSnapPoint.mult(mainWindow.displayQualityScale);
		Point snapStartGraphPixels = polygonRoadSnapStart == null ? null : polygonRoadSnapStart.mult(mainWindow.displayQualityScale);
		TrimmedRoadPath trimmed = trimRoadPathIfPathOvershootsMouse(edges, roadStart, end, snapStartGraphPixels, snapEndGraphPixels);
		edges = trimmed.path();
		Center start = trimmed.start();
		end = trimmed.end();
		Point roadStartRI = start.loc.mult(1.0 / mainWindow.displayQualityScale);
		Point endRI = end.loc.mult(1.0 / mainWindow.displayQualityScale);
		boolean snapStartActive = polygonRoadSnapStart != null && !polygonRoadSnapStart.isCloseEnough(roadStartRI);
		boolean snapEndActive = currentEndSnapPoint != null && !currentEndSnapPoint.isCloseEnough(endRI);
		// Show the full Delaunay path AND any snap bridges. The actual road drawn at release keeps every Center on the path
		// (the bridges are added as extra nodes BEYOND roadStart/end, not as replacements for them), so the preview must do
		// the same — otherwise the preview hides Center crossings the user will actually see in the drawn road.
		if (edges != null && !edges.isEmpty())
		{
			mapEditingPanel.addHighlightedEdges(edges, EdgeType.Delaunay);
			if (snapEndActive)
			{
				mapEditingPanel.addPolylinesToHighlight(List.of(end.loc, currentEndSnapPoint.mult(mainWindow.displayQualityScale)));
			}
			if (snapStartActive)
			{
				mapEditingPanel.addPolylinesToHighlight(List.of(polygonRoadSnapStart.mult(mainWindow.displayQualityScale), start.loc));
			}
		}
		else
		{
			// Empty-path case (roadStart == end): the release branch synthesizes a 1-node road and bridges to the snap point,
			// so the preview should show that same bridge instead of an empty highlight.
			mapEditingPanel.addHighlightedEdges(edges, EdgeType.Delaunay);
			if (snapEndActive)
			{
				mapEditingPanel.addPolylinesToHighlight(List.of(start.loc, currentEndSnapPoint.mult(mainWindow.displayQualityScale)));
			}
			if (snapStartActive)
			{
				mapEditingPanel.addPolylinesToHighlight(List.of(polygonRoadSnapStart.mult(mainWindow.displayQualityScale), start.loc));
			}
		}
		updateControlPointDisplay(e.getPoint(), LineType.ROAD);
		mapEditingPanel.repaint();
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		if ((riversButton.isSelected() || roadsButton.isSelected()) && modeWidget.isEraseMode())
		{
			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.clearHighlightedPolylines();
		}
		if (roadsButton.isSelected() && modeWidget.isDrawMode())
		{
			freeHandRoadSnapPoint = null;
			mapEditingPanel.clearHoveredControlPoint();
			// Keep circles and preview path visible so the user can see them even when the mouse exits.
		}
		if (riversButton.isSelected() && modeWidget.isDrawMode() && isFreeHandRiverDrawMode())
		{
			freeHandRiverSnapPoint = null;
			mapEditingPanel.clearHoveredControlPoint();
		}
		if ((riversButton.isSelected() || roadsButton.isSelected()) && modeWidget.isEditMode())
		{
			// Clear hover state and all the brush-hover preview visuals (the hover ring, the orange would-be-selected CP
			// circles, and the orange hover polylines), but keep the sticky-line CPs and segment highlight visible so the
			// user can see what they had selected even with the cursor off the map. With a wide brush, an edge river can be
			// under the brush as the cursor leaves the map, so these previews must be cleared or they stick on screen.
			clearHoverState();
			mapEditingPanel.clearHoveredControlPoint();
			mapEditingPanel.clearRoadControlPointCircles();
			mapEditingPanel.clearHoverPolylines();
		}
		mapEditingPanel.hideBrush();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onAfterShowMap()
	{
		java.awt.Point mousePosition = mapEditingPanel.getMousePosition();
		updateHighlightsForMousePosition(mousePosition);
	}

	@Override
	public void onSwitchingTo()
	{
		super.onSwitchingTo();
		updater.doWhenMapIsReadyForInteractions(() ->
		{
			if (isSelected())
			{
				updateHighlightsForMousePosition(mapEditingPanel.getMousePosition());
			}
		});
	}

	private void updateHighlightsForMousePosition(java.awt.Point mousePosition)
	{
		if (mousePosition == null)
		{
			return;
		}

		if (roadsButton.isSelected() && modeWidget.isDrawMode())
		{
			updateControlPointDisplay(mousePosition, LineType.ROAD);
			mapEditingPanel.repaint();
		}
		else
		{
			highlightHoverCentersOrEdgesAndBrush(mousePosition);
		}
	}


	@Override
	public void onSwitchingAway()
	{
		cancelFreeHandDrawing(LineType.ROAD);
		cancelFreeHandDrawing(LineType.RIVER);
		clearSelection();
		clearHoverState();
		if (selectedRegion != null)
		{
			selectedRegion = null;
			mapEditingPanel.clearSelectedCenters();
		}
	}

	@Override
	protected void onAfterUndoRedo()
	{
		cancelFreeHandDrawing(LineType.ROAD);
		cancelFreeHandDrawing(LineType.RIVER);
		// Sticky/hover may reference river/road instances that no longer exist after undo/redo.
		clearSelection();
		clearHoverState();
		selectedRegion = null;
		mapEditingPanel.clearSelectedCenters();
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearHighlightedPolylines();
		mapEditingPanel.repaint();
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean refreshImagePreviews)
	{
		areRegionColorsVisible = settings.drawRegionColors;
		areRegionBoundariesVisible = settings.drawRegionBoundaries;
		areRoadsVisible = settings.drawRoads;

		// These settings are part of MapSettings, so they get pulled in by undo/redo, but I exclude them here
		// because it feels weird to me to have them change with undo/redo since they don't directly affect the map.
		if (!isUndoRedoOrAutomaticChange)
		{
			baseColorPanel.setBackground(AwtBridge.toAwtColor(settings.regionBaseColor));
			hueSlider.setValue(settings.hueRange);
			saturationSlider.setValue(settings.saturationRange);
			brightnessSlider.setValue(settings.brightnessRange);

			// I'm setting this color here because I only want it to change when you create new settings or load settings from a file,
			// not on undo/redo or in response to the ThemePanel changing.
			colorDisplay.setBackground(AwtBridge.toAwtColor(settings.regionBaseColor));
		}

		// Clear any selection
		selectedRegion = null;
		mapEditingPanel.clearSelectedCenters();

		showOrHideBrushOptions();
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.regionBaseColor = AwtBridge.fromAwtColor(baseColorPanel.getBackground());
		settings.hueRange = hueSlider.getValue();
		settings.saturationRange = saturationSlider.getValue();
		settings.brightnessRange = brightnessSlider.getValue();
	}

	@Override
	public void handleEnablingAndDisabling(MapSettings settings)
	{
		// There's nothing to do because this tool never disables anything.
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
	}

	@Override
	protected void onBeforeUndoRedo()
	{
		cancelFreeHandDrawing(LineType.ROAD);
	}
}
