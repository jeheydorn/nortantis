package nortantis.platform.skia;

import nortantis.Stroke;
import nortantis.StrokeType;
import nortantis.geom.FloatPoint;
import nortantis.platform.*;
import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.Image;
import nortantis.util.Logger;
import org.jetbrains.skia.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A Painter implementation that batches drawing operations and submits them asynchronously to the GPU thread via GPUExecutor.
 *
 * Key behaviors: - Operations are captured as lambdas: (canvas, paint) -> { ... } - Paint state is snapshotted once per batch (not per
 * operation) - Flush triggers: batch size reached, paint state change, await() called - await() waits for all pending futures
 */
public class GPUBatchingPainter extends Painter
{
	private static final int DEFAULT_BATCH_SIZE = 100;

	private final Surface gpuSurface;
	private final SkiaImage targetImage;
	private final DrawQuality quality;
	private final int batchSize;

	// Current batch of operations
	private final List<BiConsumer<Canvas, Paint>> currentBatch;

	// Pending futures from submitted batches
	private final List<CompletableFuture<Void>> pendingFutures;
	private final boolean trackedDither;

	// Current paint state that will be used when the batch is flushed
	private PaintState currentPaintState;

	// Transform state (applied per-batch, not per-operation)
	private Matrix33 currentTransform;

	// Clip state
	private Rect currentClip;

	// Manual batch mode flag
	private boolean manualBatchMode = false;

	// Track if disposed
	private boolean disposed = false;

	// Track source images referenced in the current batch (for drawImage calls)
	// These images need to know this painter has pending operations referencing them
	private final Set<SkiaImage> sourceImagesInCurrentBatch = new HashSet<>();

	/**
	 * Immutable snapshot of paint state for a batch.
	 */
	private static class PaintState
	{
		final int color;
		final BlendMode blendMode;
		final int alpha;
		final PaintMode paintMode;
		final float strokeWidth;
		final PaintStrokeCap strokeCap;
		final PaintStrokeJoin strokeJoin;
		final PathEffect pathEffect;
		final Shader shader;
		final boolean antiAlias;
		final boolean dither;

		PaintState(int color, BlendMode blendMode, int alpha, PaintMode paintMode, float strokeWidth, PaintStrokeCap strokeCap, PaintStrokeJoin strokeJoin, PathEffect pathEffect, Shader shader,
				boolean antiAlias, boolean dither)
		{
			this.color = color;
			this.blendMode = blendMode;
			this.alpha = alpha;
			this.paintMode = paintMode;
			this.strokeWidth = strokeWidth;
			this.strokeCap = strokeCap;
			this.strokeJoin = strokeJoin;
			this.pathEffect = pathEffect;
			this.shader = shader;
			this.antiAlias = antiAlias;
			this.dither = dither;
		}

		/**
		 * Creates a Skia Paint object from this state.
		 */
		Paint toPaint()
		{
			Paint paint = new Paint();
			paint.setColor(color);
			paint.setBlendMode(blendMode);
			paint.setAlpha(alpha);
			paint.setMode(paintMode);
			paint.setStrokeWidth(strokeWidth);
			paint.setStrokeCap(strokeCap);
			paint.setStrokeJoin(strokeJoin);
			paint.setPathEffect(pathEffect);
			paint.setShader(shader);
			paint.setAntiAlias(antiAlias);
			paint.setDither(dither);
			return paint;
		}

		/**
		 * Checks if this state equals another (for determining if flush is needed).
		 */
		boolean isEquivalent(PaintState other)
		{
			if (other == null)
				return false;
			return color == other.color && blendMode == other.blendMode && alpha == other.alpha && paintMode == other.paintMode && Float.compare(strokeWidth, other.strokeWidth) == 0
					&& strokeCap == other.strokeCap && strokeJoin == other.strokeJoin && pathEffect == other.pathEffect // Reference
																														// comparison for
																														// effects
					&& shader == other.shader // Reference comparison for shaders
					&& antiAlias == other.antiAlias;
		}
	}

