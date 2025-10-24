/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
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

package org.apposed.appose.util;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apposed.appose.Appose;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Utility methods supporting download and unpacking of remote archives.
 *
 * @author Carlos Garcia Lopez de Haro
 * @author Curtis Rueden
 */
public final class Downloads {
	
	private Downloads() {
		// Prevent instantiation of utility class.
	}

	/**
	 * Decompress a bzip2 file into a new file.
	 * The method is needed because Micromamba is distributed as a .tar.bz2 file and
	 * many distributions do not have tools readily available to extract the required files.
	 * @param source
	 * 	.bzip2 file
	 * @param destination
	 * 	destination folder where the contents of the file are going to be decompressed
	 * @throws FileNotFoundException if the .bzip2 file is not found or does not exist
	 * @throws IOException if the source file already exists or there is any error with the decompression
	 * @throws InterruptedException if the thread where the decompression is happening is interrupted
	 */
	public static void unBZip2(File source, File destination) throws FileNotFoundException, IOException, InterruptedException {
		try (
			BZip2CompressorInputStream input = new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(source)));
			FileOutputStream output = new FileOutputStream(destination);
		) {
			copy(input, output);
		}
	}

	/**
	 * Copies the content of an InputStream into an OutputStream.
	 *
	 * @param input
	 * 	the InputStream to copy
	 * @param output
	 * 	the target, may be null to simulate output to dev/null on Linux and NUL on Windows
	 * @return the number of bytes copied
	 * @throws IOException if an error occurs copying the streams
	 * @throws InterruptedException if the thread where this is happening is interrupted
	 */
	private static long copy(final InputStream input, final OutputStream output) throws IOException, InterruptedException {
		int bufferSize = 4096;
		final byte[] buffer = new byte[bufferSize];
		int n = 0;
		long count = 0;
		while (-1 != (n = input.read(buffer))) {
			if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Decompressing stopped.");
			if (output != null) {
				output.write(buffer, 0, n);
			}
			count += n;
		}
		return count;
	}

	public static void unpack(final File inputFile, final File outputDir) throws FileNotFoundException, IOException, InterruptedException {
		String filename = inputFile.getName().toLowerCase();
		if (filename.endsWith(".tar")) unTar(inputFile, outputDir);
		else if (filename.endsWith(".tar.bz2")) unBZip2(inputFile, outputDir);
		else if (filename.endsWith(".tar.gz")) unTarGz(inputFile, outputDir);
		else if (filename.endsWith(".zip")) unZip(inputFile, outputDir);
		else throw new IllegalArgumentException("Unsupported archive type for file: " + inputFile.getName());
	}

	/**
	 * Decompress a zip file into a directory.
	 * @param source .zip file
	 * @param destination
	 * 	destination folder where the contents of the file are going to be decompressed
	 * @throws FileNotFoundException if the .zip file is not found or does not exist
	 * @throws IOException if the source file already exists or there is any error with the decompression
	 * @throws InterruptedException if the thread where the decompression is happening is interrupted
	 */
	public static void unZip(File source, File destination) throws FileNotFoundException, IOException, InterruptedException {
		try (
			InputStream is = new FileInputStream(source);
			ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(new BufferedInputStream(is));
		) {
			ZipArchiveEntry entry = null;
			while ((entry = zipInputStream.getNextEntry()) != null) {
				final File outputFile = new File(destination, entry.getName());
				if (entry.isDirectory()) {
					if (!outputFile.exists()) {
						if (!outputFile.mkdirs()) {
							throw new IOException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
						}
					}
				} else {
					if (!outputFile.getParentFile().exists()) {
						if (!outputFile.getParentFile().mkdirs())
							throw new IOException("Failed to create directory " + outputFile.getParentFile().getAbsolutePath());
					}
					try (OutputStream outputFileStream = new FileOutputStream(outputFile)) {
						copy(zipInputStream, outputFileStream);
					}
					// Set executable permission if the entry had it
					if ((entry.getUnixMode() & 0100) != 0) {
						outputFile.setExecutable(true);
					}
				}
			}
		}
	}

	/**
	 * Untar an input file into an output file.
	 * The output file is created in the output folder, having the same name
	 * as the input file, minus the '.tar' extension.
	 *
	 * @param inputFile     the input .tar file
	 * @param outputDir     the output directory file.
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void unTar(final File inputFile, final File outputDir) throws FileNotFoundException, IOException, InterruptedException {

		try (
				InputStream is = new FileInputStream(inputFile);
				TarArchiveInputStream debInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
				) {
		    TarArchiveEntry entry = null;
		    while ((entry = debInputStream.getNextEntry()) != null) {
		        final File outputFile = new File(outputDir, entry.getName());
		        if (entry.isDirectory()) {
		            if (!outputFile.exists()) {
		                if (!outputFile.mkdirs()) {
		                    throw new IOException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
		                }
		            }
		        } else {
		        	if (!outputFile.getParentFile().exists()) {
		        	    if (!outputFile.getParentFile().mkdirs())
		        	        throw new IOException("Failed to create directory " + outputFile.getParentFile().getAbsolutePath());
		        	}
		            try (OutputStream outputFileStream = new FileOutputStream(outputFile)) {
		            	copy(debInputStream, outputFileStream);
		            }
		        }
		    }
		} catch (ArchiveException e) {
			throw new IOException(e);
		}

	}

	/**
	 * Decompress a gzip file and then untar it into a directory.
	 * This method handles .tar.gz files (gzipped tar archives).
	 *
	 * @param inputFile     the input .tar.gz file
	 * @param outputDir     the output directory file.
	 * @throws IOException if there is any error reading or writing
	 * @throws FileNotFoundException if the file is not found
	 * @throws InterruptedException if the thread is interrupted
	 */
	public static void unTarGz(final File inputFile, final File outputDir) throws FileNotFoundException, IOException, InterruptedException {
		try (
				InputStream is = new FileInputStream(inputFile);
				InputStream gzipIs = new GzipCompressorInputStream(new BufferedInputStream(is));
				TarArchiveInputStream tarInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", gzipIs);
				) {
		    TarArchiveEntry entry = null;
		    while ((entry = tarInputStream.getNextEntry()) != null) {
		        final File outputFile = new File(outputDir, entry.getName());
		        if (entry.isDirectory()) {
		            if (!outputFile.exists()) {
		                if (!outputFile.mkdirs()) {
		                    throw new IOException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
		                }
		            }
		        } else {
		        	if (!outputFile.getParentFile().exists()) {
		        	    if (!outputFile.getParentFile().mkdirs())
		        	        throw new IOException("Failed to create directory " + outputFile.getParentFile().getAbsolutePath());
		        	}
		            try (OutputStream outputFileStream = new FileOutputStream(outputFile)) {
		            	copy(tarInputStream, outputFileStream);
		            }
		        }
		    }
		} catch (ArchiveException e) {
			throw new IOException(e);
		}
	}
	
	/**
	 * This method should be used when we get the following response codes from
	 * a {@link HttpURLConnection}:
	 * - {@link HttpURLConnection#HTTP_MOVED_TEMP}
	 * - {@link HttpURLConnection#HTTP_MOVED_PERM}
	 * - {@link HttpURLConnection#HTTP_SEE_OTHER}
	 * 
	 * If that is not the response code or the connection does not work, the url
	 * returned will be the same as the provided.
	 * If the method is used corretly, it will return the URL to which the original URL
	 * has been redirected
	 * @param url
	 * 	original url. Connecting to that url must give a 301, 302 or 303 response code
	 * @return the redirected url
	 * @throws MalformedURLException if the url does not fulfil the requirements for an url to be correct
	 * @throws URISyntaxException if the url is incorrect or there is no internet connection
	 */
	public static URL redirectedURL(URL url) throws MalformedURLException, URISyntaxException {
		int statusCode;
		HttpURLConnection conn;
		try {
			conn = (HttpURLConnection) url.openConnection();
			statusCode = conn.getResponseCode();
		} catch (IOException ex) {
			return url;
		}
		if (statusCode < 300 || statusCode > 308)
			return url;
		String newURL = conn.getHeaderField("Location");
		try {
			return redirectedURL(new URL(newURL));
		} catch (MalformedURLException ex) {
		}
		try {
			if (newURL.startsWith("//"))
				return redirectedURL(new URL("http:" + newURL));
			else
				throw new MalformedURLException();
		} catch (MalformedURLException ex) {
		}
        URI uri = url.toURI();
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String mainDomain = scheme + "://" + host;
		return redirectedURL(new URL(mainDomain + newURL));
	}

	/**
	 * Get the size of the file stored in the given URL
	 * @param url
	 * 	url where the file is stored
	 * @return the size of the file
	 */
	public static long getFileSize(URL url) {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("User-Agent", userAgent());
			if (conn.getResponseCode() >= 300 && conn.getResponseCode() <= 308)
				return getFileSize(redirectedURL(url));
			if (conn.getResponseCode() != 200)
				throw new Exception("Unable to connect to: " + url.toString());
			long size = conn.getContentLengthLong();
			conn.disconnect();
			return size;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (Exception ex) {
			ex.printStackTrace();
			String msg = "Unable to connect to " + url.toString();
			System.out.println(msg);
			return 1;
		}
	}

	private static String userAgent() {
		final String javaVersion = System.getProperty("java.version");
		final String osName = System.getProperty("os.name");
		final String osVersion = System.getProperty("os.version");
		final String osArch = System.getProperty("os.arch");
		final String os = osName + "-" +
			(osVersion != null ? osVersion + "-" : "") + osArch;
		return "Appose/" + Appose.version() +
			" (Java " + javaVersion + "/" + os + ")";
	}
}
