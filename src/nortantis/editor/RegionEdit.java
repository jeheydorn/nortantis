package nortantis.editor;

import java.io.Serializable;

import nortantis.platform.Color;

@SuppressWarnings("serial")
public class RegionEdit implements Serializable
{
	public Color color;
	public int regionId;

	public RegionEdit(int regionId, Color color)
	{
		this.color = color;
		this.regionId = regionId;
	}

	public RegionEdit deepCopy()
	{
		return new RegionEdit(regionId, color);
	}

	@Override
	public int hashCode()
	{
		return regionId;
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
		RegionEdit other = (RegionEdit) obj;
		if (color == null)
		{
			if (other.color != null)
				return false;
		}
		else if (!color.equals(other.color))
			return false;
		if (regionId != other.regionId)
			return false;
		return true;
	}

}
