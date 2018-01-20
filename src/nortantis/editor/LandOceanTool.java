package nortantis.editor;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

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
	public void handleZoomChange(double zoomLevel)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected JPanel createToolsOptionsPanel()
	{
		// TODO Auto-generated method stub
		return null;
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
		return null;
	}

}
