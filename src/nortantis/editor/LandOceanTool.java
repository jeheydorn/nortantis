package nortantis.editor;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import nortantis.MapSettings;

public class LandOceanTool extends EditorTool
{

	public LandOceanTool(MapSettings settings)
	{
		super(settings);
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
		
		JLabel lblTools = new JLabel("Tool:");

		return toolOptionsPanel;
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		
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
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onBeforeCreateMap()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map)
	{
		settings.edits.initializeCenterEdits(mapParts.graph.centers);
		settings.edits.initializeRegionEdits(mapParts.graph.regions);
		return null;
	}

	@Override
	public void onSwitchingAway()
	{
		// TODO Auto-generated method stub
		
	}

}
