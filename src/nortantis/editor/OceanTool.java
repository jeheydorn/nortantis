package nortantis.editor;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import nortantis.ImagePanel;
import util.ImageHelper;

public class OceanTool implements EditorTool
{

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
		ImagePanel displayPanel = new TextEditingPanel(placeHolder);
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

}
