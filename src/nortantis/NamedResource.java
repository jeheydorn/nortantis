package nortantis;

import java.io.Serializable;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONObject;

@SuppressWarnings("serial")
public class NamedResource implements Serializable
{
	public final String name;
	public final String artPack;

	public NamedResource(String artPack, String fileOrFolderName)
	{
		super();
		this.name = fileOrFolderName;
		assert name != null;
		this.artPack = artPack;
		assert artPack != null;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJSon()
	{
		JSONObject obj = new JSONObject();
		obj.put("name", name);
		obj.put("artPack", artPack);
		return obj;
	}

	public static NamedResource fromJson(JSONObject obj)
	{
		if (obj == null)
		{
			return null;
		}
		String fileName = (String) obj.get("name");
		String artPack = (String) obj.get("artPack");
		return new NamedResource(artPack, fileName);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(artPack, name);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		NamedResource other = (NamedResource) obj;
		return Objects.equals(artPack, other.artPack) && Objects.equals(name, other.name);
	}

	@Override
	public String toString()
	{
		return FilenameUtils.getBaseName(name) + " [" + artPack + "]";
	}

}
