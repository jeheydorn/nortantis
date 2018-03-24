package nortantis.editor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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

import hoten.geom.Point;
import hoten.voronoi.Center;
import nortantis.MapSettings;
import nortantis.Region;
import nortantis.RunSwing;

public class LandOceanTool extends EditorTool
{

	private JPanel colorDisplay;
	private JPanel colorChooserPanel;
	private BufferedImage map;
	
	private JRadioButton landButton;
	private JRadioButton oceanButton;
	private JRadioButton fillRegionColorButton;
	private JRadioButton paintColorButton;
	private JRadioButton mergeRegionsButton;
	private Region selectedRegion;
	private JRadioButton selectColorButton;
	
	private JComboBox<ImageIcon> brushSizeComboBox;
	private JPanel brushSizePanel;
	private List<Integer> brushSizes;

	public LandOceanTool(MapSettings settings, EditorDialog dialog)
	{
		super(settings, dialog);
	}

	@Override
	public String getToolbarName()
	{
		return "Land and Ocean";
	}

	@Override
	public void onBeforeSaving()
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected JPanel createToolsOptionsPanel()
	{
		JPanel toolOptionsPanel = new JPanel();
		toolOptionsPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		toolOptionsPanel.setLayout(new BoxLayout(toolOptionsPanel, BoxLayout.Y_AXIS));
		
		// Tools
		JLabel toolsLabel = new JLabel("Brush:");
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
				if (colorChooserPanel != null && settings.drawRegionColors)
				{
					colorChooserPanel.setVisible(paintColorButton.isSelected() || fillRegionColorButton.isSelected());
				}
				
				if (brushSizeComboBox != null)
				{
					brushSizePanel.setVisible(paintColorButton.isSelected() || oceanButton.isSelected() || landButton.isSelected());
				}
			}
	    };
	    oceanButton.addActionListener(listener);
	    
	    paintColorButton = new JRadioButton("Paint color");
	    fillRegionColorButton = new JRadioButton("Fill region color");
	    mergeRegionsButton = new JRadioButton("Merge regions");
	    selectColorButton = new JRadioButton("Select color by region");
	    landButton = new JRadioButton("Land");
	    if (settings.drawRegionColors)
	    {
			
		    group.add(paintColorButton);
		    radioButtons.add(paintColorButton);
		    paintColorButton.addActionListener(listener);

		    
		    group.add(fillRegionColorButton);
		    radioButtons.add(fillRegionColorButton);
		    fillRegionColorButton.addActionListener(listener);
		    
		   
		    group.add(mergeRegionsButton);
		    radioButtons.add(mergeRegionsButton);
		    mergeRegionsButton.addActionListener(listener);

		    
		    group.add(selectColorButton);
		    radioButtons.add(selectColorButton);
		    selectColorButton.addActionListener(listener);
	    }
	    else
	    {
		    group.add(landButton);
		    radioButtons.add(landButton);
		    landButton.addActionListener(listener);
	    }
	    oceanButton.setSelected(true); // Selected by default
	    EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, toolsLabel, radioButtons);
	    
	    // Color chooser
	    if (settings.drawRegionColors)
	    {
		    JLabel colorLabel = new JLabel("Color:");
		    
		    colorDisplay = new JPanel();
		    colorDisplay.setPreferredSize(new Dimension(50, 25));
		    colorDisplay.setBackground(settings.landColor);
	    	
			JButton chooseButton = new JButton("Choose");
			chooseButton.setBounds(814, 314, 87, 25);
			chooseButton = new JButton("Choose");
			chooseButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					RunSwing.showColorPickerWithPreviewPanel(toolOptionsPanel, colorDisplay, "Text color");
				}
			});
			
			JPanel chooserPanel = new JPanel();
			chooserPanel.setLayout(new BoxLayout(chooserPanel, BoxLayout.X_AXIS));
			chooserPanel.add(colorDisplay);
			chooserPanel.add(Box.createHorizontalGlue());
			chooserPanel.add(chooseButton);
			
			colorChooserPanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, colorLabel, chooserPanel);
	    }
	    listener.actionPerformed(null);

	    JLabel brushSizeLabel = new JLabel("Brush size:");
	    brushSizeComboBox = new JComboBox<>();
	    brushSizes = Arrays.asList(1, 25, 70);
	    int largest = 70;
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
	    

		return toolOptionsPanel;
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
		if (map == null) 
		{
			// The map is not visible;
			return;
		}
		if (oceanButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			for (Center center : selected)
			{
				CenterEdit edit = settings.edits.centerEdits.get(center.index);
				edit.isWater = true;
			}
			handleMapChange(selected);
		}
		else if (paintColorButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			for (Center center : selected)
			{
				CenterEdit edit = settings.edits.centerEdits.get(center.index);
				edit.isWater = false;
				edit.regionId = getOrCreateRegionIdForEdit(center, colorDisplay.getBackground());
			}
			handleMapChange(selected);
		}
		else if (landButton.isSelected())
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			for (Center center : selected)
			{
				CenterEdit edit = settings.edits.centerEdits.get(center.index);
				// Still need to add region IDs to edits because the user might switch to region editing later.
				edit.regionId = getOrCreateRegionIdForEdit(center, settings.landColor);
				edit.isWater = false;
			}
			handleMapChange(selected);

		}
		else if (fillRegionColorButton.isSelected())
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
		else if (selectColorButton.isSelected())
		{
			Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
			if (center != null)
			{
				if (center != null && center.region != null)
				{
					colorDisplay.setBackground(center.region.backgroundColor);
					selectColorButton.setSelected(false);
					paintColorButton.setSelected(true);
				}
			}
		}	
	}
	
	private Set<Center> getSelectedCenters(java.awt.Point point)
	{
		Set<Center> selected = new HashSet<Center>();
		
		if (brushSizeComboBox.getSelectedIndex() == 0)
		{
			Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(point.getX(), point.getY()));
			if (center != null)
			{
				selected.add(center);
			}
			return selected;
		}
		
		int brushRadius = brushSizes.get(brushSizeComboBox.getSelectedIndex())/2;
		for (int x = point.x - brushRadius; x < point.x + brushRadius; x++)
		{
			for (int y = point.y - brushRadius; y < point.y + brushRadius; y++)
			{
				float deltaX = (float)(point.x - x);
				float deltaXSquared = deltaX * deltaX;
				float deltaY = (float)(point.y - y);
				float deltaYSquared = deltaY * deltaY;
				if (Math.sqrt(deltaXSquared + deltaYSquared) <= brushRadius)
				{
					Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(x, y));
					if (center != null)
					{
						selected.add(center);
					}
				}
			}
		}
		return selected;
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
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		if (mapParts != null && mapParts.graph != null && map != null)
		{
			mapEditingPanel.clearHighlightedCenters();
		
			mapEditingPanel.setGraph(mapParts.graph);

			if (oceanButton.isSelected() || paintColorButton.isSelected() || landButton.isSelected())
			{		
				Set<Center> selected = getSelectedCenters(e.getPoint());
				mapEditingPanel.addAllHighlightedCenters(selected);
				mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);
			}
			else if (selectColorButton.isSelected() || mergeRegionsButton.isSelected() || fillRegionColorButton.isSelected())
			{
				Center center = mapParts.graph.findClosestCenter(new Point(e.getX(), e.getY()), true);			
				if (center != null)
				{
					if (center.region != null)
					{
						System.out.println("Region: " + center.region.id);
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
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		handleMouseClickOnMap(e);
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
		settings.oceanEffects = 0;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map, boolean mapNeedsRedraw)
	{
		this.map = map;
		if (!mapNeedsRedraw)
		{
			mapEditingPanel.clearProcessingCenters();
		}
		return map;
	}

	@Override
	public void onSwitchingAway()
	{
	}

	@Override
	public void onSelected()
	{
		mapEditingPanel.setHighlightColor(new Color(255,227,74));
		
	}

	@Override
	protected void onAfterUndoRedo()
	{
		// TODO Auto-generated method stub
		
	}
}
