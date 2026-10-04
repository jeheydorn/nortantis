package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * A JPanel panel for showing images, which undoes the scaling Java adds to the Graphics object in paintComponent based on the display
 * settings of the operating system. That scaling is by default done in low quality, although even with a rendering hint to do it in high
 * quality, it still looks bad. This class has the advantage of undoing that scaling so the images look good, but the disadvantage that that
 * image will not scale according to the user's display settings for the OS, and that any user interactions with this panel must multiply
 * the mouse position by osScale.
 */
@SuppressWarnings("serial")
public class UnscaledImagePanel extends JPanel
{
	public double osScale = 1.0;
	private BufferedImage image;
	protected AffineTransform transformWithOsScaling;

	public UnscaledImagePanel()
	{
		osScale = SwingHelper.getOSScale();
	}

	public UnscaledImagePanel(BufferedImage image)
	{
		this();
		this.image = image;
	}

	public void setImage(BufferedImage image)
	{
		this.image = image;
		revalidate();
		repaint();
	}

	public BufferedImage getImage()
	{
		return image;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (image != null)
		{
			Graphics2D g2 = (Graphics2D) g;
			transformWithOsScaling = g2.getTransform();
			g2.scale(1.0 / osScale, 1.0 / osScale);
			// The translation is left on the Graphics so that subclasses can draw overlays in image pixel coordinates.
			java.awt.Point offset = getImageOffsetInPixels();
			g2.translate(offset.x, offset.y);
			drawImage(g2, image);
		}
	}

	/**
	 * Draws the image at the origin of g2, which is in the image's pixel coordinates. Subclasses can override this to draw something else
	 * in the image's place.
	 */
	protected void drawImage(Graphics2D g2, BufferedImage image)
	{
		g2.drawImage(image, 0, 0, null);
	}

	/**
	 * Returns the offset, in image pixels, at which the image is drawn within this panel.
	 */
	protected java.awt.Point getImageOffsetInPixels()
	{
		return new java.awt.Point(0, 0);
	}

	@Override
	public Dimension getPreferredSize()
	{
		if (image == null)
		{
			return super.getPreferredSize();
		}
		return new Dimension(pixelsToPanelSize(image.getWidth(), osScale), pixelsToPanelSize(image.getHeight(), osScale));
	}

	/**
	 * Converts a size in image pixels to this panel's preferred size along that dimension.
	 */
	static int pixelsToPanelSize(int sizeInPixels, double osScale)
	{
		return (int) Math.round(sizeInPixels / osScale);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return getPreferredSize();
	}
}
