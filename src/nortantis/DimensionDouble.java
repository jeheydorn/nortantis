package nortantis;

public class DimensionDouble implements Comparable<DimensionDouble>
{
    public double width, height;

    public DimensionDouble(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    public double getWidth() 
    {
    	return width;
    }
    public double getHeight()
    {
    	return height;
    }
    
    @Override
    public String toString() {
        return width + ", " + height;
    }

       
	@Override
	public int compareTo(DimensionDouble other)
	{
		int c1 = Double.compare(width, other.width);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;
		
		int c2 = Double.compare(height, other.height);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;
	
		return 0;
	}

}
