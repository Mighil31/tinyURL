package com.mighil.tinyurl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiKeysTest {

    @Test
    void parsesKeysAndTiersIgnoringWhitespaceAndCase() {
        assertThat(ApiKeys.parse(" a:free , b:PRO ,")).isEqualTo(Map.of("a", Tier.FREE, "b", Tier.PRO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " , ", "a", "a:gold", ":free", "a:free:x", "a:free,a:pro"})
    void rejectsBadSpecs(String spec) {
        assertThatThrownBy(() -> ApiKeys.parse(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownOrMissingKeyHasNoTier() {
        ApiKeys keys = new ApiKeys("a:free");
        assertThat(keys.tierOf("a")).contains(Tier.FREE);
        assertThat(keys.tierOf("b")).isEmpty();
        assertThat(keys.tierOf(null)).isEmpty();
    }

    @Test
    void fingerprintIsStableHexAndNotTheKey() {
        assertThat(ApiKeys.fingerprint("secret"))
                .hasSize(64)
                .matches("[0-9a-f]+")
                .isEqualTo(ApiKeys.fingerprint("secret"))
                .isNotEqualTo(ApiKeys.fingerprint("secret2"))
                .doesNotContain("secret");
    }
}
