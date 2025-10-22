package nortantis.swing;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.border.EtchedBorder;

import nortantis.DebugFlags;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.Region;
import nortantis.RoadDrawer;
import nortantis.editor.CenterEdit;
import nortantis.editor.EdgeEdit;
import nortantis.editor.EdgeType;
import nortantis.editor.MapUpdater;
import nortantis.editor.RegionEdit;
import nortantis.editor.Road;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.util.ComparableCounter;
import nortantis.util.Counter;
import nortantis.util.GeometryHelper;
import nortantis.util.Tuple2;

public class LandWaterTool extends EditorTool
{

	private JPanel colorDisplay;
	private RowHider colorChooserHider;

	private JRadioButton landButton;
	private JRadioButton oceanButton;
	private JRadioButton lakesButton;
	private JRadioButton riversButton;
	private RowHider riverOptionHider;
	private JSlider riverWidthSlider;
	private Corner riverStart;
	private Center roadStart;
	private RowHider modeHider;
	private JRadioButton fillRegionColorButton;
	private JRadioButton paintRegionButton;
	private JRadioButton mergeRegionsButton;
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
	private JRadioButton roadsButton;
	static String toolbarName = "Land and Water";
	static String colorGeneratorSettingsName = "Color Generator Settings";

	public LandWaterTool(MainWindow mainWindow, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(mainWindow, toolsPanel, mapUpdater);
	}

