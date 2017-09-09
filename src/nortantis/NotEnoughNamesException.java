package nortantis;

@SuppressWarnings("serial")
public class NotEnoughNamesException extends Exception 
{
	public NotEnoughNamesException()
	{
	}
	
	@Override
	public String getMessage()
	{
		return "The selected books do not have enough names to generate new names. Try selecting more books.";
	}
}
