package nortantis;

import java.awt.geom.Area;
import java.awt.image.BufferedImage;

import nortantis.graph.geom.Point;
import nortantis.util.AssetsPath;

/**
 * Stores things needed to draw an icon onto the map.
 * @author joseph
 *
 */
public class IconDrawTask implements Comparable<IconDrawTask>
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
	
	/**
	 * A flag to tell which icons could not be drawn because they don't fit in the space they are supposed to be drawn. 
	 */
	boolean failedToDraw;
	IconType type;
	
	public IconDrawTask(BufferedImage icon, BufferedImage mask, IconType type, Point centerLoc, int scaledWidth,
			boolean needsScale, boolean ignoreMaxSize)
	{
		this(icon, mask, type, centerLoc, scaledWidth, needsScale, ignoreMaxSize, "");
	}

	public IconDrawTask(BufferedImage icon, BufferedImage mask, IconType type, Point centerLoc, int scaledWidth,
			boolean needsScale, boolean ignoreMaxSize, String fileName)
	{
		this.icon = icon;
		this.mask = mask;
		this.centerLoc = centerLoc;
		this.scaledWidth = scaledWidth;
		this.needsScale = needsScale;
		this.type = type;
		
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
			// The path passed to ImageCache.getInstance insn't important so long as other calls to getScaledImageByWidth
			// use the same path, since getScaledImageByWidth doesn't load images from disk.
	       	icon = ImageCache.getInstance(AssetsPath.getInstallPath()).getScaledImageByWidth(icon, scaledWidth);
	      	mask = ImageCache.getInstance(AssetsPath.getInstallPath()).getScaledImageByWidth(mask, scaledWidth);
		}
	}
	
	public Area createArea()
	{
		return new Area(new java.awt.Rectangle((int)(centerLoc.x - scaledWidth/2.0), (int)(centerLoc.y - scaledHeight/2.0), scaledWidth, scaledHeight));
	}
	
	public nortantis.graph.geom.Rectangle createBounds()
	{
		return new nortantis.graph.geom.Rectangle(centerLoc.x - scaledWidth/2.0, centerLoc.y - scaledHeight/2.0, scaledWidth, scaledHeight);
	}
	
	public boolean overlaps(nortantis.graph.geom.Rectangle bounds)
	{
		return createBounds().overlaps(bounds);
	}

	@Override
	public int compareTo(IconDrawTask other)
	{
		return Integer.compare(yBottom, other.yBottom);
	}
}