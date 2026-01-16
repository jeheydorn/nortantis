package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.*;
import nortantis.platform.Color;
import nortantis.platform.Image;
import org.imgscalr.Scalr.Method;
import org.jetbrains.skia.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class SkiaImage extends Image
{
	Bitmap bitmap;
	private final int width;
	private final int height;
	private org.jetbrains.skia.Image cachedSkiaImage;

	public SkiaImage(int width, int height, ImageType type)
	{
		super(type);
		this.width = width;
		this.height = height;
		ImageInfo imageInfo = getImageInfoForType(type, width, height);
		this.bitmap = createBitmap(imageInfo);
	}

	public SkiaImage(Bitmap bitmap, ImageType type)
	{
		super(type);
		this.bitmap = bitmap;
		this.width = bitmap.getWidth();
		this.height = bitmap.getHeight();
	}

	private Bitmap createBitmap(ImageInfo imageInfo)
	{
		Bitmap bitmap = new Bitmap();
		bitmap.allocPixels(imageInfo);
		Color eraseColor = hasAlpha() ? Color.transparentBlack : Color.black;
		bitmap.erase(eraseColor.getRGB());
		return bitmap;
	}

	public static ImageInfo getImageInfoForType(ImageType type, int width, int height)
	{
		ColorType colorType = toSkiaBitmapType(type);
		return switch (type)
		{
			case ARGB -> new ImageInfo(width, height, colorType, ColorAlphaType.UNPREMUL, null);
			case RGB -> new ImageInfo(width, height, colorType, ColorAlphaType.OPAQUE, null);
			case Grayscale8Bit -> new ImageInfo(width, height, colorType, ColorAlphaType.OPAQUE, null);
			case Grayscale16Bit -> new ImageInfo(width, height, colorType, ColorAlphaType.OPAQUE, null);
			case Binary -> new ImageInfo(width, height, colorType, ColorAlphaType.OPAQUE, null);
		};
	}


	private static ColorType toSkiaBitmapType(ImageType type)
	{
		if (type == ImageType.ARGB)
		{
			return ColorType.Companion.getN32();
		}
		if (type == ImageType.RGB)
		{
			return ColorType.Companion.getN32();
		}
		if (type == ImageType.Grayscale8Bit)
		{
			return ColorType.GRAY_8;
		}
		if (type == ImageType.Binary)
		{
			return ColorType.GRAY_8;
		}
		if (type == ImageType.Grayscale16Bit)
		{
			return ColorType.RGBA_F16;
		}
		else
		{
			throw new IllegalArgumentException("Unimplemented Skia image type: " + type);
		}
	}

	/**
	 * Returns the number of bytes per pixel for this image's color type.
	 */
	private int getBytesPerPixel()
	{
		ImageType type = getType();
		if (type == ImageType.Grayscale8Bit || type == ImageType.Binary)
		{
			return 1;
		}
		if (type == ImageType.Grayscale16Bit)
		{
			return 8; // RGBA_F16 = 16 bits * 4 channels = 8 bytes
		}
		// ARGB and RGB use N32 = 4 bytes
		return 4;
	}

	/**
	 * Returns true if this image uses a grayscale format (1 byte per pixel).
	 */
	private boolean isGrayscaleFormat()
	{
		ImageType type = getType();
		return type == ImageType.Grayscale8Bit || type == ImageType.Binary;
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

	void invalidateCachedImage()
	{
		if (cachedSkiaImage != null)
		{
			cachedSkiaImage.close();
			cachedSkiaImage = null;
		}
	}

	@Override
	public PixelReader innerCreateNewPixelReader(IntRectangle bounds)
	{
		return new SkiaPixelReader(this, bounds);
	}

	@Override
	public PixelReaderWriter innerCreateNewPixelReaderWriter(IntRectangle bounds)
	{
		return new SkiaPixelReaderWriter(this, bounds);
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
		return new SkiaPainter(new Canvas(bitmap, new SurfaceProps()), quality);
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

		if (method == Method.ULTRA_QUALITY || method == Method.QUALITY)
		{
			return scaleHighQuality(width, height);
		}

		// Use Surface-based rendering for standard quality
		Surface surface = Surface.Companion.makeRasterN32Premul(width, height);
		Canvas canvas = surface.getCanvas();

		// Create image from source bitmap and draw scaled
		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);
		canvas.drawImageRect(srcImage, Rect.makeXYWH(0, 0, width, height));
		srcImage.close();

		// Get the result as an image snapshot and extract pixels to a new bitmap
		org.jetbrains.skia.Image resultImage = surface.makeImageSnapshot();
		Bitmap scaledBitmap = new Bitmap();
		ImageInfo scaledImageInfo = new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null);
		scaledBitmap.allocPixels(scaledImageInfo);
		resultImage.readPixels(scaledBitmap, 0, 0);
		resultImage.close();
		surface.close();

		return new SkiaImage(scaledBitmap, getType());
	}

	/**
	 * Scales the image using high-quality sampling with mipmaps. Uses FilterMipmap with linear filtering and linear mipmap
	 * interpolation, which provides better results than bilinear filtering when downscaling significantly.
	 */
	private Image scaleHighQuality(int width, int height)
	{
		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);

		// Create destination bitmap and get its pixmap
		Bitmap scaledBitmap = new Bitmap();
		scaledBitmap.allocPixels(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
		Pixmap dstPixmap = scaledBitmap.peekPixels();

		// Use FilterMipmap with linear filtering and linear mipmap interpolation for high-quality downscaling
		SamplingMode sampling = new FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR);
		srcImage.scalePixels(dstPixmap, sampling, false);
		srcImage.close();

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
		// Skia doesn't have a direct "sub-bitmap" that shares data easily like BufferedImage.getSubimage,
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

		// Use Surface-based rendering, which is more reliable
		Surface surface = Surface.Companion.makeRasterN32Premul(w, h);
		Canvas canvas = surface.getCanvas();

		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);
		canvas.drawImageRect(srcImage, Rect.makeXYWH(bounds.x, bounds.y, bounds.width, bounds.height), Rect.makeXYWH(0, 0, w, h));
		srcImage.close();

		// Get the result as an image snapshot and extract pixels to a new bitmap
		org.jetbrains.skia.Image resultImage = surface.makeImageSnapshot();
		Bitmap subBitmap = new Bitmap();
		ImageInfo subImageInfo = new ImageInfo(w, h, bitmap.getImageInfo().getColorType(), bitmap.getImageInfo().getColorAlphaType(), null);
		subBitmap.allocPixels(subImageInfo);
		resultImage.readPixels(subBitmap, 0, 0);
		resultImage.close();
		surface.close();

		return new SkiaImage(subBitmap, addAlphaChanel ? ImageType.ARGB : getType());
	}

	@Override
	public Image copyAndAddAlphaChanel()
	{
		if (hasAlpha())
			return deepCopy();
		// TODO if performance is a concern, I could make ImageType.RGB be the same as ARB under the hood, so I just have to change metadata and return a deep copy here.

		SkiaImage result = new SkiaImage(width, height, ImageType.ARGB);
		Painter p = result.createPainter();
		p.drawImage(this, 0, 0);
		return result;
	}

	public BufferedImage toBufferedImage()
	{
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

		byte[] bytes = bitmap.readPixels(bitmap.getImageInfo(), width * 4, 0, 0);

		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
		IntBuffer intBuffer = buffer.asIntBuffer();
		intBuffer.get(pixels);

		return bi;
	}

	/**
	 * Reads all pixels from the Skia bitmap into an int[] array. Format: ARGB, one int per pixel, row-major order.
	 * For grayscale images, converts single-byte gray values to ARGB format.
	 */
	public int[] readPixelsToIntArray()
	{
		int bytesPerPixel = getBytesPerPixel();
		int rowStride = width * bytesPerPixel;
		byte[] bytes = bitmap.readPixels(bitmap.getImageInfo(), rowStride, 0, 0);

		if (bytes == null)
		{
			return null;
		}

		int[] pixels = new int[width * height];

		if (isGrayscaleFormat())
		{
			// Convert 1-byte grayscale to ARGB int format
			for (int i = 0; i < pixels.length; i++)
			{
				int gray = bytes[i] & 0xFF;
				pixels[i] = (255 << 24) | (gray << 16) | (gray << 8) | gray;
			}
		}
		else
		{
			ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer().get(pixels);
		}
		return pixels;
	}

	/**
	 * Reads a rectangular region of pixels into an int[] array.
	 * For grayscale images, converts single-byte gray values to ARGB format.
	 */
	int[] readPixelsToIntArray(int srcX, int srcY, int regionWidth, int regionHeight)
	{
		int bytesPerPixel = getBytesPerPixel();
		int rowStride = regionWidth * bytesPerPixel;
		byte[] bytes = bitmap.readPixels(bitmap.getImageInfo(), rowStride, srcX, srcY);

		if (bytes == null)
		{
			return null;
		}

		int[] pixels = new int[regionWidth * regionHeight];

		if (isGrayscaleFormat())
		{
			// Convert 1-byte grayscale to ARGB int format
			for (int i = 0; i < pixels.length; i++)
			{
				int gray = bytes[i] & 0xFF;
				pixels[i] = (255 << 24) | (gray << 16) | (gray << 8) | gray;
			}
		}
		else
		{
			ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer().get(pixels);
		}
		return pixels;
	}

	/**
	 * Writes an int[] array back to the Skia bitmap. Format: ARGB, one int per pixel, row-major order.
	 * For grayscale images, extracts the gray value from ARGB and writes single bytes.
	 */
	void writePixelsFromIntArray(int[] pixels)
	{
		int bytesPerPixel = getBytesPerPixel();
		int rowStride = width * bytesPerPixel;

		if (isGrayscaleFormat())
		{
			// Convert ARGB int format to 1-byte grayscale
			byte[] bytes = new byte[pixels.length];
			for (int i = 0; i < pixels.length; i++)
			{
				// Extract red channel as gray value (assumes gray pixels have R=G=B)
				byte red = (byte) ((pixels[i] >> 16) & 0xFF); // TODO maybe put back on one line below
				bytes[i] = red;
			}
			bitmap.installPixels(bitmap.getImageInfo(), bytes, rowStride);
		}
		else
		{
			ByteBuffer buffer = ByteBuffer.allocate(pixels.length * 4).order(ByteOrder.nativeOrder());
			buffer.asIntBuffer().put(pixels);
			bitmap.installPixels(bitmap.getImageInfo(), buffer.array(), rowStride);
		}
		invalidateCachedImage();
	}

	/**
	 * Writes an int[] array to a rectangular region of the Skia bitmap. Uses a temporary bitmap and canvas drawing for efficiency with
	 * large images. For grayscale images, extracts gray values from ARGB ints.
	 */
	void writePixelsToRegion(int[] regionPixels, int destX, int destY, int regionWidth, int regionHeight)
	{
		// Create a temporary bitmap with the region pixels
		Bitmap tempBitmap = new Bitmap();
		ImageInfo tempImageInfo;

		if (isGrayscaleFormat())
		{
			// For grayscale, create a GRAY_8 temp bitmap
			tempImageInfo = new ImageInfo(regionWidth, regionHeight, ColorType.GRAY_8, ColorAlphaType.OPAQUE, null);
			tempBitmap.allocPixels(tempImageInfo);

			// Convert ARGB int format to 1-byte grayscale
			byte[] bytes = new byte[regionPixels.length];
			for (int i = 0; i < regionPixels.length; i++)
			{
				// Extract red channel as gray value (assumes gray pixels have R=G=B)
				bytes[i] = (byte) ((regionPixels[i] >> 16) & 0xFF);
			}
			tempBitmap.installPixels(tempImageInfo, bytes, regionWidth);
		}
		else
		{
			tempImageInfo = new ImageInfo(regionWidth, regionHeight, ColorType.Companion.getN32(), bitmap.getImageInfo().getColorAlphaType(), null);
			tempBitmap.allocPixels(tempImageInfo);

			ByteBuffer buffer = ByteBuffer.allocate(regionPixels.length * 4).order(ByteOrder.nativeOrder());
			buffer.asIntBuffer().put(regionPixels);
			tempBitmap.installPixels(tempImageInfo, buffer.array(), regionWidth * 4);
		}

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
