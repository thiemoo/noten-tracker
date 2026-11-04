package de.notentracker.ui;

/** Eintrag für Filter-Auswahllisten; {@code value == null} steht für "Alle". */
record Choice<T>(String label, T value) {

    static <T> Choice<T> all(String label) {
        return new Choice<>(label, null);
    }

    boolean matches(T candidate) {
        return value == null || value.equals(candidate);
    }

    @Override
    public String toString() {
        return label;
    }
}
