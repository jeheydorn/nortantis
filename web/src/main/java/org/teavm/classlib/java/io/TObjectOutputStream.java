package org.teavm.classlib.java.io;

/**
 * Stands in for java.io.ObjectOutputStream when compiling to JavaScript, which has no Java serialization. Nortantis' dependencies refer to it, but map
 * generation never serializes objects.
 */
public class TObjectOutputStream
{
	public TObjectOutputStream(java.io.OutputStream out)
	{
		throw new UnsupportedOperationException();
	}

	public void defaultWriteObject()
	{
		throw new UnsupportedOperationException();
	}

	public void writeObject(Object o)
	{
		throw new UnsupportedOperationException();
	}

	public void writeByte(int v)
	{
		throw new UnsupportedOperationException();
	}

	public void close()
	{
		throw new UnsupportedOperationException();
	}

	public void flush()
	{
		throw new UnsupportedOperationException();
	}

	public void writeUTF(String s)
	{
		throw new UnsupportedOperationException();
	}

	public void writeShort(int v)
	{
		throw new UnsupportedOperationException();
	}

	public void writeLong(long v)
	{
		throw new UnsupportedOperationException();
	}

	public void writeInt(int v)
	{
		throw new UnsupportedOperationException();
	}

	public void writeFloat(float v)
	{
		throw new UnsupportedOperationException();
	}

	public void writeDouble(double v)
	{
		throw new UnsupportedOperationException();
	}
}
