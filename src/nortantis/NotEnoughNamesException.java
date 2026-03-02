package nortantis;

import nortantis.swing.translation.Translation;

@SuppressWarnings("serial")
public class NotEnoughNamesException extends RuntimeException
{
	public NotEnoughNamesException()
	{
	}

	@Override
	public String getMessage()
	{
		return Translation.get("error.notEnoughNames");
	}
}
