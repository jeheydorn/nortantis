package cs673;

import java.awt.Color;
import java.awt.image.BufferedImage;

import javax.swing.JColorChooser;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import util.ImageHelper;

/**
 * For showing a preview of a background color when choosing the background color of a map.
 */
@SuppressWarnings("serial")
public class BGColorPreviewPanel extends ImagePanel implements ChangeListener
{
	private JColorChooser colorChooser;
	private BufferedImage originalBackground;
	private Color color;

	public BGColorPreviewPanel()
	{
	}
	
	@Override
	public void stateChanged(ChangeEvent arg0)
	{
		this.color = colorChooser.getColor();
		setColor(colorChooser.getColor());
	}
	
	public void setColor(Color color)
	{
		this.color = color;
		if (originalBackground != null)
		{
			image = ImageHelper.colorify2(originalBackground, color);
	    	repaint();
		}
	}
	
	public Color getColor()
	{
		return color;
	}
	
	public void setColorChooser(JColorChooser chooser)
	{
		this.colorChooser = chooser;
	}
	
	@Override
	public void setImage(BufferedImage image)
	{
		originalBackground = ImageHelper.convertToGrayscale(image);

		if (color == null)
			this.image = originalBackground;
		else
			this.image = ImageHelper.colorify2(originalBackground, color);
	}

}
