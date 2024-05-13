package org.apposed.appose.shm;

public class Main {

	public static void main(String[] args) {
		String name = "/psm_2cd4ccb1";
		int flags = 2562;
		int mode = 384;

		// Create shared memory
		int shmid = LibC.INSTANCE.shm_open(name, flags, mode);
		if (shmid == -1) {
			System.err.println("Error creating shared memory");
			return;
		}
		System.out.println("Shared memory created successfully");

		// Do something with the shared memory

		// Unlink shared memory
		int result = LibC.INSTANCE.shm_unlink(name);
		if (result == -1) {
			System.err.println("Error unlinking shared memory");
			return;
		}
		System.out.println("Shared memory unlinked successfully");
	}
}
