package nortantis.swing;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

@SuppressWarnings("serial")
/**
 * A JPanel panel for showing images, which undoes the scaling Java adds to the Graphics object in paintComponent based on the display
 * settings of the operating system. That scaling is by default done in low quality, although even with a rendering hint to do it in high
 * quality, it still looks bad. This class has the advantage of undoing that scaling so the images look good, but the disadvantage that that
 * image will not scale according to the user's display settings for the OS, and that any user interactions with this panel must multiply
 * the mouse position by osScale.
 */
public class UnscaledImagePanel extends JPanel
{
	public double osScale = 1.0;
	private BufferedImage image;
	protected AffineTransform transformWithOsScaling;

	public UnscaledImagePanel(BufferedImage image)
	{
		this.image = image;
	}

	public UnscaledImagePanel()
	{

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
			Graphics2D g2 = ((Graphics2D) g);
			transformWithOsScaling = g2.getTransform();

			// Undo the scaling Java adds because of Windows Display settings scaling apps. It makes images look bad.
			osScale = g2.getTransform().getScaleX();
			g2.scale(1.0 / osScale, 1.0 / osScale);

			g.drawImage(image, 0, 0, null);
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		if (image == null)
		{
			return super.getPreferredSize();
		}

		// Calculate size based on original image dimensions and current OS scaling factor. This is the "unscaled" size in terms of how
		// Swing layout managers should perceive it.
		return new Dimension((int) (image.getWidth() * (1.0 / osScale)), (int) (image.getHeight() * (1.0 / osScale)));
	}

	@Override
	public Dimension getMinimumSize()
	{
		// For an image panel that you want to display at a fixed "unscaled" size,
		// the minimum size can often be the same as the preferred size.
		return getPreferredSize();
	}


	@Override
	public Dimension getMaximumSize()
	{
		// Similarly, the maximum size can also be the same as the preferred size
		// if you don't want the component to grow beyond its intended display size.
		return getPreferredSize();
	}
}
