package nortantis.platform.skia;

import nortantis.util.Logger;
import org.jetbrains.skia.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton that manages a dedicated GPU thread with a job queue for serialized GPU operations. All GPU operations (context creation,
 * surface creation, drawing) must happen on this thread.
 *
 * Thread safety: Operations can be submitted from any thread, but execution happens only on the GPU thread.
 */
public class GPUExecutor
{
	/**
	 * Rendering modes for controlling GPU and shader usage.
	 */
	public enum RenderingMode
	{
		/** CPU+GPU dual-backing: large images have both a CPU bitmap and GPU surface (safe default) */
		HYBRID,
		/** GPU-only for large images: reduces memory by ~50% but requires GPU hardware */
		GPU,
		/** CPU rendering with Skia shader rasterizer (no GPU required) */
		CPU_SHADERS,
		/** Traditional pixel-by-pixel CPU operations (no shaders) */
		CPU
	}

	private static final RenderingMode defaultRenderingMode = RenderingMode.HYBRID;

	private static volatile GPUExecutor instance;
	private static final Object instanceLock = new Object();

	// Rendering mode override - set before getInstance() is called, or call reset() to reinitialize
	private static volatile RenderingMode renderingModeOverride = defaultRenderingMode;

	// Pluggable GL context provider
	private static volatile GLContextProvider contextProvider;

	private final Thread gpuThread;
	private final BlockingQueue<GPUJob<?>> jobQueue;
	private final AtomicBoolean running;
	private final AtomicBoolean initialized;
	private volatile DirectContext directContext;
	private volatile boolean gpuAvailable;
	private volatile boolean shadersEnabled;
	private volatile int maxTextureSize = 8192; // Default fallback, will be queried from GPU

	// The context provider instance used during initialization (owned by GPU thread)
	private GLContextProvider activeProvider;

	// Shutdown sentinel job
	private static final GPUJob<?> SHUTDOWN_SENTINEL = new GPUJob<>(() -> null);

	/**
	 * Internal job wrapper that holds a callable and its completion future.
	 */
	private static class GPUJob<T>
	{
		final Callable<T> callable;
		final CompletableFuture<T> future;

		GPUJob(Callable<T> callable)
		{
			this.callable = callable;
			this.future = new CompletableFuture<>();
		}
	}

	private GPUExecutor()
	{
		this.jobQueue = new LinkedBlockingQueue<>();
		this.running = new AtomicBoolean(true);
		this.initialized = new AtomicBoolean(false);
		this.gpuAvailable = false;

		// Create and start the dedicated GPU thread
		this.gpuThread = new Thread(this::gpuThreadLoop, "GPU-Executor-Thread");
		this.gpuThread.setDaemon(true);
		this.gpuThread.start();

		// Wait for initialization to complete
		waitForInitialization();
	}

	/**
	 * Returns the singleton GPUExecutor instance.
	 */
	public static GPUExecutor getInstance()
	{
		if (instance == null)
		{
			synchronized (instanceLock)
			{
				if (instance == null)
				{
					instance = new GPUExecutor();
				}
			}
		}
		return instance;
	}

	/**
	 * Sets the rendering mode. Can be called at any time to change rendering behavior. If not set, this class uses a default determined by defaultRenderingMode.
	 *
	 * @param mode
	 *            The rendering mode to use
	 */
	public static void setRenderingMode(RenderingMode mode)
	{
		renderingModeOverride = mode;
	}

	/**
	 * Sets the rendering mode to the default rendering mode.
	 */
	public static void setRenderingModeToDefault()
	{
		renderingModeOverride = defaultRenderingMode;
	}

	/**
	 * Returns the current effective rendering mode. If no override has been set (null), returns defaultRenderingMode.
	 */
	public static RenderingMode getRenderingMode()
	{
		RenderingMode override = renderingModeOverride;
		return override != null ? override : defaultRenderingMode;
	}

	/**
	 * Sets the GL context provider used for GPU initialization. Call this before getInstance() if you want to use a custom provider (e.g.,
	 * an EGL-based provider on Android). If not set, LWJGLContextProvider is discovered via reflection.
	 */
	public static void setGLContextProvider(GLContextProvider provider)
	{
		contextProvider = provider;
	}

	/**
	 * Returns true when the rendering mode is GPU and GPU hardware is available. In this mode, large non-grayscale images are backed only
	 * by a GPU surface (no CPU bitmap), reducing memory usage.
	 */
	public static boolean isGpuOnlyMode()
	{
		return getRenderingMode() == RenderingMode.GPU && getInstance().isGPUAvailable();
	}

	/**
	 * Returns true if GPU acceleration is available and enabled.
	 */
	public boolean isGPUAvailable()
	{
		if (getRenderingMode() == RenderingMode.CPU || getRenderingMode() == RenderingMode.CPU_SHADERS)
		{
			return false;
		}
		return gpuAvailable && directContext != null && running.get();
	}

