package nortantis.editor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.SwingWorker;

import hoten.voronoi.Center;
import nortantis.CenterIcon;
import nortantis.CenterIconType;
import nortantis.CenterTrees;
import nortantis.IconDrawer;
import nortantis.MapSettings;
import util.Helper;
import util.ImageHelper;

public class IconTool extends EditorTool
{

	private JRadioButton mountainsButton;
	private JRadioButton treesButton;
	private BufferedImage mapWithouticons;
	private boolean hasDrawnIconsBefore;
	private boolean iconsAreDrawing;
	private boolean iconsNeedRedraw;
	private JComboBox<ImageIcon> brushSizeComboBox;
	private JPanel brushSizePanel;
	private JRadioButton hillsButton;
	private JRadioButton dunesButton;
	private IconTypeButtons mountainTypes;
	private IconTypeButtons hillTypes;
	private IconTypeButtons duneTypes;
	private IconTypeButtons treeTypes;
	private JSlider densitySlider;
	private Random rand;
	private JPanel densityPanel;

	public IconTool(MapSettings settings, EditorDialog parent)
	{
		super(settings, parent);
		rand = new Random();
	}

	@Override
	public String getToolbarName()
	{
		return "Icons";
	}

	@Override
	public void onBeforeSaving()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSwitchingAway()
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
		{
			JLabel brushLabel = new JLabel("Brush:");
			ButtonGroup group = new ButtonGroup();
			List<JComponent> radioButtons = new ArrayList<>();
			
			mountainsButton = new JRadioButton("Mountains");
		    group.add(mountainsButton);
		    radioButtons.add(mountainsButton);
		    mountainsButton.setSelected(true);
		    mountainsButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});
	
			hillsButton = new JRadioButton("Hills");
		    group.add(hillsButton);
		    radioButtons.add(hillsButton);
		    hillsButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});

		    
			dunesButton = new JRadioButton("Dunes");
		    group.add(dunesButton);
		    radioButtons.add(dunesButton);
		    dunesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});
	
			treesButton = new JRadioButton("Trees");
		    group.add(treesButton);
		    radioButtons.add(treesButton);
		    treesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});
	
		    EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, brushLabel, 
		    		radioButtons);
		}
	    
		mountainTypes = createRadioButtonsForIconType(toolOptionsPanel, IconDrawer.mountainsName);
		hillTypes = createRadioButtonsForIconType(toolOptionsPanel, IconDrawer.hillsName);
		duneTypes = createRadioButtonsForIconType(toolOptionsPanel, IconDrawer.sandDunesName);
		treeTypes = createRadioButtonsForIconType(toolOptionsPanel, IconDrawer.treesName);
		
		JLabel densityLabel = new JLabel("density:");
		densitySlider = new JSlider(1, 20);
		densityPanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, densityLabel, densitySlider);
		
		mountainsButton.doClick();
	    
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

	    
	    return toolOptionsPanel;
	}
	
	private void updateTypePanels()
	{
		mountainTypes.panel.setVisible(mountainsButton.isSelected());
		hillTypes.panel.setVisible(hillsButton.isSelected());
		duneTypes.panel.setVisible(dunesButton.isSelected());
		treeTypes.panel.setVisible(treesButton.isSelected());
		densityPanel.setVisible(treesButton.isSelected());
	}
	
	private IconTypeButtons createRadioButtonsForIconType(JPanel toolOptionsPanel, String iconType)
	{
	    JLabel typeLabel = new JLabel("Type:");
	    ButtonGroup group = new ButtonGroup();
	    List<JRadioButton> radioButtons = new ArrayList<>();
	    for (String groupId : IconDrawer.getDistinctIconGroupIds(iconType))
	    {
	    	JRadioButton button = new JRadioButton(groupId);
	    	group.add(button);
	    	radioButtons.add(button);
	    }
	    if (radioButtons.size() > 0)
	    {
	    	((JRadioButton)radioButtons.get(0)).setSelected(true);
	    }
	    return new IconTypeButtons(EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, typeLabel, radioButtons), radioButtons);
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
		if (mapParts == null || mapParts.graph == null || mapWithouticons == null) 
		{
			// The map is not visible;
			return;
		}

		Set<Center> selected = getSelectedLandCenters(e.getPoint());

		if (mountainsButton.isSelected())
		{
			String rangeId = mountainTypes.getSelectedOption();
			for (Center center : selected)
			{
				settings.edits.centerEdits.get(center.index).icon = new CenterIcon(CenterIconType.Mountain, rangeId, Math.abs(rand.nextInt()));
			}
		}
		else if (hillsButton.isSelected())
		{
			String rangeId = hillTypes.getSelectedOption();
			for (Center center : selected)
			{
				settings.edits.centerEdits.get(center.index).icon = new CenterIcon(CenterIconType.Hill, rangeId, Math.abs(rand.nextInt()));
			}
		}
		else if (dunesButton.isSelected())
		{
			String rangeId = duneTypes.getSelectedOption();
			for (Center center : selected)
			{
				settings.edits.centerEdits.get(center.index).icon = new CenterIcon(CenterIconType.Dune, rangeId, Math.abs(rand.nextInt()));
			}		
		}
		else if (treesButton.isSelected())
		{
			String treeType = treeTypes.getSelectedOption();
			for (Center center : selected)
			{
				settings.edits.centerEdits.get(center.index).trees = new CenterTrees(treeType, densitySlider.getValue() / 10.0, 
						Math.abs(rand.nextLong()));
			}		
		}
		handleMapChange(selected);	
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{		
	}
	
	private Set<Center> getSelectedLandCenters(java.awt.Point point)
	{
		Set<Center> selected = getSelectedCenters(point);
		return selected.stream().filter(c -> !c.isWater).collect(Collectors.toSet());
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		setUndoPoint();
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		if (mapParts == null || mapParts.graph == null || mapWithouticons == null) 
		{
			// The map is not visible;
			return;
		}
		
		mapEditingPanel.clearHighlightedCenters();
		
		mapEditingPanel.setGraph(mapParts.graph);
		
		Set<Center> selected = getSelectedCenters(e.getPoint());
		mapEditingPanel.addAllHighlightedCenters(selected);
		mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);	
		mapEditingPanel.repaint();
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
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
		settings.drawIcons = false;
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map, boolean mapNeedsRedraw)
	{
		mapWithouticons = ImageHelper.deepCopy(map);
		
		mapParts.iconDrawer.drawAllIcons(map, mapParts.landBackground);
		
		if(!hasDrawnIconsBefore)
		{
			copyOfEditsWhenToolWasSelected = Helper.deepCopy(settings.edits);
			hasDrawnIconsBefore = true;
		}

		return map;
	}
	
	private void updateIconsInBackgroundThread()
	{
		if (iconsAreDrawing)
		{
			iconsNeedRedraw = true;
			return;
		}
		
		iconsAreDrawing = true;

	    SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
	    {
	        @Override
	        public synchronized BufferedImage doInBackground() 
	        {	
				try
				{
					BufferedImage map = ImageHelper.deepCopy(mapWithouticons);
					mapParts.iconDrawer.clearAndAddIconsFromEdits(settings.edits);
					mapParts.iconDrawer.drawAllIcons(map, mapParts.landBackground);
					
					return map;
				} 
				catch (Exception e)
				{
					e.printStackTrace();
			        JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				} 
	        	
	        	return null;
	        }
	        
	        @Override
	        public void done()
	        {
	        	BufferedImage map = null;
	            try 
	            {
	            	map = get();
	            } 
	            catch (InterruptedException | java.util.concurrent.ExecutionException e) 
	            {
	                throw new RuntimeException(e);
	            }
	            
	            iconsAreDrawing = false;
	            if (iconsNeedRedraw)
	            {
	            	updateIconsInBackgroundThread();
	            }
            	iconsNeedRedraw = false;
	            
         		mapEditingPanel.clearProcessingCenters();
	            
              	mapEditingPanel.image = map;
        		mapEditingPanel.repaint();
            	// Tell the scroll pane to update itself.
            	mapEditingPanel.revalidate();
	        }
	    };
	    worker.execute();
	}
	
	@Override
	protected void onAfterUndoRedo()
	{
		// TODO Auto-generated method stub
		
	}
	
	private Set<Center> getSelectedCenters(java.awt.Point point)
	{
		return getSelectedCenters(point, brushSizes.get(brushSizeComboBox.getSelectedIndex()));
	}
	
	private void handleMapChange(Set<Center> centers)
	{
		mapEditingPanel.addAllProcessingCenters(centers);
		mapEditingPanel.repaint();
		
		updateIconsInBackgroundThread();
	}




}
