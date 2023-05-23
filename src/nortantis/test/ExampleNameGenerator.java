package nortantis.test;

import java.util.Random;

import nortantis.MapSettings;
import nortantis.TextDrawer;
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
		
		final String requiredPrefix = "Cha";
		final String requiredSuffix = "";
		
		System.out.println("Person names: ");
		for (@SuppressWarnings("unused") int i : new Range(numberToGenerate))
		{
			String name = "";
			while (true)
			{
				name = textDrawer.generatePersonName("%s", true);
				if (requiredPrefix == null || requiredPrefix.equals("") || name.toLowerCase().startsWith(requiredPrefix.toLowerCase()))
				{
					if (requiredSuffix == null || requiredSuffix.equals("") || name.toLowerCase().endsWith(requiredSuffix.toLowerCase()))
					{
						if (!name.contains(" "))
						{
							break;
						}
					}
				}
			}
			System.out.println(name);
		}
		System.out.println("");

		System.out.println("Place names: ");
		for (@SuppressWarnings("unused") int i : new Range(numberToGenerate))
		{
			String name = "";
			while (true)
			{
				name = textDrawer.generatePersonName("%s", true);
				if (requiredPrefix == null || requiredPrefix.equals("") || name.toLowerCase().startsWith(requiredPrefix.toLowerCase()))
				{
					if (requiredSuffix == null || requiredSuffix.equals("") || name.toLowerCase().endsWith(requiredSuffix.toLowerCase()))
					{
						if (!name.contains(" "))
						{
							break;
						}
					}
				}
			}
			System.out.println(name);
		}

		return;
	}
}
