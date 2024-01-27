package nortantis.platform;

public abstract class Transform
{
	public abstract Transform copy();
	
	public abstract void rotate(double angle, double x, double y);
	
	public static Transform createEmpty()
	{
		return PlatformFactory.getInstance().createEmptyTransform();
	}
}
