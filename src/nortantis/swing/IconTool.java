package nortantis.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.util.AssetsPath;

public class IconTool extends EditorTool
{

	private JRadioButton mountainsButton;
	private JRadioButton treesButton;
	private JComboBox<ImageIcon> brushSizeComboBox;
	private RowHider brushSizeHider;
	private JRadioButton hillsButton;
	private JRadioButton dunesButton;
	private IconTypeButtons mountainTypes;
	private IconTypeButtons hillTypes;
	private IconTypeButtons duneTypes;
	private IconTypeButtons treeTypes;
	private IconTypeButtons cityTypes;
	private JSlider densitySlider;
	private Random rand;
	private RowHider densityHider;
	private JRadioButton eraseButton;
	private JRadioButton eraseAllButton;
	private JRadioButton eraseMountainsButton;
	private JRadioButton eraseHillsButton;
	private JRadioButton eraseDunesButton;
	private JRadioButton eraseTreesButton;
	private RowHider eraseOptionsHider;
	private JRadioButton riversButton;
	private JRadioButton citiesButton;
	private RowHider riverOptionHider;
	private JSlider riverWidthSlider;
	private Corner riverStart;
	private JCheckBox highlightRiversCheckbox;
	private JRadioButton eraseRiversButton;
	private JRadioButton eraseCitiesButton;
	private RowHider cityTypeHider;

	public IconTool(MainWindow parent, ToolsPanel toolsPanel)
	{
		super(parent, toolsPanel);
		rand = new Random();
	}

	@Override
	public String getToolbarName()
	{
		return "Icons";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get(AssetsPath.get(), "internal/Icon tool.png").toString();
	}

	@Override
	public void onBeforeSaving()
	{		
	}

	@Override
	public void onSwitchingAway()
	{
		mapEditingPanel.setHighlightRivers(false);
	}

