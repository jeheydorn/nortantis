package nortantis.platform.skia;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.DirectContext;
import org.jetbrains.skia.ImageInfo;
import org.jetbrains.skia.Surface;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import nortantis.util.Logger;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

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
		/** Behavior is determined by the default in defaultRenderingMode */
		DEFAULT,
		/** GPU acceleration with shaders (fastest, requires GPU hardware) */
		GPU,
		/** CPU rendering with Skia shader rasterizer (no GPU required) */
		CPU_SHADERS,
		/** Traditional pixel-by-pixel CPU operations (no shaders) */
		CPU
	}
	private static final RenderingMode defaultRenderingMode = RenderingMode.GPU;

	private static volatile GPUExecutor instance;
	private static final Object instanceLock = new Object();

	// Rendering mode override - set before getInstance() is called, or call reset() to reinitialize
	private static volatile RenderingMode renderingModeOverride = null;

	private final Thread gpuThread;
	private final BlockingQueue<GPUJob<?>> jobQueue;
	private final AtomicBoolean running;
	private final AtomicBoolean initialized;
	private volatile DirectContext directContext;
	private volatile boolean gpuAvailable;
	private volatile boolean shadersEnabled;
	private volatile int maxTextureSize = 8192; // Default fallback, will be queried from GPU

	// LWJGL/GLFW resources (owned by GPU thread)
	private long glfwWindow = NULL;
	private boolean glfwInitialized = false;

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
	 * Sets the rendering mode. Can be called at any time to change rendering behavior.
	 * If not set, defaults to GPU mode with auto-detection (uses GPU if available, otherwise falls back to CPU shaders).
	 *
	 * @param mode
	 *            The rendering mode to use
	 */
	public static void setRenderingMode(RenderingMode mode)
	{
		renderingModeOverride = mode;
	}

	/**
	 * Returns the current rendering mode override, or null if using auto-detection.
	 */
	public static RenderingMode getRenderingMode()
	{
		return renderingModeOverride;
	}

	/**
	 * Returns true if GPU acceleration is available and enabled.
	 */
	public boolean isGPUAvailable()
	{
		if (renderingModeOverride != null && renderingModeOverride != RenderingMode.DEFAULT && renderingModeOverride != RenderingMode.GPU)
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
		if (renderingModeOverride == RenderingMode.CPU)
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
	 * Shuts down the GPU executor and releases all resources. Should be called during application shutdown.
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
	 * Initializes the GPU context. Called on the GPU thread.
	 */
	private void initializeGPUContext()
	{
		try
		{
			// Determine rendering mode from override or default to GPU with auto-detection
			RenderingMode mode = renderingModeOverride;
			if (mode == null || mode == RenderingMode.DEFAULT)
			{
				mode = defaultRenderingMode;
			}

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

			// GPU mode - try to initialize GPU
			// Initialize GLFW and create OpenGL context
			if (!initializeGLFW())
			{
				gpuAvailable = false;
				Logger.println("GPUExecutor: Failed to initialize GLFW, falling back to CPU shaders");
				return;
			}

			// Create Skia DirectContext
			directContext = tryCreateDirectContext();

			if (directContext != null)
			{
				gpuAvailable = true;
				Logger.println("GPUExecutor: GPU acceleration enabled on dedicated thread");
			}
			else
			{
				gpuAvailable = false;
				Logger.println("GPUExecutor: GPU acceleration not available, falling back to CPU shaders");
				cleanupGLFW();
			}
		}
		catch (Exception | UnsatisfiedLinkError e)
		{
			gpuAvailable = false;
			directContext = null;
			Logger.println("GPUExecutor: Failed to initialize GPU context: " + e.getMessage());
			cleanupGLFW();
		}
	}

	/**
	 * Initializes GLFW and creates a hidden window with an OpenGL context. Must be called on the GPU thread.
	 */
	private boolean initializeGLFW()
	{
		try
		{
			// Check for headless environment
			if (isHeadlessEnvironment())
			{
				Logger.println("GPUExecutor: Headless environment detected, skipping GLFW initialization");
				return false;
			}

			// Set up error callback
			GLFWErrorCallback.createPrint(System.err).set();

			// Initialize GLFW
			if (!glfwInit())
			{
				Logger.println("GPUExecutor: Unable to initialize GLFW");
				return false;
			}
			glfwInitialized = true;

			// Configure GLFW for an invisible window
			glfwDefaultWindowHints();
			glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
			glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

			// Request OpenGL 3.3 core profile
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
			glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

			// For macOS compatibility
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("mac"))
			{
				glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
			}

			// Create a small hidden window
			glfwWindow = glfwCreateWindow(1, 1, "GPU Executor Context", NULL, NULL);
			if (glfwWindow == NULL)
			{
				Logger.println("GPUExecutor: Failed to create GLFW window");
				glfwTerminate();
				glfwInitialized = false;
				return false;
			}

			// Make the OpenGL context current on this thread
			glfwMakeContextCurrent(glfwWindow);

			// Initialize LWJGL's OpenGL bindings
			GL.createCapabilities();

			// Query the maximum texture size supported by this GPU
			maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
			Logger.println("GPUExecutor: GLFW/OpenGL context created successfully on GPU thread (max texture size: " + maxTextureSize + ")");
			return true;
		}
		catch (Exception | UnsatisfiedLinkError e)
		{
			Logger.println("GPUExecutor: GLFW initialization failed: " + e.getMessage());
			cleanupGLFW();
			return false;
		}
	}

	/**
	 * Checks if running in a headless environment.
	 */
	private boolean isHeadlessEnvironment()
	{
		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			return true;
		}

		String display = System.getenv("DISPLAY");
		String os = System.getProperty("os.name").toLowerCase();
		if ((os.contains("nux") || os.contains("nix")) && (display == null || display.isEmpty()))
		{
			return true;
		}

		return false;
	}

	/**
	 * Attempts to create a DirectContext for Skia. Must be called on the GPU thread with an active OpenGL context.
	 */
	private DirectContext tryCreateDirectContext()
	{
		try
		{
			DirectContext ctx = DirectContext.Companion.makeGL();

			if (ctx != null)
			{
				// Verify by creating a small test surface
				try
				{
					Surface testSurface = Surface.Companion.makeRenderTarget(ctx, false, new ImageInfo(16, 16, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
					if (testSurface != null)
					{
						testSurface.close();
						return ctx;
					}
					else
					{
						ctx.close();
						return null;
					}
				}
				catch (Exception e)
				{
					ctx.close();
					return null;
				}
			}

			return null;
		}
		catch (Exception | UnsatisfiedLinkError e)
		{
			return null;
		}
	}

	/**
	 * Safely cleans up GLFW resources. Must be called on the GPU thread.
	 */
	private void cleanupGLFW()
	{
		try
		{
			if (glfwWindow != NULL)
			{
				glfwDestroyWindow(glfwWindow);
				glfwWindow = NULL;
			}
			if (glfwInitialized)
			{
				glfwTerminate();
				glfwInitialized = false;
			}
		}
		catch (Exception e)
		{
			// Ignore cleanup errors
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

		cleanupGLFW();
		gpuAvailable = false;
	}
}
