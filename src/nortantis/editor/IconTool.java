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
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;

import hoten.geom.Point;
import hoten.voronoi.Center;
import hoten.voronoi.Corner;
import hoten.voronoi.Edge;
import hoten.voronoi.VoronoiGraph;
import nortantis.CenterIcon;
import nortantis.CenterIconType;
import nortantis.CenterTrees;
import nortantis.IconDrawer;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.util.ImageHelper;

public class IconTool extends EditorTool
{

	private JRadioButton mountainsButton;
	private JRadioButton treesButton;
	private BufferedImage mapWithouticons;
	private JComboBox<ImageIcon> brushSizeComboBox;
	private JPanel brushSizePanel;
	private JRadioButton hillsButton;
	private JRadioButton dunesButton;
	private IconTypeButtons mountainTypes;
	private IconTypeButtons hillTypes;
	private IconTypeButtons duneTypes;
	private IconTypeButtons treeTypes;
	private IconTypeButtons cityTypes;
	private JSlider densitySlider;
	private Random rand;
	private JPanel densityPanel;
	private JRadioButton eraseButton;
	private JRadioButton eraseAllButton;
	private JRadioButton eraseMountainsButton;
	private JRadioButton eraseHillsButton;
	private JRadioButton eraseDunesButton;
	private JRadioButton eraseTreesButton;
	private JPanel eraseOptionsPanel;
	private JRadioButton riversButton;
	private JRadioButton citiesButton;
	private JPanel riverOptionPanel;
	private JSlider riverWidthSlider;
	private Corner riverStart;
	private JCheckBox showRiversOnTopCheckBox;
	private JRadioButton eraseRiversButton;
	private JRadioButton eraseCitiesButton;

	public IconTool(MapSettings settings, EditorFrame parent)
	{
		super(settings, parent);
		rand = new Random();
	}

	@Override
	public String getToolbarName()
	{
		return "Icon";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get("assets/internal/Icon tool.png").toString();
	}

