package nortantis.swing;

import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.platform.ImageHelper;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * For showing a preview of a background color when choosing the background color of a map.
 */
@SuppressWarnings("serial")
public class BGColorPreviewPanel extends ImagePanel
{
	private BufferedImage originalBackground;
	private Color color;
	private ImageHelper.ColorizeAlgorithm colorizeAlgorithm;

	public BGColorPreviewPanel()
	{
	}

	public void setColor(Color color)
	{
		this.color = color;
		colorizeImageIfPresent(color);
	}

	private void colorizeImageIfPresent(Color color)
	{
		if (originalBackground != null)
		{
			colorizeImage(color);
			repaint();
		}
	}

	public void setColorizeAlgorithm(ImageHelper.ColorizeAlgorithm colorizeAlgorithm)
	{
		this.colorizeAlgorithm = colorizeAlgorithm;
	}

	public Color getColor()
	{
		return color;
	}

	@Override
	public void setImage(BufferedImage image)
	{
		originalBackground = image;

		if (color == null || colorizeAlgorithm == ImageHelper.ColorizeAlgorithm.none)
		{
			super.setImage(originalBackground);
		}
		else
		{
			colorizeImage(color);
		}
	}

	private void colorizeImage(Color color)
	{
		if (colorizeAlgorithm != ImageHelper.ColorizeAlgorithm.none)
		{
			Image grayscale = ImageHelper.getInstance().convertToGrayscale(AwtBridge.fromBufferedImage(originalBackground));
			super.setImage(AwtBridge.toBufferedImage(ImageHelper.getInstance().colorize(grayscale, AwtBridge.fromAwtColor(color), colorizeAlgorithm)));
		}
	}

}