	@Override
	protected JPanel createToolsOptionsPanel()
	{
		SwingHelper.resetGridY();
		
		JPanel toolOptionsPanel = new JPanel();
		toolOptionsPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		toolOptionsPanel.setLayout(new GridBagLayout());
		
		// Tools
		{
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

	
		    SwingHelper.addLabelAndComponentsToPanelVertical(toolOptionsPanel, "Brush:", "", 
		    		radioButtons);
		}
	    
		mountainTypes = createRadioButtonsForIconType(toolOptionsPanel, IconType.mountains);
		hillTypes = createRadioButtonsForIconType(toolOptionsPanel, IconType.hills);
		duneTypes = createRadioButtonsForIconType(toolOptionsPanel, IconType.sand);
		treeTypes = createRadioButtonsForIconType(toolOptionsPanel, IconType.trees);
		// Prevent cacti from being the default tree brush
		if (treeTypes.buttons.size() > 1 && treeTypes.buttons.get(0).getText().equals("cacti"))
		{
			treeTypes.buttons.get(1).setSelected(true);
		}
		
		JLabel lblType = new JLabel(toolsPanel.cityIconsType == null || toolsPanel.cityIconsType.isEmpty() ? "<not set>"
				: toolsPanel.cityIconsType);
		JButton changeButton = new JButton("Change");
		changeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				// TODO Throw up a modal dialog that warns what will happen and sets toolsPanel.cityIconsType. 
				// It should also update lblType and cause a full re-draw.
				
				//toolsPanel.cityIconsSetComboBox = new JComboBox<String>();	
				
				//SwingHelper.initializeComboBoxItems(cityIconsSetComboBox, ImageCache.getInstance().getIconSets(IconType.cities), 
					//	settings.cityIconSetName);

			}
		});
		cityTypeHider = SwingHelper.addLabelAndComponentsToPanelVertical(toolOptionsPanel, "City icons type:", "", 
				Arrays.asList(lblType, changeButton));
		
		cityTypes = createRadioButtonsForCities(toolOptionsPanel);
		
		// River options
		{
			riverWidthSlider = new JSlider(1, 15);
			riverWidthSlider.setValue(1);
			riverWidthSlider.setPreferredSize(new Dimension(160, 50));
			SwingHelper.setSliderWidthForSidePanel(riverWidthSlider);
		    riverOptionHider = SwingHelper.addLabelAndComponentToPanel(toolOptionsPanel, "Width:", "", riverWidthSlider);
		}
		
		// Eraser options
		{
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
		    eraseOptionsHider = SwingHelper.addLabelAndComponentsToPanelVertical(toolOptionsPanel, "Erase:", "", radioButtons);
		}
		
		densitySlider = new JSlider(1, 50);
		densitySlider.setValue(10);
		densitySlider.setPreferredSize(new Dimension(160, 50));
		SwingHelper.setSliderWidthForSidePanel(densitySlider);
		densityHider = SwingHelper.addLabelAndComponentToPanel(toolOptionsPanel, "Density:", "", densitySlider);
	    
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
	    brushSizeHider = SwingHelper.addLabelAndComponentToPanel(toolOptionsPanel, "Brush size:", "", brushSizeComboBox);
	    
	    highlightRiversCheckbox = new JCheckBox("Highlight rivers");
	    highlightRiversCheckbox.setToolTipText("Highlight rivers to make them easier to see.");
	    SwingHelper.addLabelAndComponentToPanel(toolOptionsPanel, "", "", highlightRiversCheckbox);
	    highlightRiversCheckbox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mapEditingPanel.setHighlightRivers(highlightRiversCheckbox.isSelected());
				mapEditingPanel.repaint();
			}
		});
	    		
	    
	    // Prevent the panel from shrinking when components are hidden.
	    toolOptionsPanel.add(Box.createRigidArea(new Dimension(SwingHelper.sidePanelPreferredWidth - 25, 0)));
	    
		mountainsButton.doClick();
			    
	    return toolOptionsPanel;
	}
	
	private void updateTypePanels()
	{
		mountainTypes.hider.setVisible(mountainsButton.isSelected());
		hillTypes.hider.setVisible(hillsButton.isSelected());
		duneTypes.hider.setVisible(dunesButton.isSelected());
		treeTypes.hider.setVisible(treesButton.isSelected());
		cityTypes.hider.setVisible(citiesButton.isSelected());
		cityTypeHider.setVisible(citiesButton.isSelected());
		densityHider.setVisible(treesButton.isSelected());
		eraseOptionsHider.setVisible(eraseButton.isSelected());
		riverOptionHider.setVisible(riversButton.isSelected());
		brushSizeHider.setVisible(!riversButton.isSelected());
	}
	
	private IconTypeButtons createRadioButtonsForIconType(JPanel toolOptionsPanel, IconType iconType)
	{
	    ButtonGroup group = new ButtonGroup();
	    List<JRadioButton> radioButtons = new ArrayList<>();
	    for (String groupId : ImageCache.getInstance().getIconGroupNames(iconType, iconType == IconType.cities ? toolsPanel.cityIconsType : null))
	    {
	    	JRadioButton button = new JRadioButton(groupId);
	    	group.add(button);
	    	radioButtons.add(button);
	    }
	    if (radioButtons.size() > 0)
	    {
	    	((JRadioButton)radioButtons.get(0)).setSelected(true);
	    }
	    return new IconTypeButtons(SwingHelper.addLabelAndComponentsToPanelVertical(toolOptionsPanel, "Type:", "", radioButtons), radioButtons);
	}
	
	private IconTypeButtons createRadioButtonsForCities(JPanel toolOptionsPanel)
	{
	    ButtonGroup group = new ButtonGroup();
	    List<JRadioButton> radioButtons = new ArrayList<>();
	    for (String fileNameWithoutWidthOrExtension : ImageCache.getInstance()
	    		.getIconGroupFileNamesWithoutWidthOrExtension(IconType.cities, null, toolsPanel.cityIconsType))
	    {
	    	JRadioButton button = new JRadioButton(fileNameWithoutWidthOrExtension);
	    	group.add(button);
	    	radioButtons.add(button);
	    }
	    if (radioButtons.size() > 0)
	    {
	    	((JRadioButton)radioButtons.get(0)).setSelected(true);
	    }
	    return new IconTypeButtons(SwingHelper.addLabelAndComponentsToPanelVertical(toolOptionsPanel, "Cities:", "", radioButtons), radioButtons);
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

		Set<Center> selected = getSelectedLandCenters(e.getPoint());

		if (mountainsButton.isSelected())
		{
			String rangeId = mountainTypes.getSelectedOption();
			for (Center center : selected)
			{
				mainWindow.edits.centerEdits.get(center.index).icon = new CenterIcon(CenterIconType.Mountain, rangeId, Math.abs(rand.nextInt()));
			}
		}
		else if (hillsButton.isSelected())
		{
			String rangeId = hillTypes.getSelectedOption();
			for (Center center : selected)
			{
				mainWindow.edits.centerEdits.get(center.index).icon = new CenterIcon(CenterIconType.Hill, rangeId, Math.abs(rand.nextInt()));
			}
		}
		else if (dunesButton.isSelected())
		{
			String rangeId = duneTypes.getSelectedOption();
			for (Center center : selected)
			{
				mainWindow.edits.centerEdits.get(center.index).icon = new CenterIcon(CenterIconType.Dune, rangeId, Math.abs(rand.nextInt()));
			}		
		}
		else if (treesButton.isSelected())
		{
			String treeType = treeTypes.getSelectedOption();
			for (Center center : selected)
			{
				mainWindow.edits.centerEdits.get(center.index).trees = new CenterTrees(treeType, densitySlider.getValue() / 10.0, 
						Math.abs(rand.nextLong()));
			}		
		}
		else if (citiesButton.isSelected())
		{
			String cityName = cityTypes.getSelectedOption();
			for (Center center : selected)
			{
				CenterIcon cityIcon = new CenterIcon(CenterIconType.City, cityName);
				// Only add the city if it will be drawn. That way, if somebody later shrinks the city image or swaps out 
				// the image files, previously hidden cities don't start popping up along coastlines and lakes.
				// Note that all icons can fail to draw because they would overlap an ocean or lake, but I don't think it's
				// a big deal for other icon types.
				if (mainWindow.mapParts.iconDrawer.doesCityFitOnLand(center, new CenterIcon(CenterIconType.City, cityName)))
				{
					mainWindow.edits.centerEdits.get(center.index).icon = cityIcon;
				}
			}		
		}
		else if (eraseButton.isSelected())
		{
			if (eraseAllButton.isSelected())
			{
				for (Center center : selected)
				{
					mainWindow.edits.centerEdits.get(center.index).trees = null;
					mainWindow.edits.centerEdits.get(center.index).icon = null;
					for (Edge edge : center.borders)
					{
						EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
						if (eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
						{
							eEdit.riverLevel = 0;
						}
					}
				}
			}
			else if (eraseMountainsButton.isSelected())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
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
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
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
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
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
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					cEdit.trees = null;
				}	
			}
			else if (eraseCitiesButton.isSelected())
			{
				for (Center center : selected)
				{
					CenterEdit cEdit = mainWindow.edits.centerEdits.get(center.index);
					if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.City)
					{
						cEdit.icon = null;
					}
				}	
			}
			else if (eraseRiversButton.isSelected())
			{
				// When deleting rivers with the single-point brush size, highlight the closest edge instead of a polygon.
				Set<Edge> possibleRivers = getSelectedEdges(e.getPoint(), brushSizes.get(brushSizeComboBox.getSelectedIndex()));
				for (Edge edge : possibleRivers)
				{
					EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
					eEdit.riverLevel = 0;
				}
				mapEditingPanel.clearHighlightedEdges();
			}
		}
		handleMapChange(selected);
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
			riverStart = mainWindow.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
		}
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{		
		if (riversButton.isSelected())
		{
			Corner end = mainWindow.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
			Set<Edge> river = filterOutOceanAndCoastEdges(mainWindow.mapParts.graph.findPathGreedy(riverStart, end));
			for (Edge edge : river)
			{
				int base = (riverWidthSlider.getValue() - 1);
				int riverLevel = (base * base * 2) + VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn + 1;
				mainWindow.edits.edgeEdits.get(edge.index).riverLevel = riverLevel;
			}
			riverStart = null;
			mapEditingPanel.clearHighlightedEdges();
			mapEditingPanel.repaint();
			
			if (river.size() > 0)
			{
				mainWindow.createAndShowMapIncrementalUsingEdges(river);
			}
		}
		
		undoer.setUndoPoint(this);
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
			highlightHoverCentersOrEdgesAndBrush(e);
			mapEditingPanel.repaint();
		}
	}
	
	private void highlightHoverCentersOrEdgesAndBrush(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.hideBrush();
		if (eraseButton.isSelected() && eraseRiversButton.isSelected())
		{
			int brushDiameter = brushSizes.get(brushSizeComboBox.getSelectedIndex());
			if (brushDiameter > 1)
			{
				mapEditingPanel.showBrush(e.getPoint(), brushDiameter);
			}
			Set<Edge> candidates = getSelectedEdges(e.getPoint(), brushDiameter);
			for (Edge edge : candidates)
			{
				EdgeEdit eEdit = mainWindow.edits.edgeEdits.get(edge.index);
				if (eEdit.riverLevel > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
				{
					mapEditingPanel.addHighlightedEdge(edge);
				}
			}
		}
		else
		{
			Set<Center> selected = getSelectedCenters(e.getPoint());
			mapEditingPanel.addHighlightedCenters(selected);
			mapEditingPanel.setCenterHighlightMode(HighlightMode.outlineEveryCenter);	
		}
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (riversButton.isSelected())
		{
			if (riverStart != null)
			{
				mapEditingPanel.clearHighlightedEdges();
				Corner end = mainWindow.mapParts.graph.findClosestCorner(getPointOnGraph(e.getPoint()));
				Set<Edge> river = filterOutOceanAndCoastEdges(mainWindow.mapParts.graph.findPathGreedy(riverStart, end));
				mapEditingPanel.addHighlightedEdges(river);
				mapEditingPanel.repaint();
			}
		}
		else
		{
			highlightHoverCentersOrEdgesAndBrush(e);
			handleMousePressOrDrag(e);
		}
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.hideBrush();
		if (eraseButton.isSelected() && eraseRiversButton.isSelected())
		{
			mapEditingPanel.clearHighlightedEdges();
		}
		mapEditingPanel.repaint();
	}
	
	@Override
	public void onActivate()
	{
		mapEditingPanel.setHighlightRivers(highlightRiversCheckbox.isSelected());
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map)
	{	
 		return map;
	}
	
	@Override
	protected void onAfterUndoRedo(MapChange change)
	{	
		mainWindow.createAndShowMapFromChange(change);
	}
	
	private Set<Center> getSelectedCenters(java.awt.Point pointFromMouse)
	{
		return getSelectedCenters(pointFromMouse, brushSizes.get(brushSizeComboBox.getSelectedIndex()));
	}
	
	private void handleMapChange(Set<Center> centers)
	{
		mainWindow.createAndShowMapIncrementalUsingCenters(centers);
	}




}
