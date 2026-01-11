package nortantis.platform;

import java.io.InputStream;

import nortantis.platform.awt.AwtPixelReader;
import nortantis.platform.awt.AwtPixelReaderWriter;
import org.imgscalr.Scalr.Method;

import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;

public abstract class Image
{
	private final ImageType type;
	private final float maxPixelLevelAsFloat;
	protected PixelReader currentPixelReader;

	protected Image(ImageType type)
	{
		this.type = type;
		maxPixelLevelAsFloat = getMaxPixelLevel();
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

	/**
	 * Creates an image with the given bounds in this image, backed by the same data as the original image. This means that modifications to
	 * the result will modify the original image.
	 */
	public abstract Image getSubImage(IntRectangle bounds);

	/**
	 * Creates an image with the given bounds in this image, backed by a copy of the data from the original image. This means that
	 * modifications to the result will NOT modify the original image.
	 */
	public Image copySubImage(IntRectangle bounds)
	{
		return copySubImage(bounds, false);
	}

	public abstract Image copySubImage(IntRectangle bounds, boolean addAlphaChanel);

	public abstract Image copyAndAddAlphaChanel();

	public Image copyAndRemoveAlphaChanel()
	{
		Image result = Image.create(getWidth(), getHeight(), ImageType.RGB);
		try (PixelReaderWriter resultPixels = result.createPixelReaderWriter(); PixelReader thisPixels = createPixelReader())
		{
			for (int y = 0; y < getHeight(); y++)
				for (int x = 0; x < getWidth(); x++)
				{
					resultPixels.setRGB(x, y, thisPixels.getRGB(x, y));
				}
		}
		return result;
	}

	/**
	 * Begins a pixel read session. This caches pixel data for efficient single-pixel reads. Use with try-with-resources to ensure the
	 * session is properly closed.
	 *
	 * If a write session is in progress, it will be auto-flushed with a warning.
	 *
	 * @return A session object that should be closed when done reading.
	 */
	public abstract PixelReader createNewPixelReader();

	/**
	 * Runs when pixel reads finish.
	 */
	public abstract void endPixelReadsOrWrites();

	/**
	 * Begins a pixel write session. This caches pixel data for efficient single-pixel writes. Use with try-with-resources to ensure the
	 * session is properly closed and changes are flushed.
	 *
	 * If a read session is in progress, it will be auto-ended with a warning.
	 *
	 * @return A session object that should be closed when done writing.
	 */
	public abstract PixelReaderWriter createNewPixelReaderWriter();


	public PixelReader createPixelReader()
	{
		if (currentPixelReader != null)
		{
			if (currentPixelReader instanceof PixelReaderWriter)
			{
				throw new IllegalStateException("Pixel reader and writer already created");
			}
			throw new IllegalStateException("Pixel reader already created");
		}

		currentPixelReader = createNewPixelReader();
		return currentPixelReader;
	}

	public PixelReaderWriter createPixelReaderWriter()
	{
		if (currentPixelReader != null)
		{
			if (currentPixelReader instanceof PixelReaderWriter)
			{
				throw new IllegalStateException("Pixel reader and writer already created");
			}
			throw new IllegalStateException("Pixel reader already created");
		}

		currentPixelReader = createNewPixelReaderWriter();
		return (PixelReaderWriter) currentPixelReader;
	}
}
