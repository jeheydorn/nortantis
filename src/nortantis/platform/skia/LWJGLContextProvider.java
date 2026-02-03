package nortantis.platform.skia;

import nortantis.util.Logger;
import org.jetbrains.skia.*;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLContextProvider implementation using LWJGL/GLFW for desktop OpenGL context creation.
 */
public class LWJGLContextProvider implements GLContextProvider
{
	private long glfwWindow = NULL;
	private boolean glfwInitialized = false;
	private int maxTextureSize = 8192;

	@Override
	public boolean initialize()
	{
		try
		{
			if (isHeadless())
			{
				Logger.println("LWJGLContextProvider: Headless environment detected, skipping GLFW initialization");
				return false;
			}

			GLFWErrorCallback.createPrint(System.err).set();

			if (!glfwInit())
			{
				Logger.println("LWJGLContextProvider: Unable to initialize GLFW");
				return false;
			}
			glfwInitialized = true;

			glfwDefaultWindowHints();
			glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
			glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
			glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("mac"))
			{
				glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
			}

			glfwWindow = glfwCreateWindow(1, 1, "GPU Executor Context", NULL, NULL);
			if (glfwWindow == NULL)
			{
				Logger.println("LWJGLContextProvider: Failed to create GLFW window");
				glfwTerminate();
				glfwInitialized = false;
				return false;
			}

			glfwMakeContextCurrent(glfwWindow);
			GL.createCapabilities();

			maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
			Logger.println(
					"LWJGLContextProvider: GLFW/OpenGL context created successfully (max texture size: " + maxTextureSize + ")");
			return true;
		}
		catch (Exception | UnsatisfiedLinkError e)
		{
			Logger.println("LWJGLContextProvider: GLFW initialization failed: " + e.getMessage());
			cleanup();
			return false;
		}
	}

	@Override
	public int getMaxTextureSize()
	{
		return maxTextureSize;
	}

	@Override
	public boolean isHeadless()
	{
		String display = System.getenv("DISPLAY");
		String os = System.getProperty("os.name").toLowerCase();
		if ((os.contains("nux") || os.contains("nix")) && (display == null || display.isEmpty()))
		{
			return true;
		}

		try
		{
			return java.awt.GraphicsEnvironment.isHeadless();
		}
		catch (Throwable e)
		{
			// java.awt may not be available (e.g., on Android)
			return false;
		}
	}

	@Override
	public DirectContext createDirectContext()
	{
		try
		{
			DirectContext ctx = DirectContext.Companion.makeGL();

			if (ctx != null)
			{
				try
				{
					Surface testSurface = Surface.Companion.makeRenderTarget(ctx, false,
							new ImageInfo(16, 16, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
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

	@Override
	public void cleanup()
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
}
