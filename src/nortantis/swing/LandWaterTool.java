package nortantis.swing;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

import nortantis.MapSettings;
import nortantis.Region;
import nortantis.editor.CenterEdit;
import nortantis.editor.MapChange;
import nortantis.editor.MapUpdater;
import nortantis.editor.RegionEdit;
import nortantis.graph.voronoi.Center;
import nortantis.util.AssetsPath;
import nortantis.util.Tuple2;

public class LandWaterTool extends EditorTool
{

	private JPanel colorDisplay;
	private RowHider colorChooserHider;
	
	private JRadioButton landButton;
	private JRadioButton oceanButton;
	private JRadioButton lakeButton;
	private JRadioButton fillRegionColorButton;
	private JRadioButton paintRegionButton;
	private JRadioButton mergeRegionsButton;
	private Region selectedRegion;
	private JToggleButton selectColorFromMapButton;
	private JCheckBox highlightLakesCheckbox;
	
	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private RowHider selectColorHider;
	private JCheckBox onlyUpdateLandCheckbox;
	
	private JSlider hueSlider;
	private JSlider saturationSlider;
	private JSlider brightnessSlider;
	private boolean areRegionColorsVisible;

	public LandWaterTool(MainWindow mainWindow, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(mainWindow, toolsPanel, mapUpdater);
	}

	@Override
	public String getToolbarName()
	{
		return "Land and Water";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get(AssetsPath.get(), "internal/Land Water tool.png").toString();
	}
	
	@Override
	public void onBeforeSaving()
	{
	}

	@Override
	protected JPanel createToolsOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();
		
		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		
		
		List<JComponent> radioButtons = new ArrayList<>();
		ButtonGroup group = new ButtonGroup();
		oceanButton = new JRadioButton("Ocean");
	    group.add(oceanButton);
	    radioButtons.add(oceanButton);
		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.clearSelectedCenters();
				if (colorChooserHider != null && areRegionColorsVisible)
				{
					boolean isVisible = paintRegionButton.isSelected() || fillRegionColorButton.isSelected();
					colorChooserHider.setVisible(isVisible);
					selectColorHider.setVisible(isVisible);
				}
				
				if (brushSizeComboBox != null)
				{
					brushSizeHider.setVisible(paintRegionButton.isSelected() || oceanButton.isSelected() || lakeButton.isSelected() || landButton.isSelected());
				}
				
