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
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <sys/stat.h>

// Function to get the size of a shared memory segment given its file descriptor
long get_shared_memory_size(int fd) {
    struct stat shm_stat;
    if (fstat(fd, &shm_stat) == -1) {
        perror("fstat");
        return -1;
    }
    return (long)shm_stat.st_size;
}

// Function to create a shared memory segment, modified to accept a long for size
int create_shared_memory(const char *name, long size) {
    int fd = shm_open(name, O_CREAT | O_RDWR, 0666);
    if (fd < 0) {
        perror("shm_open");
        return -1;
    }
    long already_size = get_shared_memory_size(fd);
    if (already_size > 0) {
        return fd;
    }

    if (ftruncate(fd, size) == -1) {
        perror("ftruncate");
        close(fd);
        return -1;
    }

    return fd;
}

// Function to unlink a shared memory segment
void unlink_shared_memory(const char *name) {
    if (shm_unlink(name) == -1) {
        perror("shm_unlink");
    }
}

int main() {
    const char *shm_name = "/myshm";
    size_t shm_size = 1024;

    // Create shared memory
    int shm_fd = create_shared_memory(shm_name, shm_size);
    if (shm_fd < 0) {
        exit(EXIT_FAILURE);
    }

    // Perform operations with shared memory here
    // ...

    // Close the shared memory file descriptor
    close(shm_fd);

    // Unlink shared memory
    unlink_shared_memory(shm_name);

    return 0;
}

