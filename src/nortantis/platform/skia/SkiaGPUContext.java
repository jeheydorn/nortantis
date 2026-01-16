package nortantis.platform.skia;

import nortantis.util.Logger;
import org.jetbrains.skia.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Manages a singleton GPU DirectContext for Skia rendering.
 * Uses LWJGL to create an offscreen OpenGL context for GPU acceleration.
 *
 * Thread safety: GPU operations should be restricted to a single thread (typically EDT for Swing apps).
 */
public class SkiaGPUContext
{
	private static volatile DirectContext directContext;
	private static volatile GPUBackend backend;
	private static volatile boolean gpuAvailable = true;
	private static volatile boolean initialized = false;
	private static final Object lock = new Object();

	// LWJGL/GLFW resources
	private static long glfwWindow = NULL;
	private static boolean glfwInitialized = false;

	/**
	 * GPU backend types supported by Skia.
	 */
	public enum GPUBackend
	{
		OPENGL,
		METAL,
		DIRECT3D,
		VULKAN
	}

	/**
	 * Returns the shared DirectContext for GPU operations.
	 * Lazily initializes the context on first call.
	 *
	 * @return The DirectContext, or null if GPU is not available
	 */
	public static DirectContext getContext()
	{
		if (!gpuAvailable)
		{
			return null;
		}

		if (!initialized)
		{
			synchronized (lock)
			{
				if (!initialized)
				{
					initializeContext();
				}
			}
		}

		return directContext;
	}

	/**
	 * Checks if GPU acceleration is available.
	 */
	public static boolean isGPUAvailable()
	{
		if (!initialized)
		{
			// Trigger lazy initialization
			getContext();
		}
		return gpuAvailable && directContext != null;
	}

	/**
	 * Releases the GPU context and associated resources.
	 * Should be called during application shutdown.
	 */
	public static void releaseContext()
	{
		synchronized (lock)
		{
			if (directContext != null)
			{
				try
				{
					directContext.abandon();
					directContext.close();
				}
				catch (Exception e)
				{
					// Ignore cleanup errors
				}
				directContext = null;
			}

			// Clean up GLFW resources
			cleanupGLFW();

			initialized = false;
		}
	}

	/**
	 * Detects the best GPU backend for the current platform.
	 */
	public static GPUBackend detectBestBackend()
	{
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("mac"))
		{
			return GPUBackend.METAL;
		}
		if (os.contains("win"))
		{
			// OpenGL is more widely compatible on Windows
			return GPUBackend.OPENGL;
		}
		if (os.contains("nux") || os.contains("nix"))
		{
			return GPUBackend.OPENGL;
		}

