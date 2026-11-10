package com.girhub.deripas.ai.friday.hud;

import lombok.experimental.UtilityClass;

@UtilityClass
final class Ansi {

    static final String RESET = "[0m";
    static final String RED = "[31m";
    static final String BRIGHT_RED = "[91m";
    static final String DIM_RED = "[2;31m";
    static final String BOLD = "[1m";

    static String red(String text) {
        return RED + text + RESET;
    }

    static String brightRed(String text) {
        return BRIGHT_RED + text + RESET;
    }

    static String dim(String text) {
        return DIM_RED + text + RESET;
    }

    static String bold(String text) {
        return BOLD + text + RESET;
    }
}
