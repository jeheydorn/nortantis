package nortantis.platform.skia;

import nortantis.geom.IntRectangle;
import nortantis.platform.*;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.util.Logger;
import org.imgscalr.Scalr.Method;
import org.jetbrains.skia.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SkiaImage extends Image
{
	// Shared Cleaner instance for all SkiaImage instances (creates one daemon thread)
	private static final Cleaner CLEANER = Cleaner.create();

	// The cleanable registration - calling clean() runs cleanup and deregisters
	private final Cleaner.Cleanable cleanable;

	// Separate state object for Cleaner - must not reference SkiaImage to allow GC
	private final ResourceState resourceState;

	private final int width;
	private final int height;

	private static final int GPU_THRESHOLD_PIXELS = 256 * 256;

	// When true, forces all images to use CPU rendering regardless of size
	private static volatile boolean forceCPU = false;

	// Track active GPU batching painters for await before pixel access (painters drawing ONTO this image)
	private final Set<GPUBatchingPainter> activePainters = Collections.newSetFromMap(new ConcurrentHashMap<>());

	// Track painters that have pending batched operations using this image as a SOURCE in drawImage calls
	private final Set<GPUBatchingPainter> referencingPainters = Collections.newSetFromMap(new ConcurrentHashMap<>());

	/**
	 * Holds GPU and CPU resources separately from SkiaImage so that the Cleaner can clean them up when SkiaImage becomes unreachable. This
	 * class must NOT hold any reference to SkiaImage, otherwise it would prevent GC from ever collecting the image.
	 */
	private static class ResourceState implements Runnable
	{
		// Using volatile for thread-safety since Cleaner runs on its own thread
		volatile Bitmap bitmap;
		volatile org.jetbrains.skia.Image cachedSkiaImage;
		volatile Surface gpuSurface;
		volatile org.jetbrains.skia.Image gpuTexture;
		volatile ImageLocation location;
		volatile boolean isGpuEnabled;

		ResourceState(Bitmap bitmap)
		{
			this.bitmap = bitmap;
		}

		@Override
		public void run()
		{
			// This is the cleanup action - runs on Cleaner thread when SkiaImage is GC'd
			// or immediately when cleanable.clean() is called

			// Capture references and null them out atomically
			final Surface surfaceToClose = gpuSurface;
			final org.jetbrains.skia.Image textureToClose = gpuTexture;
			final org.jetbrains.skia.Image cachedToClose = cachedSkiaImage;
			final Bitmap bitmapToClose = bitmap;

			gpuSurface = null;
			gpuTexture = null;
			cachedSkiaImage = null;
			bitmap = null;
			isGpuEnabled = false;
			location = ImageLocation.CPU_ONLY;

			// Submit ALL cleanup to GPUExecutor to ensure it happens after any pending
			// draw operations that may reference these resources. This prevents use-after-free
			// crashes when an image is closed while batched draw operations still reference it.
			if (GPUExecutor.getInstance().isGPUAvailable())
			{
				GPUExecutor.getInstance().submitAsync(() ->
				{
					if (surfaceToClose != null)
						surfaceToClose.close();
					if (textureToClose != null)
						textureToClose.close();
					if (cachedToClose != null)
						cachedToClose.close();
					if (bitmapToClose != null)
						bitmapToClose.close();
				});
			}
			else
			{
				// No GPU available, close everything directly
				if (surfaceToClose != null)
					surfaceToClose.close();
				if (textureToClose != null)
					textureToClose.close();
				if (cachedToClose != null)
					cachedToClose.close();
				if (bitmapToClose != null)
					bitmapToClose.close();
			}
		}
	}

	public SkiaImage(int width, int height, ImageType type)
	{
		super(type);
		this.width = width;
		this.height = height;
		ImageInfo imageInfo = getImageInfoForType(type, width, height);
		this.resourceState = new ResourceState(createBitmap(imageInfo));
		initializeGPUState();
		this.cleanable = CLEANER.register(this, resourceState);
	}

	public SkiaImage(Bitmap bitmap, ImageType type)
	{
		super(type);
		this.width = bitmap.getWidth();
		this.height = bitmap.getHeight();
		this.resourceState = new ResourceState(bitmap);
		initializeGPUState();
		this.cleanable = CLEANER.register(this, resourceState);
	}

	/**
	 * Releases resources held by this image, ensuring GPU resources are cleaned up on the GPU thread to avoid crashes from finalizers
	 * running on the wrong thread. This method is idempotent - calling it multiple times is safe.
	 *
	 * If there are pending batched draw operations using this image as a source, cleanup is deferred to the Cleaner (via GC) to avoid
	 * blocking/deadlocks.
	 */
	@Override
	public void close()
	{
		// Wait for any painters drawing onto this image to complete
		awaitPendingPainters();

		// If there are painters with pending batched operations using this image as a source,
		// skip immediate cleanup to avoid blocking. The Cleaner will handle cleanup when
		// this image becomes unreachable and GC runs.
		if (!referencingPainters.isEmpty())
		{
			return;
		}

		// clean() is idempotent - runs the cleanup action and deregisters from Cleaner
		cleanable.clean();
	}

	/**
	 * Initializes GPU state based on image size and GPU availability.
	 */
	private void initializeGPUState()
	{
		resourceState.isGpuEnabled = shouldUseGPU();
		resourceState.location = resourceState.isGpuEnabled ? ImageLocation.CPU_DIRTY : ImageLocation.CPU_ONLY;
		resourceState.gpuSurface = null;
		resourceState.gpuTexture = null;
	}

	/**
	 * Forces all SkiaImage instances to use CPU rendering regardless of size. Useful for tests that need deterministic rendering behavior.
	 */
	public static void setForceCPU(boolean force)
	{
		forceCPU = force;
	}

	public static boolean isForceCPU()
	{
		return forceCPU;
	}

	/**
	 * Determines if this image should use GPU acceleration based on size and availability. Uses GPU for medium-sized images, but falls back
	 * to CPU for very large images that exceed the GPU's maximum texture size.
	 */
	private boolean shouldUseGPU()
	{
		if (forceCPU)
		{
			return false;
		}
		if (!GPUExecutor.getInstance().isGPUAvailable())
		{
			return false;
		}
		int maxTextureSize = GPUExecutor.getInstance().getMaxTextureSize();
		return getPixelCount() >= GPU_THRESHOLD_PIXELS && width <= maxTextureSize && height <= maxTextureSize;
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
	boolean isGrayscaleFormat()
	{
		ImageType type = getType();
		return type == ImageType.Grayscale8Bit || type == ImageType.Binary;
	}

	public Bitmap getBitmap()
	{
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current before returning
		return resourceState.bitmap;
	}

	public org.jetbrains.skia.Image getSkiaImage()
	{
		// If GPU has latest data and we have a surface, and we're on the GPU thread, use GPU snapshot
		if (resourceState.isGpuEnabled && resourceState.location != ImageLocation.CPU_DIRTY && resourceState.gpuSurface != null && GPUExecutor.getInstance().isOnGPUThread())
		{
			if (resourceState.gpuTexture == null)
			{
				resourceState.gpuTexture = resourceState.gpuSurface.makeImageSnapshot();
			}
			if (resourceState.gpuTexture != null)
			{
				return resourceState.gpuTexture;
			}
		}

		// If GPU has the latest data but we're not on GPU thread, sync to CPU first
		if (resourceState.isGpuEnabled && resourceState.location == ImageLocation.GPU_DIRTY)
		{
			ensureCPUData();
		}

		// Return CPU bitmap-based image
		if (resourceState.cachedSkiaImage == null)
		{
			resourceState.cachedSkiaImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(resourceState.bitmap);
		}
		return resourceState.cachedSkiaImage;
	}

	void invalidateCachedImage()
	{
		if (resourceState.cachedSkiaImage != null)
		{
			resourceState.cachedSkiaImage.close();
			resourceState.cachedSkiaImage = null;
		}
	}

	/**
	 * Lazily creates the GPU surface when needed for drawing. Does nothing if GPU is not enabled for this image.
	 */
	private void ensureGPUSurface()
	{
		if (!resourceState.isGpuEnabled)
		{
			return;
		}

		if (resourceState.gpuSurface == null)
		{
			resourceState.gpuSurface = GPUExecutor.getInstance().createGPUSurface(width, height);
			if (resourceState.gpuSurface == null)
			{
				// GPU surface creation failed, fall back to CPU
				resourceState.isGpuEnabled = false;
				resourceState.location = ImageLocation.CPU_ONLY;
				return;
			}
		}

		// If CPU has modifications, upload them to GPU
		if (resourceState.location == ImageLocation.CPU_DIRTY)
		{
			syncCPUToGPU();
		}
	}

	/**
	 * Ensures CPU bitmap has the latest data. If GPU was modified, syncs GPU data to CPU.
	 */
	private void ensureCPUData()
	{
		if (resourceState.location == ImageLocation.GPU_DIRTY)
		{
			syncGPUToCPU();
		}
	}

	/**
	 * Reads GPU surface pixels into the CPU bitmap. Must submit the GPU access to the GPU thread.
	 */
	private void syncGPUToCPU()
	{
		if (resourceState.gpuSurface == null)
		{
			return;
		}

		try
		{
			// GPU surface access must happen on the GPU thread
			final Surface surfaceRef = resourceState.gpuSurface;
			final Bitmap bitmapRef = resourceState.bitmap;

			GPUExecutor.getInstance().submit(() ->
			{
				// Flush any pending GPU commands to ensure the surface is up-to-date
				surfaceRef.flushAndSubmit(true); // true = sync

				// Read directly from surface instead of using a snapshot.
				// Skia handles the conversion from PREMUL surface to the bitmap's alpha type.
				surfaceRef.readPixels(bitmapRef, 0, 0);
				return null;
			});

			// Invalidate the cached Skia image since bitmap changed
			invalidateCachedImage();
			invalidateGPUTexture();

			resourceState.location = ImageLocation.SYNCHRONIZED;
		}
		catch (Exception e)
		{
			System.err.println("SkiaImage: Failed to sync GPU to CPU: " + e.getMessage());
			// Fall back to CPU-only mode
			resourceState.isGpuEnabled = false;
			resourceState.location = ImageLocation.CPU_ONLY;
		}
	}

	/**
	 * Uploads CPU bitmap data to the GPU surface. Must submit the GPU access to the GPU thread.
	 */
	private void syncCPUToGPU()
	{
		if (resourceState.gpuSurface == null)
		{
			return;
		}

		try
		{
			// GPU surface access must happen on the GPU thread
			final Surface surfaceRef = resourceState.gpuSurface;
			final Bitmap bitmapRef = resourceState.bitmap;

			GPUExecutor.getInstance().submit(() ->
			{
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

			resourceState.location = ImageLocation.SYNCHRONIZED;
		}
		catch (Exception e)
		{
			System.err.println("SkiaImage: Failed to sync CPU to GPU: " + e.getMessage());
			// Fall back to CPU-only mode
			resourceState.isGpuEnabled = false;
			resourceState.location = ImageLocation.CPU_ONLY;
		}
	}

	/**
	 * Marks the CPU bitmap as having the latest data (GPU is stale). Called after pixel write operations.
	 */
	public void markCPUDirty()
	{
		if (resourceState.isGpuEnabled && resourceState.location != ImageLocation.CPU_ONLY)
		{
			resourceState.location = ImageLocation.CPU_DIRTY;
			invalidateGPUTexture();
		}
		invalidateCachedImage();
	}

	/**
	 * Updates a region of the GPU surface from the CPU bitmap. Used for partial updates to avoid full texture uploads.
	 */
	void updateGPURegion(int x, int y, int width, int height)
	{
		if (!resourceState.isGpuEnabled || resourceState.gpuSurface == null)
		{
			markCPUDirty();
			return;
		}

		try
		{
			// Clip to image bounds
			int safeX = Math.max(0, x);
			int safeY = Math.max(0, y);
			int safeRight = Math.min(this.width, x + width);
			int safeBottom = Math.min(this.height, y + height);

			if (safeX >= safeRight || safeY >= safeBottom)
			{
				return;
			}

			int safeW = safeRight - safeX;
			int safeH = safeBottom - safeY;

			final Surface surfaceRef = resourceState.gpuSurface;

			ImageInfo info = resourceState.bitmap.getImageInfo();
			int bytesPerPixel = getBytesPerPixel();
			int rowBytes = safeW * bytesPerPixel;

			// Create info for the region
			ImageInfo regionInfo = new ImageInfo(safeW, safeH, info.getColorType(), info.getColorAlphaType(), info.getColorSpace());

			// Read pixels from CPU bitmap
			byte[] pixelData = resourceState.bitmap.readPixels(regionInfo, rowBytes, safeX, safeY);

			if (pixelData == null)
			{
				markCPUDirty();
				return;
			}

			GPUExecutor.getInstance().submit(() ->
			{
				Canvas gpuCanvas = surfaceRef.getCanvas();

				// Create a temporary bitmap for the region
				Bitmap regionBitmap = new Bitmap();
				regionBitmap.allocPixels(regionInfo);
				regionBitmap.installPixels(regionInfo, pixelData, rowBytes);

				org.jetbrains.skia.Image regionImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(regionBitmap);

				gpuCanvas.drawImage(regionImage, safeX, safeY);

				regionImage.close();
				regionBitmap.close();
				return null;
			});

			invalidateGPUTexture();
		}
		catch (Exception e)
		{
			Logger.printError("SkiaImage: Failed to update GPU region: " + e.getMessage(), e);
			markCPUDirty();
		}
	}

	/**
	 * Marks the GPU surface as having the latest data (CPU is stale). Called after GPU drawing operations.
	 */
	private void markGPUDirty()
	{
		if (resourceState.isGpuEnabled)
		{
			resourceState.location = ImageLocation.GPU_DIRTY;
			invalidateGPUTexture();
			invalidateCachedImage();
		}
	}

	/**
	 * Invalidates the GPU texture snapshot.
	 */
	private void invalidateGPUTexture()
	{
		if (resourceState.gpuTexture != null)
		{
			resourceState.gpuTexture.close();
			resourceState.gpuTexture = null;
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
	 * Called by GPUBatchingPainter when it is closed. Removes the painter from the active set.
	 */
	void onPainterClosed(GPUBatchingPainter painter)
	{
		activePainters.remove(painter);
	}

	/**
	 * Called by GPUBatchingPainter when this image is used as a source in a drawImage call and the operation is added to a batch. The
	 * painter will be removed when the batch completes.
	 */
	void addReferencingPainter(GPUBatchingPainter painter)
	{
		referencingPainters.add(painter);
	}

	/**
	 * Called by GPUBatchingPainter when a batch containing drawImage operations using this image as a source has completed execution on the
	 * GPU thread.
	 */
	void removeReferencingPainter(GPUBatchingPainter painter)
	{
		referencingPainters.remove(painter);
	}

	/**
	 * Waits for all painters that have pending batched operations using this image as a source.
	 */
	private void awaitReferencingPainters()
	{
		for (GPUBatchingPainter painter : referencingPainters)
		{
			painter.await();
		}
	}

	@Override
	public void prepareForDrawing()
	{
		if (resourceState.isGpuEnabled)
		{
			ensureGPUSurface();
		}
	}

	@Override
	public PixelReader innerCreateNewPixelReader(IntRectangle bounds)
	{
		awaitPendingPainters();
		ensureCPUData(); // Sync GPU->CPU if needed
		if (isGrayscaleFormat())
		{
			return new SkiaGrayscalePixelReader(this, bounds);
		}
		return new SkiaPixelReader(this, bounds);
	}

	@Override
	public PixelReaderWriter innerCreateNewPixelReaderWriter(IntRectangle bounds)
	{
		awaitPendingPainters();
		ensureCPUData(); // Sync GPU->CPU if needed
		if (isGrayscaleFormat())
		{
			return new SkiaGrayscalePixelReaderWriter(this, bounds);
		}
		return new SkiaPixelReaderWriter(this, bounds);
	}

	@Override
	protected PixelWriter innerCreateNewPixelWriter(IntRectangle bounds)
	{
		awaitPendingPainters();
		// Note: No ensureCPUData() call - we're writing, not reading
		if (isGrayscaleFormat())
		{
			return new SkiaGrayscalePixelReaderWriter(this, bounds, false);
		}
		return new SkiaPixelReaderWriter(this, bounds, false);
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
		if (resourceState.isGpuEnabled && GPUExecutor.getInstance().isGPUAvailable())
		{
			try
			{
				ensureGPUSurface();
				if (resourceState.gpuSurface != null)
				{
					markGPUDirty();
					// Create a GPUBatchingPainter for async GPU operations
					GPUBatchingPainter painter = new GPUBatchingPainter(resourceState.gpuSurface, this, quality);
					activePainters.add(painter);
					return painter;
				}
			}
			catch (Exception e)
			{
				Logger.printError("SkiaImage: GPU painter failed, falling back to CPU: " + e.getMessage(), e);
				// Fallback to CPU
				resourceState.isGpuEnabled = false;
				resourceState.location = ImageLocation.CPU_ONLY;
			}
		}
		return new SkiaPainter(new Canvas(resourceState.bitmap, new SurfaceProps()), quality);
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

		if (method == Method.ULTRA_QUALITY || method == Method.QUALITY || method == Method.BALANCED)
		{
			return scaleHighQuality(width, height);
		}

		// Try GPU-accelerated scaling if source is GPU-enabled
		// The entire GPU operation must run on the GPU thread
		if (resourceState.isGpuEnabled && GPUExecutor.getInstance().isGPUAvailable())
		{
			final int targetWidth = width;
			final int targetHeight = height;
			final ImageType resultType = getType();
			final Bitmap srcBitmap = resourceState.bitmap;

			try
			{
				Bitmap scaledBitmap = GPUExecutor.getInstance().submit(() ->
				{
					// Create GPU surface on GPU thread
					DirectContext ctx = GPUExecutor.getInstance().getContext();
					if (ctx == null)
						return null;

					Surface gpuDestSurface = Surface.Companion.makeRenderTarget(ctx, false, new ImageInfo(targetWidth, targetHeight, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
					if (gpuDestSurface == null)
						return null;

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
		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(resourceState.bitmap);
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
	 * Scales the image using high-quality sampling with mipmaps. Uses FilterMipmap with linear filtering and linear mipmap interpolation,
	 * which provides better results than bilinear filtering when downscaling significantly.
	 */
	private Image scaleHighQuality(int width, int height)
	{
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(resourceState.bitmap);

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
		return new SkiaImage(resourceState.bitmap.makeClone(), getType());
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

		// Wait for any pending GPU painters to finish
		awaitPendingPainters();

		// Try GPU-accelerated copy if source is GPU-enabled and has a GPU surface
		// The entire GPU operation must run on the GPU thread
		if (resourceState.isGpuEnabled && resourceState.gpuSurface != null && GPUExecutor.getInstance().isGPUAvailable())
		{
			final int targetW = w;
			final int targetH = h;
			final int srcX = bounds.x;
			final int srcY = bounds.y;
			final int srcW = bounds.width;
			final int srcH = bounds.height;
			final ImageType resultType = addAlphaChanel ? ImageType.ARGB : getType();
			final ColorType colorType = resourceState.bitmap.getImageInfo().getColorType();
			final ColorAlphaType alphaType = resourceState.bitmap.getImageInfo().getColorAlphaType();

			// Determine source: use GPU surface only if GPU has exclusive latest data (GPU_DIRTY),
			// otherwise use CPU bitmap to maintain consistency with existing behavior
			final boolean useGPUSource = resourceState.location == ImageLocation.GPU_DIRTY && resourceState.gpuSurface != null;
			final Surface srcSurface = useGPUSource ? resourceState.gpuSurface : null;
			final Bitmap srcBitmap = useGPUSource ? null : resourceState.bitmap;

			try
			{
				Bitmap subBitmap = GPUExecutor.getInstance().submit(() ->
				{
					// Create GPU surface on GPU thread
					DirectContext ctx = GPUExecutor.getInstance().getContext();
					if (ctx == null)
						return null;

					Surface gpuDestSurface = Surface.Companion.makeRenderTarget(ctx, false, new ImageInfo(targetW, targetH, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
					if (gpuDestSurface == null)
						return null;

					try
					{
						Canvas gpuCanvas = gpuDestSurface.getCanvas();

						// Create source image from GPU surface or CPU bitmap
						org.jetbrains.skia.Image srcImage;
						if (srcSurface != null)
						{
							// GPU has latest data - use GPU surface snapshot (no CPU sync needed)
							srcImage = srcSurface.makeImageSnapshot();
						}
						else
						{
							// CPU has latest data - use CPU bitmap
							srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(srcBitmap);
						}

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

		org.jetbrains.skia.Image srcImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(resourceState.bitmap);
		canvas.drawImageRect(srcImage, Rect.makeXYWH(bounds.x, bounds.y, bounds.width, bounds.height), Rect.makeXYWH(0, 0, w, h));
		srcImage.close();

		// Read directly from surface
		Bitmap subBitmap = new Bitmap();
		ImageInfo subImageInfo = new ImageInfo(w, h, resourceState.bitmap.getImageInfo().getColorType(), resourceState.bitmap.getImageInfo().getColorAlphaType(), null);
		subBitmap.allocPixels(subImageInfo);
		surface.readPixels(subBitmap, 0, 0);
		surface.close();

		return new SkiaImage(subBitmap, addAlphaChanel ? ImageType.ARGB : getType());
	}

	@Override
	public Image copyAndAddAlphaChanel()
	{
		if (hasAlpha())
		{
			return deepCopy();
		}

		// TODO if performance is a concern, I could make ImageType.RGB be the same as ARB under the hood, so I just have to change metadata
		// and return a deep copy here.

		SkiaImage result = new SkiaImage(width, height, ImageType.ARGB);
		try (Painter p = result.createPainter())
		{
			p.drawImage(this, 0, 0);
		}
		return result;
	}

	public BufferedImage toBufferedImage()
	{
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

		byte[] bytes = resourceState.bitmap.readPixels(resourceState.bitmap.getImageInfo(), width * 4, 0, 0);

		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
		IntBuffer intBuffer = buffer.asIntBuffer();
		intBuffer.get(pixels);

		return bi;
	}

	/**
	 * Reads all pixels from the Skia bitmap into an int[] array. Format: ARGB, one int per pixel, row-major order. For grayscale images,
	 * converts single-byte gray values to ARGB format.
	 */
	public int[] readPixelsToIntArray()
	{
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
		int bytesPerPixel = getBytesPerPixel();
		int rowStride = width * bytesPerPixel;
		byte[] bytes = resourceState.bitmap.readPixels(resourceState.bitmap.getImageInfo(), rowStride, 0, 0);

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
	 * Reads a rectangular region of pixels into an int[] array. For grayscale images, converts single-byte gray values to ARGB format.
	 */
	int[] readPixelsToIntArray(int srcX, int srcY, int regionWidth, int regionHeight)
	{
		awaitPendingPainters();
		ensureCPUData(); // Ensure CPU bitmap is current
		int bytesPerPixel = getBytesPerPixel();
		int rowStride = regionWidth * bytesPerPixel;
		ImageInfo destinationInfo = new ImageInfo(regionWidth, regionHeight, resourceState.bitmap.getImageInfo().getColorType(), resourceState.bitmap.getImageInfo().getColorAlphaType(), null);
		byte[] bytes = resourceState.bitmap.readPixels(destinationInfo, rowStride, srcX, srcY);

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
	 * Writes an int[] array back to the Skia bitmap. Format: ARGB, one int per pixel, row-major order. For grayscale images, extracts the
	 * gray value from ARGB and writes single bytes.
	 */
	void writePixelsFromIntArray(int[] pixels)
	{
		awaitPendingPainters();

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
			resourceState.bitmap.installPixels(resourceState.bitmap.getImageInfo(), bytes, rowStride);
		}
		else
		{
			ByteBuffer buffer = ByteBuffer.allocate(pixels.length * 4).order(ByteOrder.nativeOrder());
			buffer.asIntBuffer().put(pixels);
			resourceState.bitmap.installPixels(resourceState.bitmap.getImageInfo(), buffer.array(), rowStride);
		}
		invalidateCachedImage();
	}

	/**
	 * Writes an int[] array to a rectangular region of the Skia bitmap. Uses a temporary bitmap and canvas drawing for efficiency with
	 * large images. For grayscale images, extracts gray values from ARGB ints.
	 */
	void writePixelsToRegion(int[] regionPixels, int destX, int destY, int regionWidth, int regionHeight)
	{
		awaitPendingPainters();

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
			tempImageInfo = new ImageInfo(regionWidth, regionHeight, ColorType.Companion.getN32(), resourceState.bitmap.getImageInfo().getColorAlphaType(), null);
			tempBitmap.allocPixels(tempImageInfo);

			ByteBuffer buffer = ByteBuffer.allocate(regionPixels.length * 4).order(ByteOrder.nativeOrder());
			buffer.asIntBuffer().put(regionPixels);
			tempBitmap.installPixels(tempImageInfo, buffer.array(), regionWidth * 4);
		}

		// Draw the temp image onto the main bitmap using Canvas
		Canvas canvas = new Canvas(resourceState.bitmap, new SurfaceProps());
		org.jetbrains.skia.Image tempImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(tempBitmap);
		canvas.drawImage(tempImage, destX, destY);
		tempImage.close();
		canvas.close();
		tempBitmap.close();
		invalidateCachedImage();
	}

	/**
	 * Reads grayscale pixels directly as a byte array, avoiding int[] conversion overhead. For use with grayscale image formats
	 * (Grayscale8Bit, Binary).
	 */
	byte[] readGrayscalePixels(IntRectangle bounds)
	{
		awaitPendingPainters();
		ensureCPUData();

		int x = bounds != null ? bounds.x : 0;
		int y = bounds != null ? bounds.y : 0;
		int w = bounds != null ? bounds.width : width;
		int h = bounds != null ? bounds.height : height;

		ImageInfo info = new ImageInfo(w, h, ColorType.GRAY_8, ColorAlphaType.OPAQUE, null);
		return resourceState.bitmap.readPixels(info, w, x, y);
	}

	/**
	 * Writes grayscale pixels directly from a byte array to the full image. For use with grayscale image formats (Grayscale8Bit, Binary).
	 */
	void writeGrayscalePixels(byte[] pixels)
	{
		awaitPendingPainters();
		resourceState.bitmap.installPixels(resourceState.bitmap.getImageInfo(), pixels, width);
		invalidateCachedImage();
	}

	/**
	 * Writes grayscale pixels to a rectangular region of the image. For use with grayscale image formats (Grayscale8Bit, Binary).
	 */
	void writeGrayscalePixelsToRegion(byte[] regionPixels, IntRectangle bounds)
	{
		awaitPendingPainters();
		ImageInfo tempImageInfo = new ImageInfo(bounds.width, bounds.height, ColorType.GRAY_8, ColorAlphaType.OPAQUE, null);
		Bitmap tempBitmap = new Bitmap();
		tempBitmap.allocPixels(tempImageInfo);
		tempBitmap.installPixels(tempImageInfo, regionPixels, bounds.width);

		Canvas canvas = new Canvas(resourceState.bitmap, new SurfaceProps());
		org.jetbrains.skia.Image tempImage = org.jetbrains.skia.Image.Companion.makeFromBitmap(tempBitmap);
		canvas.drawImage(tempImage, bounds.x, bounds.y);
		tempImage.close();
		canvas.close();
		tempBitmap.close();
		invalidateCachedImage();
	}

	public boolean isGpuEnabled()
	{
		return resourceState.isGpuEnabled;
	}

	/**
	 * Replaces this image's pixels by drawing from the source surface. Used for in-place shader operations where the result is written back
	 * to the original image. This method stays on GPU when possible to avoid expensive GPU-CPU-GPU transfers.
	 *
	 * @param source
	 *            The surface containing the shader result
	 * @param isOnGPUThread
	 *            True if this is being called from the GPU executor thread
	 */
	void replaceFromSurface(Surface source, boolean isOnGPUThread)
	{
		awaitPendingPainters();

		// Flush pending GPU commands to ensure the source surface is fully rendered
		source.flushAndSubmit(true);

		// Make an image snapshot from the source surface
		org.jetbrains.skia.Image snapshot = source.makeImageSnapshot();

		try
		{
			// Check if we can use GPU-to-GPU path
			// Skip GPU path for grayscale images because GPU surface is always N32 (RGBA) format
			// and syncGPUToCPU can't convert N32 back to grayscale correctly
			boolean canUseGPUPath = isOnGPUThread && resourceState.isGpuEnabled && resourceState.gpuSurface != null && resourceState.bitmap.getColorType() == ColorType.Companion.getN32();

			if (canUseGPUPath)
			{
				// GPU path: draw directly to GPU surface (fast GPU-to-GPU copy)
				Canvas gpuCanvas = resourceState.gpuSurface.getCanvas();
				gpuCanvas.drawImage(snapshot, 0, 0);
				// Flush the destination surface to ensure the draw is complete
				resourceState.gpuSurface.flushAndSubmit(true);
				markGPUDirty();
			}
			else
			{
				// CPU path: draw to bitmap
				Canvas canvas = new Canvas(resourceState.bitmap, new SurfaceProps());
				canvas.drawImage(snapshot, 0, 0);
				canvas.close();
				markCPUDirty();
			}
		}
		finally
		{
			snapshot.close();
		}
	}

}