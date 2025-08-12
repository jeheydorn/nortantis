package nortantis;

public enum BorderPosition
{
	Outside_map, Over_map;
	
	public String toString()
	{
		return name().replace("_", " ");
	}
}
