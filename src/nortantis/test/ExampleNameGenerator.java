package nortantis.test;

import java.util.Random;

import nortantis.MapSettings;
import nortantis.TextDrawer;
import nortantis.editor.NameType;
import nortantis.util.Range;

public class ExampleNameGenerator
{
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Expected exactly one argument");
			return;
		}
		MapSettings settings = new MapSettings(args[0]);
		settings.textRandomSeed = new Random().nextLong();
		TextDrawer textDrawer = new TextDrawer(settings, 1.0);

		final int numberToGenerate = 50;
		
		final String requiredPrefix = "";
		final String requiredSuffix = "";

		System.out.println("Person names: ");
		generateNamesForType(numberToGenerate, NameType.Person, requiredPrefix, requiredSuffix, textDrawer);
		System.out.println("");

		System.out.println("Place names: ");
		generateNamesForType(numberToGenerate, NameType.Place, requiredPrefix, requiredSuffix, textDrawer);
	}
	
	private static void generateNamesForType(int numberToGenerate, NameType type, String requiredPrefix, String requiredSuffix, TextDrawer textDrawer)
	{
		final int maxAttempts = 10000;

		for (@SuppressWarnings("unused")
		int i : new Range(numberToGenerate))
		{
			String name = "";
			int attemptCount = 0;
			while (true)
			{
				if (type == NameType.Person)
				{
					name = textDrawer.generatePersonName("%s", true, requiredPrefix);
				}
				else
				{
					name = textDrawer.generatePlaceName("%s", true, requiredPrefix);
				}
				if (requiredSuffix == null || requiredSuffix.equals("") || name.toLowerCase().endsWith(requiredSuffix.toLowerCase()))
				{
					if (!name.contains(" "))
					{
						break;
					}
				}
				
				attemptCount++;
				if (attemptCount >= maxAttempts)
				{
					System.out.println("Unable to generate enough names with the given contraints. Try adding more books or reducing the required suffix.");
					return;
				}
			}
			System.out.println(name);
		}
	}
}