				if (areRegionColorsVisible)
				{
					onlyUpdateLandCheckbox.setVisible(paintRegionButton.isSelected());
				}
			}
	    };
	    oceanButton.addActionListener(listener);
	    
	    lakeButton = new JRadioButton("Lake");
	    group.add(lakeButton);
	    radioButtons.add(lakeButton);
	    lakeButton.setToolTipText("Lakes are the same as ocean except they have no ocean effects (waves or darkening) along coastlines.");
	    lakeButton.addActionListener(listener);
	    
	    paintRegionButton = new JRadioButton("Paint region");
	    fillRegionColorButton = new JRadioButton("Fill region color");
	    mergeRegionsButton = new JRadioButton("Merge regions");
	    landButton = new JRadioButton("Land");
	    if (areRegionColorsVisible)
	    {
			
		    group.add(paintRegionButton);
		    radioButtons.add(paintRegionButton);
		    paintRegionButton.addActionListener(listener);

		    
		    group.add(fillRegionColorButton);
		    radioButtons.add(fillRegionColorButton);
		    fillRegionColorButton.addActionListener(listener);
		    
		   
		    group.add(mergeRegionsButton);
		    radioButtons.add(mergeRegionsButton);
		    mergeRegionsButton.addActionListener(listener);
	    }
	    else
	    {
		    group.add(landButton);
		    radioButtons.add(landButton);
		    landButton.addActionListener(listener);
	    }
	    	    	    
	    oceanButton.setSelected(true); // Selected by default
	    organizer.addLabelAndComponentsToPanelVertical("Brush:", "", radioButtons);
	    	    
	    // Color chooser
	    if (areRegionColorsVisible)
	    {   
		    colorDisplay = SwingHelper.createColorPickerPreviewPanel();
		    //colorDisplay.setBackground(mainWindow.settings.landColor); // TODO Replace this with a field in this tool for changing the base color and generating new ones.
	    	
			JButton chooseButton = new JButton("Choose");
			chooseButton.setBounds(814, 314, 87, 25);
			chooseButton = new JButton("Choose");
			chooseButton.addActionListener(new ActionListener() 
			{
				public void actionPerformed(ActionEvent e) 
				{
					if (selectColorFromMapButton.isSelected())
					{
						selectColorFromMapButton.setSelected(false);
						selectedRegion = null;
						mapEditingPanel.clearSelectedCenters();
						
					}
					SwingHelper.showColorPickerWithPreviewPanel(toolOptionsPanel, colorDisplay, "Text color");
				}
			});
			
			JPanel chooserPanel = new JPanel();
			chooserPanel.setLayout(new BoxLayout(chooserPanel, BoxLayout.X_AXIS));
			chooserPanel.add(colorDisplay);
			chooserPanel.add(Box.createHorizontalGlue());
			chooserPanel.add(chooseButton);
			
			colorChooserHider = organizer.addLabelAndComponentToPanel("Color:", "", chooserPanel);
			
			selectColorFromMapButton = new JToggleButton("Select color from map");
			selectColorFromMapButton.setToolTipText("To select the color of an existing region, click this button, then click that region on the map.");
			selectColorHider = organizer.addLabelAndComponentToPanel("", "", selectColorFromMapButton);

	    }

	
	    Tuple2<JComboBox<ImageIcon>, RowHider> brushSizeTuple = organizer.addBrushSizeComboBox(brushSizes);
	    brushSizeComboBox = brushSizeTuple.getFirst();
	    brushSizeHider = brushSizeTuple.getSecond();
	 
	    
	    highlightLakesCheckbox = new JCheckBox("Highlight lakes");
	    highlightLakesCheckbox.setToolTipText("Highlight lakes to make them easier to see.");
	    highlightLakesCheckbox.setSelected(true);
	    organizer.addLabelAndComponentToPanel("", "", highlightLakesCheckbox);
	    highlightLakesCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.setHighlightLakes(highlightLakesCheckbox.isSelected());
				mapEditingPanel.repaint();
			}
		});
	    
	    if (areRegionColorsVisible)
	    {
		    onlyUpdateLandCheckbox = new JCheckBox("Only update land");
		    onlyUpdateLandCheckbox.setToolTipText("Causes the paint region brush to not create new land in the ocean.");
		    organizer.addLabelAndComponentToPanel("", "", onlyUpdateLandCheckbox);
	    }
	    
	    listener.actionPerformed(null);

	    
	    
	    // TODO Put these where they belong:
