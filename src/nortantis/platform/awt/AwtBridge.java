package nortantis.platform.awt;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import nortantis.platform.*;
import nortantis.platform.skia.SkiaImage;

/**
 * Bridge class for converting between platform-agnostic types and AWT types. This class can efficiently handle both AWT and Skia platform
 * types, using optimized bulk operations when possible.
 */
public class AwtBridge
{
	/**
	 * Converts any platform Image to a BufferedImage for use with Swing components.
	 */
	public static BufferedImage toBufferedImage(Image image)
	{
		if (image == null)
		{
			return null;
		}

		if (image instanceof AwtImage)
		{
			return ((AwtImage) image).image;
		}

		if (image instanceof SkiaImage)
		{
			SkiaImage skiaImage = (SkiaImage) image;
			int width = skiaImage.getWidth();
			int height = skiaImage.getHeight();

			BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			int[] destPixels = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

			int[] srcPixels = skiaImage.readPixelsToIntArray();
			if (srcPixels != null)
			{
				System.arraycopy(srcPixels, 0, destPixels, 0, srcPixels.length);
			}

			return bi;
		}

		// Fallback for unknown image types: pixel-by-pixel conversion
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				bi.setRGB(x, y, image.getRGB(x, y));
			}
		}
		return bi;
	}

	/**
	 * Creates an AwtImage from a BufferedImage.
	 */
	public static Image fromBufferedImage(BufferedImage image)
	{
		if (image == null)
		{
			return null;
		}
		return new AwtImage(image);
	}

	/**
	 * Converts any platform Color to a java.awt.Color for use with Swing components.
	 */
	public static java.awt.Color toAwtColor(Color color)
	{
		if (color == null)
		{
			return null;
		}

		if (color instanceof AwtColor)
		{
			return ((AwtColor) color).color;
		}

		// Works for SkiaColor and any other Color implementation
		return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	/**
	 * Creates an AwtColor from a java.awt.Color.
	 */
	public static Color fromAwtColor(java.awt.Color color)
	{
		if (color == null)
		{
			return null;
		}
		return new AwtColor(color);
	}

	public static Color fromAwtColorToPlatformColor(java.awt.Color color)
	{
		if (color == null)
		{
			return null;
		}
		return PlatformFactory.getInstance().createColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	/**
	 * Converts any platform Font to a java.awt.Font for use with Swing components.
	 */
	public static java.awt.Font toAwtFont(Font font)
	{
		if (font == null)
		{
			return null;
		}

		if (font instanceof AwtFont)
		{
			return ((AwtFont) font).font;
		}

		// Works for SkiaFont and any other Font implementation
		int awtStyle = java.awt.Font.PLAIN;
		FontStyle style = font.getStyle();
		if (style == FontStyle.Bold)
		{
			awtStyle = java.awt.Font.BOLD;
		}
		else if (style == FontStyle.Italic)
		{
			awtStyle = java.awt.Font.ITALIC;
		}
		else if (style == FontStyle.BoldItalic)
		{
			awtStyle = java.awt.Font.BOLD | java.awt.Font.ITALIC;
		}
		return new java.awt.Font(font.getName(), awtStyle, (int) font.getSize());
	}

	/**
	 * Creates an AwtFont from a java.awt.Font.
	 */
	public static Font fromAwtFont(java.awt.Font font)
	{
		if (font == null)
		{
			return null;
		}
		return new AwtFont(font);
	}

	/**
	 * Wraps a Graphics2D in an AwtPainter for platform-agnostic drawing. Note: When using this with SkiaFactory as the main platform, the
	 * Painter will only work correctly with AWT-based platform types (use fromAwtColor, fromAwtFont for colors/fonts passed to this
	 * Painter).
	 */
	public static Painter wrapGraphics(Graphics2D g)
	{
		if (g == null)
		{
			return null;
		}
		return new AwtPainter(g);
	}
}
