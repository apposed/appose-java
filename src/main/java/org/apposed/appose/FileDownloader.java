package org.apposed.appose;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public class FileDownloader {
	private ReadableByteChannel rbc;
	private FileOutputStream fos;
	private static final long CHUNK_SIZE = 1024 * 1024 * 5;
	
	public FileDownloader(ReadableByteChannel rbc, FileOutputStream fos) {
		this.rbc = rbc;
		this.fos = fos;
	}
	
	/**
	 * Download a file without the possibility of interrupting the download
	 * @throws IOException if there is any error downloading the file from the url
	 */
	public void call() throws IOException  {
		fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
	}
	
	/**
	 * Download a file with the possibility of interrupting the download if the parentThread is
	 * interrupted
	 * 
	 * @param parentThread
	 * 	thread from where the download was launched, it is the reference used to stop the download
	 * @throws IOException if there is any error downloading the file from the url
	 * @throws InterruptedException if the download is interrupted because the parentThread is interrupted
	 */
	public void call(Thread parentThread) throws IOException, InterruptedException {
        long position = 0;
        while (true) {
            long transferred = fos.getChannel().transferFrom(rbc, position, CHUNK_SIZE);
            if (transferred == 0) {
                break;
            }

            position += transferred;
            if (!parentThread.isAlive()) {
                // Close resources if needed and exit
                closeResources();
                throw new InterruptedException("File download was interrupted.");
            }
        }
    }

    private void closeResources() throws IOException {
        if (rbc != null) rbc.close();
        if (fos != null) fos.close();
    }
}