	@Override
	public void onBeforeSaving()
	{		
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
		    
			riversButton = new JRadioButton("Rivers");
		    group.add(riversButton);
		    radioButtons.add(riversButton);
		    riversButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});
		    
			citiesButton = new JRadioButton("Cities");
		    group.add(citiesButton);
		    radioButtons.add(citiesButton);
		    citiesButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					updateTypePanels();
				}
			});
		    
			eraseButton = new JRadioButton("Erase");
		    group.add(eraseButton);
		    radioButtons.add(eraseButton);
		    eraseButton.addActionListener(new ActionListener()
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
		cityTypes = createRadioButtonsForCities(toolOptionsPanel);
		
		// River options
		{
			JLabel widthLabel = new JLabel("Width:");
			riverWidthSlider = new JSlider(1, 15);
			riverWidthSlider.setValue(1);
			riverWidthSlider.setPreferredSize(new Dimension(160, 50));
		    riverOptionPanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, widthLabel, riverWidthSlider);
		}
		
		// Eraser options
		{
		    JLabel typeLabel = new JLabel("Erase:");
		    ButtonGroup group = new ButtonGroup();
		    List<JRadioButton> radioButtons = new ArrayList<>();
		    
		    eraseAllButton = new JRadioButton("All");
		    group.add(eraseAllButton);
		    radioButtons.add(eraseAllButton);
		    
		    eraseMountainsButton = new JRadioButton(mountainsButton.getText());
		    group.add(eraseMountainsButton);
		    radioButtons.add(eraseMountainsButton);

		    eraseHillsButton = new JRadioButton(hillsButton.getText());
		    group.add(eraseHillsButton);
		    radioButtons.add(eraseHillsButton);

		    eraseDunesButton = new JRadioButton(dunesButton.getText());
		    group.add(eraseDunesButton);
		    radioButtons.add(eraseDunesButton);

		    eraseTreesButton = new JRadioButton(treesButton.getText());
		    group.add(eraseTreesButton);
		    radioButtons.add(eraseTreesButton);
		    
		    eraseCitiesButton = new JRadioButton(citiesButton.getText());
		    group.add(eraseCitiesButton);
		    radioButtons.add(eraseCitiesButton);

		    eraseRiversButton = new JRadioButton(riversButton.getText());
		    group.add(eraseRiversButton);
		    radioButtons.add(eraseRiversButton);

		    eraseAllButton.setSelected(true);
		    eraseOptionsPanel = EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, typeLabel, radioButtons);
		}
		
		JLabel densityLabel = new JLabel("density:");
		densitySlider = new JSlider(1, 50);
		densitySlider.setValue(10);
		densitySlider.setPreferredSize(new Dimension(160, 50));
		densityPanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, densityLabel, densitySlider);
	    
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
	    
	    JLabel showRiversLabel = new JLabel("");
	    showRiversOnTopCheckBox = new JCheckBox("Show rivers on top?");
	    showRiversOnTopCheckBox.setToolTipText("Temporarily show rivers on top of icons to make them visible in the editor.");
	    EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, showRiversLabel, showRiversOnTopCheckBox);
	    showRiversOnTopCheckBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				createAndShowMap();
			}
		});
	    		
	    
	    // Prevent the panel from shrinking when components are hidden.
	    toolOptionsPanel.add(Box.createRigidArea(new Dimension(EditorFrame.toolsPanelWidth - 25, 0)));
	    
		mountainsButton.doClick();
	    
	    return toolOptionsPanel;
	}
	
	private void updateTypePanels()
	{
		mountainTypes.panel.setVisible(mountainsButton.isSelected());
		hillTypes.panel.setVisible(hillsButton.isSelected());
		duneTypes.panel.setVisible(dunesButton.isSelected());
		treeTypes.panel.setVisible(treesButton.isSelected());
		cityTypes.panel.setVisible(citiesButton.isSelected());
		densityPanel.setVisible(treesButton.isSelected());
		eraseOptionsPanel.setVisible(eraseButton.isSelected());
		riverOptionPanel.setVisible(riversButton.isSelected());
		brushSizePanel.setVisible(!riversButton.isSelected());
	}
	
	private IconTypeButtons createRadioButtonsForIconType(JPanel toolOptionsPanel, String iconType)
	{
	    JLabel typeLabel = new JLabel("Type:");
	    ButtonGroup group = new ButtonGroup();
	    List<JRadioButton> radioButtons = new ArrayList<>();
	    for (String groupId : IconDrawer.getIconGroupNames(iconType, iconType == IconDrawer.citiesName ? settings.cityIconSetName : null))
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
	
	private IconTypeButtons createRadioButtonsForCities(JPanel toolOptionsPanel)
	{
	    JLabel typeLabel = new JLabel("Cities:");
	    ButtonGroup group = new ButtonGroup();
	    List<JRadioButton> radioButtons = new ArrayList<>();
	    for (String fileNameWithoutWidthOrExtension : IconDrawer.getIconGroupFileNamesWithoutWidthOrExtension(IconDrawer.citiesName, null, settings.cityIconSetName))
	    {
	    	JRadioButton button = new JRadioButton(fileNameWithoutWidthOrExtension);
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
	}
	
	private void handleMousePressOrDrag(MouseEvent e)
	{
		if (riversButton.isSelected())
		{
			return;
		}
		
		boolean needsFullRedraw = false;

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
		else if (citiesButton.isSelected())
		{
			String cityName = cityTypes.getSelectedOption();
			for (Center center : selected)
			{
				settings.edits.centerEdits.get(center.index).icon = new CenterIcon(CenterIconType.City, cityName);
			}		
		}
		else if (eraseButton.isSelected())
		{
			if (eraseAllButton.isSelected())
			{
				for (Center center : selected)
				{
					settings.edits.centerEdits.get(center.index).trees = null;
					settings.edits.centerEdits.get(center.index).icon = null;
					for (Edge edge : center.borders)
					{
						EdgeEdit eEdit = settings.edits.edgeEdits.get(edge.index);
						if (eEdit.riverLevel >= VoronoiGraph.riversThinnerThanThisWillNotBeDrawn)
						{
							needsFullRedraw = true;
							eEdit.riverLevel = 0;
						}
					}
				}
			}
			else if (eraseMountainsButton.isSelected())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = settings.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Mountain)
					{
						cEdit.icon = null;
					}
				}	
			}
			else if (eraseHillsButton.isSelected())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = settings.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Hill)
					{
						cEdit.icon = null;
					}
				}	
			}
			else if (eraseDunesButton.isSelected())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = settings.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Dune)
					{
						cEdit.icon = null;
					}
				}	
			}
			else if (eraseTreesButton.isSelected())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = settings.edits.centerEdits.get(center.index);
					cEdit.trees = null;
				}	
			}
			else if (eraseCitiesButton.isSelected())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = settings.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.City)
					{
						cEdit.icon = null;
					}
				}	
			}
			else if (eraseRiversButton.isSelected())
			{
				for (Center center : selected)
				{
					for (Edge edge : center.borders)
					{
						EdgeEdit eEdit = settings.edits.edgeEdits.get(edge.index);
						if (eEdit.riverLevel >= VoronoiGraph.riversThinnerThanThisWillNotBeDrawn)
						{
							needsFullRedraw = true;
							eEdit.riverLevel = 0;
						}
					}
				}	
			}
		}
		handleMapChange(selected, !needsFullRedraw);
	}
	
	private Set<Center> getSelectedLandCenters(java.awt.Point point)
	{
		Set<Center> selected = getSelectedCenters(point);
		return selected.stream().filter(c -> !c.isWater).collect(Collectors.toSet());
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{		
		handleMousePressOrDrag(e);
		
		if (riversButton.isSelected())
		{
			riverStart = mapParts.graph.findClosestCorner(new Point(e.getX(), e.getY()));
		}
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{		
		if (riversButton.isSelected())
		{
			Corner end = mapParts.graph.findClosestCorner(new Point(e.getX(), e.getY()));
			Set<Edge> river = filterOutOceanAndCoastEdges(mapParts.graph.findPathGreedy(riverStart, end));
			for (Edge edge : river)
			{
				int base = (riverWidthSlider.getValue() - 1);
				if (base >= 2)
				{
					base += 1; // Level 3 looks just like level 2, so skip it.
				}
				int riverLevel = (base * base * 6) + VoronoiGraph.riversThinnerThanThisWillNotBeDrawn + 1;
				settings.edits.edgeEdits.get(edge.index).riverLevel = riverLevel;
			}
			riverStart = null;
			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.addAllProcessingEdges(river);
			mapEditingPanel.repaint();
			
			if (river.size() > 0)
			{
				createAndShowMap();
			}
		}
		
		setUndoPoint();
	}
	
	private Set<Edge> filterOutOceanAndCoastEdges(Set<Edge> edges)
	{
		return edges.stream().filter(e -> (e.d0 == null || !e.d0.isWater ) && (e.d1 == null || !e.d1.isWater)).collect(Collectors.toSet());
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		
		if (!riversButton.isSelected())
		{
			highlightHoverCenters(e);
			mapEditingPanel.repaint();
		}
	}
	
	private void highlightHoverCenters(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		Set<Center> selected = getSelectedCenters(e.getPoint());
		mapEditingPanel.addAllHighlightedCenters(selected);
		mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);	
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (riversButton.isSelected())
		{
			if (riverStart != null)
			{
				mapEditingPanel.clearHighlightedEdges();
				Corner end = mapParts.graph.findClosestCorner(new Point(e.getX(), e.getY()));
				Set<Edge> river = filterOutOceanAndCoastEdges(mapParts.graph.findPathGreedy(riverStart, end));
				mapEditingPanel.setHighlightedEdges(river);
				mapEditingPanel.repaint();
			}
		}
		else
		{
			highlightHoverCenters(e);
			handleMousePressOrDrag(e);
		}
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
		settings.drawIcons = false;
		settings.drawRivers = false;
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map, boolean  isQuickUpdate)
	{
		if (!isQuickUpdate)
		{
			mapWithouticons = ImageHelper.deepCopy(map);
			
			if (!showRiversOnTopCheckBox.isSelected())
			{
				MapCreator.drawRivers(settings, mapParts.graph, map, mapParts.sizeMultiplier);
			}
			mapParts.iconDrawer.drawAllIcons(map, mapParts.landBackground);
		}
		
		if (showRiversOnTopCheckBox.isSelected())
		{
			MapCreator.drawRivers(settings, mapParts.graph, map, mapParts.sizeMultiplier);
		}
				 		
 		return map;
	}
	
	@Override
	protected BufferedImage drawMapQuickUpdate()
	{
		BufferedImage map = ImageHelper.deepCopy(mapWithouticons);
		if (!showRiversOnTopCheckBox.isSelected())
		{
			MapCreator.drawRivers(settings, mapParts.graph, map, mapParts.sizeMultiplier);
		}
		mapParts.iconDrawer.clearAndAddIconsFromEdits(settings.edits, mapParts.sizeMultiplier);
		mapParts.iconDrawer.drawAllIcons(map, mapParts.landBackground);
		if (showRiversOnTopCheckBox.isSelected())
		{
			MapCreator.drawRivers(settings, mapParts.graph, map, mapParts.sizeMultiplier);
		}

		return map;
	}
	
	@Override
	protected void onAfterUndoRedo(boolean requiresFullRedraw)
	{	
		createAndShowMap();
	}
	
	private Set<Center> getSelectedCenters(java.awt.Point point)
	{
		return getSelectedCenters(point, brushSizes.get(brushSizeComboBox.getSelectedIndex()));
	}
	
	private void handleMapChange(Set<Center> centers, boolean onlyUpdateIcons)
	{
		mapEditingPanel.addAllProcessingCenters(centers);
		mapEditingPanel.repaint();
		
		createAndShowMap(onlyUpdateIcons);
	}




}
