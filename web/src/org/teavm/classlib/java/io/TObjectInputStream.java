package org.teavm.classlib.java.io;

/**
 * Stands in for java.io.ObjectInputStream when compiling to JavaScript, which has no Java serialization. Nortantis' dependencies refer to it, but map
 * generation never serializes objects.
 */
public class TObjectInputStream
{
	public TObjectInputStream(java.io.InputStream in)
	{
		throw new UnsupportedOperationException();
	}

	public void defaultReadObject()
	{
		throw new UnsupportedOperationException();
	}

	public Object readObject()
	{
		throw new UnsupportedOperationException();
	}

	public byte readByte()
	{
		throw new UnsupportedOperationException();
	}

	public void close()
	{
		throw new UnsupportedOperationException();
	}

	public String readUTF()
	{
		throw new UnsupportedOperationException();
	}

	public short readShort()
	{
		throw new UnsupportedOperationException();
	}

	public long readLong()
	{
		throw new UnsupportedOperationException();
	}

	public int readInt()
	{
		throw new UnsupportedOperationException();
	}

	public float readFloat()
	{
		throw new UnsupportedOperationException();
	}

	public double readDouble()
	{
		throw new UnsupportedOperationException();
	}
}