	/**
	 * Returns true if shader operations are enabled. When true, Skia shaders will be used for image operations (on GPU if available,
	 * otherwise on CPU). When false, traditional pixel-by-pixel CPU operations are used instead.
	 */
	public boolean isShadersEnabled()
	{
		if (getRenderingMode() == RenderingMode.CPU)
		{
			return false;
		}
		return shadersEnabled;
	}

	/**
	 * Returns the maximum texture dimension supported by the GPU. This is queried from GL_MAX_TEXTURE_SIZE during initialization. Returns a
	 * default fallback value if GPU is not available.
	 */
	public int getMaxTextureSize()
	{
		return maxTextureSize;
	}

	/**
	 * Returns true if the current thread is the GPU thread.
	 */
	public boolean isOnGPUThread()
	{
		return Thread.currentThread() == gpuThread;
	}

	/**
	 * Returns the DirectContext. Only valid when called from the GPU thread. Package-private for use by GPUBatchingPainter.
	 */
	DirectContext getContext()
	{
		if (!isOnGPUThread())
		{
			throw new IllegalStateException("DirectContext can only be accessed from the GPU thread");
		}
		return directContext;
	}

	/**
	 * Submits a callable to execute on the GPU thread and blocks until completion.
	 *
	 * @param callable
	 *            The operation to execute
	 * @return The result of the callable
	 * @throws RuntimeException
	 *             if the callable throws an exception
	 */
	public <T> T submit(Callable<T> callable)
	{
		if (!running.get())
		{
			throw new IllegalStateException("GPUExecutor has been shut down");
		}

		// If already on GPU thread, execute directly
		if (isOnGPUThread())
		{
			try
			{
				return callable.call();
			}
			catch (Exception e)
			{
				throw new RuntimeException("GPU operation failed", e);
			}
		}

		// Submit to queue and wait for result
		GPUJob<T> job = new GPUJob<>(callable);
		jobQueue.offer(job);

		try
		{
			return job.future.get();
		}
		catch (Exception e)
		{
			throw new RuntimeException("GPU operation failed", e);
		}
	}

	/**
	 * Submits a callable to execute on the GPU thread asynchronously.
	 *
	 * @param callable
	 *            The operation to execute
	 * @return A CompletableFuture that completes with the result
	 */
	public <T> CompletableFuture<T> submitAsync(Callable<T> callable)
	{
		if (!running.get())
		{
			CompletableFuture<T> future = new CompletableFuture<>();
			future.completeExceptionally(new IllegalStateException("GPUExecutor has been shut down"));
			return future;
		}

		// If already on GPU thread, execute directly
		if (isOnGPUThread())
		{
			CompletableFuture<T> future = new CompletableFuture<>();
			try
			{
				future.complete(callable.call());
			}
			catch (Exception e)
			{
				future.completeExceptionally(e);
			}
			return future;
		}

		GPUJob<T> job = new GPUJob<>(callable);
		jobQueue.offer(job);
		return job.future;
	}

	/**
	 * Submits a runnable to execute on the GPU thread asynchronously.
	 *
	 * @param runnable
	 *            The operation to execute
	 * @return A CompletableFuture that completes when done
	 */
	public CompletableFuture<Void> submitAsync(Runnable runnable)
	{
		return submitAsync(() ->
		{
			runnable.run();
			return null;
		});
	}

