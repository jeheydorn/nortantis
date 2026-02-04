package nortantis.platform.awt;

import nortantis.platform.*;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.Image;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Bridge class for converting between platform-agnostic types and AWT types.
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

		// Fallback: pixel-by-pixel conversion
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		try (PixelReader pixels = image.createPixelReader())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					bi.setRGB(x, y, pixels.getRGB(x, y));
				}
			}
		}
		return bi;
	}

	/**
	 * Converts any platform Image to an AwtImage.
	 */
	public static AwtImage toAwtImage(Image image)
	{
		if (image == null)
		{
			return null;
		}

		if (image instanceof AwtImage)
		{
			return (AwtImage) image;
		}

		return new AwtImage(toBufferedImage(image));
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
	 * Wraps a BufferedImage in an AwtImage without copying. Use this when you need to write back to the original BufferedImage (e.g., for
	 * incremental updates to a display image).
	 */
	public static Image wrapBufferedImage(BufferedImage image)
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

		return new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	/**
	 * Creates a Color of the default platform implementation from a java.awt.Color.
	 */
	public static Color fromAwtColor(java.awt.Color color)
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
	 * Creates a Font for the current platform from a java.awt.Font.
	 */
	public static Font fromAwtFont(java.awt.Font font)
	{
		if (font == null)
		{
			return null;
		}

		return new AwtFont(font);
	}
}
