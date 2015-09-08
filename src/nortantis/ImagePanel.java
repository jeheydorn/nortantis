package nortantis;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

@SuppressWarnings("serial")
public class ImagePanel extends JPanel
{
	protected BufferedImage image;
	
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
	}
	
	@Override
	  protected void paintComponent(Graphics g) {

	    super.paintComponent(g);
	        g.drawImage(image, 0, 0, null);
	}
	
	@Override
	public Dimension getPreferredSize()
	{
		return image == null ? super.getPreferredSize() : new Dimension(image.getWidth(), image.getHeight());
	}

	// From http://stackoverflow.com/questions/25255287/mouse-coordinates-relative-to-imageicon-within-a-jscrollpane
	protected Point getImageLocation() {

        Point p = null;
        if (image != null) {
            int x = (getWidth() - image.getWidth()) / 2;
            int y = (getHeight() - image.getHeight()) / 2;
            p = new Point(x, y);
        }
        return p;
    }
	
	// From http://stackoverflow.com/questions/25255287/mouse-coordinates-relative-to-imageicon-within-a-jscrollpane
    public Point toImageContext(Point p) 
    {
        Point imgLocation = getImageLocation();
        Point relative = new Point(p);
        relative.x -= imgLocation.x;
        relative.y -= imgLocation.y;
        return relative;
    }

}

