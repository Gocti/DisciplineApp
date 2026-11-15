package ui;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletionException;

public final class StorageErrorDialog {

    private StorageErrorDialog() {
    }

    public static void show(
            Component parent,
            String action,
            Throwable error
    ) {
        var cause = error;

        while (cause instanceof CompletionException
                && cause.getCause() != null) {
            cause = cause.getCause();
        }

        JOptionPane.showMessageDialog(
                parent,
                "Не удалось " + action + ".\n\n"
                        + cause.getMessage(),
                "Ошибка хранилища",
                JOptionPane.ERROR_MESSAGE
        );
    }
}