	/**
	 * Creates a GPU surface on the GPU thread.
	 *
	 * @param width
	 *            Surface width
	 * @param height
	 *            Surface height
	 * @return GPU Surface, or null if GPU is not available
	 */
	public Surface createGPUSurface(int width, int height)
	{
		if (!isGPUAvailable())
		{
			return null;
		}

		return submit(() ->
		{
			if (directContext == null)
			{
				return null;
			}
			try
			{
				return Surface.Companion.makeRenderTarget(directContext, false, // budgeted
						new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
			}
			catch (Exception e)
			{
				Logger.printError("GPUExecutor: Failed to create GPU surface: " + e.getMessage(), e);
				return null;
			}
		});
	}

	/**
	 * Shuts down the GPU executor and releases all resources. Only needed if the executor must be torn down and recreated within a running
	 * process.
	 */
	public void shutdown()
	{
		if (!running.compareAndSet(true, false))
		{
			return; // Already shut down
		}

		// Signal the GPU thread to stop
		jobQueue.offer(SHUTDOWN_SENTINEL);

		// Wait for GPU thread to finish
		try
		{
			gpuThread.join(5000); // 5 second timeout
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		// Clear the singleton
		synchronized (instanceLock)
		{
			if (instance == this)
			{
				instance = null;
			}
		}
	}

	/**
	 * The main loop that runs on the GPU thread.
	 */
	private void gpuThreadLoop()
	{
		try
		{
			// Initialize GPU context on this thread
			initializeGPUContext();
			initialized.set(true);

			// Process jobs until shutdown
			while (running.get())
			{
				try
				{
					GPUJob<?> job = jobQueue.poll(100, TimeUnit.MILLISECONDS);
					if (job == null)
					{
						continue;
					}

					if (job == SHUTDOWN_SENTINEL)
					{
						break;
					}

					executeJob(job);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		finally
		{
			// Clean up GPU resources on this thread
			cleanupGPUContext();

			// Complete any remaining jobs with errors
			GPUJob<?> remainingJob;
			while ((remainingJob = jobQueue.poll()) != null)
			{
				if (remainingJob != SHUTDOWN_SENTINEL)
				{
					remainingJob.future.completeExceptionally(new IllegalStateException("GPUExecutor shut down"));
				}
			}
		}
	}

	/**
	 * Executes a single job and completes its future.
	 */
	@SuppressWarnings("unchecked")
	private <T> void executeJob(GPUJob<T> job)
	{
		try
		{
			T result = job.callable.call();
			job.future.complete(result);
		}
		catch (Exception e)
		{
			job.future.completeExceptionally(e);
		}
	}

	/**
	 * Waits for the GPU thread to complete initialization.
	 */
	private void waitForInitialization()
	{
		while (!initialized.get())
		{
			try
			{
				Thread.sleep(10);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Resolves the GLContextProvider to use. If one was set explicitly, use that. Otherwise, try to discover LWJGLContextProvider via
	 * reflection. Returns null if no provider is available.
	 */
	private GLContextProvider resolveContextProvider()
	{
		GLContextProvider provider = contextProvider;
		if (provider != null)
		{
			return provider;
		}

		// Try reflective discovery of LWJGLContextProvider
		try
		{
			Class<?> clazz = Class.forName("nortantis.platform.skia.LWJGLContextProvider");
			return (GLContextProvider) clazz.getDeclaredConstructor().newInstance();
		}
		catch (Exception e)
		{
			Logger.println("GPUExecutor: LWJGLContextProvider not available: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Initializes the GPU context. Called on the GPU thread.
	 */
	private void initializeGPUContext()
	{
		try
		{
			// Determine rendering mode to use.
			RenderingMode mode = getRenderingMode();

			// Set shader availability based on mode
			shadersEnabled = (mode != RenderingMode.CPU);

			if (mode == RenderingMode.CPU)
			{
				gpuAvailable = false;
				Logger.println("GPUExecutor: CPU mode - GPU and shaders disabled, using pixel-by-pixel rendering.");
				return;
			}

			if (mode == RenderingMode.CPU_SHADERS)
			{
				gpuAvailable = false;
				Logger.println("GPUExecutor: CPU_SHADERS mode - shaders will run on CPU.");
				return;
			}

			// GPU mode - try to initialize GPU via provider
			activeProvider = resolveContextProvider();

			if (activeProvider == null)
			{
				gpuAvailable = false;
				if (mode == RenderingMode.GPU || mode == RenderingMode.HYBRID)
				{
					Logger.println("GPUExecutor: No GLContextProvider available, falling back to CPU shaders");
				}
				return;
			}

			if (activeProvider.isHeadless())
			{
				gpuAvailable = false;
				Logger.println("GPUExecutor: Headless environment detected, falling back to CPU shaders");
				return;
			}

			if (!activeProvider.initialize())
			{
				gpuAvailable = false;
				Logger.println("GPUExecutor: Failed to initialize GL context, falling back to CPU shaders");
				activeProvider = null;
				return;
			}

			maxTextureSize = activeProvider.getMaxTextureSize();

			// Create Skia DirectContext
			directContext = activeProvider.createDirectContext();

			if (directContext != null)
			{
				gpuAvailable = true;
				Logger.println("GPUExecutor: GPU acceleration enabled on dedicated thread");
			}
			else
			{
				gpuAvailable = false;
				Logger.println("GPUExecutor: GPU acceleration not available, falling back to CPU shaders");
				activeProvider.cleanup();
				activeProvider = null;
			}
		}
		catch (Exception | UnsatisfiedLinkError e)
		{
			gpuAvailable = false;
			directContext = null;
			Logger.println("GPUExecutor: Failed to initialize GPU context: " + e.getMessage());
			if (activeProvider != null)
			{
				activeProvider.cleanup();
				activeProvider = null;
			}
		}
	}

	/**
	 * Cleans up the GPU context. Called on the GPU thread.
	 */
	private void cleanupGPUContext()
	{
		try
		{
			if (directContext != null)
			{
				directContext.abandon();
				directContext.close();
				directContext = null;
			}
		}
		catch (Exception e)
		{
			// Ignore cleanup errors
		}

		if (activeProvider != null)
		{
			activeProvider.cleanup();
			activeProvider = null;
		}
		gpuAvailable = false;
	}
}
