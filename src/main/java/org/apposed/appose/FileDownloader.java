/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2024 Appose developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.apposed.appose;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public class FileDownloader {
	private static final long CHUNK_SIZE = 1024 * 1024 * 5;

	private final ReadableByteChannel rbc;
	private final FileOutputStream fos;

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
