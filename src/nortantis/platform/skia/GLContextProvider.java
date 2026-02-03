package nortantis.platform.skia;

import org.jetbrains.skia.DirectContext;

/**
 * Abstraction for OpenGL context creation to decouple GPUExecutor from LWJGL/GLFW. On desktop, use LWJGLContextProvider. On Android,
 * provide an EGL-based implementation.
 */
public interface GLContextProvider
{
	/**
	 * Initializes the OpenGL context. Returns true if successful.
	 */
	boolean initialize();

	/**
	 * Returns the maximum texture dimension supported by the GPU.
	 */
	int getMaxTextureSize();

	/**
	 * Returns true if running in a headless environment where GPU context creation is not possible.
	 */
	boolean isHeadless();

	/**
	 * Creates a Skia DirectContext backed by the OpenGL context. Returns null on failure.
	 */
	DirectContext createDirectContext();

	/**
	 * Releases all OpenGL/windowing resources.
	 */
	void cleanup();
}
