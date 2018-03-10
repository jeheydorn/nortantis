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
import java.util.stream.Collectors;

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
import javax.swing.Painter;

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
	private JRadioButton selectColorButton;
	
	private Set<Center> currentlyUpdating;
	private Set<Point> queuedClicks;

	public LandOceanTool(MapSettings settings, EditorDialog dialog)
	{
		super(settings, dialog);
		currentlyUpdating = new HashSet<>();
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
					colorChooserPanel.setVisible(!oceanButton.isSelected());
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

	    // Brush sizes
	    JLabel brushSizeLabel = new JLabel("Brush size:");
	    JComboBox<ImageIcon> brushSizeComboBox = new JComboBox<>();
	    List<Integer> brushSizes = Arrays.asList(1, 25, 70);
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
	    EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, brushSizeLabel, brushSizeComboBox);
	    

		return toolOptionsPanel;
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
		if (map != null) // If the map is visible ...
		{
			Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
			if (center != null)
			{
				
				if (oceanButton.isSelected())
				{
					CenterEdit edit = settings.edits.centerEdits.get(center.index);
					edit.isWater = true;
					handleMapChange(center);
				}
				else if (paintColorButton.isSelected())
				{
					CenterEdit edit = settings.edits.centerEdits.get(center.index);
					edit.isWater = false;
					edit.regionId = getOrCreateRegionIdForEdit(center, colorDisplay.getBackground());
					handleMapChange(center);
				}
				else if (landButton.isSelected())
				{
					CenterEdit edit = settings.edits.centerEdits.get(center.index);
					// Still need to add region IDs to edits because the user might switch to region editing later.
					edit.regionId = getOrCreateRegionIdForEdit(center, settings.landColor);
					edit.isWater = false;
					handleMapChange(center);
				}
				else if (fillRegionColorButton.isSelected())
				{
					// TODO
					handleMapChange(center);
				}
				else if (mergeRegionsButton.isSelected())
				{
					handleMapChange(center);
				}
				else if (selectColorButton.isSelected())
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
	}
	
	private int getOrCreateRegionIdForEdit(Center center, Color color)
	{
		// If a neighboring center has the desired region color, then use that region.
		if (center.region != null)
		{
			for (Center neighbor : center.neighbors)
			{
				if (neighbor.region != null && neighbor.region.backgroundColor.equals(color))
				{
					return neighbor.region.id;
				}
			}
		}
		
		// Find the closest region of that color.
       	Optional<Center> opt = mapParts.graph.centers.stream()
       			.filter(c -> c.region != null && c.region.backgroundColor.equals(color))
        		.min((c1, c2) -> Double.compare(c1.loc.distanceTo(center.loc), c2.loc.distanceTo(center.loc)));
       	if (opt.isPresent())
       	{
       		return opt.get().region.id;
       	}
       	else
       	{
       		int largestRegionId;
       		if (mapParts.graph.regions.isEmpty())
       		{
       			largestRegionId = -1;
       		}
       		else
       		{
       			largestRegionId = mapParts.graph.regions.stream().max((r1, r2) -> Integer.compare(r1.id, r2.id)).get().id;
       		}
       		
       		int newRegionId = largestRegionId + 1;
       		
       		RegionEdit regionEdit = new RegionEdit(newRegionId, color);
       		settings.edits.regionEdits.add(regionEdit);
       		
       		return newRegionId;
       	}
	}

	
	private void handleMapChange(Center center)
	{	
		mapEditingPanel.addProcessingCenter(center);
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
		if (mapParts != null && mapParts.graph != null)
		{
			mapEditingPanel.clearHighlightedCenters();
			
			Center c = mapParts.graph.findClosestCenter(new Point(e.getX(), e.getY()), true);
			// TODO remove
			if (c != null)
				if (c.region != null)
					System.out.println("Region id: " + c.region.id);
				else
					System.out.println("No region");
			
			if (c != null)
			{
				mapEditingPanel.setGraph(mapParts.graph);

				if (oceanButton.isSelected() || paintColorButton.isSelected() || landButton.isSelected())
				{		
					mapEditingPanel.addHighlightedCenter(c);
					mapEditingPanel.setCenterHighlightMode(false);
					mapEditingPanel.repaint();
				}
				else if (selectColorButton.isSelected() || mergeRegionsButton.isSelected() || fillRegionColorButton.isSelected())
				{
					if (c.region != null)
					{
						mapEditingPanel.addAllHighlightedCenters(c.region.getCenters());
						mapEditingPanel.setCenterHighlightMode(true);
						mapEditingPanel.repaint();
					}
					else
					{
						mapEditingPanel.repaint();
					}
				}
			}
			else
			{
				mapEditingPanel.repaint();
			}
		}
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		handleMouseClickOnMap(e);
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
}
