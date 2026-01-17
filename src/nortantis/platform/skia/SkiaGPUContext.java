package nortantis.platform.skia;

import org.jetbrains.skia.DirectContext;
import org.jetbrains.skia.Surface;

/**
 * Manages GPU context for Skia rendering.
 *
 * This class now delegates to GPUExecutor for all GPU operations.
 * All GPU operations happen on a dedicated GPU thread managed by GPUExecutor.
 *
 * @deprecated Use {@link GPUExecutor} directly for GPU operations.
 */
@Deprecated
public class SkiaGPUContext
{
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
	 *
	 * @return The DirectContext, or null if GPU is not available
	 * @deprecated Use GPUExecutor for GPU operations. Direct context access is not thread-safe.
	 */
	@Deprecated
	public static DirectContext getContext()
	{
		// Cannot safely return context - it can only be accessed from GPU thread
		// Return null to indicate unavailability for legacy code
		return null;
	}

	/**
	 * Checks if GPU acceleration is available.
	 *
	 * @return true if GPU is available
	 */
	public static boolean isGPUAvailable()
	{
		return GPUExecutor.getInstance().isGPUAvailable();
	}

	/**
	 * Releases the GPU context and associated resources.
	 * Should be called during application shutdown.
	 *
	 * @deprecated Use {@link GPUExecutor#shutdown()} instead.
	 */
	@Deprecated
	public static void releaseContext()
	{
		GPUExecutor.getInstance().shutdown();
	}

	/**
	 * Detects the best GPU backend for the current platform.
	 *
	 * @return The detected GPU backend
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
			return GPUBackend.OPENGL;
		}
		if (os.contains("nux") || os.contains("nix"))
		{
			return GPUBackend.OPENGL;
		}

		return GPUBackend.OPENGL;
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
		return GPUExecutor.getInstance().createGPUSurface(width, height);
	}

	/**
	 * Returns the current GPU backend type.
	 *
	 * @return The GPU backend, or OPENGL as default
	 */
	public static GPUBackend getBackend()
	{
		return detectBestBackend();
	}

	/**
	 * Forces GPU to be disabled. Useful for testing CPU fallback.
	 *
	 * @deprecated GPU enable/disable should be controlled via system property.
	 */
	@Deprecated
	public static void disableGPU()
	{
		// Cannot disable GPU after executor is started
		// Use -Dnortantis.gpu.enable=false instead
	}

	/**
	 * Re-enables GPU and attempts to reinitialize the context.
	 *
	 * @deprecated GPU enable/disable should be controlled via system property.
	 */
	@Deprecated
	public static void enableGPU()
	{
		// Cannot enable GPU after executor is started
		// Use -Dnortantis.gpu.enable=true instead
	}

	/**
	 * Returns true if the GLFW window/context is valid.
	 *
	 * @return true if GPU context is valid
	 */
	public static boolean hasValidGLContext()
	{
		return GPUExecutor.getInstance().isGPUAvailable();
	}
}
