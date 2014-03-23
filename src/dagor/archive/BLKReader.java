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
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

/**
 * This class doesn't currently actually really do anything.
 * Still trying to figure out how the BLK file works.
 *
 */
public class BLKReader {
  
  private File file;
  
  private String[] fileNames;
  private int[] fileLocations;
  private int[] fileLengths;
  
  private DataInputStream dis;
  
  private final static Logger LOG = Logger.getLogger(BLKReader.class.getName());
  
  public BLKReader(File file) throws IOException{
    this.file = file;
    LOG.info("Opening archive: " + file.getAbsolutePath());
    //byte[] primaryHeader = getPrimaryHeader();
    processSecondaryHeader();
    LOG.info("Archive contains " + fileNames.length + " files.");
  }
  
  /**
   * Reads file names and indexes/locations of archived files.
   * @throws IOException
   */
  private void processSecondaryHeader() throws IOException{
    byte[] header = new byte[0x14];
    refreshStream();
    dis.readFully(header);
    ByteBuffer buf = ByteBuffer.wrap(header);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    
    //System.out.println(bytesToHex(header));
    
    int fileCount = buf.getInt(0x0C);
    int fileCountBytesLen = (0x40 + fileCount -1)*2;
    System.out.println(fileCount);
    header = new byte[0x14 + fileCountBytesLen];
    System.out.println(header.length);
    dis.readFully(header, 0x14, fileCountBytesLen);
    System.out.println(bytesToHex(header));
    buf = ByteBuffer.wrap(header);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    buf.position(0x14);
    
    short[] fileNameLengths = new short[fileCount];
    int totalFileNameLength = 0;
    int y = 2;
    for(int i=0; i < fileCount;i++){
      y++;
      if(y % 8 == 0){
        y++;
        System.out.println("Unused num:" + buf.getShort());
      }
      short length = buf.getShort();
      fileNameLengths[i] = length;
      totalFileNameLength += length;
      System.out.println("mod: " + y % 8 + ", idy: " + y + ", idi: " + i + ", Len: " + length);
    }
    System.out.println(totalFileNameLength);
    //System.exit(1);
    byte[] fileNameBytes = new byte[totalFileNameLength];
    dis.readFully(fileNameBytes);
    buf = ByteBuffer.wrap(fileNameBytes);
    String[] filenames = new String[fileCount];
    for(int i=0; i < fileNameLengths.length; i++){
      byte[] strBytes = new byte[fileNameLengths[i]];
      buf.get(strBytes);
      filenames[i] = new String(strBytes);
      System.out.println(filenames[i]);
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
    this.dis = new DataInputStream(is);
  }
  
  
  /**
   * Extracts all files in the archive to the specified directory.
   * @param destination Directory to extract to.
   * @throws IOException
   */
  public void extractAll(File destination) throws IOException{
    for(int i=0; i < fileNames.length;i++){
      extract(i, destination);
    }
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
        extract(i, destination);
        return;
      }
    }
  }
  
  private void extract(int index, File destination) throws IOException{
    String fileName = fileNames[index];
    int location = fileLocations[index];
    int length = fileLengths[index];
    
    destination = new File(destination.getPath() + File.separator +  fileName);
    LOG.info("Extracting File: " + fileName + " to " + destination.getAbsolutePath() + 
        " with size of " + length + " bytes.");
    refreshStream();
    dis.skip(location);
    
    //TODO: perhaps stream this rather than load file into memory.
    byte[] output = new byte[length];
    dis.readFully(output);
    dis.close();
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
  
  public static void main(String[] args) throws IOException, DataFormatException{
    //BLKReader vrfsr = new BLKReader(new File("C:\\Users\\M\\Documents\\dagortest\\gamedata\\flightmodels\\b-17g_china_rocaf.blk"));
    BLKReader vrfsr = new BLKReader(new File("C:\\Users\\M\\Documents\\dagortest\\gamedata\\flightmodels\\a5m4.blk"));
    for(String fname: vrfsr.getFileNames()){
      System.out.println(fname);
    }
  }
  
  
  final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
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
