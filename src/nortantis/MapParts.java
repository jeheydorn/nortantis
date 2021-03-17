package nortantis;

import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import hoten.voronoi.Center;

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
	 * Used only as an output during map creation.
	 */
	public BufferedImage landBackground;
	
	/**
	 * Used only as an output during map creation.
	 */
	public List<Set<Center>> mountainGroups;
	
	/**
	 * Used only as an output during map creation.
	 */
	public TextDrawer textDrawer;
	
	/*
	 * Input and output. But regionColors will be generated each time.
	 */
	public Background background;
	
	/**
	 * Input and output.
	 */
	public IconDrawer iconDrawer;
	
	/**
	 * Used only as an output during map creation.
	 */
	public double sizeMultiplier;
	
	/**
	 * Used only as an output during map creation;
	 */
	public List<IconDrawTask> cityDrawTasks;
	
}