	// Mutable state tracking (mirrors what would be in a Paint object)
	private int trackedColor = 0xFF000000; // Black
	private BlendMode trackedBlendMode = BlendMode.SRC_OVER;
	private int trackedAlpha = 255;
	private PaintMode trackedPaintMode = PaintMode.FILL;
	private float trackedStrokeWidth = 1.0f;
	private PaintStrokeCap trackedStrokeCap = PaintStrokeCap.ROUND;
	private PaintStrokeJoin trackedStrokeJoin = PaintStrokeJoin.ROUND;
	private PathEffect trackedPathEffect = null;
	private Shader trackedShader = null;
	private boolean trackedAntiAlias;

	// Font tracking
	private SkiaFont currentFont;

	/**
	 * Creates a new GPUBatchingPainter for the given GPU surface and target image.
	 *
	 * @param gpuSurface
	 *            The GPU surface to draw on
	 * @param targetImage
	 *            The SkiaImage that owns this painter (for cleanup tracking)
	 * @param quality
	 *            The draw quality setting
	 */
	public GPUBatchingPainter(Surface gpuSurface, SkiaImage targetImage, DrawQuality quality)
	{
		this(gpuSurface, targetImage, quality, DEFAULT_BATCH_SIZE);
	}

	/**
	 * Creates a new GPUBatchingPainter with custom batch size.
	 */
	public GPUBatchingPainter(Surface gpuSurface, SkiaImage targetImage, DrawQuality quality, int batchSize)
	{
		this.gpuSurface = gpuSurface;
		this.targetImage = targetImage;
		this.quality = quality;
		this.batchSize = batchSize;
		this.currentBatch = new ArrayList<>(batchSize);
		this.pendingFutures = new ArrayList<>();
		this.trackedAntiAlias = (quality == DrawQuality.High);
		this.trackedDither = (quality == DrawQuality.High);
		this.currentTransform = Matrix33.Companion.getIDENTITY();
		this.currentClip = null;

		// Initialize paint state
		snapshotPaintState();
	}

	/**
	 * Takes a snapshot of the current paint state.
	 */
	private void snapshotPaintState()
	{
		currentPaintState = new PaintState(trackedColor, trackedBlendMode, trackedAlpha, trackedPaintMode, trackedStrokeWidth, trackedStrokeCap, trackedStrokeJoin, trackedPathEffect, trackedShader,
				trackedAntiAlias, trackedDither);
	}

	/**
	 * Checks if the current tracked state differs from the snapshot, and flushes if needed.
	 */
	private void checkPaintStateChange()
	{
		PaintState newState = new PaintState(trackedColor, trackedBlendMode, trackedAlpha, trackedPaintMode, trackedStrokeWidth, trackedStrokeCap, trackedStrokeJoin, trackedPathEffect, trackedShader,
				trackedAntiAlias, trackedDither);

		if (!newState.isEquivalent(currentPaintState))
		{
			flushBatch();
			currentPaintState = newState;
		}
	}

	/**
	 * Adds an operation to the current batch.
	 */
	private void addOperation(BiConsumer<Canvas, Paint> operation)
	{
		if (disposed)
		{
			Logger.println("GPUBatchingPainter: Operation added after dispose");
			return;
		}

		currentBatch.add(operation);

		// Auto-flush if batch is full and not in manual mode
		if (!manualBatchMode && currentBatch.size() >= batchSize)
		{
			flushBatch();
		}
	}

