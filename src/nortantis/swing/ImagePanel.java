package nortantis.swing;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

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

	public BufferedImage getImage()
	{
		return image;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		g.drawImage(image, 0, 0, null);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return image == null ? super.getPreferredSize() : new Dimension(image.getWidth(), image.getHeight());
	}

}
