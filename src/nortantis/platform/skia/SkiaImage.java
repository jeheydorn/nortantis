package nortantis.platform.skia;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ImageInfo;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceProps;
import org.imgscalr.Scalr.Method;
import nortantis.geom.IntRectangle;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;

public class SkiaImage extends Image
{
	private Bitmap bitmap;
	private final int width;
	private final int height;
	private org.jetbrains.skia.Image cachedSkiaImage;

	public SkiaImage(int width, int height, ImageType type)
	{
		super(type);
		this.width = width;
		this.height = height;
		this.bitmap = new Bitmap();
		this.bitmap.allocPixels(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
	}

	public SkiaImage(Bitmap bitmap, ImageType type)
	{
		super(type);
		this.bitmap = bitmap;
		this.width = bitmap.getWidth();
		this.height = bitmap.getHeight();
	}

	public Bitmap getBitmap()
	{
		return bitmap;
	}

	public org.jetbrains.skia.Image getSkiaImage()
	{
		if (cachedSkiaImage == null)
		{
			cachedSkiaImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);
		}
		return cachedSkiaImage;
	}

	private void invalidateCachedImage()
	{
		if (cachedSkiaImage != null)
		{
			cachedSkiaImage.close();
			cachedSkiaImage = null;
		}
	}

	@Override
	public int getBandLevel(int x, int y, int band)
	{
		int rgb = getRGB(x, y);
		if (band == 0)
			return (rgb >> 16) & 0xFF;
		if (band == 1)
			return (rgb >> 8) & 0xFF;
		if (band == 2)
			return rgb & 0xFF;
		return (rgb >> 24) & 0xFF;
	}

	@Override
	public void setBandLevel(int x, int y, int band, int level)
	{
		int rgb = getRGB(x, y);
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = (rgb >> 24) & 0xFF;

		if (band == 0)
			r = level;
		else if (band == 1)
			g = level;
		else if (band == 2)
			b = level;
		else if (band == 3)
			a = level;

		setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
	}

	@Override
	public int getAlpha(int x, int y)
	{
		return (getRGB(x, y) >> 24) & 0xFF;
	}

	@Override
	public void setPixelColor(int x, int y, Color color)
	{
		setRGB(x, y, color.getRGB());
	}

	@Override
	public int getRGB(int x, int y)
	{
		return bitmap.getColor(x, y);
	}

	@Override
	public int getRGB(int[] data, int x, int y)
	{
		if (data == null)
		{
			return getRGB(x, y);
		}
		return data[y * width + x];
	}

	@Override
	public void setRGB(int x, int y, int rgb)
	{
		// This is slow, but we'll use shaders later.
		// Note: bitmap.getPixels() / bitmap.setPixels() could be used for bulk.
		// For single pixel, we might need a canvas or use the buffer directly.
		Canvas canvas = new Canvas(bitmap, new SurfaceProps());
		org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint();
		paint.setColor(rgb);
		canvas.drawPoint(x + 0.5f, y + 0.5f, paint);
		canvas.close();
		paint.close();
		invalidateCachedImage();
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue)
	{
		setRGB(x, y, (255 << 24) | (red << 16) | (green << 8) | blue);
	}

	@Override
	public void setRGB(int x, int y, int red, int green, int blue, int alpha)
	{
		setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
	}

	@Override
	public void setRGB(int[] data, int x, int y, int red, int green, int blue)
	{
		if (data == null)
		{
			setRGB(x, y, red, green, blue);
		}
		else
		{
			data[y * width + x] = (255 << 24) | (red << 16) | (green << 8) | blue;
		}
	}

