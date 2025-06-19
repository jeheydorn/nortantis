package nortantis.platform;

import java.io.InputStream;

import org.imgscalr.Scalr.Method;

import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;

public abstract class Image
{
	private ImageType type;
	private float maxPixelLevelAsFloat;

	protected Image(ImageType type)
	{
		this.type = type;
		maxPixelLevelAsFloat = getMaxPixelLevel();
	}

	public final int getGrayLevel(int x, int y)
	{
		return getBandLevel(x, y, 0);
	}

	public abstract int getBandLevel(int x, int y, int band);

	public void setGrayLevel(int x, int y, int level)
	{
		setBandLevel(x, y, 0, level);
	}

	public abstract void setBandLevel(int x, int y, int band, int level);

	public abstract int getAlphaLevel(int x, int y);

	public abstract void setPixelColor(int x, int y, Color color);

	public abstract int getRGB(int x, int y);

	public abstract int getRGB(int[] data, int x, int y);

	public abstract void setRGB(int x, int y, int rgb);

	public abstract void setRGB(int x, int y, int red, int green, int blue);

	public abstract void setRGB(int x, int y, int red, int green, int blue, int alpha);

	public abstract void setRGB(int[] data, int x, int y, int red, int green, int blue);

	public abstract void setRGB(int[] data, int x, int y, int red, int green, int blue, int alpha);

	public abstract void setAlpha(int x, int y, int alpha);

	public abstract Color getPixelColor(int x, int y);

	public float getNormalizedPixelLevel(int x, int y)
	{
		return getGrayLevel(x, y) / maxPixelLevelAsFloat;
	}

	public abstract int getWidth();

	public abstract int getHeight();

	public IntDimension size()
	{
		return new IntDimension(getWidth(), getHeight());
	}
	
	public int getPixelCount()
	{
		return getWidth() * getHeight();
	}

	public ImageType getType()
	{
		return type;
	}

	public boolean isGrayscaleOrBinary()
	{
		return type == ImageType.Grayscale8Bit || type == ImageType.Binary || type == ImageType.Grayscale16Bit;
	}

	public abstract Painter createPainter(DrawQuality quality);

	public Painter createPainter()
	{
		return createPainter(DrawQuality.Normal);
	}

	public static Image create(int width, int height, ImageType type)
	{
		return PlatformFactory.getInstance().createImage(width, height, type);
	}

	public static Image read(String filePath)
	{
		return PlatformFactory.getInstance().readImage(filePath);
	}

	public static Image read(InputStream stream)
	{
		return PlatformFactory.getInstance().readImage(stream);
	}

	public void write(String filePath)
	{
		PlatformFactory.getInstance().writeImage(this, filePath);
	}

	public abstract Image scale(Method method, int width, int height);

	public boolean hasAlpha()
	{
		return type == ImageType.ARGB;
	}

	public int getMaxPixelLevel()
	{
		return getMaxPixelLevelForType(type);
	}

	public static int getMaxPixelLevelForType(ImageType type)
	{
		if (type == ImageType.Grayscale16Bit)
		{
			return 65535;
		}
		if (type == ImageType.Binary)
		{
			return 1;
		}
		else
		{
			return 255;
		}
	}

	public abstract Image deepCopy();

	public abstract Image crop(IntRectangle bounds);

	public abstract int[] getDataIntBased();

	public abstract boolean isIntBased();
	
	public abstract Image copyAndAddAlphaChanel();

}
