package nortantis.swing;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

@SuppressWarnings("serial")
public class UnscaledImageToggleButton extends JToggleButton
{

	public UnscaledImageToggleButton()
	{
		super();
	}

	BufferedImage toDraw;

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		if (toDraw != null)
		{
			Graphics2D g2 = ((Graphics2D) g);
			AffineTransform transformWithOsScaling = g2.getTransform();

			// Undo the scaling Java adds because of Windows Display settings scaling apps. It makes images look bad.
			double osScale = g2.getTransform().getScaleX();
			g2.scale(1.0 / osScale, 1.0 / osScale);

			Insets insets = getInsets();
			int topBorderWidth = insets.top;
			int leftBorderWidth = insets.left;

			g.drawImage(toDraw, (int) (leftBorderWidth * osScale), (int) (topBorderWidth * osScale), null);
			g2.setTransform(transformWithOsScaling);
		}
	}

	@Override
	public void setIcon(Icon icon)
	{
		if (icon == null)
		{
			setIcon(null);
			toDraw = null;
			return;
		}

		double osScale = SwingHelper.getOSScale();
		// Set the icon to a fake one that is the size we want.
		super.setIcon(new ImageIcon(new BufferedImage((int) (icon.getIconWidth() / osScale), (int) (icon.getIconHeight() / osScale),
				BufferedImage.TYPE_INT_ARGB)));
		toDraw = convertIconToBufferedImage(icon);
	}

	public static BufferedImage convertIconToBufferedImage(Icon icon)
	{
		int width = icon.getIconWidth();
		int height = icon.getIconHeight();
		BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bufferedImage.createGraphics();
		icon.paintIcon(null, g2d, 0, 0);
		g2d.dispose();
		return bufferedImage;
	}
}
