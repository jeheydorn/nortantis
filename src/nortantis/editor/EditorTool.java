package nortantis.editor;

import javax.swing.JPanel;

import nortantis.ImagePanel;

public interface EditorTool
{
	String getToolbarName();
	
	JPanel getToolOptionsPanel();
	
	ImagePanel getDisplayPanel();
	
	void onBeforeSaving();
	
	/**
	 * Handles when zoom level changes in the main display.
	 * @param zoomLevel Between 0.25 and 1.
	 */
	void handleZoomChange(double zoomLevel);
}
