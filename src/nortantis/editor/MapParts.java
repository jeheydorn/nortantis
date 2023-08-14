package nortantis.editor;

import java.awt.image.BufferedImage;

import nortantis.Background;
import nortantis.IconDrawer;
import nortantis.TextDrawer;
import nortantis.WorldGraph;

/**
 * Holds pieces of a map created while generating it which are needed for editing it. This is also used to cache some parts
 * for faster drawing when editing.
 *
 */
public class MapParts
{
	/**
	 * Used as an input and output during map creation.
	 */
	public WorldGraph graph;
	
	/**
	 *  Input and output.
	 */
	public BufferedImage landBackground;
		
	/**
	 * Used only as an output during map creation.
	 */
	public TextDrawer textDrawer;
	
	/*
	 * Input and output.
	 */
	public Background background;
	
	/**
	 * Input and output.
	 */
	public IconDrawer iconDrawer;

	
}
