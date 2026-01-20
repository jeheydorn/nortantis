package nortantis.platform.skia;

import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.util.Logger;
import org.jetbrains.skia.*;
import org.jetbrains.skiko.*;

import javax.swing.*;
import java.awt.*;

/**
 * A Swing JPanel that uses Skiko's SkiaLayer for GPU-accelerated rendering.
 *
 * This class provides an alternative to the LWJGL-based GPU context by using
 * Skiko's built-in SkiaLayer component, which manages its own GPU context.
 *
 * Usage:
 * <pre>
 * SkiaLayerPanel panel = new SkiaLayerPanel();
 * panel.setImage(mySkiaImage);
 * frame.add(panel);
 * </pre>
 *
 * Note: This is currently not wired up to the main application.
 * It's provided for manual testing of SkiaLayer-based GPU rendering.
 */
public class SkiaLayerPanel extends JPanel
{
	private final SkiaLayer skiaLayer;
	private SkiaImage currentImage;
	private boolean fitToPanel = true;

	public SkiaLayerPanel()
	{
		setLayout(new BorderLayout());

		// Create SkiaLayer with default settings
		// The SkiaLayer manages its own GPU context internally
		skiaLayer = createSkiaLayer();

		add(skiaLayer, BorderLayout.CENTER);

		Logger.println("SkiaLayerPanel: Created with SkiaLayer for GPU rendering");
	}

	/**
	 * Creates a SkiaLayer with appropriate settings.
	 */
	private SkiaLayer createSkiaLayer()
	{
		// Use reflection-safe approach to handle different Skiko versions
		try
		{
			// Try the simpler 4-argument constructor first
			return new SkiaLayer(
				null,   // externalAccessibleFactory
				new SkiaLayerProperties(),  // Use default properties
				null,   // analytics
				PixelGeometry.UNKNOWN
			);
		}
		catch (Exception e)
		{
			Logger.println("SkiaLayerPanel: Using fallback SkiaLayer creation: " + e.getMessage());
			// If that fails, try with minimal arguments via reflection or other approach
			try
			{
				// Create with explicit property values matching available API
				SkiaLayerProperties props = new SkiaLayerProperties();
				return new SkiaLayer(null, props, null, PixelGeometry.UNKNOWN);
			}
			catch (Exception e2)
			{
				throw new RuntimeException("Failed to create SkiaLayer: " + e2.getMessage(), e2);
			}
		}
	}

	/**
	 * Initializes the render delegate. Must be called after the panel is added to a visible container.
	 */
	public void initializeRendering()
	{
		skiaLayer.setRenderDelegate(new SkiaRenderDelegate());
	}

	/**
	 * Sets the image to display in this panel.
	 *
	 * @param image The SkiaImage to display (must be a SkiaImage instance)
	 */
	public void setImage(Image image)
	{
		if (image instanceof SkiaImage)
		{
			this.currentImage = (SkiaImage) image;
			skiaLayer.repaint();
		}
		else if (image != null)
		{
			Logger.println("SkiaLayerPanel: Warning - image must be a SkiaImage instance");
		}
		else
		{
			this.currentImage = null;
			skiaLayer.repaint();
		}
	}

	/**
	 * Gets the currently displayed image.
	 */
	public SkiaImage getImage()
	{
		return currentImage;
	}

	/**
	 * Sets whether the image should be scaled to fit the panel.
	 *
	 * @param fit true to scale image to fit, false to display at original size
	 */
	public void setFitToPanel(boolean fit)
	{
		this.fitToPanel = fit;
		skiaLayer.repaint();
	}

	/**
	 * Returns whether the image is scaled to fit the panel.
	 */
	public boolean isFitToPanel()
	{
		return fitToPanel;
	}

	/**
	 * Forces a repaint of the SkiaLayer.
	 */
	public void refresh()
	{
		skiaLayer.repaint();
	}

	/**
	 * Returns the underlying SkiaLayer for advanced usage.
	 */
	public SkiaLayer getSkiaLayer()
	{
		return skiaLayer;
	}

	@Override
	public void removeNotify()
	{
		super.removeNotify();
		// Clean up when removed from component hierarchy
		skiaLayer.dispose();
	}

	/**
	 * Internal render delegate that handles the actual Skia drawing.
	 */
	private class SkiaRenderDelegate implements SkikoRenderDelegate
	{
		@Override
		public void onRender(org.jetbrains.skia.Canvas canvas, int width, int height, long nanoTime)
		{
			// Clear with a background color
			canvas.clear(0xFF2D2D2D); // Dark gray background

			if (currentImage == null)
			{
				return;
			}

			org.jetbrains.skia.Image skiaImg = currentImage.getSkiaImage();
			if (skiaImg == null)
			{
				return;
			}

			int imgWidth = currentImage.getWidth();
			int imgHeight = currentImage.getHeight();

			if (fitToPanel && (imgWidth > width || imgHeight > height))
			{
				// Scale to fit while maintaining aspect ratio
				float scaleX = (float) width / imgWidth;
				float scaleY = (float) height / imgHeight;
				float scale = Math.min(scaleX, scaleY);

				float scaledWidth = imgWidth * scale;
				float scaledHeight = imgHeight * scale;

				// Center the image
				float x = (width - scaledWidth) / 2;
				float y = (height - scaledHeight) / 2;

				canvas.drawImageRect(
					skiaImg,
					Rect.makeXYWH(0, 0, imgWidth, imgHeight),
					Rect.makeXYWH(x, y, scaledWidth, scaledHeight)
				);
			}
			else
			{
				// Draw at original size, centered
				float x = (width - imgWidth) / 2f;
				float y = (height - imgHeight) / 2f;
				canvas.drawImage(skiaImg, Math.max(0, x), Math.max(0, y));
			}
		}
	}

	/**
	 * Simple test method to verify SkiaLayer is working.
	 * Creates a window with a test pattern.
	 */
	public static void testSkiaLayer()
	{
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("SkiaLayer Test");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setSize(800, 600);

			SkiaLayerPanel panel = new SkiaLayerPanel();

			// Create a test image with a gradient
			SkiaImage testImage = new SkiaImage(400, 300, nortantis.platform.ImageType.ARGB);
			try (Painter painter = testImage.createPainter())
			{
				// Draw a gradient background
				for (int y = 0; y < 300; y++)
				{
					int r = (int) (255 * y / 300.0);
					int b = 255 - r;
					painter.setColor(nortantis.platform.Color.create(r, 100, b));
					painter.drawLine(0, y, 400, y);
				}

				// Draw some text
				painter.setColor(nortantis.platform.Color.white);
				painter.drawString("SkiaLayer GPU Test", 50, 150);
			}

			panel.setImage(testImage);

			frame.add(panel);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			// Initialize rendering after frame is visible
			panel.initializeRendering();

			Logger.println("SkiaLayerPanel: Test window opened");
		});
	}
}
