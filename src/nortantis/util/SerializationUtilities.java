/**
 * This file is a modified version of SerializationUtilities.java from statnlp.jar used for CS 479.
 */
package nortantis.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SerializationUtilities {

	/**
	 * Uses compression
	 * 
	 * @param object
	 * @param fileName
	 * @throws IOException
	 */
	public static void serialize(Serializable object, String fileName) throws IOException {
		serialize(object, fileName, true);
	}
	
	/**
	 * Convenience method used to serialize and write-out objects to a file.
	 * 
	 * @param object 		object to serialize
	 * @param fileName 		the filename to serialize to
	 * @throws IOException 	if there is a problem during serialization
	 */
	public static void serialize(Serializable object, String fileName, boolean compress) throws IOException
	{
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileName));
		serialize(object, bos, compress);
	}

	/**
	 * Uses compression
	 * 
	 * @param object
	 * @param os
	 * @throws IOException
	 */
	public static void serialize(Serializable object, OutputStream os) throws IOException {
		serialize(object, os, true);
	}
	
	/**
	 * Convenience method used to serialize and write-out objects to an arbitrary output stream.
	 * Note: this method does NOT wrap the output stream with a BufferedOutputStream.
	 * 
	 * @param object 		object to serialize
	 * @param os 			the output stream to serialize to
	 * @param compress		whether or not to compress the output stream
	 * @throws IOException 	if there is a problem during serialization
	 */
	private static void serialize(Serializable object, OutputStream os, boolean compress) throws IOException {
		
		ObjectOutputStream oos;
		if (compress) {
			oos = new ObjectOutputStream(new GZIPOutputStream(os));
		} else {
			oos = new ObjectOutputStream(os);
		}
		oos.writeObject(object);
		oos.flush();
		oos.close();
	}


	/**
	 * Convenience method used to deserialize objects from a file
	 * 
	 * @param <T>
	 * @param filename
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T deserialize(String filename) throws FileNotFoundException, IOException, ClassNotFoundException {
		return (T)deserialize(new BufferedInputStream(new FileInputStream(filename)));
	}

	/**
	 * Convenience method used to deserialize objects from an arbitrary input stream.
	 * Note: this method does not wrap the stream with a BufferedInputStream. For 
	 * deserializing objects from files, consider using deserialize(String filenam).
	 * 
	 * @param <T> 	the type of the object to return
	 * @param is	the input stream to deserialize from
	 * @return		the deserialized object
	 * @throws IOException	if there is a problem during deserialization
	 * @throws ClassNotFoundException	if the class to be deserialized is missing
	 */
	@SuppressWarnings("unchecked")
	private static <T> T deserialize(InputStream is) throws IOException, ClassNotFoundException {
		
		ObjectInputStream ois;
		is.mark(100);
		try {
			ois = new ObjectInputStream(new GZIPInputStream(is));
		} catch (IOException o) {
			is.reset();
			ois = new ObjectInputStream(is);
		}
		T object = (T) ois.readObject();
		ois.close();
		return object;
	}
}
