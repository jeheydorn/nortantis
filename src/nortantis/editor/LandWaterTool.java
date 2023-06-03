package nortantis.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;

import hoten.geom.Point;
import hoten.voronoi.Center;
import nortantis.Region;
import nortantis.RunSwing;

public class LandWaterTool extends EditorTool
{

	private JPanel colorDisplay;
	private JPanel colorChooserPanel;
	
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
	private JPanel brushSizePanel;
	private JPanel selectColorPanel;

	public LandWaterTool(EditorFrame dialog)
	{
		super(dialog);
	}

	@Override
	public String getToolbarName()
	{
		return "Land and Water";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get("assets/internal/Land Water tool.png").toString();
	}
	
	@Override
	public void onBeforeSaving()
	{
	}

	@Override
	protected JPanel createToolsOptionsPanel()
	{
		JPanel toolOptionsPanel = new JPanel();
		toolOptionsPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		toolOptionsPanel.setLayout(new BoxLayout(toolOptionsPanel, BoxLayout.Y_AXIS));
		
		// Tools
		JLabel brushLabel = new JLabel("Brush:");
		List<JComponent> radioButtons = new ArrayList<>();
		ButtonGroup group = new ButtonGroup();
		oceanButton = new JRadioButton("Ocean");
	    group.add(oceanButton);
	    radioButtons.add(oceanButton);
	    toolOptionsPanel.add(oceanButton);
		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.clearSelectedCenters();
				if (colorChooserPanel != null && areRegionColorsVisible())
				{
					boolean isVisible = paintRegionButton.isSelected() || fillRegionColorButton.isSelected();
					colorChooserPanel.setVisible(isVisible);
					selectColorPanel.setVisible(isVisible);
				}
				
				if (brushSizeComboBox != null)
				{
					brushSizePanel.setVisible(paintRegionButton.isSelected() || oceanButton.isSelected() || lakeButton.isSelected() || landButton.isSelected());
				}
			}
	    };
	    oceanButton.addActionListener(listener);
	    
	    lakeButton = new JRadioButton("Lake");
	    group.add(lakeButton);
	    radioButtons.add(lakeButton);
	    toolOptionsPanel.add(lakeButton);
	    lakeButton.setToolTipText("Lakes are the same as ocean except they have no ocean effects (waves or darkening) along coastlines.");
	    lakeButton.addActionListener(listener);
	    
	    paintRegionButton = new JRadioButton("Paint region");
	    fillRegionColorButton = new JRadioButton("Fill region color");
	    mergeRegionsButton = new JRadioButton("Merge regions");
	    landButton = new JRadioButton("Land");
	    if (areRegionColorsVisible())
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
	    EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, brushLabel, radioButtons);
	    
	    // Color chooser
	    if (areRegionColorsVisible())
	    {
		    JLabel colorLabel = new JLabel("Color:");
		    
		    colorDisplay = new JPanel();
		    colorDisplay.setPreferredSize(new Dimension(50, 25));
		    colorDisplay.setBackground(parent.settings.landColor);
	    	
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
					RunSwing.showColorPickerWithPreviewPanel(toolOptionsPanel, colorDisplay, "Text color");
				}
			});
			
			JPanel chooserPanel = new JPanel();
			chooserPanel.setLayout(new BoxLayout(chooserPanel, BoxLayout.X_AXIS));
			chooserPanel.add(colorDisplay);
			chooserPanel.add(Box.createHorizontalGlue());
			chooserPanel.add(chooseButton);
			
			colorChooserPanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, colorLabel, chooserPanel);
			
			selectColorFromMapButton = new JToggleButton("Select color from map");
			selectColorFromMapButton.setToolTipText("To select the color of an existing region, click this button, then click that region on the map.");
			selectColorPanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, new JLabel(""), selectColorFromMapButton);

	    }
	    listener.actionPerformed(null);

	    JLabel brushSizeLabel = new JLabel("Brush size:");
	    brushSizeComboBox = new JComboBox<>();
	    int largest = Collections.max(brushSizes);
	    for (int brushSize : brushSizes)
	    {
	    	if (brushSize == 1)
	    	{
	    		brushSize = 4; // Needed to make it visible
	    	}
	    	BufferedImage image = new BufferedImage(largest, largest, BufferedImage.TYPE_INT_ARGB);
	    	Graphics2D g = image.createGraphics();
	    	g.setColor(Color.white);
	    	g.setColor(Color.black);
	    	g.fillOval(largest/2 - brushSize/2, largest/2 - brushSize/2, brushSize, brushSize);
	    	brushSizeComboBox.addItem(new ImageIcon(image));
	    }
	    brushSizePanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, brushSizeLabel, brushSizeComboBox);
	    
	    JLabel highlightLakesLabel = new JLabel("");
	    highlightLakesCheckbox = new JCheckBox("Highlight lakes");
	    highlightLakesCheckbox.setToolTipText("Highlight lakes to make them easier to see.");
	    highlightLakesCheckbox.setSelected(true);
	    EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, highlightLakesLabel, highlightLakesCheckbox);
	    highlightLakesCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.setHighlightLakes(highlightLakesCheckbox.isSelected());
				mapEditingPanel.repaint();
			}
		});
	    
	    // Prevent the panel from shrinking when components are hidden.
	    toolOptionsPanel.add(Box.createRigidArea(new Dimension(EditorFrame.toolsPanelWidth - 25, 0)));

		return toolOptionsPanel;
	}
	
	private boolean areRegionColorsVisible()
	{
		return parent.settings.drawRegionColors && (!parent.settings.generateBackgroundFromTexture || parent.settings.colorizeLand);
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
				CenterEdit edit = parent.settings.edits.centerEdits.get(center.index);
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
					CenterEdit edit = parent.settings.edits.centerEdits.get(center.index);
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
				CenterEdit edit = parent.settings.edits.centerEdits.get(center.index);
				// Still need to add region IDs to edits because the user might switch to region editing later.
				Integer newRegionId = getOrCreateRegionIdForEdit(center, parent.settings.landColor);
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
				Center center = parent.mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
				if (center != null)
				{
					Region region = center.region;
					if (region != null)
					{
						RegionEdit edit = parent.settings.edits.regionEdits.get(region.id);
						edit.color = colorDisplay.getBackground();
						Set<Center> regionCenters = region.getCenters();
						mapEditingPanel.addProcessingCenters(regionCenters);
						handleMapChange(regionCenters);
					}
				}
			}
		}
		else if (mergeRegionsButton.isSelected())
		{
			Center center = parent.mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
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
							for (CenterEdit c : parent.settings.edits.centerEdits)
							{
								assert c != null;
								if (c.regionId != null && c.regionId == region.id)
								{
									c.regionId = selectedRegion.id;
								}
								
							}
							mapEditingPanel.addProcessingCenters(region.getCenters());
							parent.settings.edits.regionEdits.remove(region.id);
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
		Center center = parent.mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
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
			CenterEdit neighborEdit = parent.settings.edits.centerEdits.get(neighbor.index);
			if (neighborEdit.regionId != null && parent.settings.edits.regionEdits.get(neighborEdit.regionId).color.equals(color))
			{
				return neighborEdit.regionId;
			}
		}
		
		// Find the closest region of that color.
       	Optional<CenterEdit> opt = parent.settings.edits.centerEdits.stream()
       			.filter(cEdit1 -> cEdit1.regionId != null && parent.settings.edits.regionEdits.get(cEdit1.regionId).color.equals(color))
        		.min((cEdit1, cEdit2) -> Double.compare(
        				parent.mapParts.graph.centers.get(cEdit1.index).loc.distanceTo(center.loc), 
        				parent.mapParts.graph.centers.get(cEdit2.index).loc.distanceTo(center.loc)));
       	if (opt.isPresent())
       	{
       		return opt.get().regionId;
       	}
       	else
       	{
       		int largestRegionId;
       		if (parent.settings.edits.regionEdits.isEmpty())
       		{
       			largestRegionId = -1;
       		}
       		else
       		{
       			largestRegionId = parent.settings.edits.regionEdits.values().stream().max((r1, r2) -> Integer.compare(r1.regionId, r2.regionId)).get().regionId;
       		}
       		
       		int newRegionId = largestRegionId + 1;
       		
       		RegionEdit regionEdit = new RegionEdit(newRegionId, color);
       		parent.settings.edits.regionEdits.put(newRegionId, regionEdit);
       		
       		return newRegionId;
       	}
	}
	
	private void handleMapChange(Set<Center> centers)
	{		
		parent.createAndShowMapIncrementalUsingCenters(centers);	
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
			Center center = parent.mapParts.graph.findClosestCenter(new Point(e.getX(), e.getY()), true);			
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
		parent.createAndShowMapFromChange(change);
	}
}