	@Override
	public void setRGB(int[] data, int x, int y, int red, int green, int blue, int alpha)
	{
		if (data == null)
		{
			setRGB(x, y, red, green, blue, alpha);
		}
		else
		{
			data[y * width + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
		}
	}

	@Override
	public void setAlpha(int x, int y, int alpha)
	{
		int rgb = getRGB(x, y);
		setRGB(x, y, (alpha << 24) | (rgb & 0xFFFFFF));
	}

	@Override
	public Color getPixelColor(int x, int y)
	{
		return new SkiaColor(getRGB(x, y), hasAlpha());
	}

	@Override
	public int getWidth()
	{
		return width;
	}

	@Override
	public int getHeight()
	{
		return height;
	}

	@Override
	public Painter createPainter(DrawQuality quality)
	{
		return new SkiaPainter(new Canvas(bitmap, new SurfaceProps()));
	}

	@Override
	public Image scale(Method method, int width, int height)
	{
		// Handle degenerate cases - Skia requires positive dimensions
		if (width <= 0 || height <= 0)
		{
			width = Math.max(1, width);
			height = Math.max(1, height);
		}

		// Use Surface-based rendering which is more reliable in Skia
		Surface surface = Surface.Companion.makeRasterN32Premul(width, height);
		Canvas canvas = surface.getCanvas();

		// Create image from source bitmap and draw scaled
		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);
		canvas.drawImageRect(srcImage, org.jetbrains.skia.Rect.makeXYWH(0, 0, width, height));
		srcImage.close();

		// Get the result as an image snapshot and extract pixels to a new bitmap
		org.jetbrains.skia.Image resultImage = surface.makeImageSnapshot();
		Bitmap scaledBitmap = new Bitmap();
		scaledBitmap.allocPixels(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
		resultImage.readPixels(scaledBitmap, 0, 0);
		resultImage.close();
		surface.close();

		return new SkiaImage(scaledBitmap, getType());
	}

	@Override
	public Image deepCopy()
	{
		return new SkiaImage(bitmap.makeClone(), getType());
	}

	@Override
	public Image getSubImage(IntRectangle bounds)
	{
		// Skia doesn't have a direct "sub-bitmap" that shares data easily like BufferedImage.getSubimage
		// but we can create one that points to the same pixels.
		// For simplicity now, let's copy.
		return copySubImage(bounds, false);
	}

	@Override
	public Image copySubImage(IntRectangle bounds, boolean addAlphaChanel)
	{
		// Handle degenerate cases - Skia requires positive dimensions
		int w = Math.max(1, bounds.width);
		int h = Math.max(1, bounds.height);

		// Use Surface-based rendering which is more reliable
		Surface surface = Surface.Companion.makeRasterN32Premul(w, h);
		Canvas canvas = surface.getCanvas();

		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);
		canvas.drawImageRect(srcImage,
				org.jetbrains.skia.Rect.makeXYWH(bounds.x, bounds.y, bounds.width, bounds.height),
				org.jetbrains.skia.Rect.makeXYWH(0, 0, w, h));
		srcImage.close();

		// Get the result as an image snapshot and extract pixels to a new bitmap
		org.jetbrains.skia.Image resultImage = surface.makeImageSnapshot();
		Bitmap subBitmap = new Bitmap();
		subBitmap.allocPixels(new ImageInfo(w, h, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
		resultImage.readPixels(subBitmap, 0, 0);
		resultImage.close();
		surface.close();

		return new SkiaImage(subBitmap, addAlphaChanel ? ImageType.ARGB : getType());
	}

	@Override
	public int[] getDataIntBased()
	{
		return null; // As requested, not supported for Skia implementation yet.
	}

	@Override
	public boolean isIntBased()
	{
		return false;
	}

	@Override
	public Image copyAndAddAlphaChanel()
	{
		if (hasAlpha())
			return deepCopy();
		return new SkiaImage(bitmap, ImageType.ARGB);
	}

	public BufferedImage toBufferedImage()
	{
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

		byte[] bytes = bitmap.readPixels(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null), width * 4, 0, 0);

		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
		IntBuffer intBuffer = buffer.asIntBuffer();
		intBuffer.get(pixels);

		return bi;
	}

	/**
	 * Reads all pixels from the Skia bitmap into an int[] array. Format: ARGB, one int per pixel, row-major order.
	 */
	public int[] readPixelsToIntArray()
	{
		byte[] bytes = bitmap.readPixels(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null), width * 4, 0, 0);

		if (bytes == null)
		{
			return null;
		}

		int[] pixels = new int[width * height];
		ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer().get(pixels);
		return pixels;
	}

	/**
	 * Reads a rectangular region of pixels into an int[] array.
	 */
	public int[] readPixelsToIntArray(int srcX, int srcY, int regionWidth, int regionHeight)
	{
		byte[] bytes = bitmap.readPixels(new ImageInfo(regionWidth, regionHeight, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null), regionWidth * 4, srcX, srcY);

		if (bytes == null)
		{
			return null;
		}

		int[] pixels = new int[regionWidth * regionHeight];
		ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer().get(pixels);
		return pixels;
	}

	/**
	 * Writes an int[] array back to the Skia bitmap. Format: ARGB, one int per pixel, row-major order.
	 */
	public void writePixelsFromIntArray(int[] pixels)
	{
		ByteBuffer buffer = ByteBuffer.allocate(pixels.length * 4).order(ByteOrder.nativeOrder());
		buffer.asIntBuffer().put(pixels);
		bitmap.installPixels(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null), buffer.array(), width * 4);
		invalidateCachedImage();
	}

	/**
	 * Writes an int[] array to a rectangular region of the Skia bitmap. Uses a temporary bitmap and canvas drawing for efficiency with
	 * large images.
	 */
	public void writePixelsToRegion(int[] regionPixels, int destX, int destY, int regionWidth, int regionHeight)
	{
		// Create a temporary bitmap with the region pixels
		Bitmap tempBitmap = new Bitmap();
		tempBitmap.allocPixels(new ImageInfo(regionWidth, regionHeight, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));

		ByteBuffer buffer = ByteBuffer.allocate(regionPixels.length * 4).order(ByteOrder.nativeOrder());
		buffer.asIntBuffer().put(regionPixels);
		tempBitmap.installPixels(new ImageInfo(regionWidth, regionHeight, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null), buffer.array(), regionWidth * 4);

		// Draw the temp image onto the main bitmap using Canvas
		Canvas canvas = new Canvas(bitmap, new SurfaceProps());
		org.jetbrains.skia.Image tempImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(tempBitmap);
		canvas.drawImage(tempImage, destX, destY);
		tempImage.close();
		canvas.close();
		tempBitmap.close();
		invalidateCachedImage();
	}
}