	/**
	 * Flushes the current batch to the GPU thread.
	 */
	private void flushBatch()
	{
		if (currentBatch.isEmpty())
		{
			return;
		}

		// Capture the batch and state for the async task
		final List<BiConsumer<Canvas, Paint>> batchToSubmit = new ArrayList<>(currentBatch);
		final PaintState stateToUse = currentPaintState;
		final Matrix33 transformToUse = currentTransform;
		final Rect clipToUse = currentClip;

		// Capture and clear source images for this batch
		final Set<SkiaImage> sourceImagesForThisBatch = new HashSet<>(sourceImagesInCurrentBatch);
		sourceImagesInCurrentBatch.clear();

		currentBatch.clear();

		// Reference to this painter for the completion callback
		final GPUBatchingPainter thisPainter = this;

		// Submit to GPU thread
		CompletableFuture<Void> future = GPUExecutor.getInstance().submitAsync(() ->
		{
			Canvas canvas = gpuSurface.getCanvas();
			Paint paint = stateToUse.toPaint();

			try
			{
				// Save canvas state
				canvas.save();

				// Apply transform
				if (transformToUse != null && !transformToUse.equals(Matrix33.Companion.getIDENTITY()))
				{
					canvas.setMatrix(transformToUse);
				}

				// Apply clip
				if (clipToUse != null)
				{
					canvas.clipRect(clipToUse);
				}

				// Execute all operations in the batch
				for (BiConsumer<Canvas, Paint> op : batchToSubmit)
				{
					op.accept(canvas, paint);
				}

				// Restore canvas state
				canvas.restore();
			}
			finally
			{
				paint.close();

				// Unregister from source images now that the batch has completed
				for (SkiaImage sourceImage : sourceImagesForThisBatch)
				{
					sourceImage.removeReferencingPainter(thisPainter);
				}
			}
		});

		synchronized (pendingFutures)
		{
			pendingFutures.add(future);

			// Clean up completed futures periodically
			pendingFutures.removeIf(CompletableFuture::isDone);
		}
	}

	@Override
	public void await()
	{
		List<CompletableFuture<Void>> futuresToWait;

		synchronized (pendingFutures)
		{
			// Flush any remaining operations
			flushBatch();

			// Take a snapshot and clear to avoid ConcurrentModificationException
			// if another thread calls flushBatch while we're waiting
			futuresToWait = new ArrayList<>(pendingFutures);
			pendingFutures.clear();
		}

		// Wait for all pending futures (outside synchronized block)
		for (CompletableFuture<Void> future : futuresToWait)
		{
			try
			{
				future.join();
			}
			catch (Exception e)
			{
				Logger.printError("GPUBatchingPainter: Error waiting for batch", e);
			}
		}
	}

	@Override
	public void setManualBatchMode(boolean manual)
	{
		this.manualBatchMode = manual;
	}

	@Override
	public void dispose()
	{
		if (disposed)
		{
			return;
		}
		disposed = true;

		// Flush and await remaining operations
		await();

		// Notify the target image that this painter is done
		if (targetImage != null)
		{
			targetImage.onPainterClosed(this);
		}
	}

	// ===== Drawing Operations =====

	@Override
	public void drawImage(Image image, int x, int y)
	{
		if (!(image instanceof SkiaImage))
		{
			throw new IllegalArgumentException("GPUBatchingPainter.drawImage requires SkiaImage");
		}
		final SkiaImage skImage = (SkiaImage) image;
		final org.jetbrains.skia.Image skiaNativeImage = skImage.getSkiaImage();
		trackSourceImage(skImage);
		addOperation((canvas, paint) -> canvas.drawImage(skiaNativeImage, x, y, paint));
	}

	@Override
	public void drawImage(Image image, int x, int y, int width, int height)
	{
		if (!(image instanceof SkiaImage))
		{
			throw new IllegalArgumentException("GPUBatchingPainter.drawImage requires SkiaImage");
		}
		final SkiaImage skImage = (SkiaImage) image;
		final org.jetbrains.skia.Image skiaNativeImage = skImage.getSkiaImage();
		trackSourceImage(skImage);
		addOperation((canvas, paint) -> canvas.drawImageRect(skiaNativeImage, Rect.makeXYWH(x, y, width, height), paint));
	}

	/**
	 * Tracks a source image that is being used in the current batch. Registers this painter with the image so that closing the image will
	 * wait for this batch to complete.
	 */
	private void trackSourceImage(SkiaImage sourceImage)
	{
		if (sourceImagesInCurrentBatch.add(sourceImage))
		{
			// First time this image is used in current batch - register with it
			sourceImage.addReferencingPainter(this);
		}
	}

	@Override
	public void setAlphaComposite(AlphaComposite composite, float alpha)
	{
		setAlphaComposite(composite);
		trackedAlpha = (int) (alpha * 255);
		checkPaintStateChange();
	}

