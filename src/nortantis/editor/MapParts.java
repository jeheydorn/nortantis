package nortantis.editor;

import nortantis.Background;
import nortantis.IconDrawer;
import nortantis.NameCreator;
import nortantis.WorldGraph;
import nortantis.platform.Color;
import nortantis.platform.Image;

/**
 * Holds pieces of a map created while generating it which are needed for editing it. This is also used to cache some parts for faster
 * drawing when editing.
 *
 */
public class MapParts
{
	public MapParts()
	{

	}

	public MapParts(MapParts other)
	{
		this.graph = other.graph;
		this.textBackground = other.textBackground;
		this.nameCreator = other.nameCreator;
		this.background = other.background;
		this.iconDrawer = other.iconDrawer;
		this.frayedBorderBlur = other.frayedBorderBlur;
		this.frayedBorderMask = other.frayedBorderMask;
		this.frayedBorderColor = other.frayedBorderColor;
		this.grunge = other.grunge;
		this.mapBeforeAddingText = other.mapBeforeAddingText;
	}

	/**
	 * Used as an input and output during map creation.
	 */
	public WorldGraph graph;

	/**
	 * Input and output. Needed for text drawing to allow erasing icons near letters.
	 */
	public Image textBackground;

	/**
	 * Used only as an output during map creation.
	 */
	public NameCreator nameCreator;

	/*
	 * Input and output.
	 */
	public Background background;

	/**
	 * Output.
	 */
	public IconDrawer iconDrawer;

	/**
	 * Input and output.
	 */
	public Image frayedBorderBlur;

	/**
	 * Input and output.
	 */
	public Image frayedBorderMask;

	/**
	 * This is stored here because the editor needs it to re-draw frayed borders, and the editor doesn't keep the MapSettings object around.
	 */
	public Color frayedBorderColor;

	/**
	 * Input and output.
	 */
	public Image grunge;

	/**
	 * This field caches the map just before adding text and other values need for text drawing so that enabling/disabling text in the
	 * editor is fast.
	 */
	public Image mapBeforeAddingText;

}
