package io.github.kgonia.typesafe;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.ResourceBundle;

/** A {@link System.Logger} that records every message. */
final class CapturingLogger implements System.Logger {

    record Line(Level level, String message) {
    }

    final List<Line> lines = Collections.synchronizedList(new ArrayList<>());

    @Override
    public String getName() {
        return "capturing";
    }

    @Override
    public boolean isLoggable(Level level) {
        return true;
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        lines.add(new Line(level, msg));
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        lines.add(new Line(level, format));
    }

    List<String> messages() {
        synchronized (lines) {
            return lines.stream().map(Line::message).toList();
        }
    }
}
