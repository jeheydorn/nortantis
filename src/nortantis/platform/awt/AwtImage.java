package nortantis.platform.awt;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;

import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PixelReader;
import nortantis.platform.PixelReaderWriter;
import nortantis.platform.PixelWriter;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

class AwtImage extends Image
{
	public BufferedImage image;

	public AwtImage(int width, int height, ImageType type)
	{
		super(type);
		image = new BufferedImage(width, height, toBufferedImageType(type));
	}

	public AwtImage(BufferedImage bufferedImage)
	{
		super(toImageType(bufferedImage.getType()));
		image = bufferedImage;
	}

	/**
	 * Returns true if the BufferedImage uses an int-based format that is compatible with our expected ARGB/RGB pixel interpretation.
	 * TYPE_INT_BGR is excluded because it has reversed byte order.
	 */
	boolean isCompatibleIntFormat()
	{
		int type = image.getType();
		return type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB;
	}

	@Override
	public PixelReader createPixelReader()
	{
		return new AwtPixelReader(this);
	}

	@Override
	public PixelReaderWriter createPixelReaderWriter()
	{
		return new AwtPixelReaderWriter(this);
	}

	@Override
	public PixelReader innerCreateNewPixelReader(IntRectangle bounds)
	{
		return new AwtPixelReader(this, bounds);
	}

	@Override
	public PixelReaderWriter innerCreateNewPixelReaderWriter(IntRectangle bounds)
	{
		return new AwtPixelReaderWriter(this, bounds);
	}

	@Override
	protected PixelWriter innerCreateNewPixelWriter(IntRectangle bounds)
	{
		// AWT uses a backing array reference, so no initial read is needed anyway
		return new AwtPixelReaderWriter(this, bounds);
	}

	private int toBufferedImageType(ImageType type)
	{
		if (type == ImageType.ARGB)
		{
			return BufferedImage.TYPE_INT_ARGB;
		}
		if (type == ImageType.RGB)
		{
			return BufferedImage.TYPE_INT_RGB;
		}
		if (type == ImageType.Grayscale8Bit)
		{
			return BufferedImage.TYPE_BYTE_GRAY;
		}
		if (type == ImageType.Binary)
		{
			return BufferedImage.TYPE_BYTE_BINARY;
		}
		if (type == ImageType.Grayscale16Bit)
		{
			return BufferedImage.TYPE_USHORT_GRAY;
		}
		else
		{
			throw new IllegalArgumentException("Unimplemented BufferedImage type: " + type);
		}
	}

	private static ImageType toImageType(int bufferedImageType)
	{
		if (bufferedImageType == BufferedImage.TYPE_INT_ARGB || bufferedImageType == BufferedImage.TYPE_INT_ARGB_PRE || bufferedImageType == BufferedImage.TYPE_4BYTE_ABGR
				|| bufferedImageType == BufferedImage.TYPE_4BYTE_ABGR_PRE)
		{
			return ImageType.ARGB;
		}
		if (bufferedImageType == BufferedImage.TYPE_INT_RGB || bufferedImageType == BufferedImage.TYPE_INT_BGR || bufferedImageType == BufferedImage.TYPE_3BYTE_BGR
				|| bufferedImageType == BufferedImage.TYPE_USHORT_565_RGB || bufferedImageType == BufferedImage.TYPE_USHORT_555_RGB || bufferedImageType == BufferedImage.TYPE_BYTE_INDEXED)
		{
			return ImageType.RGB;
		}
		if (bufferedImageType == BufferedImage.TYPE_BYTE_GRAY)
		{
			return ImageType.Grayscale8Bit;
		}
		if (bufferedImageType == BufferedImage.TYPE_BYTE_BINARY)
		{
			return ImageType.Binary;
		}
		if (bufferedImageType == BufferedImage.TYPE_USHORT_GRAY)
		{
			return ImageType.Grayscale16Bit;
		}
		else
		{
			throw new IllegalArgumentException("Unrecognized buffered image type: " + bufferedImageTypeToString(bufferedImageType));
		}
	}

	@Override
	public int getWidth()
	{
		return image.getWidth();
	}

	@Override
	public int getHeight()
	{
		return image.getHeight();
	}

	@Override
	public Painter createPainter(DrawQuality quality)
	{
		java.awt.Graphics2D g = image.createGraphics();
		if (quality == DrawQuality.High)
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		}

		return new AwtPainter(g);
	}

	private static String bufferedImageTypeToString(int type)
	{
		if (type == BufferedImage.TYPE_3BYTE_BGR)
			return "TYPE_3BYTE_BGR";
		if (type == BufferedImage.TYPE_4BYTE_ABGR)
			return "TYPE_4BYTE_ABGR";
		if (type == BufferedImage.TYPE_4BYTE_ABGR_PRE)
			return "TYPE_4BYTE_ABGR_PRE";
		if (type == BufferedImage.TYPE_BYTE_BINARY)
			return "TYPE_BYTE_BINARY";
		if (type == BufferedImage.TYPE_BYTE_GRAY)
			return "TYPE_BYTE_GRAY";
		if (type == BufferedImage.TYPE_BYTE_INDEXED)
			return "TYPE_BYTE_INDEXED";
		if (type == BufferedImage.TYPE_INT_RGB)
			return "TYPE_INT_RGB";
		if (type == BufferedImage.TYPE_INT_ARGB)
			return "TYPE_INT_ARGB";
		if (type == BufferedImage.TYPE_INT_ARGB_PRE)
			return "TYPE_INT_ARGB_PRE";
		if (type == BufferedImage.TYPE_INT_BGR)
			return "TYPE_INT_BGR";
		if (type == BufferedImage.TYPE_USHORT_555_RGB)
			return "TYPE_USHORT_555_RGB";
		if (type == BufferedImage.TYPE_USHORT_565_RGB)
			return "TYPE_USHORT_565_RGB";
		if (type == BufferedImage.TYPE_USHORT_GRAY)
			return "TYPE_USHORT_GRAY";
		return "unknown";
	}

	@Override
	public Image scale(Method method, int width, int height)
	{
		// This library is described at
		// http://stackoverflow.com/questions/1087236/java-2d-image-resize-ignoring-bicubic-bilinear-interpolation-rendering-hints-os
		Image scaled = new AwtImage(Scalr.resize(image, method, width, height));

		if (isGrayscaleOrBinary() && !scaled.isGrayscaleOrBinary())
		{
			scaled = ImageHelper.convertImageToType(scaled, getType());
		}

		return scaled;
	}

	@Override
	public Image deepCopy()
	{
		java.awt.image.ColorModel cm = image.getColorModel();
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		WritableRaster raster = image.copyData(null);
		return new AwtImage(new BufferedImage(cm, raster, isAlphaPremultiplied, null));
	}

	@Override
	public Image getSubImage(IntRectangle bounds)
	{
		return new AwtImage(image.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height));
	}

	@Override
	public Image copySubImage(IntRectangle bounds, boolean addAlphaChanel)
	{
		Image sub = getSubImage(bounds);
		Image result = Image.create(bounds.width, bounds.height, addAlphaChanel ? ImageType.ARGB : getType());
		try (Painter p = result.createPainter())
		{
			p.drawImage(sub, 0, 0);
		}
		return result;
	}

	@Override
	public Image copyAndAddAlphaChanel()
	{
		if (hasAlpha())
		{
			return deepCopy();
		}

		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

		// Draw the original image onto the new image
		Graphics2D g2d = copy.createGraphics();
		g2d.drawImage(image, 0, 0, null);

		return new AwtImage(copy);
	}
}
