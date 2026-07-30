package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@SuppressWarnings("serial")
public class ImagePanel extends JPanel
{
	private BufferedImage image;

	public ImagePanel()
	{

	}

	public ImagePanel(BufferedImage image)
	{
		this.image = image;
	}

	public void setImage(BufferedImage image)
	{
		this.image = image;
		revalidate();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		// Fill the background rather than leaving it to the look and feel. Synth-based look and feels - which is what "System" resolves to
		// on Linux, where it is GTK - paint an opaque panel with the color from their own style and ignore setBackground, so a panel showing
		// no image would be invisible against the panel behind it. That is the state the land and ocean color previews are in before a map
		// is open. FlatLaf and the Windows look and feel honor setBackground, which is why this only showed up on Linux.
		if (isOpaque())
		{
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
		}

		g.drawImage(image, 0, 0, null);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return image == null ? super.getPreferredSize() : new Dimension(image.getWidth(), image.getHeight());
	}

}
