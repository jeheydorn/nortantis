package nortantis.platform;

import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import org.imgscalr.Scalr.Method;

import java.io.InputStream;

public abstract class Image
{
	private final ImageType type;

	protected Image(ImageType type)
	{
		this.type = type;
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
	 * Creates an image with the given bounds in this image, backed by the same data as the original image. This means that modifications to the result will modify the original image.
	 */
	public abstract Image getSubImage(IntRectangle bounds);

	/**
	 * Creates an image with the given bounds in this image, backed by a copy of the data from the original image. This means that modifications to the result will NOT modify the original image.
	 */
	public Image copySubImage(IntRectangle bounds)
	{
		return copySubImage(bounds, false);
	}

	public abstract Image copySubImage(IntRectangle bounds, boolean addAlphaChanel);

	public abstract Image copyAndAddAlphaChanel();

	/**
	 * Creates a pixel reader that is restricted to read from the given bounds of this image. For the Skia implementation, this is much more efficient than creating a pixel reader for the entire
	 * image.
	 */
	protected abstract PixelReader innerCreateNewPixelReader(IntRectangle bounds);

	protected abstract PixelReaderWriter innerCreateNewPixelReaderWriter(IntRectangle bounds);


	public PixelReader createPixelReader()
	{
		return createPixelReader(null);
	}

	/**
	 * Creates a pixel reader that is restricted to read/write in the given bounds of this image. For the Skia implementation, this is much more efficient than creating a pixel reader for the entire
	 * image.
	 *
	 * @bounds If not null, then is the bounds in the image the reader should be for. If null, then create a reader for the while image. Note that passing in a non-null bounds that restricts reading
	 * 		to a subset of this image does not change the coordinates you should use when accessing pixels through the reader. Also, if the bounds you give extends behind the image, it will be clipped.
	 */
	public PixelReader createPixelReader(IntRectangle bounds)
	{
		if (bounds != null)
		{
			IntRectangle intersection = bounds.findIntersection(new IntRectangle(0, 0, getWidth(), getHeight()));
			if (intersection == null)
			{
				bounds = new IntRectangle(bounds.x, bounds.y, 0, 0);
			}
		}
		return innerCreateNewPixelReader(bounds);
	}

	public PixelReaderWriter createPixelReaderWriter()
	{
		return createPixelReaderWriter(null);
	}

	/**
	 * Creates a pixel reader/writier that is restricted to read/write in the given bounds of this image. For the Skia implementation, this is much more efficient than creating a pixel reader for the
	 * entire image.
	 *
	 * @bounds If not null, then is the bounds in the image the reader/writer should be for. If null, then create a reader for the while image. Note that passing in a non-null bounds that restricts
	 * 		reading/writing to a subset of this image does not change the coordinates you should use when accessing pixels through the reader/writer. Also, if the bounds you give extends behind the
	 * 		image, it will be clipped.
	 */
	public PixelReaderWriter createPixelReaderWriter(IntRectangle bounds)
	{
		if (bounds != null)
		{
			IntRectangle intersection = bounds.findIntersection(new IntRectangle(0, 0, getWidth(), getHeight()));
			if (intersection == null)
			{
				bounds = new IntRectangle(bounds.x, bounds.y, 0, 0);
			}
		}
		return innerCreateNewPixelReaderWriter(bounds);
	}
}