	@Override
	public void setAlphaComposite(AlphaComposite composite)
	{
		trackedBlendMode = toBlendMode(composite);
		checkPaintStateChange();
	}

	private BlendMode toBlendMode(AlphaComposite composite)
	{
		return switch (composite)
		{
			case SrcOver -> BlendMode.SRC_OVER;
			case Src -> BlendMode.SRC;
			case SrcAtop -> BlendMode.SRC_ATOP;
			case DstIn -> BlendMode.DST_IN;
			case Dst -> BlendMode.DST;
			case DstOver -> BlendMode.DST_OVER;
			case SrcIn -> BlendMode.SRC_IN;
			case SrcOut -> BlendMode.SRC_OUT;
			case DstOut -> BlendMode.DST_OUT;
			case DstAtop -> BlendMode.DST_ATOP;
			case Xor -> BlendMode.XOR;
			case Clear -> BlendMode.CLEAR;
		};
	}

	@Override
	public void setColor(Color color)
	{
		if (!(color instanceof SkiaColor))
		{
			throw new IllegalArgumentException("GPUBatchingPainter.setColor requires SkiaColor");
		}
		trackedShader = null; // Clear gradient
		trackedColor = color.getRGB();
		// Extract alpha from the color to ensure semi-transparent colors work correctly.
		// Without this, toPaint()'s paint.setAlpha(alpha) would overwrite the color's alpha.
		trackedAlpha = color.getAlpha();
		checkPaintStateChange();
	}

