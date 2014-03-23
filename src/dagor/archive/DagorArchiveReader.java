package dagor.archive;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.zip.InflaterInputStream;

public class DagorArchiveReader {
  
  private File file;
  
  private boolean zlib;
  
  private String[] fileNames;
  private int[] fileLocations;
  private int[] fileLengths;
  
  private DataInputStream dis;
  
  private final static Logger LOG = Logger.getLogger(DagorArchiveReader.class.getName());
  
  public DagorArchiveReader(File file) throws IOException{
    this.file = file;
    LOG.info("Opening archive: " + file.getAbsolutePath());
    byte[] primaryHeader = getPrimaryHeader();
    zlib = isZLib(primaryHeader);
    LOG.info("Archive is " + (zlib? "" : "not") + " zlib deflated.");
    processSecondaryHeader();
    LOG.info("Archive contains " + fileNames.length + " files.");
  }
  
  /**
   * @return First 32 bytes of file
   * @throws IOException
   */
  private byte[] getPrimaryHeader() throws IOException{
    try(DataInputStream is = new DataInputStream(new FileInputStream(file))){
      byte[] primaryHeader = new byte[0x20];
      is.readFully(primaryHeader);
      return primaryHeader;
    }
  }
  
  /**
   * Guesses if the file contains zlib compressed data.
   * I don't know how reliable this method is.
   * @param header First 32 bytes of file.
   * @return If the file is deflated with zlib.
   */
  private static boolean isZLib(byte[] header){
    return (header[0x10] == (byte)0x78 && 
        (
            header[0x11] == (byte)0x01 ||
            header[0x11] == (byte)0x9C ||
            header[0x11] == (byte)0xDA ));
  }
  
  /**
   * Reads file names and indexes/locations of archived files.
   * @throws IOException
   */
  private void processSecondaryHeader() throws IOException{
    byte[] subHeader = new byte[0x30];
    refreshStream();
    dis.readFully(subHeader);
    ByteBuffer shBuf = ByteBuffer.wrap(subHeader);
    shBuf.order(ByteOrder.LITTLE_ENDIAN);
    
    int fileCount = shBuf.getInt(0x04);
    int fileNameLocationsPos = shBuf.getInt(0);
    int fileLocationsPos = shBuf.getInt(0x10);
    int archiveDescriptorLength = fileLocationsPos + (fileCount * 0x10);
    subHeader = Arrays.copyOf(subHeader, archiveDescriptorLength);
    
    dis.readFully(subHeader, 0x30, archiveDescriptorLength - 0x30);
    dis.close();
    int[] fileNameLocations = readFileNameLocations(subHeader, fileCount, fileNameLocationsPos);
    fileNames = readFileNames(subHeader, fileNameLocations);
    int[] locAndLen = readFileLocationsAndLength(subHeader, fileCount, fileLocationsPos);
    fileLocations = new int[fileCount];
    fileLengths = new int[fileCount];
    for(int i=0;i < locAndLen.length; i+=2){
      fileLocations[i/2] = locAndLen[i];
      fileLengths[i/2] = locAndLen[i+1];
    }
  }
  
  /**
   * Reinitializes the data stream from the beginning.
   * If file is deflated it inflates the stream.
   * @throws IOException
   */
  private void refreshStream() throws IOException{
    if(this.dis != null){
      dis.close();
    }
    InputStream is = new FileInputStream(file);
    
    //do not include primary header in stream
    is.skip(0x10);
    
    if(zlib){
      is = new InflaterInputStream(is);
    }
    this.dis = new DataInputStream(is);
  }
  
  /**
   * Reads the locations of the file name strings from the secondary header.
   * @param data Secondary header bytes
   * @param fileCount The number of files in the archive.
   * @param position The point at which to read the locations from.
   * @return Indexes of the start of the file name strings.
   */
  private int[] readFileNameLocations(byte[] data, int fileCount, int position){
    ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    int[] locations = new int[fileCount];
    buf.position(position);
    for(int i=0; i < fileCount; i++){
      locations[i] = buf.getInt();
      buf.position(buf.position() + 4);
    }
    return locations;
  }
  
  /**
   * Read null terminated file names from secondary header using provided coordinates.
   * @param data Secondary header bytes
   * @param locations Positions of the start of each file name.
   * @return Array of file names.
   */
  private static String[] readFileNames(byte[] data, int[] locations){
    String[] fileNames = new String[locations.length];
    for(int i=0; i < locations.length;i++){
      int location = locations[i];
      int y=0;
      while(data[location + y]!= 0x00){
        y++;
      }
      fileNames[i] = new String(Arrays.copyOfRange(data, location, location + y));
    }
    return fileNames;
  }
  
  /**
   * Read the location and lengths of archived files.
   * @param data Secondary header bytes.
   * @param fileCount Number of files in archive.
   * @param position The point at which to read the locations from.
   * @return An array of alternating location and length data. 
   */
  private int[] readFileLocationsAndLength(byte[] data, int fileCount, int position){
    ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    int[] locations = new int[fileCount *2];
    buf.position(position);
    for(int i=0; i < fileCount; i++){
      locations[i*2] = buf.getInt();
      locations[(i*2)+1] = buf.getInt();
      buf.position(buf.position() + 8);
    }
    return locations;
  }
  
  /**
   * Extract file by name to specified directory.
   * @param fileName Name of the file to extract.
   * @param destination The directory to extract the file to.
   * @throws IOException
   */
  public void extract(String fileName, File destination) throws IOException{
    for(int i=0; i < fileNames.length; i++){
      if(fileName.equals(fileNames[i])){
        refreshStream();
        dis.skip(fileLocations[i]);
        extract(fileName, fileLengths[i], destination);
        dis.close();
        return;
      }
    }
  }
  
  /**
   * Extracts all files in the archive to the specified directory.
   * @param destination Directory to extract to.
   * @throws IOException
   */
  public void extractAll(File destination) throws IOException{
    refreshStream();
    int endOfLast=0;
    for(int i=0; i < fileNames.length;i++){
      String fileName = fileNames[i];
      int location = fileLocations[i];
      int length = fileLengths[i];
      if(i==0){
        dis.skip(location);
      }else{
        if(location!=0){
          dis.skip(location - (endOfLast));
        }
      }
      extract(fileName, length, destination);
      if(location!=0){
        endOfLast = location + length;
      }
    }
  }
  
  private void extract(String fileName, int length, File destination) throws IOException{
    destination = new File(destination.getPath() + File.separator +  fileName);
    LOG.info("Extracting File: " + fileName + " to " + destination.getAbsolutePath() + 
        " with size of " + length + " bytes.");
    byte[] output = new byte[length];
    dis.readFully(output);
    if(!destination.exists()){
      destination.getParentFile().mkdirs();
      destination.createNewFile();
    }
    try(OutputStream ops = new FileOutputStream(destination)){
      ops.write(output);
    }
  }

  public String[] getFileNames() {
    return fileNames;
  }
  
  final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
  
  /**
   * Produced hexeditor like output for debug purposes.
   * @param bytes
   * @return Hex representation of bytes as string.
   */
  public static String bytesToHex(byte[] bytes) {
    StringBuilder hexChars = new StringBuilder();
    for ( int j = 0; j < bytes.length; j++ ) {
      int v = bytes[j] & 0xFF;
      if(j % 16 == 0)hexChars.append("\n");
      hexChars.append(hexArray[v >>> 4]);
      hexChars.append(hexArray[v & 0x0F]);
      hexChars.append(' ');
    }
    return new String(hexChars);
  }

}
