package org.apposed.appose.shm;

import com.sun.jna.Pointer;

public interface ShmImpl {
    static ShmImpl create(String name, boolean create, int size) {
        switch (ShmUtils.os) {
            case OSX:
                return new ShmMacOS(name, create, size);
            case LINUX:
                return new ShmLinux(name, create, size);
            case WINDOWS:
                return new ShmWindows(name, create, size);
            default:
                throw new UnsupportedOperationException("not implemented for " + ShmUtils.os);
        }
    }

    /**
     * Unique name that identifies the shared memory block.
     *
     * @return The name of the shared memory.
     */
    String name();

    /**
     * Size in bytes.
     *
     * @return The length in bytes of the shared memory.
     */
    int size();

    /**
     * JNA pointer to the shared memory segment.
     *
     * @return the pointer to the shared memory segment
     */
    Pointer pointer();

    /**
     * Closes access to the shared memory from this instance but does
     * not destroy the shared memory block.
     */
    void close() throws Exception;

    /**
     * Requests that the underlying shared memory block be destroyed.
     * In order to ensure proper cleanup of resources, unlink should be
     * called once (and only once) across all processes which have access
     * to the shared memory block.
     */
    default void unlink() throws Exception {
        throw new UnsupportedOperationException();
    }
}
