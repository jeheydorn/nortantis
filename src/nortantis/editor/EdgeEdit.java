package nortantis.editor;

import java.io.Serializable;
import java.util.Objects;

@SuppressWarnings("serial")
public class EdgeEdit implements Serializable
{

	public int riverLevel;
	public final int index;

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
