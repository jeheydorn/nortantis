package nortantis;

public enum TextType
{
	Title,
	Region,
	Mountain_range,
	Other_mountains,
	City,
	River;
	
	public String toString() 
	{
        return name().replace("_"," ");
    }
}
