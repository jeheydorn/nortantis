package nortantis.editor;

import nortantis.swing.translation.Translation;

public enum ExportAction
{
	SaveToFile, OpenInDefaultImageViewer;

	@Override
	public String toString()
	{
		return Translation.get("ExportAction." + name());
	}
}