package nortantis;

public enum IconType
{
	// These names must match the folder names in assets\icons.
	mountains, hills, sand, trees, cities, decorations;

	public String getSingularName()
	{
		switch (this)
		{
		case mountains:
			return "mountain";
		case hills:
			return "hill";
		case sand:
			return "sand";
		case trees:
			return "tree";
		case cities:
			return "city";
		case decorations:
			return "decoration";
		default:
			assert false;
			return toString();
		}
	}
	
	public String getSingularNameForGUILowerCase()
	{
		if (this == sand)
		{
			return "dunes";
		}
		return getSingularName();
	}
	
	public String getNameForGUI()
	{
		if (this == sand)
		{
			return "Dunes";
		}
		//Capitalize first letter.
		return toString().substring(0, 1).toUpperCase() + toString().substring(1);
	}
}
