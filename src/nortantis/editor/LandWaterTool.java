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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;

import hoten.geom.Point;
import hoten.voronoi.Center;
import nortantis.MapSettings;
import nortantis.Region;
import nortantis.RunSwing;

public class LandWaterTool extends EditorTool
{

	private JPanel colorDisplay;
	private JPanel colorChooserPanel;
	
	private JRadioButton landButton;
	private JRadioButton waterButton;
	private JRadioButton fillRegionColorButton;
	private JRadioButton paintRegionButton;
	private JRadioButton mergeRegionsButton;
	private Region selectedRegion;
	private JToggleButton selectColorFromMapButton;
	
	private JComboBox<ImageIcon> brushSizeComboBox;
	private JPanel brushSizePanel;
	private JPanel selectColorPanel;

	public LandWaterTool(MapSettings settings, EditorFrame dialog)
	{
		super(settings, dialog);
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
		waterButton = new JRadioButton("Ocean");
	    group.add(waterButton);
	    radioButtons.add(waterButton);
	    toolOptionsPanel.add(waterButton);
		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (colorChooserPanel != null && areRegionColorsVisible())
				{
					boolean isVisible = paintRegionButton.isSelected() || fillRegionColorButton.isSelected();
					colorChooserPanel.setVisible(isVisible);
					selectColorPanel.setVisible(isVisible);
				}
				
				if (brushSizeComboBox != null)
				{
					brushSizePanel.setVisible(paintRegionButton.isSelected() || waterButton.isSelected() || landButton.isSelected());
				}
			}
	    };
	    waterButton.addActionListener(listener);
	    
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
	    waterButton.setSelected(true); // Selected by default
	    EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, brushLabel, radioButtons);
	    
	    // Color chooser
	    if (areRegionColorsVisible())
	    {
		    JLabel colorLabel = new JLabel("Color:");
		    
		    colorDisplay = new JPanel();
		    colorDisplay.setPreferredSize(new Dimension(50, 25));
		    colorDisplay.setBackground(settings.landColor);
	    	
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
	    
	    // Prevent the panel from shrinking when components are hidden.
	    toolOptionsPanel.add(Box.createRigidArea(new Dimension(EditorFrame.toolsPanelWidth - 25, 0)));

		return toolOptionsPanel;
	}
	
	private boolean areRegionColorsVisible()
	{
		return settings.drawRegionColors && (!settings.generateBackgroundFromTexture || settings.colorizeLand);
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}
	
	private void handleMousePressOrDrag(MouseEvent e)
	{
		if (waterButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			boolean hasChange = false;
			for (Center center : selected)
			{
				CenterEdit edit = settings.edits.centerEdits.get(center.index);
				hasChange |= !edit.isWater;
				edit.isWater = true;
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
					CenterEdit edit = settings.edits.centerEdits.get(center.index);
					hasChange |= edit.isWater;
					edit.isWater = false;
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
				CenterEdit edit = settings.edits.centerEdits.get(center.index);
				// Still need to add region IDs to edits because the user might switch to region editing later.
				Integer newRegionId = getOrCreateRegionIdForEdit(center, settings.landColor);
				hasChange |= (edit.regionId == null) || newRegionId != edit.regionId;
				edit.regionId = newRegionId;
				hasChange |= edit.isWater;
				edit.isWater = false;
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
				Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
				if (center != null)
				{
					Region region = center.region;
					if (region != null)
					{
						RegionEdit edit = settings.edits.regionEdits.get(region.id);
						edit.color = colorDisplay.getBackground();
						handleMapChange(region.getCenters());
					}
				}
			}
		}
		else if (mergeRegionsButton.isSelected())
		{
			Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
			if (center != null)
			{
				Region region = center.region;
				if (region != null)
				{
					if (selectedRegion == null)
					{
						selectedRegion = region;
					}
					else
					{
						if (region == selectedRegion)
						{
							// Cancel the selection
							selectedRegion = null;
						}
						else
						{
							for (CenterEdit c : settings.edits.centerEdits)
							{
								assert c != null;
								assert region != null;
								if (c.regionId != null && c.regionId == region.id)
								{
									c.regionId = selectedRegion.id;
								}
								
							}
							settings.edits.regionEdits.remove(region.id);
							handleMapChange(region.getCenters());
							selectedRegion = null;
						}
					}
				}
			}
		}
	}
	
	private void selectColorFromMap(MouseEvent e)
	{
		Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
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
			CenterEdit neighborEdit = settings.edits.centerEdits.get(neighbor.index);
			if (neighborEdit.regionId != null && settings.edits.regionEdits.get(neighborEdit.regionId).color.equals(color))
			{
				return neighborEdit.regionId;
			}
		}
		
		// Find the closest region of that color.
       	Optional<CenterEdit> opt = settings.edits.centerEdits.stream()
       			.filter(cEdit1 -> cEdit1.regionId != null && settings.edits.regionEdits.get(cEdit1.regionId).color.equals(color))
        		.min((cEdit1, cEdit2) -> Double.compare(
        				mapParts.graph.centers.get(cEdit1.index).loc.distanceTo(center.loc), 
        				mapParts.graph.centers.get(cEdit2.index).loc.distanceTo(center.loc)));
       	if (opt.isPresent())
       	{
       		return opt.get().regionId;
       	}
       	else
       	{
       		int largestRegionId;
       		if (settings.edits.regionEdits.isEmpty())
       		{
       			largestRegionId = -1;
       		}
       		else
       		{
       			largestRegionId = settings.edits.regionEdits.values().stream().max((r1, r2) -> Integer.compare(r1.regionId, r2.regionId)).get().regionId;
       		}
       		
       		int newRegionId = largestRegionId + 1;
       		
       		RegionEdit regionEdit = new RegionEdit(newRegionId, color);
       		settings.edits.regionEdits.put(newRegionId, regionEdit);
       		
       		return newRegionId;
       	}
	}
	
	private void handleMapChange(Set<Center> centers)
	{
		mapEditingPanel.addAllProcessingCenters(centers);
		mapEditingPanel.repaint();
		
		createAndShowMap();	
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		handleMousePressOrDrag(e);
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		setUndoPoint();
	}
	
	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
	
		if (waterButton.isSelected() || paintRegionButton.isSelected() && !selectColorFromMapButton.isSelected() || landButton.isSelected())
		{		
			Set<Center> selected = getSelectedCenters(e.getPoint());
			mapEditingPanel.addAllHighlightedCenters(selected);
			mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
		}
		else if (paintRegionButton.isSelected() && selectColorFromMapButton.isSelected() || mergeRegionsButton.isSelected() || fillRegionColorButton.isSelected())
		{
			Center center = mapParts.graph.findClosestCenter(new Point(e.getX(), e.getY()), true);			
			if (center != null)
			{
				if (center.region != null)
				{
					mapEditingPanel.addAllHighlightedCenters(center.region.getCenters());
				}
				if (selectedRegion != null)
				{
					mapEditingPanel.addAllHighlightedCenters(selectedRegion.getCenters());
				}
				mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineGroup);
			}
		}
		mapEditingPanel.repaint();
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		handleMousePressOrDrag(e);
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.repaint();
	}

	@Override
	protected void onBeforeCreateMap()
	{
		// Change a few settings to make map creation faster.
		settings.resolution = zoom;
		settings.landBlur = 0;
		settings.oceanEffectSize = 0;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;
		settings.drawIcons = true;
		settings.drawRivers = true;
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map, boolean isQuickUpdate)
	{
		return map;
	}

	@Override
	public void onSwitchingAway()
	{
	}

	@Override
	protected void onAfterUndoRedo(boolean requiresFullRedraw)
	{
		selectedRegion = null;
		createAndShowMap();
	}
}