	@Override
	public String getToolbarName()
	{
		return toolbarName;
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
	public String getImageIconFilePath()
	{
		return Paths.get(Assets.getAssetsPath(), "internal/Land Water tool.png").toString();
	}

	@Override
	public void onBeforeSaving()
	{
	}

	@Override
	protected JPanel createToolOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

		List<JComponent> radioButtons = new ArrayList<>();
		ButtonGroup group = new ButtonGroup();
		oceanButton = new JRadioButton("Ocean");
		group.add(oceanButton);
		radioButtons.add(oceanButton);
		brushActionListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.clearSelectedCenters();
				if (areRegionColorsVisible)
				{
					boolean isVisible = paintRegionButton.isSelected() || fillRegionColorButton.isSelected();
					colorChooserHider.setVisible(isVisible);
					selectColorHider.setVisible(isVisible);
					generateColorButtonHider.setVisible(isVisible);
					colorGeneratorSettingsHider.setVisible(isVisible);
					onlyUpdateLandCheckboxHider.setVisible(paintRegionButton.isSelected());
				}
				else
				{
					colorChooserHider.setVisible(false);
					selectColorHider.setVisible(false);
					generateColorButtonHider.setVisible(false);
					colorGeneratorSettingsHider.setVisible(false);
					onlyUpdateLandCheckbox.setVisible(landButton.isSelected());
				}

				showOrHideNewRegionButton();
				newRegionButton.setSelected(false);

				if (brushSizeComboBox != null)
				{
					brushSizeHider.setVisible(paintRegionButton.isSelected() || oceanButton.isSelected() || lakesButton.isSelected()
							|| landButton.isSelected() || (riversButton.isSelected() && modeWidget.isEraseMode())
							|| (roadsButton.isSelected() && modeWidget.isEraseMode()));
				}

				showOrHideRoadAndRiverOptions();
			}
		};
		oceanButton.addActionListener(brushActionListener);

		lakesButton = new JRadioButton("Lakes");
		group.add(lakesButton);
		radioButtons.add(lakesButton);
		lakesButton.setToolTipText(
				"Lakes are the same as ocean except ocean effects (waves or shading) along their shores can be disabled, and they don't do coastline smoothing when enabled.");
		lakesButton.addActionListener(brushActionListener);

		riversButton = new JRadioButton("Rivers");
		group.add(riversButton);
		radioButtons.add(riversButton);
		riversButton.addActionListener(brushActionListener);

		paintRegionButton = new JRadioButton("Paint region");
		fillRegionColorButton = new JRadioButton("Fill region color");
		mergeRegionsButton = new JRadioButton("Merge regions");
		landButton = new JRadioButton("Land");
		roadsButton = new JRadioButton("Roads");

		group.add(paintRegionButton);
		radioButtons.add(paintRegionButton);
		paintRegionButton.addActionListener(brushActionListener);

		group.add(fillRegionColorButton);
		radioButtons.add(fillRegionColorButton);
		fillRegionColorButton.addActionListener(brushActionListener);

		group.add(mergeRegionsButton);
		radioButtons.add(mergeRegionsButton);
		mergeRegionsButton.addActionListener(brushActionListener);

		group.add(landButton);
		radioButtons.add(landButton);
		landButton.addActionListener(brushActionListener);

		group.add(roadsButton);
		radioButtons.add(roadsButton);
		roadsButton.addActionListener(brushActionListener);

		oceanButton.setSelected(true); // Selected by default
		organizer.addLabelAndComponentsVertical("Brush:", "", radioButtons);

		// Create new region button
		{
			newRegionButton = new JToggleButton("Create New Political Region");
			newRegionButtonHider = organizer.addLabelAndComponent("",
					"Toggle this to start a new political region with the next brushstroke.", newRegionButton);
		}

		// River options
		{
			modeWidget = new DrawModeWidget("Draw rivers", "Erase rivers", false, "", false, "",
					() -> brushActionListener.actionPerformed(null));
			modeHider = modeWidget.addToOrganizer(organizer, "Whether to draw or erase rivers");

			riverWidthSlider = new JSlider(1, 15);
			final int initialValue = 1;
			riverWidthSlider.setValue(initialValue);
			SwingHelper.setSliderWidthForSidePanel(riverWidthSlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(riverWidthSlider);
			riverOptionHider = sliderWithDisplay.addToOrganizer(organizer, "Width:",
					"River width to draw. Note that different widths might look the same depending on the resolution the map is drawn at.");
		}

		// Color chooser
		colorDisplay = SwingHelper.createColorPickerPreviewPanel();
		colorDisplay.setBackground(Color.black);

		JButton chooseButton = new JButton("Choose");
		chooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				cancelSelectColorFromMap();
				SwingHelper.showColorPickerWithPreviewPanel(toolOptionsPanel, colorDisplay, "Region Color");
			}
		});
		colorChooserHider = organizer.addLabelAndComponentsHorizontal("Color:", "", Arrays.asList(colorDisplay, chooseButton),
				SwingHelper.colorPickerLeftPadding);

		selectColorFromMapButton = new JToggleButton("Select Color From Map");
		selectColorFromMapButton
				.setToolTipText("To select the color of an existing region, click this button, then click that region on the map.");
		selectColorHider = organizer.addLabelAndComponent("", "", selectColorFromMapButton, 0);

		JButton generateColorButton = new JButton("Generate Color");
		generateColorButton.setToolTipText("Generate a new color based on the random generation settings below.");
		generateColorButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				cancelSelectColorFromMap();
				Color newColor = AwtFactory
						.unwrap(MapCreator.generateColorFromBaseColor(new Random(), AwtFactory.wrap(baseColorPanel.getBackground()),
								hueSlider.getValue(), saturationSlider.getValue(), brightnessSlider.getValue()));
				colorDisplay.setBackground(newColor);
			}
		});
		generateColorButtonHider = organizer.addLabelAndComponent("", "", generateColorButton, 2);

		Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
		brushSizeComboBox = brushSizeTuple.getFirst();
		brushSizeHider = brushSizeTuple.getSecond();

		onlyUpdateLandCheckbox = new JCheckBox("Only update existing land");
		onlyUpdateLandCheckbox.setToolTipText("Causes the paint region brush to not create new land in the ocean.");
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
	}

	private JPanel createColorGeneratorOptionsPanel(JPanel toolOptionsPanel)
	{
		GridBagOrganizer organizer = new GridBagOrganizer();
		organizer.panel.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), colorGeneratorSettingsName));

		baseColorPanel = SwingHelper.createColorPickerPreviewPanel();
		final JButton baseColorChooseButton = new JButton("Choose");
		baseColorChooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				SwingHelper.showColorPicker(toolOptionsPanel, baseColorPanel, "Base Color", () ->
				{
				});
			}
		});
		organizer.addLabelAndComponentsHorizontal("Base color:",
				"The base color for generating new region colors. This is the map's land color when not coloring regions.",
				Arrays.asList(baseColorPanel, baseColorChooseButton), SwingHelper.borderWidthBetweenComponents);

		hueSlider = new JSlider();
		hueSlider.setPaintTicks(true);
		hueSlider.setPaintLabels(true);
		hueSlider.setMinorTickSpacing(20);
		hueSlider.setMajorTickSpacing(100);
		hueSlider.setMaximum(360);
		organizer.addLabelAndComponent("Hue range:",
				"The possible range of hue values for generated region colors. The range is centered at the base color hue.", hueSlider);

		saturationSlider = new JSlider();
		saturationSlider.setPaintTicks(true);
		saturationSlider.setPaintLabels(true);
		saturationSlider.setMinorTickSpacing(20);
		saturationSlider.setMaximum(255);
		saturationSlider.setMajorTickSpacing(100);
		organizer.addLabelAndComponent("Saturation range:",
				"The possible range of saturation values for generated region colors. The range is centered at the land color saturation.",
				saturationSlider);

		brightnessSlider = new JSlider();
		brightnessSlider.setPaintTicks(true);
		brightnessSlider.setPaintLabels(true);
		brightnessSlider.setMinorTickSpacing(20);
		brightnessSlider.setMaximum(255);
		brightnessSlider.setMajorTickSpacing(100);
		organizer.addLabelAndComponent("Brightness range:",
				"The possible range of brightness values for generated region colors. The range is centered at the land color brightness.",
				brightnessSlider);

		return organizer.panel;
	}

	private void showOrHideBrushOptions()
	{
		paintRegionButton.setVisible(areRegionColorsVisible);
		fillRegionColorButton.setVisible(areRegionColorsVisible);
		mergeRegionsButton.setVisible(areRegionBoundariesVisible || areRegionColorsVisible);
		landButton.setVisible(!areRegionColorsVisible);
		roadsButton.setVisible(areRoadsVisible);

		colorChooserHider.setVisible(areRegionColorsVisible);
		selectColorHider.setVisible(areRegionColorsVisible);

		if (landButton.isSelected() && !landButton.isVisible())
		{
			paintRegionButton.setSelected(true);
		}
		else if (paintRegionButton.isSelected() && !paintRegionButton.isVisible())
		{
			landButton.setSelected(true);
		}
		else if (mergeRegionsButton.isSelected() && !mergeRegionsButton.isVisible())
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
		newRegionButtonHider.setVisible(areRegionBoundariesVisible && landButton.isSelected());
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}

	private Integer regionIdToExpand;

	private void handleMousePressOrDrag(MouseEvent e, boolean isMouseDrag)
	{

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
					if (eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
					{
						eEdit.riverLevel = 0;
					}
				}
				hasChange |= !edit.isWater;
				hasChange |= edit.isLake != lakesButton.isSelected();
				// Note that I'm nulling out trees in the assignment below because any trees that failed to draw previously should be
				// cleared out when the Center becomes water.
				mainWindow.edits.centerEdits.put(edit.index,
						new CenterEdit(edit.index, true, lakesButton.isSelected(), null, edit.icon, null));
			}
			if (hasChange)
			{
				handleMapChange(selected);
			}
		}
		else if (paintRegionButton.isSelected())
		{
			if (selectColorFromMapButton.isSelected())
			{
				selectColorFromMap(e);
			}
			else
			{
				Set<Center> selected = getSelectedCenters(e.getPoint());

				boolean hasChange = false;
				for (Center center : selected)
				{
					CenterEdit edit = mainWindow.edits.centerEdits.get(center.index);
					if (onlyUpdateLandCheckbox.isSelected() && edit.isWater)
					{
						continue;
					}
					hasChange |= edit.isWater;
					Integer newRegionId = getOrCreateRegionIdForEdit(center, colorDisplay.getBackground());
					hasChange |= (edit.regionId == null) || newRegionId != edit.regionId;
					mainWindow.edits.centerEdits.put(edit.index,
							new CenterEdit(edit.index, false, false, newRegionId, edit.icon, edit.trees));
				}
				if (hasChange)
				{
					handleMapChange(selected);
				}
			}
		}
		else if (landButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());

			if (!isMouseDrag)
			{
				// Set the id of the region that will be expanded as the user drags the mouse.

				if (newRegionButton.isSelected())
				{
					Color color = mainWindow.getLandColor();
					regionIdToExpand = createNewRegion(color);
					newRegionButton.setSelected(false);
				}
				else
				{
					Counter<Integer> regionIdCounts = new ComparableCounter<>();
					for (Center center : selected)
					{
						CenterEdit edit = mainWindow.edits.centerEdits.get(center.index);
						if (edit.regionId != null)
						{
							regionIdCounts.addCount(edit.regionId, 1);
						}
					}

					if (regionIdCounts.isEmpty())
					{
						regionIdToExpand = null;
					}
					else
					{
						regionIdToExpand = regionIdCounts.argmax();
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
						edit.color = AwtFactory.wrap(colorDisplay.getBackground());
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
				Set<Edge> possibleRivers = getSelectedEdges(e.getPoint(), brushSizes.get(brushSizeComboBox.getSelectedIndex()),
						EdgeType.Voronoi);
				Set<Edge> changed = new HashSet<>();
				for (Edge edge : possibleRivers)
				{
					EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
					if (eEdit.riverLevel > 0)
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
			double brushRadiusResolutionInvariant = (double) ((brushDiameter / mainWindow.zoom) * mapEditingPanel.osScale)
					/ (2 * mainWindow.displayQualityScale);
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
				colorDisplay.setBackground(AwtFactory.unwrap(center.region.backgroundColor));
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
		// When the land is a single color, and the user clicked down on centers that had a region id, use that one.
		if (regionIdToExpand != null)
		{
			return regionIdToExpand;
		}

		// If a neighboring center has the desired region color, then use that region.
		for (Center neighbor : center.neighbors)
		{
			CenterEdit neighborEdit = mainWindow.edits.centerEdits.get(neighbor.index);
			if (neighborEdit.regionId != null && (!areRegionColorsVisible
					|| AwtFactory.unwrap(mainWindow.edits.regionEdits.get(neighborEdit.regionId).color).equals(color)))
			{
				return neighborEdit.regionId;
			}
		}

		// Find the closest region of that color.
		Optional<CenterEdit> opt = mainWindow.edits.centerEdits.values().stream().filter(cEdit1 -> cEdit1.regionId != null
				&& (!areRegionColorsVisible || AwtFactory.unwrap(mainWindow.edits.regionEdits.get(cEdit1.regionId).color).equals(color)))
				.min((cEdit1, cEdit2) -> Double.compare(updater.mapParts.graph.centers.get(cEdit1.index).loc.distanceTo(center.loc),
						updater.mapParts.graph.centers.get(cEdit2.index).loc.distanceTo(center.loc)));
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
			largestRegionId = mainWindow.edits.regionEdits.values().stream().max((r1, r2) -> Integer.compare(r1.regionId, r2.regionId))
					.get().regionId;
		}

		int newRegionId = largestRegionId + 1;

		RegionEdit regionEdit = new RegionEdit(newRegionId, AwtFactory.wrap(color));
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
		else if (roadsButton.isSelected() && modeWidget.isDrawMode())
		{
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
				mainWindow.edits.edgeEdits.get(edge.index).riverLevel = riverLevel;
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
		else if (roadsButton.isSelected() && modeWidget.isDrawMode() && roadStart != null)
		{
			Center end = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
			List<Edge> edges = updater.mapParts.graph.findShortestPath(roadStart, end, (_, _, distance) -> distance);
			List<Road> changed = RoadDrawer.addRoadsFromEdgesInEditor(edges, updater.mapParts.graph, mainWindow.edits.roads,
					mainWindow.displayQualityScale);

			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.clearHighlightedPolylines();
			mapEditingPanel.repaint();

			updater.addRoadsToRedrawLowPriority(changed, mainWindow.displayQualityScale);
			updater.createAndShowMapIncrementalUsingEdges(new HashSet<Edge>(edges));

		}

		updater.dowWhenMapIsNotDrawing(() -> updater.createAndShowLowPriorityChanges());

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

		if (oceanButton.isSelected() || lakesButton.isSelected() || paintRegionButton.isSelected() && !selectColorFromMapButton.isSelected()
				|| landButton.isSelected())
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
		else if (paintRegionButton.isSelected() && selectColorFromMapButton.isSelected() || mergeRegionsButton.isSelected()
				|| fillRegionColorButton.isSelected())
		{
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
				if (eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
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
		else if (roadsButton.isSelected() && modeWidget.isDrawMode())
		{
			if (roadStart != null)
			{
				mapEditingPanel.clearHighlightedEdges();
				Center end = updater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
				List<Edge> edges = updater.mapParts.graph.findShortestPath(roadStart, end, (_, _, distance) -> distance);
				mapEditingPanel.addHighlightedEdges(edges, EdgeType.Delaunay);
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
		if (selectedRegion != null)
		{
			selectedRegion = null;
			mapEditingPanel.clearSelectedCenters();
		}
	}

	@Override
	protected void onAfterUndoRedo()
	{
		selectedRegion = null;
		mapEditingPanel.clearSelectedCenters();
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.repaint();
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages,
			boolean willDoImagesRefresh)
	{
		areRegionColorsVisible = settings.drawRegionColors;
		areRegionBoundariesVisible = settings.drawRegionBoundaries;
		areRoadsVisible = settings.drawRoads;

		// These settings are part of MapSettings, so they get pulled in by undo/redo, but I exclude them here
		// because it feels weird to me to have them change with undo/redo since they don't directly affect the map.
		if (!isUndoRedoOrAutomaticChange)
		{
			baseColorPanel.setBackground(AwtFactory.unwrap(settings.regionBaseColor));
			hueSlider.setValue(settings.hueRange);
			saturationSlider.setValue(settings.saturationRange);
			brightnessSlider.setValue(settings.brightnessRange);

			// I'm setting this color here because I only want it to change when you create new settings or load settings from a file,
			// not on undo/redo or in response to the ThemePanel changing.
			colorDisplay.setBackground(AwtFactory.unwrap(settings.regionBaseColor));
		}

		// Clear any selection
		selectedRegion = null;
		mapEditingPanel.clearSelectedCenters();

		showOrHideBrushOptions();
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.regionBaseColor = AwtFactory.wrap(baseColorPanel.getBackground());
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
	}
}