	@Override
	public void drawRect(int x, int y, int width, int height)
	{
		trackedPaintMode = PaintMode.STROKE;
		checkPaintStateChange();
		addOperation((canvas, paint) ->
		{
			paint.setMode(PaintMode.STROKE);
			canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint);
		});
	}

	@Override
	public void rotate(double angle, double pivotX, double pivotY)
	{
		// Flush before transform change
		flushBatch();

		// Apply rotation to current transform
		float angleDegrees = (float) Math.toDegrees(angle);
		Matrix33 rotation = Matrix33.Companion.makeRotate(angleDegrees, (float) pivotX, (float) pivotY);
		currentTransform = currentTransform.makeConcat(rotation);
	}

	@Override
	public void translate(double x, double y)
	{
		// Flush before transform change
		flushBatch();

		Matrix33 translation = Matrix33.Companion.makeTranslate((float) x, (float) y);
		currentTransform = currentTransform.makeConcat(translation);
	}

	@Override
	public void setFont(Font font)
	{
		if (!(font instanceof SkiaFont))
		{
			throw new IllegalArgumentException("GPUBatchingPainter.setFont requires SkiaFont");
		}
		this.currentFont = (SkiaFont) font;
	}

	@Override
	public void drawString(String string, double x, double y)
	{
		if (currentFont == null)
		{
			return;
		}
		final SkiaFont fontToUse = currentFont;
		trackedPaintMode = PaintMode.FILL;
		checkPaintStateChange();
		addOperation((canvas, paint) ->
		{
			paint.setMode(PaintMode.FILL);
			canvas.drawString(string, (float) x, (float) y, fontToUse.skiaFont, paint);
		});
	}

	@Override
	public void setTransform(Transform transform)
	{
		if (!(transform instanceof SkiaTransform))
		{
			throw new IllegalArgumentException("GPUBatchingPainter.setTransform requires SkiaTransform");
		}
		// Flush before transform change
		flushBatch();
		currentTransform = ((SkiaTransform) transform).matrix;
	}

	@Override
	public Transform getTransform()
	{
		return new SkiaTransform(currentTransform);
	}

	@Override
	public Font getFont()
	{
		return currentFont;
	}

	@Override
	public Color getColor()
	{
		return new SkiaColor(trackedColor, true);
	}

	@Override
	public void fillPolygon(int[] xPoints, int[] yPoints)
	{
		if (xPoints.length == 0)
			return;

		// Create path copy for the lambda
		final int[] xCopy = xPoints.clone();
		final int[] yCopy = yPoints.clone();

		trackedPaintMode = PaintMode.FILL;
		checkPaintStateChange();

		addOperation((canvas, paint) ->
		{
			Path path = new Path();
			path.moveTo(xCopy[0], yCopy[0]);
			for (int i = 1; i < xCopy.length; i++)
			{
				path.lineTo(xCopy[i], yCopy[i]);
			}
			path.closePath();
			paint.setMode(PaintMode.FILL);
			canvas.drawPath(path, paint);
			path.close();
		});
	}

	@Override
	public void drawPolygon(int[] xPoints, int[] yPoints)
	{
		if (xPoints.length == 0)
			return;

		final int[] xCopy = xPoints.clone();
		final int[] yCopy = yPoints.clone();

		trackedPaintMode = PaintMode.STROKE;
		checkPaintStateChange();

		addOperation((canvas, paint) ->
		{
			Path path = new Path();
			path.moveTo(xCopy[0], yCopy[0]);
			for (int i = 1; i < xCopy.length; i++)
			{
				path.lineTo(xCopy[i], yCopy[i]);
			}
			path.closePath();
			paint.setMode(PaintMode.STROKE);
			canvas.drawPath(path, paint);
			path.close();
		});
	}

	@Override
	public void drawPolyline(int[] xPoints, int[] yPoints)
	{
		if (xPoints.length == 0)
			return;

		final int[] xCopy = xPoints.clone();
		final int[] yCopy = yPoints.clone();

		trackedPaintMode = PaintMode.STROKE;
		checkPaintStateChange();

		addOperation((canvas, paint) ->
		{
			Path path = new Path();
			path.moveTo(xCopy[0], yCopy[0]);
			for (int i = 1; i < xCopy.length; i++)
			{
				path.lineTo(xCopy[i], yCopy[i]);
			}
			paint.setMode(PaintMode.STROKE);
			canvas.drawPath(path, paint);
			path.close();
		});
	}

	@Override
	public void drawPolygonFloat(List<FloatPoint> points)
	{
		if (points.isEmpty())
			return;

		final List<FloatPoint> pointsCopy = new ArrayList<>(points);

		trackedPaintMode = PaintMode.STROKE;
		checkPaintStateChange();

		addOperation((canvas, paint) ->
		{
			Path path = new Path();
			path.moveTo(pointsCopy.get(0).x, pointsCopy.get(0).y);
			for (int i = 1; i < pointsCopy.size(); i++)
			{
				path.lineTo(pointsCopy.get(i).x, pointsCopy.get(i).y);
			}
			path.closePath();
			paint.setMode(PaintMode.STROKE);
			canvas.drawPath(path, paint);
			path.close();
		});
	}

	@Override
	public void setGradient(float x1, float y1, Color color1, float x2, float y2, Color color2)
	{
		if (!(color1 instanceof SkiaColor) || !(color2 instanceof SkiaColor))
		{
			throw new IllegalArgumentException("GPUBatchingPainter.setGradient requires SkiaColor");
		}
		trackedShader = org.jetbrains.skia.Shader.Companion.makeLinearGradient(x1, y1, x2, y2, new int[] { color1.getRGB(), color2.getRGB() }, null,
				org.jetbrains.skia.GradientStyle.Companion.getDEFAULT());
		checkPaintStateChange();
	}

	@Override
	public void setBasicStroke(float width)
	{
		trackedPaintMode = PaintMode.STROKE;
		trackedStrokeWidth = width;
		trackedStrokeCap = PaintStrokeCap.ROUND;
		trackedStrokeJoin = PaintStrokeJoin.ROUND;
		trackedPathEffect = null;
		checkPaintStateChange();
	}

	@Override
	public void setStrokeToSolidLineWithNoEndDecorations(float width)
	{
		trackedPaintMode = PaintMode.STROKE;
		trackedStrokeWidth = width;
		trackedStrokeCap = PaintStrokeCap.BUTT;
		trackedStrokeJoin = PaintStrokeJoin.ROUND;
		trackedPathEffect = null;
		checkPaintStateChange();
	}

	@Override
	public void setStroke(Stroke stroke, double resolutionScale)
	{
		float width = stroke.width * (float) resolutionScale;

		if (stroke.type == StrokeType.Solid)
		{
			setBasicStroke(width);
		}
		else
		{
			float scale = ((float) resolutionScale) * stroke.width;
			float[] intervals;

			if (stroke.type == StrokeType.Dashes)
			{
				intervals = new float[] { 6f * scale, 3f * scale };
				trackedStrokeCap = PaintStrokeCap.BUTT;
			}
			else if (stroke.type == StrokeType.Rounded_Dashes)
			{
				intervals = new float[] { 6f * scale, 4f * scale };
				trackedStrokeCap = PaintStrokeCap.ROUND;
			}
			else if (stroke.type == StrokeType.Dots)
			{
				final float scaleBecauseDotsLookSmallerThanDashes = (3.9f / 2.7f);
				float dotScale = scale * scaleBecauseDotsLookSmallerThanDashes;
				intervals = new float[] { 0f, 2.0f * dotScale };
				trackedStrokeCap = PaintStrokeCap.ROUND;
				width *= scaleBecauseDotsLookSmallerThanDashes;
			}
			else
			{
				throw new UnsupportedOperationException("Unrecognized stroke type: " + stroke.type);
			}

			trackedPaintMode = PaintMode.STROKE;
			trackedStrokeWidth = width;
			trackedPathEffect = PathEffect.Companion.makeDash(intervals, 0f);
			trackedStrokeJoin = PaintStrokeJoin.ROUND;
			checkPaintStateChange();
		}
	}

	@Override
	public void drawLine(int x1, int y1, int x2, int y2)
	{
		trackedPaintMode = PaintMode.STROKE;
		checkPaintStateChange();
		addOperation((canvas, paint) ->
		{
			paint.setMode(PaintMode.STROKE);
			canvas.drawLine(x1, y1, x2, y2, paint);
		});
	}

	@Override
	public void drawLine(float x1, float y1, float x2, float y2)
	{
		trackedPaintMode = PaintMode.STROKE;
		checkPaintStateChange();
		addOperation((canvas, paint) ->
		{
			paint.setMode(PaintMode.STROKE);
			canvas.drawLine(x1, y1, x2, y2, paint);
		});
	}

	@Override
	public void drawOval(int x, int y, int width, int height)
	{
		trackedPaintMode = PaintMode.STROKE;
		checkPaintStateChange();
		addOperation((canvas, paint) ->
		{
			paint.setMode(PaintMode.STROKE);
			canvas.drawOval(Rect.makeXYWH(x, y, width, height), paint);
		});
	}

	@Override
	public void fillOval(int x, int y, int width, int height)
	{
		trackedPaintMode = PaintMode.FILL;
		checkPaintStateChange();
		addOperation((canvas, paint) ->
		{
			paint.setMode(PaintMode.FILL);
			canvas.drawOval(Rect.makeXYWH(x, y, width, height), paint);
		});
	}

	@Override
	public void fillRect(int x, int y, int width, int height)
	{
		trackedPaintMode = PaintMode.FILL;
		checkPaintStateChange();
		addOperation((canvas, paint) ->
		{
			paint.setMode(PaintMode.FILL);
			canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint);
		});
	}

	@Override
	public int stringWidth(String string)
	{
		if (currentFont == null)
			return 0;

		// This needs synchronous execution - font metrics need immediate result
		return GPUExecutor.getInstance().submit(() ->
		{
			Paint paint = currentPaintState.toPaint();
			try
			{
				return (int) currentFont.skiaFont.measureTextWidth(string, paint);
			}
			finally
			{
				paint.close();
			}
		});
	}

	@Override
	public int charWidth(char c)
	{
		return stringWidth(String.valueOf(c));
	}

	@Override
	public int getFontAscent()
	{
		if (currentFont == null)
			return 0;
		return (int) -currentFont.skiaFont.getMetrics().getAscent();
	}

	@Override
	public int getFontDescent()
	{
		if (currentFont == null)
			return 0;
		return (int) currentFont.skiaFont.getMetrics().getDescent();
	}

	@Override
	public void setClip(int x, int y, int width, int height)
	{
		// Flush before clip change
		flushBatch();
		currentClip = Rect.makeXYWH(x, y, width, height);
	}
}
