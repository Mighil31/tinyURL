package com.mighil.tinyurl.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void generatesFixedLengthBase62Codes() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.next()).hasSize(CodeGenerator.LENGTH).matches("[0-9A-Za-z]+");
        }
    }

    @Test
    void codesAreNotRepeatedInASmallSample() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSize(10_000);
    }
}