		// Default fallback
		return GPUBackend.OPENGL;
	}

	/**
	 * Initializes the GPU context for the detected platform.
	 * Uses LWJGL/GLFW to create an offscreen OpenGL context.
	 * Sets gpuAvailable to false if initialization fails.
	 */
	private static void initializeContext()
	{
		try
		{
			// GPU is disabled by default due to compatibility issues between LWJGL and Skiko.
			// Set -Dnortantis.gpu.enable=true to try GPU acceleration.
			String enableGpu = "true";// System.getProperty("nortantis.gpu.enable", "false"); TODO put back or remove
			if (!Boolean.parseBoolean(enableGpu))
			{
				gpuAvailable = false;
				Logger.println("SkiaGPUContext: GPU disabled by default, using CPU rendering. Set -Dnortantis.gpu.enable=true to try GPU.");
				initialized = true;
				return;
			}

			backend = detectBestBackend();

			// Initialize GLFW and create an offscreen OpenGL context
			if (!initializeGLFW())
			{
				gpuAvailable = false;
				Logger.println("SkiaGPUContext: Failed to initialize GLFW, using CPU rendering");
				return;
			}

			// Now try to create the Skia DirectContext
			directContext = tryCreateDirectContext();

			if (directContext != null)
			{
				gpuAvailable = true;
				Logger.println("SkiaGPUContext: GPU acceleration enabled with backend: " + backend);
			}
			else
			{
				gpuAvailable = false;
				Logger.println("SkiaGPUContext: GPU acceleration not available, using CPU rendering");
			}
		}
		catch (Exception | UnsatisfiedLinkError e)
		{
			gpuAvailable = false;
			directContext = null;
			Logger.println("SkiaGPUContext: Failed to initialize GPU context: " + e.getMessage());
		}
		finally
		{
			initialized = true;
		}
	}

	/**
	 * Initializes GLFW and creates a hidden window with an OpenGL context.
	 *
	 * @return true if successful, false otherwise
	 */
	private static boolean initializeGLFW()
	{
		try
		{
			// Check for headless environment
			if (isHeadlessEnvironment())
			{
				Logger.println("SkiaGPUContext: Headless environment detected, skipping GLFW initialization");
				return false;
			}

			// Set up error callback (suppress output to avoid noise)
			GLFWErrorCallback.createPrint(System.err).set();

			// Initialize GLFW
			if (!glfwInit())
			{
				Logger.println("SkiaGPUContext: Unable to initialize GLFW");
				return false;
			}
			glfwInitialized = true;

			// Configure GLFW for an invisible window
			glfwDefaultWindowHints();
			glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Hidden window
			glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

			// Request OpenGL 3.3 core profile (good compatibility)
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
			glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

			// For macOS compatibility
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("mac"))
			{
				glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
			}

			// Create a small hidden window (required to get an OpenGL context)
			glfwWindow = glfwCreateWindow(1, 1, "Skia GPU Context", NULL, NULL);
			if (glfwWindow == NULL)
			{
				Logger.println("SkiaGPUContext: Failed to create GLFW window");
				glfwTerminate();
				glfwInitialized = false;
				return false;
			}

			// Make the OpenGL context current on this thread
			glfwMakeContextCurrent(glfwWindow);

			// Initialize LWJGL's OpenGL bindings
			GL.createCapabilities();

			Logger.println("SkiaGPUContext: GLFW/OpenGL context created successfully");
			return true;
		}
		catch (Exception | UnsatisfiedLinkError e)
		{
			Logger.println("SkiaGPUContext: GLFW initialization failed: " + e.getMessage());
			cleanupGLFW();
			return false;
		}
	}

	/**
	 * Checks if running in a headless environment where GLFW won't work.
	 */
	private static boolean isHeadlessEnvironment()
	{
		// Check Java headless mode
		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			return true;
		}

		// Check for common CI/headless environment variables
		String display = System.getenv("DISPLAY");
		String os = System.getProperty("os.name").toLowerCase();
		if ((os.contains("nux") || os.contains("nix")) && (display == null || display.isEmpty()))
		{
			return true;
		}

		return false;
	}

	/**
	 * Safely cleans up GLFW resources.
	 */
	private static void cleanupGLFW()
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
	 * Attempts to create a DirectContext for the current platform.
	 * Assumes an OpenGL context is already current on the thread.
	 *
	 * @return DirectContext if successful, null otherwise
	 */
	private static DirectContext tryCreateDirectContext()
	{
		try
		{
			// Create Skia DirectContext using the current OpenGL context
			DirectContext ctx = DirectContext.Companion.makeGL();

			// Verify the context works by trying to create a small test surface
			if (ctx != null)
			{
				try
				{
					Surface testSurface = Surface.Companion.makeRenderTarget(
						ctx,
						false,
						new ImageInfo(16, 16, ColorType.Companion.getN32(), ColorAlphaType.PREMUL)
					);
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
			// GPU not available on this platform/configuration
			return null;
		}
	}

	/**
	 * Creates a GPU-accelerated Surface if GPU is available.
	 *
	 * @param width Surface width
	 * @param height Surface height
	 * @return GPU Surface, or null if GPU is not available
	 */
	public static Surface createGPUSurface(int width, int height)
	{
		DirectContext ctx = getContext();
		if (ctx == null)
		{
			return null;
		}

		try
		{
			return Surface.Companion.makeRenderTarget(
				ctx,
				false, // budgeted
				new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL)
			);
		}
		catch (Exception e)
		{
			Logger.printError("SkiaGPUContext: Failed to create GPU surface: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Returns the current GPU backend type.
	 */
	public static GPUBackend getBackend()
	{
		if (!initialized)
		{
			getContext();
		}
		return backend;
	}

	/**
	 * Forces GPU to be disabled. Useful for testing CPU fallback.
	 */
	public static void disableGPU()
	{
		synchronized (lock)
		{
			releaseContext();
			gpuAvailable = false;
			initialized = true;
		}
	}

	/**
	 * Re-enables GPU and attempts to reinitialize the context.
	 */
	public static void enableGPU()
	{
		synchronized (lock)
		{
			gpuAvailable = true;
			initialized = false;
		}
	}

	/**
	 * Returns true if the GLFW window/context is valid.
	 */
	public static boolean hasValidGLContext()
	{
		return glfwWindow != NULL && glfwInitialized;
	}
}
