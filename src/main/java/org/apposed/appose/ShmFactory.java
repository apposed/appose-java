package org.apposed.appose;

public interface ShmFactory {
SharedMemory create(String name, boolean create, int size);
}