//		hueSlider = new JSlider();
//		hueSlider.setPaintTicks(true);
//		hueSlider.setPaintLabels(true);
//		hueSlider.setMinorTickSpacing(20);
//		hueSlider.setMajorTickSpacing(100);
//		hueSlider.setMaximum(360);
//		hueSlider.setBounds(150, 82, 245, 79);
//		regionsPanel.add(hueSlider);
//
//		lblHueRange = new JLabel("Hue range:");
//		lblHueRange.setToolTipText(
//				"The possible range of hue values for generated region colors. The range is centered at the land color hue.");
//		lblHueRange.setBounds(12, 94, 101, 23);
//		regionsPanel.add(lblHueRange);
//
//		lblSaturationRange = new JLabel("Saturation range:");
//		lblSaturationRange.setToolTipText(
//				"The possible range of saturation values for generated region colors. The range is centered at the land color saturation.");
//		lblSaturationRange.setBounds(12, 175, 129, 23);
//		regionsPanel.add(lblSaturationRange);
//
//		saturationSlider = new JSlider();
//		saturationSlider.setPaintTicks(true);
//		saturationSlider.setPaintLabels(true);
//		saturationSlider.setMinorTickSpacing(20);
//		saturationSlider.setMaximum(255);
//		saturationSlider.setMajorTickSpacing(100);
//		saturationSlider.setBounds(150, 163, 245, 79);
//		regionsPanel.add(saturationSlider);
//
//		lblBrightnessRange = new JLabel("Brightness range:");
//		lblBrightnessRange.setToolTipText(
//				"The possible range of brightness values for generated region colors. The range is centered at the land color brightness.");
//		lblBrightnessRange.setBounds(12, 255, 129, 23);
//		regionsPanel.add(lblBrightnessRange);
//
//		brightnessSlider = new JSlider();
//		brightnessSlider.setPaintTicks(true);
//		brightnessSlider.setPaintLabels(true);
//		brightnessSlider.setMinorTickSpacing(20);
//		brightnessSlider.setMaximum(255);
//		brightnessSlider.setMajorTickSpacing(100);
//		brightnessSlider.setBounds(150, 243, 245, 79);
//		regionsPanel.add(brightnessSlider);

	    organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.66);
	    organizer.addVerticalFillerRow(toolOptionsPanel);
		return toolOptionsPanel;
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}
	
	private void handleMousePressOrDrag(MouseEvent e, boolean isMouseDrag)
	{
		if (mergeRegionsButton.isSelected() && isMouseDrag)
		{
			return;
		}
		
		highlightHoverCenters(e);
		
		if (oceanButton.isSelected() || lakeButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			boolean hasChange = false;
			for (Center center : selected)
			{
				CenterEdit edit = mainWindow.edits.centerEdits.get(center.index);
				hasChange |= !edit.isWater;
				edit.isWater = true;
				hasChange |= edit.isLake != lakeButton.isSelected();
				edit.isLake = lakeButton.isSelected(); 
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
					edit.isWater = false;
					edit.isLake = false;
					Integer newRegionId = getOrCreateRegionIdForEdit(center, colorDisplay.getBackground());
					hasChange |= (edit.regionId == null) || newRegionId != edit.regionId;
					edit.regionId = newRegionId;
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
			boolean hasChange = false;
			for (Center center : selected)
			{
				CenterEdit edit = mainWindow.edits.centerEdits.get(center.index);
				// Still need to add region IDs to edits because the user might switch to region editing later.
				Integer newRegionId = getOrCreateRegionIdForEdit(center, mainWindow.getLandColor());
				hasChange |= (edit.regionId == null) || newRegionId != edit.regionId;
				edit.regionId = newRegionId;
				hasChange |= edit.isWater;
				edit.isWater = false;
				edit.isLake = false;
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
				Center center = mapUpdater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
				if (center != null)
				{
					Region region = center.region;
					if (region != null)
					{
						RegionEdit edit = mainWindow.edits.regionEdits.get(region.id);
						edit.color = colorDisplay.getBackground();
						Set<Center> regionCenters = region.getCenters();
						handleMapChange(regionCenters);
					}
				}
			}
		}
		else if (mergeRegionsButton.isSelected())
		{
			Center center = mapUpdater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
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
							// Loop over edits instead of region.getCenters() because centers are changed by map drawing, but edits
							// should only be changed in the current thread.
							for (CenterEdit c : mainWindow.edits.centerEdits)
							{
								assert c != null;
								if (c.regionId != null && c.regionId == region.id)
								{
									c.regionId = selectedRegion.id;
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
	}
	
	private void selectColorFromMap(MouseEvent e)
	{
		Center center = mapUpdater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()));
		if (center != null)
		{
			if (center != null && center.region != null)
			{
				colorDisplay.setBackground(center.region.backgroundColor);
				selectColorFromMapButton.setSelected(false);
			}
		}
	}
	
	private Set<Center> getSelectedCenters(java.awt.Point point)
	{
		return getSelectedCenters(point, brushSizes.get(brushSizeComboBox.getSelectedIndex()));
	}
	
	private int getOrCreateRegionIdForEdit(Center center, Color color)
	{
		// If a neighboring center has the desired region color, then use that region.
		for (Center neighbor : center.neighbors)
		{
			CenterEdit neighborEdit = mainWindow.edits.centerEdits.get(neighbor.index);
			if (neighborEdit.regionId != null && mainWindow.edits.regionEdits.get(neighborEdit.regionId).color.equals(color))
			{
				return neighborEdit.regionId;
			}
		}
		
		// Find the closest region of that color.
       	Optional<CenterEdit> opt = mainWindow.edits.centerEdits.stream()
       			.filter(cEdit1 -> cEdit1.regionId != null && mainWindow.edits.regionEdits.get(cEdit1.regionId).color.equals(color))
        		.min((cEdit1, cEdit2) -> Double.compare(
        				mapUpdater.mapParts.graph.centers.get(cEdit1.index).loc.distanceTo(center.loc), 
        				mapUpdater.mapParts.graph.centers.get(cEdit2.index).loc.distanceTo(center.loc)));
       	if (opt.isPresent())
       	{
       		return opt.get().regionId;
       	}
       	else
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
       		
       		RegionEdit regionEdit = new RegionEdit(newRegionId, color);
       		mainWindow.edits.regionEdits.put(newRegionId, regionEdit);
       		
       		return newRegionId;
       	}
	}
	
	private void handleMapChange(Set<Center> centers)
	{		
		mapUpdater.createAndShowMapIncrementalUsingCenters(centers);	
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		handleMousePressOrDrag(e, false);
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		undoer.setUndoPoint(this);
	}
	
	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		highlightHoverCenters(e);
	}
	
	protected void highlightHoverCenters(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		
		if (oceanButton.isSelected() || lakeButton.isSelected() || paintRegionButton.isSelected() && !selectColorFromMapButton.isSelected() || landButton.isSelected())
		{		
			Set<Center> selected = getSelectedCenters(e.getPoint());
			mapEditingPanel.addHighlightedCenters(selected);
			mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
		}
		else if (paintRegionButton.isSelected() && selectColorFromMapButton.isSelected() || mergeRegionsButton.isSelected() || fillRegionColorButton.isSelected())
		{
			Center center = mapUpdater.mapParts.graph.findClosestCenter(getPointOnGraph(e.getPoint()), true);			
			if (center != null)
			{
				if (center.region != null)
				{
					mapEditingPanel.addHighlightedCenters(center.region.getCenters());
				}
				mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineGroup);
			}
		}
		mapEditingPanel.repaint();
	}
	
	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		handleMousePressOrDrag(e, true);
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.repaint();
	}
	
	@Override 
	public void onActivate()
	{
		mapEditingPanel.setHighlightLakes(highlightLakesCheckbox.isSelected());
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map)
	{
		return map;
	}

	@Override
	public void onSwitchingAway()
	{
		mapEditingPanel.setHighlightLakes(false);
	}

	@Override
	protected void onAfterUndoRedo(MapChange change)
	{
		selectedRegion = null;
		mapEditingPanel.clearSelectedCenters();
		mapUpdater.createAndShowMapFromChange(change);
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings)
	{
		// TODO Handle draw region colors changed
		areRegionColorsVisible = settings.drawRegionColors;
		
		// TODO put back
//		hueSlider.setValue(settings.hueRange);
//		saturationSlider.setValue(settings.saturationRange);
//		brightnessSlider.setValue(settings.brightnessRange);

		
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		// TODO Put back when ready
//		settings.hueRange = hueSlider.getValue();
//		settings.saturationRange = saturationSlider.getValue();
//		settings.brightnessRange = brightnessSlider.getValue();


		
	}
}
