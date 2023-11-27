package nortantis.editor;

import java.awt.Color;
import java.awt.image.BufferedImage;

import nortantis.Background;
import nortantis.IconDrawer;
import nortantis.TextDrawer;
import nortantis.WorldGraph;

/**
 * Holds pieces of a map created while generating it which are needed for editing it. This is also used to cache some parts for faster
 * drawing when editing.
 *
 */
public class MapParts
{
	/**
	 * Used as an input and output during map creation.
	 */
	public WorldGraph graph;

	/**
	 * Input and output. Needed for text drawing to allow erasing icons near letters.
	 */
	public BufferedImage textBackground;

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

	/**
	 * Input and output.
	 */
	public BufferedImage frayedBorderBlur;

	/**
	 * Input and output.
	 */
	public BufferedImage frayedBorderMask;

	/**
	 * This is stored here because the editor needs it to re-draw frayed borders, and the editor doesn't keep the MapSettings object around.
	 */
	public Color frayedBorderColor;

	/**
	 * Input and output.
	 */
	public BufferedImage grunge;

	/**
	 * These fields cache the map just before adding text and other values need for text drawing so that text changes in the editor can
	 * re-draw quickly. This is also useful as a cache when re-drawing to hide text in the editor.
	 */
	public BufferedImage mapBeforeAddingText;


}
