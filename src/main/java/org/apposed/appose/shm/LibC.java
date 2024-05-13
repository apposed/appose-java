package org.apposed.appose.shm;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public interface LibC extends Library {

	LibC INSTANCE = (LibC) Native.load((Platform.isMac() ? "System" : "c"), LibC.class);

	int shm_open(String name, int flags, int mode);
	int shm_unlink(String name);

	Pointer mmap(Pointer addr, int length, int prot, int flags, int fd, int offset);
	int munmap(Pointer addr, int length);

	int ftruncate(int fd, int length);
	int fstat(int fd, Stat buf);
	int close(int fd);

	@Structure.FieldOrder({"st_dev", "st_ino", "st_mode", "st_nlink", "st_uid", "st_gid", "st_rdev", "st_atime", "st_atimensec", "st_mtime", "st_mtimensec", "st_ctime", "st_ctimensec", "st_size", "st_blocks", "st_blksize", "st_flags", "st_gen", "__unused"})
	class Stat extends Structure {
		public long st_dev;         /* [XSI] ID of device containing file */
		public long st_ino;         /* [XSI] File serial number */
		public long st_mode;        /* [XSI] Mode of file (see below) */
		public long st_nlink;       /* [XSI] Number of hard links */
		public int st_uid;          /* [XSI] User ID of the file */
		public int st_gid;          /* [XSI] Group ID of the file */
		public long st_rdev;        /* [XSI] Device ID */
		public long st_atime;       /* [XSI] Time of last access */
		public long st_atimensec;   /* nsec of last access */
		public long st_mtime;       /* [XSI] Last data modification time */
		public long st_mtimensec;   /* last data modification nsec */
		public long st_ctime;       /* [XSI] Time of last status change */
		public long st_ctimensec;   /* nsec of last status change */
		public long st_size;        /* [XSI] file size, in bytes */
		public long st_blocks;      /* [XSI] blocks allocated for file */
		public long st_blksize;     /* [XSI] optimal blocksize for I/O */
		public int st_flags;        /* user defined flags for file */
		public int st_gen;          /* file generation number */
		public NativeLong[] __unused = new NativeLong[3];
	}
}