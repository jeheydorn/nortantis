package nortantis.editor;

import java.io.Serializable;
import java.util.Objects;

@SuppressWarnings("serial")
public class EdgeEdit implements Serializable
{

	/** @deprecated River data is now stored in {@link MapEdits#rivers}. Kept only for loading legacy save files. */
	@Deprecated
	public int riverLevel;
	public final int index;
	// Note - if you add any new fields here, make sure to clear them in MapCreator.applyEdgeEdits when edgeChanges is null.

	public EdgeEdit(int index, int riverLevel)
	{
		this.riverLevel = riverLevel;
		this.index = index;
	}

	public EdgeEdit deepCopy()
	{
		return new EdgeEdit(index, riverLevel);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(index, riverLevel);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EdgeEdit other = (EdgeEdit) obj;
		return index == other.index && riverLevel == other.riverLevel;
	}
}
