package nortantis;

import java.awt.Color;
import java.awt.image.BufferedImage;

import javax.swing.JColorChooser;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import nortantis.util.ImageHelper;

/**
 * For showing a preview of a background color when choosing the background color of a map.
 */
@SuppressWarnings("serial")
public class BGColorPreviewPanel extends ImagePanel implements ChangeListener
{
	private JColorChooser colorChooser;
	private BufferedImage originalBackground;
	private Color color;
	private ImageHelper.ColorifyAlgorithm colorifyAlgorithm;

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
			colorifyImage();
	    	repaint();
		}
	}
	
	public void setColorifyAlgorithm(ImageHelper.ColorifyAlgorithm colorfyAlgorithm)
	{
		this.colorifyAlgorithm = colorfyAlgorithm;
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
		originalBackground = image;

		if (color == null || colorifyAlgorithm == ImageHelper.ColorifyAlgorithm.none)
		{
			this.image = originalBackground;
		}
		else
		{
			colorifyImage();
		}
	}
	
	private void colorifyImage()
	{
		if (colorifyAlgorithm != ImageHelper.ColorifyAlgorithm.none)
		{
			image = ImageHelper.colorify(originalBackground, color, colorifyAlgorithm);
		}
	}

}
