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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SkiaImage extends Image
{
	Bitmap bitmap;
	private final int width;
	private final int height;
	private org.jetbrains.skia.Image cachedSkiaImage;

	// GPU acceleration fields
	private Surface gpuSurface;
	private org.jetbrains.skia.Image gpuTexture;
	private ImageLocation location;
	private boolean gpuEnabled;

	private static final int GPU_THRESHOLD_PIXELS =  512 * 512; // TODO Change back to 512 * 512 when I'm done testing

	// Track active GPU batching painters for await before pixel access
	private final Set<GPUBatchingPainter> activePainters = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public SkiaImage(int width, int height, ImageType type)
	{
		super(type);
		this.width = width;
		this.height = height;
		ImageInfo imageInfo = getImageInfoForType(type, width, height);
		this.bitmap = createBitmap(imageInfo);
		initializeGPUState();
	}

	public SkiaImage(Bitmap bitmap, ImageType type)
	{
		super(type);
		this.bitmap = bitmap;
		this.width = bitmap.getWidth();
		this.height = bitmap.getHeight();
		initializeGPUState();
	}

	/**
	 * Initializes GPU state based on image size and GPU availability.
	 */
	private void initializeGPUState()
	{
		gpuEnabled = shouldUseGPU();
		location = gpuEnabled ? ImageLocation.CPU_DIRTY : ImageLocation.CPU_ONLY;
		gpuSurface = null;
		gpuTexture = null;
	}

	/**
	 * Determines if this image should use GPU acceleration based on size and availability.
	 */
	private boolean shouldUseGPU()
	{
		return getPixelCount() >= GPU_THRESHOLD_PIXELS && GPUExecutor.getInstance().isGPUAvailable();
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
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current before returning
		return bitmap;
	}

	public org.jetbrains.skia.Image getSkiaImage()
	{
		// If GPU has latest data and we have a surface, and we're on the GPU thread, use GPU snapshot
		if (gpuEnabled && location != ImageLocation.CPU_DIRTY && gpuSurface != null
				&& GPUExecutor.getInstance().isOnGPUThread())
		{
			if (gpuTexture == null)
			{
				gpuTexture = gpuSurface.makeImageSnapshot();
			}
			if (gpuTexture != null)
			{
				return gpuTexture;
			}
		}

		// If GPU has the latest data but we're not on GPU thread, sync to CPU first
		if (gpuEnabled && location == ImageLocation.GPU_DIRTY)
		{
			ensureCPUData();
		}

		// Return CPU bitmap-based image
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

	/**
	 * Lazily creates the GPU surface when needed for drawing.
	 * Does nothing if GPU is not enabled for this image.
	 */
	private void ensureGPUSurface()
	{
		if (!gpuEnabled)
		{
			return;
		}

		if (gpuSurface == null)
		{
			gpuSurface = GPUExecutor.getInstance().createGPUSurface(width, height);
			if (gpuSurface == null)
			{
				// GPU surface creation failed, fall back to CPU
				gpuEnabled = false;
				location = ImageLocation.CPU_ONLY;
				return;
			}
		}

		// If CPU has modifications, upload them to GPU
		if (location == ImageLocation.CPU_DIRTY)
		{
			syncCPUToGPU();
		}
	}

	/**
	 * Ensures CPU bitmap has the latest data.
	 * If GPU was modified, syncs GPU data to CPU.
	 */
	private void ensureCPUData()
	{
		if (location == ImageLocation.GPU_DIRTY)
		{
			syncGPUToCPU();
		}
	}

	/**
	 * Reads GPU surface pixels into the CPU bitmap.
	 * Must submit the GPU access to the GPU thread.
	 */
	private void syncGPUToCPU()
	{
		if (gpuSurface == null)
		{
			return;
		}

		try
		{
			// GPU surface access must happen on the GPU thread
			final Surface surfaceRef = gpuSurface;
			final Bitmap bitmapRef = bitmap;

			GPUExecutor.getInstance().submit(() -> {
				// Flush any pending GPU commands to ensure the surface is up-to-date
				surfaceRef.flushAndSubmit(true);  // true = sync

				// Read directly from surface instead of using a snapshot.
				// Skia handles the conversion from PREMUL surface to the bitmap's alpha type.
				surfaceRef.readPixels(bitmapRef, 0, 0);
				return null;
			});

			// Invalidate the cached Skia image since bitmap changed
			invalidateCachedImage();
			invalidateGPUTexture();

			location = ImageLocation.SYNCHRONIZED;
		}
		catch (Exception e)
		{
			System.err.println("SkiaImage: Failed to sync GPU to CPU: " + e.getMessage());
			// Fall back to CPU-only mode
			gpuEnabled = false;
			location = ImageLocation.CPU_ONLY;
		}
	}

	/**
	 * Uploads CPU bitmap data to the GPU surface.
	 * Must submit the GPU access to the GPU thread.
	 */
	private void syncCPUToGPU()
	{
		if (gpuSurface == null)
		{
			return;
		}

		try
		{
			// GPU surface access must happen on the GPU thread
			final Surface surfaceRef = gpuSurface;
			final Bitmap bitmapRef = bitmap;

			GPUExecutor.getInstance().submit(() -> {
				// Draw the CPU bitmap to the GPU surface
				Canvas gpuCanvas = surfaceRef.getCanvas();
				org.jetbrains.skia.Image cpuImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmapRef);
				gpuCanvas.clear(0x00000000);
				gpuCanvas.drawImage(cpuImage, 0, 0);
				cpuImage.close();
				return null;
			});

			// Invalidate GPU texture snapshot
			invalidateGPUTexture();

			location = ImageLocation.SYNCHRONIZED;
		}
		catch (Exception e)
		{
			System.err.println("SkiaImage: Failed to sync CPU to GPU: " + e.getMessage());
			// Fall back to CPU-only mode
			gpuEnabled = false;
			location = ImageLocation.CPU_ONLY;
		}
	}

	/**
	 * Marks the CPU bitmap as having the latest data (GPU is stale).
	 * Called after pixel write operations.
	 */
	public void markCPUDirty()
	{
		if (gpuEnabled && location != ImageLocation.CPU_ONLY)
		{
			location = ImageLocation.CPU_DIRTY;
			invalidateGPUTexture();
		}
		invalidateCachedImage();
	}

	/**
	 * Marks the GPU surface as having the latest data (CPU is stale).
	 * Called after GPU drawing operations.
	 */
	private void markGPUDirty()
	{
		if (gpuEnabled)
		{
			location = ImageLocation.GPU_DIRTY;
			invalidateGPUTexture();
			invalidateCachedImage();
		}
	}

	/**
	 * Invalidates the GPU texture snapshot.
	 */
	private void invalidateGPUTexture()
	{
		if (gpuTexture != null)
		{
			gpuTexture.close();
			gpuTexture = null;
		}
	}

	@Override
	public void prepareForPixelAccess()
	{
		awaitPendingPainters();
		ensureCPUData();
	}

	/**
	 * Waits for all active GPU batching painters to complete their pending operations.
	 */
	public void awaitPendingPainters()
	{
		for (GPUBatchingPainter painter : activePainters)
		{
			painter.await();
		}
	}

	/**
	 * Called by GPUBatchingPainter when it is closed.
	 * Removes the painter from the active set.
	 */
	void onPainterClosed(GPUBatchingPainter painter)
	{
		activePainters.remove(painter);
	}

	@Override
	public void prepareForDrawing()
	{
		if (gpuEnabled)
		{
			ensureGPUSurface();
		}
	}

	@Override
	public PixelReader innerCreateNewPixelReader(IntRectangle bounds)
	{
		awaitPendingPainters();
		ensureCPUData(); // Sync GPU->CPU if needed
		return new SkiaPixelReader(this, bounds);
	}

	@Override
	public PixelReaderWriter innerCreateNewPixelReaderWriter(IntRectangle bounds)
	{
		awaitPendingPainters();
		ensureCPUData(); // Sync GPU->CPU if needed
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
		if (gpuEnabled && GPUExecutor.getInstance().isGPUAvailable())
		{
			try
			{
				ensureGPUSurface();
				if (gpuSurface != null)
				{
					markGPUDirty();
					// Create a GPUBatchingPainter for async GPU operations
					GPUBatchingPainter painter = new GPUBatchingPainter(gpuSurface, this, quality);
					activePainters.add(painter);
					return painter;
				}
			}
			catch (Exception e)
			{
				// Fallback to CPU
				System.err.println("SkiaImage: GPU painter failed, falling back to CPU: " + e.getMessage());
				gpuEnabled = false;
				location = ImageLocation.CPU_ONLY;
			}
		}
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

		// Try GPU-accelerated scaling if source is GPU-enabled
		// The entire GPU operation must run on the GPU thread
		if (gpuEnabled && GPUExecutor.getInstance().isGPUAvailable())
		{
			final int targetWidth = width;
			final int targetHeight = height;
			final ImageType resultType = getType();
			final Bitmap srcBitmap = bitmap;

			try
			{
				Bitmap scaledBitmap = GPUExecutor.getInstance().submit(() -> {
					// Create GPU surface on GPU thread
					DirectContext ctx = GPUExecutor.getInstance().getContext();
					if (ctx == null) return null;

					Surface gpuDestSurface = Surface.Companion.makeRenderTarget(ctx, false,
						new ImageInfo(targetWidth, targetHeight, ColorType.Companion.getN32(), ColorAlphaType.PREMUL));
					if (gpuDestSurface == null) return null;

					try
					{
						Canvas gpuCanvas = gpuDestSurface.getCanvas();

						// Use CPU bitmap as source (safer than GPU texture across threads)
						org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(srcBitmap);
						gpuCanvas.drawImageRect(srcImage, Rect.makeXYWH(0, 0, targetWidth, targetHeight));
						srcImage.close();

						// Flush GPU commands and read directly from surface
						gpuDestSurface.flushAndSubmit(true);
						Bitmap result = new Bitmap();
						result.allocPixels(new ImageInfo(targetWidth, targetHeight, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
						gpuDestSurface.readPixels(result, 0, 0);
						return result;
					}
					finally
					{
						gpuDestSurface.close();
					}
				});

				if (scaledBitmap != null)
				{
					return new SkiaImage(scaledBitmap, resultType);
				}
				// Fall through to CPU path if GPU operation failed
			}
			catch (Exception e)
			{
				// Fall through to CPU path
			}
		}

		// CPU path: Use Surface-based rendering for standard quality
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
		Surface surface = Surface.Companion.makeRasterN32Premul(width, height);
		Canvas canvas = surface.getCanvas();

		// Create image from source bitmap and draw scaled
		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);
		canvas.drawImageRect(srcImage, Rect.makeXYWH(0, 0, width, height));
		srcImage.close();

		// Read directly from surface
		Bitmap scaledBitmap = new Bitmap();
		ImageInfo scaledImageInfo = new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null);
		scaledBitmap.allocPixels(scaledImageInfo);
		surface.readPixels(scaledBitmap, 0, 0);
		surface.close();

		return new SkiaImage(scaledBitmap, getType());
	}

	/**
	 * Scales the image using high-quality sampling with mipmaps. Uses FilterMipmap with linear filtering and linear mipmap
	 * interpolation, which provides better results than bilinear filtering when downscaling significantly.
	 */
	private Image scaleHighQuality(int width, int height)
	{
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
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
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current before copying
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

		// Try GPU-accelerated copy if source is GPU-enabled
		// The entire GPU operation must run on the GPU thread
		if (gpuEnabled && GPUExecutor.getInstance().isGPUAvailable())
		{
			final int targetW = w;
			final int targetH = h;
			final int srcX = bounds.x;
			final int srcY = bounds.y;
			final int srcW = bounds.width;
			final int srcH = bounds.height;
			final ImageType resultType = addAlphaChanel ? ImageType.ARGB : getType();
			final ColorType colorType = bitmap.getImageInfo().getColorType();
			final ColorAlphaType alphaType = bitmap.getImageInfo().getColorAlphaType();
			final Bitmap srcBitmap = bitmap;

			try
			{
				Bitmap subBitmap = GPUExecutor.getInstance().submit(() -> {
					// Create GPU surface on GPU thread
					DirectContext ctx = GPUExecutor.getInstance().getContext();
					if (ctx == null) return null;

					Surface gpuDestSurface = Surface.Companion.makeRenderTarget(ctx, false,
						new ImageInfo(targetW, targetH, ColorType.Companion.getN32(), ColorAlphaType.PREMUL));
					if (gpuDestSurface == null) return null;

					try
					{
						Canvas gpuCanvas = gpuDestSurface.getCanvas();

						// Use CPU bitmap as source (safer than GPU texture across threads)
						org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(srcBitmap);
						gpuCanvas.drawImageRect(srcImage, Rect.makeXYWH(srcX, srcY, srcW, srcH), Rect.makeXYWH(0, 0, targetW, targetH));
						srcImage.close();

						// Flush GPU commands and read directly from surface
						gpuDestSurface.flushAndSubmit(true);
						Bitmap result = new Bitmap();
						result.allocPixels(new ImageInfo(targetW, targetH, colorType, alphaType, null));
						gpuDestSurface.readPixels(result, 0, 0);
						return result;
					}
					finally
					{
						gpuDestSurface.close();
					}
				});

				if (subBitmap != null)
				{
					return new SkiaImage(subBitmap, resultType);
				}
				// Fall through to CPU path if GPU operation failed
			}
			catch (Exception e)
			{
				// Fall through to CPU path
			}
		}

		// CPU path: Use Surface-based rendering
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
		Surface surface = Surface.Companion.makeRasterN32Premul(w, h);
		Canvas canvas = surface.getCanvas();

		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(bitmap);
		canvas.drawImageRect(srcImage, Rect.makeXYWH(bounds.x, bounds.y, bounds.width, bounds.height), Rect.makeXYWH(0, 0, w, h));
		srcImage.close();

		// Read directly from surface
		Bitmap subBitmap = new Bitmap();
		ImageInfo subImageInfo = new ImageInfo(w, h, bitmap.getImageInfo().getColorType(), bitmap.getImageInfo().getColorAlphaType(), null);
		subBitmap.allocPixels(subImageInfo);
		surface.readPixels(subBitmap, 0, 0);
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
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
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
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
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
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
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
