package nortantis.util.platform.awt;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

import nortantis.util.platform.Color;
import nortantis.util.platform.DrawQuality;
import nortantis.util.platform.Image;
import nortantis.util.platform.ImageType;
import nortantis.util.platform.Painter;

public class AwtImage extends Image
{
	public BufferedImage image;
	WritableRaster raster;
	
	public AwtImage(int width, int height, ImageType type)
	{
		super(type);
		image = new BufferedImage(width, height, toBufferedImageType(type));
		if (isGrayscaleOrBinary())
		{
			raster = image.getRaster();
		}
		createRasterIfNeeded();
	}
	
	public AwtImage(BufferedImage bufferedImage)
	{
		super(toImageType(bufferedImage.getType()));
		image = bufferedImage;
		createRasterIfNeeded();
	}
	
	private void createRasterIfNeeded()
	{
		if (isGrayscaleOrBinary())
		{
			raster = image.getRaster();
		}
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
		if (type == ImageType.Grayscale)
		{
			return BufferedImage.TYPE_BYTE_GRAY;
		}
		if (type == ImageType.Binary)
		{
			return BufferedImage.TYPE_BYTE_BINARY;
		}
		else
		{
			throw new IllegalArgumentException("Unimplemented image type: " + type);
		}
	}
	
	private static ImageType toImageType(int bufferedImageType)
	{
		if (bufferedImageType == BufferedImage.TYPE_INT_ARGB)
		{
			return ImageType.ARGB;
		}
		if (bufferedImageType == BufferedImage.TYPE_INT_RGB)
		{
			return ImageType.RGB;
		}
		if (bufferedImageType == BufferedImage.TYPE_BYTE_GRAY)
		{
			return ImageType.Grayscale;
		}
		if (bufferedImageType == BufferedImage.TYPE_BYTE_BINARY)
		{
			return ImageType.Binary;
		}
		else
		{
			throw new IllegalArgumentException("Unrecognized buffered image type: " + bufferedImageType);
		}
	}

	@Override
	public void setPixelColor(int x, int y, Color color)
	{
		image.setRGB(x, y, color.getRGB());
	}
	
	@Override
	public int getRGB(int x, int y)
	{
		return image.getRGB(x, y);
	}
	
	@Override
	public void setRGB(int x, int y, int rgb)
	{
		image.setRGB(x, y, y);
	}

	@Override
	public void setPixelLevel(int x, int y, int level)
	{
		raster.setSample(x, y, 0, level);
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new AwtColor(image.getRGB(x, y));
	}

	@Override
	public int getPixelLevel(int x, int y)
	{
		return raster.getSample(x, y, 0);
	}

	@Override
	public float getPixelLevelFloat(int x, int y)
	{
		return raster.getSampleFloat(x, y, 0);
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
		
		return new AwtPainter(image.createGraphics());
	}

}
