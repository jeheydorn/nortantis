package nortantis.platform;

/**
 * Combined read/write interface for pixel access. Extends both PixelReader for reading and PixelWriter for writing pixels.
 *
 * For Skia images, createPixelReaderWriter() reads existing pixels into an array so they can be both read and modified. Use
 * createPixelWriter() instead when you only need to write pixels without reading existing values.
 */
public interface PixelReaderWriter extends PixelReader, PixelWriter
{
}
