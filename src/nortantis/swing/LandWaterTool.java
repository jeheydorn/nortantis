package nortantis.swing;

import nortantis.*;
import nortantis.editor.*;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.GeometryHelper;
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
	private List<Point> freeHandPathRI = null;
	private Point freeHandSnapPoint = null;
	private Point polygonSnapStart = null;


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
		return "(Alt+Z)";
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
			case "de" -> -3;
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
					brushSizeHider.setVisible(oceanButton.isSelected() || lakesButton.isSelected() || landButton.isSelected() || (riversButton.isSelected() && modeWidget.isEraseMode())
							|| (roadsButton.isSelected() && modeWidget.isEraseMode()));
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

		// Create new region button
		{
			newRegionButton = new JToggleButton(Translation.get("landWaterTool.createNewRegion"));
			newRegionButton.addActionListener(e -> updateColorControlVisibility());
			newRegionButtonHider = organizer.addLabelAndComponent("", Translation.get("landWaterTool.createNewRegion.help"), newRegionButton);
		}

		// River options
		{
			modeWidget = new DrawModeWidget(Translation.get("landWaterTool.drawRivers"), Translation.get("landWaterTool.eraseRivers"), false, "", false, "",
					() -> brushActionListener.actionPerformed(null));
			modeHider = modeWidget.addToOrganizer(organizer, Translation.get("landWaterTool.riverMode.help"));

			riverWidthSlider = new JSlider(1, 15);
			final int initialValue = 1;
			riverWidthSlider.setValue(initialValue);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(riverWidthSlider);
			riverOptionHider = sliderWithDisplay.addToOrganizer(organizer, Translation.get("landWaterTool.riverWidth.label"), Translation.get("landWaterTool.riverWidth.help"));
		}

		// Road draw style (polygon vs. free-hand)
		{
			polygonDrawStyleButton = new JToggleButton(Translation.get("landWaterTool.roadStyle.polygon"));
			polygonDrawStyleButton.setSelected(true);
			polygonDrawStyleButton.addActionListener(e -> brushActionListener.actionPerformed(null));
			freeHandDrawStyleButton = new JToggleButton(Translation.get("landWaterTool.roadStyle.freeHand"));
			freeHandDrawStyleButton.addActionListener(e ->
			{
				cancelFreeHandDrawing();
				brushActionListener.actionPerformed(null);
			});
			SegmentedButtonWidget drawStyleWidget = new SegmentedButtonWidget(List.of(polygonDrawStyleButton, freeHandDrawStyleButton));
			drawStyleHider = drawStyleWidget.addToOrganizer(organizer, Translation.get("landWaterTool.roadStyle.label"),
					Translation.get("landWaterTool.roadStyle.help"));

			// Register Escape (cancel) and Enter (commit) key bindings for free-hand drawing
			mapEditingPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelFreeHandRoad");
			mapEditingPanel.getActionMap().put("cancelFreeHandRoad", new AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					cancelFreeHandDrawing();
				}
			});
			mapEditingPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commitFreeHandRoad");
			mapEditingPanel.getActionMap().put("commitFreeHandRoad", new AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					finalizeFreeHandRoad();
				}
			});
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

		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		onlyUpdateLandCheckbox = new JCheckBox(Translation.get("landWaterTool.onlyUpdateExistingLand"));
		onlyUpdateLandCheckbox.setToolTipText(Translation.get("landWaterTool.onlyUpdateExistingLand.tooltip"));
		onlyUpdateLandCheckboxHider = organizer.addLabelAndComponent("", "", onlyUpdateLandCheckbox);

		colorGeneratorSettingsHider = organizer.addLeftAlignedComponent(createColorGeneratorOptionsPanel(toolOptionsPanel));

		showOrHideBrushOptions();

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.66);
		organizer.addVerticalFillerRow();
		return toolOptionsPanel;
	}

	private void showOrHideRoadAndRiverOptions()
	{
		modeHider.setVisible(riversButton.isSelected() || roadsButton.isSelected());
		riverOptionHider.setVisible(riversButton.isSelected() && modeWidget.isDrawMode());
		drawStyleHider.setVisible(roadsButton.isSelected() && modeWidget.isDrawMode());
		if (!roadsButton.isSelected() || !modeWidget.isDrawMode())
		{
			cancelFreeHandDrawing();
			clearRoadControlPointDisplay();
			mapEditingPanel.repaint();
		}
	}

	private boolean isFreeHandDrawMode()
	{
		return freeHandDrawStyleButton != null && freeHandDrawStyleButton.isSelected();
	}

	private double getSnapRadiusRI()
	{
		// Fixed map-space radius (10 graph pixels), so snap circles scale with zoom just like the road control-point circles.
		return 10.0 / mainWindow.displayQualityScale;
	}

	/**
	 * Finds the road control point nearest to the given screen position, within the snap radius. Returns it in RI coordinates, or null.
	 */
	private Point computeSnapPoint(java.awt.Point mouseLocation)
	{
		if (updater.mapParts == null || mainWindow.edits.roads.isEmpty())
		{
			return null;
		}
		Point mouseRI = getPointOnGraph(mouseLocation).mult(1.0 / mainWindow.displayQualityScale);
		double snapRadius = getSnapRadiusRI();
		Point nearest = null;
		double minDist = snapRadius;
		for (Road road : mainWindow.edits.roads)
		{
			for (Point riPt : road.path)
			{
				double d = riPt.distanceTo(mouseRI);
				if (d < minDist)
				{
					minDist = d;
					nearest = riPt;
				}
			}
		}
		return nearest;
	}

	/**
	 * Updates the road control point display (orange/yellow snap circles) and the free-hand in-progress preview. Also sets freeHandSnapPoint.
	 */
	private void updateRoadControlPointDisplay(java.awt.Point mouseLocation)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null)
		{
			return;
		}

		freeHandSnapPoint = computeSnapPoint(mouseLocation);

		List<Point> circlesGraphPx = new ArrayList<>();
		for (Road road : mainWindow.edits.roads)
		{
			for (Point riPt : road.path)
			{
				circlesGraphPx.add(riPt.mult(mainWindow.displayQualityScale));
			}
		}
		mapEditingPanel.setRoadControlPointCircles(circlesGraphPx);

		if (freeHandSnapPoint != null)
		{
			mapEditingPanel.setHoveredRoadControlPoint(freeHandSnapPoint.mult(mainWindow.displayQualityScale));
		}
		else
		{
			mapEditingPanel.clearHoveredRoadControlPoint();
		}

		if (isFreeHandDrawMode() && freeHandPathRI != null)
		{
			Point currentRI = freeHandSnapPoint != null ? freeHandSnapPoint
					: getPointOnGraph(mouseLocation).mult(1.0 / mainWindow.displayQualityScale);
			List<Point> previewGraphPx = new ArrayList<>();
			for (Point riPt : freeHandPathRI)
			{
				previewGraphPx.add(riPt.mult(mainWindow.displayQualityScale));
			}
			previewGraphPx.add(currentRI.mult(mainWindow.displayQualityScale));
			mapEditingPanel.setFreeHandPreviewPath(previewGraphPx);
		}
	}

	private void clearRoadControlPointDisplay()
	{
		freeHandSnapPoint = null;
		mapEditingPanel.clearRoadControlPointCircles();
		mapEditingPanel.clearHoveredRoadControlPoint();
		mapEditingPanel.clearFreeHandPreviewPath();
	}

	private void cancelFreeHandDrawing()
	{
		if (freeHandPathRI == null)
		{
			return;
		}
		freeHandPathRI = null;
		polygonSnapStart = null;
		clearRoadControlPointDisplay();
		mapEditingPanel.repaint();
	}

	private void finalizeFreeHandRoad()
	{
		if (freeHandPathRI == null || freeHandPathRI.size() < 2)
		{
			freeHandPathRI = null;
			clearRoadControlPointDisplay();
			return;
		}

		List<Point> pathToCommit = freeHandPathRI;
		freeHandPathRI = null;
		clearRoadControlPointDisplay();
		mapEditingPanel.repaint();

		Road changed = RoadDrawer.addFreeHandRoadFromPoints(pathToCommit, mainWindow.edits.roads);
		RoadDrawer.removeEmptyOrSinglePointRoads(mainWindow.edits.roads);

		List<Road> changedList = changed != null ? List.of(changed) : Collections.emptyList();
		updater.addRoadsToRedrawLowPriority(changedList, mainWindow.displayQualityScale);

		List<List<Point>> pathsForCenters = changedList.stream().map(r -> (List<Point>) r.path).collect(Collectors.toList());
		updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingRoadPoints(pathsForCenters));

		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges());
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

		brushTypeWidget.updateSegmentPositions();

		brushActionListener.actionPerformed(null);

		showOrHideNewRegionButton();
	}

	private void showOrHideNewRegionButton()
	{
		newRegionButtonHider.setVisible((areRegionBoundariesVisible || areRegionColorsVisible) && landButton.isSelected());
	}

	private void updateColorControlVisibility()
	{
		boolean showColorControls = (newRegionButton.isSelected() && areRegionColorsVisible) || fillRegionColorButton.isSelected();
		colorChooserHider.setVisible(showColorControls);
		selectColorHider.setVisible(showColorControls);
		generateColorButtonHider.setVisible(showColorControls);
		colorGeneratorSettingsHider.setVisible(showColorControls);
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
		// Free-hand control-point placement and double-click finalization are handled in handleMousePressedOnMap
		// so that moving the mouse between press and release still registers the action.
	}

	private Integer regionIdToExpand;

	private void handleMousePressOrDrag(MouseEvent e, boolean isMouseDrag)
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
				for (Edge edge : center.borders)
				{
					EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
					if (eEdit != null && eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
					{
						eEdit.riverLevel = 0;
					}
				}
				hasChange |= !edit.isWater;
				hasChange |= edit.isLake != lakesButton.isSelected();
				// Note that I'm nulling out trees in the assignment below because any trees that failed to draw previously should be
				// cleared out when the Center becomes water.
				mainWindow.edits.centerEdits.put(edit.index, new CenterEdit(edit.index, true, lakesButton.isSelected(), null, edit.icon, null));
			}
			if (hasChange)
			{
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
					Color color = areRegionColorsVisible ? colorDisplay.getBackground() : mainWindow.getLandColor();
					regionIdToExpand = createNewRegion(color);
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
				Integer newRegionId = getOrCreateRegionIdForEdit(center, mainWindow.getLandColor());
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
				// When deleting rivers with the single-point brush size,
				// highlight the closest edge instead of a polygon.
				Set<Edge> possibleRivers = getSelectedEdges(e.getPoint(), brushSizes.get(brushSizeComboBox.getSelectedIndex()), EdgeType.Voronoi);
				Set<Edge> changed = new HashSet<>();
				for (Edge edge : possibleRivers)
				{
					EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
					if (eEdit != null && eEdit.riverLevel > 0)
					{
						eEdit.riverLevel = 0;
						changed.add(edge);
					}
				}
				mapEditingPanel.clearHighlightedEdges();
				updater.createAndShowMapIncrementalUsingEdges(changed);
			}
		}
		else if (roadsButton.isSelected())
		{
			if (modeWidget.isEraseMode())
			{
				List<List<Point>> roadSegmentsToRemove = getSelectedRoadSegments(e.getPoint());
				List<Road> changed = removeSegmentsAndSplitRoads(mainWindow.edits.roads, roadSegmentsToRemove);
				RoadDrawer.removeEmptyOrSinglePointRoads(mainWindow.edits.roads);
				mapEditingPanel.clearHighlightedPolylines();
				updater.createAndShowMapIncrementalUsingCenters(getCentersTouchingRoadPoints(roadSegmentsToRemove));
				updater.addRoadsToRedrawLowPriority(changed, mainWindow.displayQualityScale);
			}
		}
	}

	private Set<Center> getCentersTouchingRoadPoints(List<List<Point>> pointLists)
	{
		if (updater.mapParts == null || updater.mapParts.graph == null)
		{
			assert false;
			return Collections.emptySet();
		}

		Set<Center> result = new HashSet<>();
		for (List<Point> points : pointLists)
		{
			for (Point point : points)
			{
				Center c = updater.mapParts.graph.findClosestCenter(point.mult(mainWindow.displayQualityScale), true);
				if (c != null)
				{
					result.add(c);
				}
			}
		}
		return result;
	}

	/**
	 * Removes the given set of roadPointsToRemove from the roads in roadList.
	 * 
	 * @return Roads that have changed.
	 */
	public static List<Road> removeSegmentsAndSplitRoads(List<Road> roadList, List<List<Point>> segmentsToRemove)
	{
		List<Road> changed = new ArrayList<>();
		List<Road> newRoads = new ArrayList<>();
		Set<Point> pointsFromSegmentsToRemove = roadsToPoints(segmentsToRemove);

		for (Road road : roadList)
		{
			List<Point> path = road.path;
			List<List<Point>> splitPaths = new ArrayList<>();
			List<Point> currentPath = new ArrayList<>();
			boolean roadChanged = false;

			for (int i = 0; i < path.size(); i++)
			{
				Point point = path.get(i);
				currentPath.add(point);
				if (pointsFromSegmentsToRemove.contains(point))
				{
					roadChanged = true;
					if (!currentPath.isEmpty())
					{
						if (currentPath.size() > 1)
						{
							splitPaths.add(new ArrayList<>(currentPath));
						}
						currentPath.clear();

						if (i + 1 < path.size() && !pointsFromSegmentsToRemove.contains(path.get(i + 1)))
						{
							currentPath.add(point);
						}
					}
				}
			}
			if (currentPath.size() > 1)
			{
				splitPaths.add(currentPath);
			}

			for (List<Point> splitPath : splitPaths)
			{
				if (splitPath.size() > 1)
				{
					newRoads.add(new Road(splitPath));
				}
			}

			if (roadChanged)
			{
				changed.add(road);
			}
		}

		roadList.clear();
		roadList.addAll(newRoads);

		return changed;
	}

	private static Set<Point> roadsToPoints(List<List<Point>> roads)
	{
		Set<Point> result = new HashSet<>();
		for (List<Point> road : roads)
		{
			for (Point p : road)
			{
				result.add(p);
			}
		}
		return result;
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
		int mi = 0;
		List<Point> segmentFoundIn = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		List<List<Point>> withinRadius = findRoadSegmentsWithinRadius(targetPoint, radius);
		for (List<Point> segment : withinRadius)
		{
			if (segment.size() < 2)
			{
				continue;
			}
			for (int i = 0; i < segment.size(); i++)
			{
				double d = segment.get(i).distanceTo(targetPoint);
				if (d < closestDistance)
				{
					closestDistance = d;
					segmentFoundIn = segment;
					if (i == segment.size() - 1)
					{
						// Store the second to last index so that at the end of this method we can return the mi'th point and the one after
						// it.
						mi = i - 1;
					}
					else
					{
						mi = i;
					}
				}
			}
		}

		if (closestDistance == Double.POSITIVE_INFINITY || segmentFoundIn == null)
		{
			return Collections.emptyList();
		}

		return Arrays.asList(segmentFoundIn.get(mi), segmentFoundIn.get(mi + 1));
	}

	private List<List<Point>> findRoadSegmentsWithinRadius(Point targetPoint, double radius)
	{
		List<List<Point>> result = new ArrayList<>();
		for (Road road : mainWindow.edits.roads)
		{
			if (road.path.size() < 2)
			{
				continue;
			}

			List<Point> soFar = new ArrayList<>();
			for (int i = 0; i < road.path.size() - 1; i++)
			{
				Point p1 = road.path.get(i);
				Point p2 = road.path.get(i + 1);
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
						soFar = new ArrayList<Point>();
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
		handleMousePressOrDrag(e, false);

		if (riversButton.isSelected() && modeWidget.isDrawMode())
		{
			riverStart = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && isFreeHandDrawMode() && SwingUtilities.isRightMouseButton(e) && freeHandPathRI != null)
		{
			// Remove the last control point (like Krita), keeping at least the first point.
			if (freeHandPathRI.size() > 1)
			{
				freeHandPathRI.remove(freeHandPathRI.size() - 1);
			}
			updateRoadControlPointDisplay(e.getPoint());
			mapEditingPanel.repaint();
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && isFreeHandDrawMode() && SwingUtilities.isLeftMouseButton(e)
				&& updater.mapParts != null && updater.mapParts.graph != null)
		{
			// Use press (not click) so moving the mouse between press and release still registers the control point.
			Point riPoint = freeHandSnapPoint != null ? freeHandSnapPoint
					: getPointOnGraph(e.getPoint()).mult(1.0 / mainWindow.displayQualityScale);
			if (e.getClickCount() == 1)
			{
				if (freeHandPathRI == null)
				{
					freeHandPathRI = new ArrayList<>();
				}
				freeHandPathRI.add(riPoint);
				updateRoadControlPointDisplay(e.getPoint());
				mapEditingPanel.repaint();
			}
			else if (e.getClickCount() == 2)
			{
				// Double-click: the first press already added the final point; finalize now.
				finalizeFreeHandRoad();
			}
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && !isFreeHandDrawMode())
		{
			polygonSnapStart = computeSnapPoint(e.getPoint());
			roadStart = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
		}
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		regionIdToExpand = null;

		if (riversButton.isSelected() && modeWidget.isDrawMode() && riverStart != null)
		{
			Corner end = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
			Set<Edge> river = filterOutOceanAndCoastEdges(updater.mapParts.graph.findPathGreedy(riverStart, end));
			for (Edge edge : river)
			{
				int base = (riverWidthSlider.getValue() - 1);
				int riverLevel = (base * base * 2) + VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn + 1;
				if (mainWindow.edits.edgeEdits.containsKey(edge.index))
				{
					mainWindow.edits.edgeEdits.get(edge.index).riverLevel = riverLevel;
				}
				else
				{
					mainWindow.edits.edgeEdits.put(edge.index, new EdgeEdit(edge.index, riverLevel));
				}

			}
			riverStart = null;
			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.repaint();

			if (DebugFlags.printRiverEdgeIndexes())
			{
				System.out.println("River edge indexes:");
				for (Edge edge : river)
				{
					System.out.println(edge.index);
				}
			}

			if (river.size() > 0)
			{
				updater.createAndShowMapIncrementalUsingEdges(river);
			}
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && !isFreeHandDrawMode() && roadStart != null)
		{
			Point polygonSnapEnd = computeSnapPoint(e.getPoint());
			Center end = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
			List<Edge> edges = updater.mapParts.graph.findShortestPath(roadStart, end, (ignored1, ignored2, distance) -> distance);

			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.clearHighlightedPolylines();
			clearRoadControlPointDisplay();
			mapEditingPanel.repaint();

			List<Road> changed = RoadDrawer.addRoadsFromEdgesInEditor(edges, updater.mapParts.graph, mainWindow.edits.roads,
					mainWindow.displayQualityScale);

			// If snap points are set, extend the road's endpoints to connect to those exact control-point locations.
			// Skip if the snap point already equals the center location (already the natural start/end of the Delaunay road).
			if (polygonSnapStart != null || polygonSnapEnd != null)
			{
				Point roadStartRI = roadStart.loc.mult(1.0 / mainWindow.displayQualityScale);
				Point endRI = end.loc.mult(1.0 / mainWindow.displayQualityScale);
				for (Road road : changed)
				{
					if (road == null || road.path.isEmpty())
					{
						continue;
					}
					Point first = road.path.get(0);
					Point last = road.path.get(road.path.size() - 1);
					if (polygonSnapStart != null && !polygonSnapStart.isCloseEnough(roadStartRI))
					{
						if (first.isCloseEnough(roadStartRI))
						{
							road.path.add(0, polygonSnapStart);
						}
						else if (last.isCloseEnough(roadStartRI))
						{
							road.path.add(polygonSnapStart);
						}
					}
					if (polygonSnapEnd != null && !polygonSnapEnd.isCloseEnough(endRI))
					{
						first = road.path.get(0);
						last = road.path.get(road.path.size() - 1);
						if (last.isCloseEnough(endRI))
						{
							road.path.add(polygonSnapEnd);
						}
						else if (first.isCloseEnough(endRI))
						{
							road.path.add(0, polygonSnapEnd);
						}
					}
				}
			}

			updater.addRoadsToRedrawLowPriority(changed, mainWindow.displayQualityScale);
			updater.createAndShowMapIncrementalUsingEdges(new HashSet<Edge>(edges));

			polygonSnapStart = null;
		}

		updater.doWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges());

		undoer.setUndoPoint(UpdateType.Incremental, this);
	}

	private Set<Edge> filterOutOceanAndCoastEdges(Set<Edge> edges)
	{
		return edges.stream().filter(e -> (e.d0 == null || !e.d0.isWater) && (e.d1 == null || !e.d1.isWater)).collect(Collectors.toSet());
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		highlightHoverCentersOrEdgesAndBrush(e.getPoint());
	}

	protected void highlightHoverCentersOrEdgesAndBrush(java.awt.Point mouseLocation)
	{
		if (mouseLocation == null)
		{
			return;
		}

		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.clearHighlightedPolylines();
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
			Set<Edge> candidates = getSelectedEdges(mouseLocation, brushDiameter, EdgeType.Voronoi);

			for (Edge edge : candidates)
			{
				EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
				if (eEdit != null && eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
				{
					mapEditingPanel.addHighlightedEdge(edge, EdgeType.Voronoi);
				}
			}
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
			updateRoadControlPointDisplay(mouseLocation);
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
		if (riversButton.isSelected() && modeWidget.isDrawMode())
		{
			if (riverStart != null)
			{
				mapEditingPanel.clearHighlightedEdges();
				Corner end = updater.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
				Set<Edge> river = filterOutOceanAndCoastEdges(updater.mapParts.graph.findPathGreedy(riverStart, end));
				mapEditingPanel.addHighlightedEdges(river, EdgeType.Voronoi);
				mapEditingPanel.repaint();
			}
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && isFreeHandDrawMode())
		{
			// Update control point snap display and the in-progress preview while the mouse moves.
			updateRoadControlPointDisplay(e.getPoint());
			mapEditingPanel.repaint();
		}
		else if (roadsButton.isSelected() && modeWidget.isDrawMode())
		{
			if (roadStart != null)
			{
				mapEditingPanel.clearHighlightedEdges();
				Center end = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
				List<Edge> edges = updater.mapParts.graph.findShortestPath(roadStart, end, (ignored1, ignored2, distance) -> distance);
				mapEditingPanel.addHighlightedEdges(edges, EdgeType.Delaunay);
				updateRoadControlPointDisplay(e.getPoint());
				mapEditingPanel.repaint();
			}
		}
		else
		{
			handleMousePressOrDrag(e, true);
		}
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
			freeHandSnapPoint = null;
			mapEditingPanel.clearRoadControlPointCircles();
			mapEditingPanel.clearHoveredRoadControlPoint();
			// Keep the preview path visible so the user can see the in-progress road even when the mouse exits.
		}
		mapEditingPanel.hideBrush();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onAfterShowMap()
	{
		highlightHoverCentersOrEdgesAndBrush(mapEditingPanel.getMousePosition());
	}

	@Override
	public void onSwitchingAway()
	{
		cancelFreeHandDrawing();
		if (selectedRegion != null)
		{
			selectedRegion = null;
			mapEditingPanel.clearSelectedCenters();
		}
	}

	@Override
	protected void onAfterUndoRedo()
	{
		cancelFreeHandDrawing();
		selectedRegion = null;
		mapEditingPanel.clearSelectedCenters();
		mapEditingPanel.clearHighlightedCenters();
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
		cancelFreeHandDrawing();
	}
}
