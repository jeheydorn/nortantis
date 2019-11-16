package nortantis;

import java.awt.geom.Area;
import java.awt.image.BufferedImage;

import hoten.geom.Point;

/**
 * Stores things needed to draw an icon onto the map.
 * @author joseph
 *
 */
class IconDrawTask implements Comparable<IconDrawTask>
{
	BufferedImage icon;
	BufferedImage mask;
	Point centerLoc;
	int scaledWidth;
	int scaledHeight;
	int yBottom;
	boolean needsScale;
	boolean ignoreMaxSize;
	String fileName;
	
	public IconDrawTask(BufferedImage icon, BufferedImage mask, Point centerLoc, int scaledWidth,
			boolean needsScale, boolean ignoreMaxSize)
	{
		this(icon, mask, centerLoc, scaledWidth, needsScale, ignoreMaxSize, "");
	}

	public IconDrawTask(BufferedImage icon, BufferedImage mask, Point centerLoc, int scaledWidth,
			boolean needsScale, boolean ignoreMaxSize, String fileName)
	{
		this.icon = icon;
		this.mask = mask;
		this.centerLoc = centerLoc;
		this.scaledWidth = scaledWidth;
		this.needsScale = needsScale;
		
   		double aspectRatio = ((double)icon.getWidth())/icon.getHeight();
   		scaledHeight = (int)(scaledWidth/aspectRatio);
       	yBottom = (int)(centerLoc.y + (scaledHeight/2.0));
       	
       	this.ignoreMaxSize = ignoreMaxSize;
       	this.fileName = fileName;
	}
	
	public void scaleIcon()
	{
		if (needsScale)
		{
	       	icon = ImageCache.getInstance().getScaledImage(icon, scaledWidth);
	      	mask = ImageCache.getInstance().getScaledImage(mask, scaledWidth);
		}
	}
	
	public Area createArea()
	{
		return new Area(new java.awt.Rectangle((int)(centerLoc.x - scaledWidth/2.0), (int)(centerLoc.y - scaledHeight/2.0), scaledWidth, scaledHeight));
	}

	@Override
	public int compareTo(IconDrawTask other)
	{
		return Integer.compare(yBottom, other.yBottom);
	}
}