package nortantis;

public class StringHelper
{
	public static String trimTrailingSpacesAndUnderscores(String input)
	{
		if (input == null)
		{
			return null;
		}
		int end = input.length();
		while (end > 0 && (input.charAt(end - 1) == ' ' || input.charAt(end - 1) == '_'))
		{
			end--;
		}
		return input.substring(0, end);
	}
}
