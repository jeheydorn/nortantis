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
		return nortantis.swing.translation.Translation.get("IconType." + name() + ".singular");
	}

	public String getNameForGUI()
	{
		return nortantis.swing.translation.Translation.get("IconType." + name());
	}
}
