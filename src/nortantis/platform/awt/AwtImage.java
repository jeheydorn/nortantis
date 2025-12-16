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
import nortantis.util.ImageHelper;

class AwtImage extends Image
{
	public BufferedImage image;
	WritableRaster raster;
	Raster alphaRaster;

	public AwtImage(int width, int height, ImageType type)
	{
		super(type);
		image = new BufferedImage(width, height, toBufferedImageType(type));
		createRastersIfNeeded();
	}

	public AwtImage(BufferedImage bufferedImage)
	{
		super(toImageType(bufferedImage.getType()));
		image = bufferedImage;
		createRastersIfNeeded();
	}

	private void createRastersIfNeeded()
	{
		raster = image.getRaster();

		if (hasAlpha())
		{
			alphaRaster = image.getAlphaRaster();
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
			throw new IllegalArgumentException("Unimplemented image type: " + type);
		}
	}

	private static ImageType toImageType(int bufferedImageType)
	{
		if (bufferedImageType == BufferedImage.TYPE_INT_ARGB || bufferedImageType == BufferedImage.TYPE_INT_ARGB_PRE
				|| bufferedImageType == BufferedImage.TYPE_4BYTE_ABGR || bufferedImageType == BufferedImage.TYPE_4BYTE_ABGR_PRE)
		{
			return ImageType.ARGB;
		}
		if (bufferedImageType == BufferedImage.TYPE_INT_RGB || bufferedImageType == BufferedImage.TYPE_INT_BGR
				|| bufferedImageType == BufferedImage.TYPE_3BYTE_BGR || bufferedImageType == BufferedImage.TYPE_USHORT_565_RGB
				|| bufferedImageType == BufferedImage.TYPE_USHORT_555_RGB || bufferedImageType == BufferedImage.TYPE_BYTE_INDEXED)
		{
			return ImageType.RGB;
		}
		if (bufferedImageType == BufferedImage.TYPE_3BYTE_BGR)
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
	public int getRGB(int[] data, int x, int y)
	{
		return data[(y * image.getWidth()) + x];
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		image.setRGB(x, y, rgb);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		setRGB(x, y, (red << 16) | (green << 8) | blue);
	}

	@Override
	public void setRGB(int[] data, int x, int y, int red, int green, int blue)
	{
		// setRGB(x, y, red, green, blue);
		data[(y * image.getWidth()) + x] = (red << 16) | (green << 8) | blue;
	}

	@Override
	public void setRGB(int[] data, int x, int y, int red, int green, int blue, int alpha)
	{
		// setRGB(x, y, red, green, blue, alpha);
		data[(y * image.getWidth()) + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		return raster.getSample(x, y, band);
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		raster.setSample(x, y, band, level);
	}

	@Override
	public int getAlpha(int x, int y)
	{
		if (hasAlpha())
		{
			return alphaRaster.getSample(x, y, 0);
		}

		return 0;
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new AwtColor(image.getRGB(x, y), hasAlpha());
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
		result.createPainter().drawImage(sub, 0, 0);
		return result;
	}

	@Override
	public void setAlpha(int x, int y, int alpha)
	{
		int newColor = (image.getRGB(x, y) & 0x00FFFFFF) | (alpha << 24);
		setRGB(x, y, newColor);
	}

	@Override
	public int[] getDataIntBased()
	{
		return ((DataBufferInt) raster.getDataBuffer()).getData();
	}

	@Override
	public boolean isIntBased()
	{
		return raster.getDataBuffer() instanceof DataBufferInt;
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
		g2d.dispose();

		return new AwtImage(copy);
	}
}
