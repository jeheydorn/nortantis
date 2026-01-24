package nortantis.platform.awt;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import nortantis.platform.*;
import nortantis.platform.skia.SkiaFactory;
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
			if (image.getType() == ImageType.ARGB || image.getType() == ImageType.RGB)
			{
				// We can do this case faster using System.arracopy on the pixel array values.
				SkiaImage skiaImage = (SkiaImage) image;
				int width = skiaImage.getWidth();
				int height = skiaImage.getHeight();

				BufferedImage bi = new BufferedImage(width, height, image.getType() == ImageType.ARGB ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
				int[] destPixels = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

				int[] srcPixels = skiaImage.readPixelsToIntArray();
				if (srcPixels != null)
				{
					System.arraycopy(srcPixels, 0, destPixels, 0, srcPixels.length);
				}
				return bi;
			}
			else
			{
				return genericImageTypeToBufferedImage(image);
			}
		}

		assert false; // If I support a new image type for use with AWT, see if I can handle it here more efficiently than the code below.

		return genericImageTypeToBufferedImage(image);
	}

	private static BufferedImage genericImageTypeToBufferedImage(Image image)
	{
		// Fallback for unknown image types: pixel-by-pixel conversion
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
	 * Converts any platform Image to an Image of the type corresponding to the current PlatformFactory instance.
	 */
	private static Image toPlatformImage(Image image)
	{
		if (image == null)
		{
			return null;
		}

		if (PlatformFactory.getInstance() instanceof AwtFactory)
		{
			if (image instanceof AwtImage)
			{
				return image;
			}

			if (image instanceof SkiaImage)
			{
				return new AwtImage(toBufferedImage(image));
			}

			return new AwtImage(genericImageTypeToBufferedImage(image));
		}

		if (PlatformFactory.getInstance() instanceof SkiaFactory && image instanceof SkiaImage)
		{
			if (image instanceof AwtImage)
			{
				return slowCopyToPlatformImage(image);
			}

			if (image instanceof SkiaImage)
			{
				return image;
			}

			return slowCopyToPlatformImage(image);
		}

		return slowCopyToPlatformImage(image);
	}

	private static Image slowCopyToPlatformImage(Image image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		Image result = Image.create(width, height, image.getType());
		try (PixelReader pixels = image.createPixelReader(); PixelReaderWriter resultPixels = result.createPixelReaderWriter())
		{
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					resultPixels.setRGB(x, y, pixels.getRGB(x, y));
				}
			}
		}
		return result;
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

		return toPlatformImage(new AwtImage(image));
	}

	/**
	 * Wraps a BufferedImage in an AwtImage without converting to the platform type. Use this when you need to write back to the original
	 * BufferedImage (e.g., for incremental updates to a display image).
	 *
	 * Unlike fromBufferedImage, this does NOT convert to SkiaImage when using SkiaFactory, so changes written to the returned Image will be
	 * reflected in the original BufferedImage.
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

		// Works for SkiaColor and any other Color implementation
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
	 * Creates a generic Font for the current platform type from a java.awt.Font.
	 */
	public static Font fromAwtFont(java.awt.Font font)
	{
		if (font == null)
		{
			return null;
		}

		if (PlatformFactory.getInstance() instanceof AwtFactory)
		{
			return new AwtFont(font);
		}

		FontStyle style = FontStyle.Plain;
		if (font.isBold())
		{
			if (font.isItalic())
			{
				style = FontStyle.BoldItalic;
			}
			else
			{
				style = FontStyle.Bold;
			}
		}
		else if (font.isItalic())
		{
			style = FontStyle.Italic;
		}

		return PlatformFactory.getInstance().createFont(font.getName(), style, font.getSize());
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
