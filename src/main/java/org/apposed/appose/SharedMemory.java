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

import com.sun.jna.Pointer;
import org.apposed.appose.shm.ShmImpl;

/**
 * A shared memory block.
 * <p>
 * Each shared memory block is assigned a unique name. In this way, one process
 * can create a shared memory block with a particular name and a different
 * process can attach to that same shared memory block using that same name.
 */
public final class SharedMemory implements AutoCloseable {

    private final ShmImpl impl;

    /**
     * Creates a new shared memory block or attaches to an existing shared
     * memory block.
     *
     * @param name   the unique name for the requested shared memory, specified
     *               as a string. If {@code create==true} a new shared memory
     *               block, if {@code null} is supplied for the name, a novel
     *               name will be generated.
     * @param create whether a new shared memory block is created ({@code true})
     *               or an existing one is attached to ({@code false}).
     * @param size   size in bytes
     */
    public SharedMemory(String name, boolean create, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("'size' must be a positive integer");
        }
        if (create && size == 0) {
            throw new IllegalArgumentException("'size' must be a positive number different from zero");
        }
        if (!create && name == null) {
            throw new IllegalArgumentException("'name' can only be null if create=true");
        }
        impl = ShmImpl.create(name, create, size);
    }

    /**
     * Unique name that identifies the shared memory block.
     *
     * @return The name of the shared memory.
     */
    public String name() {
        return impl.name();
    }

    /**
     * Size in bytes.
     *
     * @return The length in bytes of the shared memory.
     */
    public long size() {
        return impl.size();
    }

    /**
     * JNA pointer to the shared memory segment.
     *
     * @return the pointer to the shared memory segment
     */
    public Pointer pointer() {
        return impl.pointer();
    }

    /**
     * Closes access to the shared memory from this instance but does
     * not destroy the shared memory block.
     */
    @Override
    public void close() throws Exception {
        try {
            impl.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "SharedMemory{impl=" + impl + '}';
    }

}

