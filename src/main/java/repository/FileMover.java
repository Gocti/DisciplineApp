package repository;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
interface FileMover {

    void move(Path source, Path target) throws IOException;
}

