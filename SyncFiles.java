import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class SyncFiles extends SimpleFileVisitor<Path> {
	
	/* Protocol request variables */	
	private final static int REQUESTSESSIONSTART = 0;
	private final static int REQUESTENDOFSYNC = 1;
	private final static int REQUESTDIRECTORYLOCK = 2;
	private final static int REQUESTLISTFILESONDIRECTORY = 3;
	private final static int REQUESTTIMEFILELASTMODIFICATION = 4;
	private final static int REQUESTFILEEXISTS = 5;
	private final static int REQUESTFILETRANSFER = 6;
	
	/* Protocol response variables */
	private final static int REQUESTOK = 100;
	private final static int FILEALREADYINUSE = 101;
	private final static int FILEDOESNTEXIST = 102;
	private final static int FILECOULDNOTBECREATED = 103;
	private final static int FILETRANSFERFAILED = 104;
	
	private DataOutputStream outToServer;
	private DataInputStream inFromServer;
	private String username;
	private Path startingDirectory;
	
	public SyncFiles(DataOutputStream outToServer, DataInputStream inFromServer, String username, Path startingDirectory) {
		this.outToServer = outToServer;
		this.inFromServer = inFromServer;
		this.username = username;
		this.startingDirectory = startingDirectory;
	}

    // Print information about
    // each type of file.
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
        if (attr.isSymbolicLink() || attr.isDirectory()) {
            return CONTINUE;
        } 
        
        else {        	
        	/* Start by asking if file exists */
        	outToServer.writeInt(REQUESTFILEEXISTS);
        	
        	byte[] message = startingDirectory.relativize(file).normalize().toString().getBytes();
        	sendToSocket(outToServer, message);
        	
        	int response = inFromServer.readInt();
        	
        	if (response == FILEDOESNTEXIST){
        		/* File does'nt exist on server, send it to server */
            	outToServer.writeInt(REQUESTFILETRANSFER);
            	
            	/* Send name of the file */
            	message = startingDirectory.relativize(file).normalize().toString().getBytes();
            	sendToSocket(outToServer, message);
            	
            	response = inFromServer.readInt();
            	
            	/* After receiving response from server, go no further if server can't create the file */
            	if (response == FILECOULDNOTBECREATED){
            		System.out.println("File <" + message.toString() + "> could not be transfered to server!");
            		return CONTINUE;
            	}
            	
            	/* If it can, send the file over the socket */
            	File myFile = new File(startingDirectory.relativize(file).normalize().toString());
                message = new byte[(int) myFile.length()];
                 
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(myFile));
                bis.read(message, 0, message.length);
                sendToSocket(outToServer, message);
                
                response = inFromServer.readInt();
            	
            	/* After receiving response from server, check if server could successfully create the file */
            	if (response == FILETRANSFERFAILED){
            		System.out.println("File <" + message.toString() + "> could not be transfered to server!");
            		return CONTINUE;
            	}
        	}

        	else{
        		/* File does exist on server, request it's last modified date */
            	outToServer.writeInt(REQUESTTIMEFILELASTMODIFICATION);
            	
            	/* Send name of the file */
            	message = startingDirectory.relativize(file).normalize().toString().getBytes();
            	sendToSocket(outToServer, message);
            	
            	long lastModificatedOnServer = inFromServer.readLong();            	
            	long lastModificatedOnClient = attr.lastModifiedTime().toMillis();
            	
            	if (lastModificatedOnClient > lastModificatedOnServer){
            		/* Bring file from server to client */
            		// TODO
            	}
            	
            	else if (lastModificatedOnClient < lastModificatedOnServer){
            		/* Send file from client to server */
            		// TODO
            	}
            	
            	/* if (lastModificatedOnClient == lastModificatedOnServer) : do nothing */
            }

        	System.out.println("File " + startingDirectory.relativize(file).normalize() + " successfully synchronised!");        	
        }
        
        return CONTINUE;
    }

    // Print each directory visited.
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
    	
    	//TODO
    	/*	synchronise files that user still does'nt have
    	
    	ask for list
    	compare lists that are'nt here
    	for each file do:
    		ask file type.
    		if directory -> create new directory, and create a new visitor onto that directory and call it
    		if not link -> create new file on client brought from server
    	*/
    	
    	String dirName = dir.toString();
    	System.out.println("Directory <" + dirName + "> successfully synchronised!");
        return CONTINUE;
    }
    
    // Lock each directory visited.
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
    	String dirName = dir.toString();
    	System.out.println("Synchronising directory <" + dirName + ">...");
    	
    	/* Ask server to lock directory of other's usage */
    	outToServer.writeInt(REQUESTDIRECTORYLOCK);
    	sendToSocket(outToServer, dirName.getBytes());
    	
    	int response = inFromServer.readInt();
    	
    	/* If server sends success response message then traverse this directory */
    	if (response == REQUESTOK)
    		return CONTINUE;
    	
    	/* Else the server denies access to this directory at the moment: do not traverse this directory */
    	
    	System.out.println("Directory synchronisation failed: Directory <" + dirName + "> already being synchronised by this user somewhere else!");
   		return SKIP_SUBTREE;
    }
    
	/* Reads info from socket */
	private byte[] readFromSocket(DataInputStream inFromServer) throws IOException {
		int length = inFromServer.readInt();
		byte[] message = new byte[length];
		inFromServer.readFully(message, 0, length);
		return message;
	}
	
	/* Sends info through socket */
	private void sendToSocket(DataOutputStream outToServer, byte[] message) throws IOException {
		outToServer.writeInt(message.length);
		outToServer.write(message);
	}
}