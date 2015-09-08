package nortantis;

/**
 * Stores the number of text strings that have been generated.
 * @author joseph
 *
 */
public class TextCounter
{
	// TODO remove
//	// Singleton
//	private TextCounter()
//	{
//	}
//	private static TextCounter instance;
//	public static TextCounter getInstance()
//	{
//		if (instance == null)
//			instance = new TextCounter();
//		return instance;
//	}
	
	private int count;
	public void increment()
	{
		count++;
	}
	public void clear()
	{
		count = 0;
	}
	public int getCount()
	{
		return count;
	}
}
