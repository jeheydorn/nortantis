package nortantis.editor;

import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import nortantis.ImagePanel;
import nortantis.MapSettings;
import util.ImageHelper;

public class OceanTool extends EditorTool
{

	public OceanTool(MapSettings settings)
	{
		super(settings);
	}

	@Override
	public String getToolbarName()
	{
		return "Ocean";
	}

	@Override
	public JPanel getToolOptionsPanel()
	{
		return new JPanel();
	}

	@Override
	public ImagePanel getDisplayPanel()
	{
		BufferedImage placeHolder = ImageHelper.read("assets/drawing_map.png");
		ImagePanel displayPanel = new MapEditingPanel(placeHolder);
		displayPanel.setLayout(new BorderLayout());
		return displayPanel;
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
		// TODO Auto-generated method stub
		return null;
	}

}
