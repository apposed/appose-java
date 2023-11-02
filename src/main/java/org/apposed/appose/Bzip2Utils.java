/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 Appose developers.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.itadaki.bzip2.BZip2InputStream;

/**
 * Utility methods  unzip bzip2 files
 */
public final class Bzip2Utils {
	
	final private static int BUFFER_SIZE = 1024 * 20;

	private Bzip2Utils() {
		// Prevent instantiation of utility class.
	}
	
	/**
	 * DEcompress a bzip2 file into a new file.
	 * The method is needed because Micromamba is distributed as a .tr.bz2 file and
	 * many distributions do not have tools readily available to extract the required files 
	 * @param source
	 * 	.bzip2 file
	 * @param destination
	 * 	destination folder where the contents of the file are going to be decompressed
	 * @throws FileNotFoundException if the .bzip2 file is not found or does not exist
	 * @throws IOException if the source file already exists or there is any error with the decompression
	 */
	public static void decompress(File source, File destination) throws FileNotFoundException, IOException {
	    try (
	    		BZip2CompressorInputStream input = new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(source)));
	    		FileOutputStream output = new FileOutputStream(destination);
	    		) {
	        IOUtils.copy(input, output);
	    }
	}
	
	/** Untar an input file into an output file.

	 * The output file is created in the output folder, having the same name
	 * as the input file, minus the '.tar' extension. 
	 * 
	 * @param inputFile     the input .tar file
	 * @param outputDir     the output directory file. 
	 * @throws IOException 
	 * @throws FileNotFoundException
	 *  
	 * @throws ArchiveException 
	 */
	private static void unTar(final File inputFile, final File outputDir) throws FileNotFoundException, IOException, ArchiveException {

	    final InputStream is = new FileInputStream(inputFile); 
	    final TarArchiveInputStream debInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
	    TarArchiveEntry entry = null; 
	    while ((entry = (TarArchiveEntry)debInputStream.getNextEntry()) != null) {
	        final File outputFile = new File(outputDir, entry.getName());
	        if (entry.isDirectory()) {
	            if (!outputFile.exists()) {
	                if (!outputFile.mkdirs()) {
	                    throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
	                }
	            }
	        } else {
	            final OutputStream outputFileStream = new FileOutputStream(outputFile); 
	            IOUtils.copy(debInputStream, outputFileStream);
	            outputFileStream.close();
	        }
	    }
	    debInputStream.close(); 

	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ArchiveException {
		String tarPath = "C:\\Users\\angel\\OneDrive\\Documentos\\pasteur\\git\\micromamba-1.5.1-1.tar";
		String mambaPath = "C:\\Users\\angel\\OneDrive\\Documentos\\pasteur\\git\\mamba";
		decompress(new File("C:\\Users\\angel\\OneDrive\\Documentos\\pasteur\\git\\micromamba-1.5.1-1.tar.bz2"), 
			new File(tarPath));
		unTar(new File(tarPath), new File(mambaPath));
	}

    // Size of the block in a standard tar file.
    private static final int BLOCK_SIZE = 512;

    public static void tarDecompress(String tarFilePath, String outputDirPath) {

        File tarFile = new File(tarFilePath);
        File outputDir = new File(outputDirPath);

        // Make sure the output directory exists
        if (!outputDir.isDirectory())
        	outputDir.mkdirs();

        try (FileInputStream fis = new FileInputStream(tarFile);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            boolean endOfArchive = false;
            byte[] block = new byte[BLOCK_SIZE];
            while (!endOfArchive) {
                // Read a block from the tar archive.
                int bytesRead = bis.read(block);
                if (bytesRead < BLOCK_SIZE) {
                    throw new IOException("Incomplete block read.");
                }

                // Check for the end of the archive. An empty block signals end.
                endOfArchive = isEndOfArchive(block);
                if (endOfArchive) {
                    break;
                }

                // Read the header from the block.
                TarHeader header = new TarHeader(block);

                // If the file size is nonzero, create an output file.
                if (header.fileSize > 0) {
                    File outputFile = new File(outputDir, header.fileName);
                    if (header.fileType == TarHeader.FileType.DIRECTORY) {
                        outputFile.mkdirs();
                    } else {
                        try (FileOutputStream fos = new FileOutputStream(outputFile);
                             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                            long fileSizeRemaining = header.fileSize;
                            while (fileSizeRemaining > 0) {
                                int toRead = (int) Math.min(fileSizeRemaining, BLOCK_SIZE);
                                bytesRead = bis.read(block, 0, toRead);
                                if (bytesRead != toRead) {
                                    throw new IOException("Unexpected end of file");
                                }
                                bos.write(block, 0, bytesRead);
                                fileSizeRemaining -= bytesRead;
                            }
                        }
                    }
                }

                // Skip to the next file entry in the tar archive by advancing to the next block boundary.
                long fileEntrySize = (header.fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE;
                long bytesToSkip = fileEntrySize - header.fileSize;
                long skipped = bis.skip(bytesToSkip);
                if (skipped != bytesToSkip) {
                    throw new IOException("Failed to skip bytes for the next entry");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isEndOfArchive(byte[] block) {
        // An empty block signals the end of the archive in a tar file.
        for (int i = 0; i < BLOCK_SIZE; i++) {
            if (block[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static class TarHeader {
        String fileName;
        int fileMode;
        int ownerId;
        int groupId;
        long fileSize;
        long modificationTime;
        int checksum;
        FileType fileType;
        String linkName;
        String magic; // UStar indicator
        String version;
        String ownerUserName;
        String ownerGroupName;
        String devMajor;
        String devMinor;
        String prefix; // Used if the file name is longer than 100 characters

        enum FileType {
            FILE, DIRECTORY, SYMLINK, CHARACTER_DEVICE, BLOCK_DEVICE, FIFO, CONTIGUOUS_FILE, GLOBAL_EXTENDED_HEADER, EXTENDED_HEADER, OTHER
        }

        TarHeader(byte[] headerBlock) {
            fileName = extractString(headerBlock, 0, 100);
            fileMode = (int) extractOctal(headerBlock, 100, 8);
            ownerId = (int) extractOctal(headerBlock, 108, 8);
            groupId = (int) extractOctal(headerBlock, 116, 8);
            fileSize = extractOctal(headerBlock, 124, 12);
            modificationTime = extractOctal(headerBlock, 136, 12);
            checksum = (int) extractOctal(headerBlock, 148, 8);
            fileType = determineFileType(headerBlock[156]);
            linkName = extractString(headerBlock, 157, 100);
            magic = extractString(headerBlock, 257, 6);
            version = extractString(headerBlock, 263, 2);
            ownerUserName = extractString(headerBlock, 265, 32);
            ownerGroupName = extractString(headerBlock, 297, 32);
            devMajor = extractString(headerBlock, 329, 8);
            devMinor = extractString(headerBlock, 337, 8);
            prefix = extractString(headerBlock, 345, 155);
            // Note: The prefix is used in conjunction with the filename to allow for longer file names.
        }

        private long extractOctal(byte[] buffer, int offset, int length) {
            String octalString = extractString(buffer, offset, length);
            return Long.parseLong(octalString, 8);
        }

        private String extractString(byte[] buffer, int offset, int length) {
            StringBuilder stringBuilder = new StringBuilder(length);
            for (int i = offset; i < offset + length; i++) {
                if (buffer[i] == 0) break; // Stop at the first null character.
                stringBuilder.append((char) buffer[i]);
            }
            return stringBuilder.toString();
        }

        private FileType determineFileType(byte typeFlag) {
            switch (typeFlag) {
                case '0':
                case '\0':
                    return FileType.FILE;
                case '2':
                    return FileType.SYMLINK;
                case '3':
                    return FileType.CHARACTER_DEVICE;
                case '4':
                    return FileType.BLOCK_DEVICE;
                case '5':
                    return FileType.DIRECTORY;
                case '6':
                    return FileType.FIFO;
                case '7':
                    return FileType.CONTIGUOUS_FILE;
                case 'g':
                    return FileType.GLOBAL_EXTENDED_HEADER;
                case 'x':
                    return FileType.EXTENDED_HEADER;
                default:
                    return FileType.OTHER;
            }
        }
    }

}
