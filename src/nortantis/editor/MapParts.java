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
	 * Input and output.
	 */
	public Image grunge;

	/**
	 * This field caches the map just before adding text and other values need for text drawing so that enabling/disabling text in the
	 * editor is fast.
	 */
	public Image mapBeforeAddingText;

	/**
	 * The ocean waves and ocean shading masks of the whole map, kept so that incremental draws that can't change them copy them rather than
	 * drawing them again, which lets those draws skip the padding ocean effects would otherwise need. Either can be null when the map has
	 * none. Only meaningful when {@link #areOceanEffectsCached} is true.
	 */
	public Image oceanWaves;
	public Image oceanShading;
	public boolean areOceanEffectsCached;

	public void setOceanEffects(Image oceanWaves, Image oceanShading)
	{
		closeOceanEffects();
		this.oceanWaves = oceanWaves;
		this.oceanShading = oceanShading;
		areOceanEffectsCached = true;
	}

	public void closeOceanEffects()
	{
		if (oceanWaves != null)
		{
			oceanWaves.close();
		}
		oceanWaves = null;
		if (oceanShading != null)
		{
			oceanShading.close();
		}
		oceanShading = null;
		areOceanEffectsCached = false;
	}

	public void closeImages()
	{
		closeOceanEffects();
		if (textBackground != null)
		{
			textBackground.close();
		}
		if (frayedBorderBlur != null)
		{
			frayedBorderBlur.close();
		}
		if (frayedBorderMask != null)
		{
			frayedBorderMask.close();
		}
		if (grunge != null)
		{
			grunge.close();
		}
		if (mapBeforeAddingText != null)
		{
			mapBeforeAddingText.close();
		}
		if (background != null)
		{
			background.closeImages();
		}
	}
}
