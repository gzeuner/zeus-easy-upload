package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.util.MetadataNormalizationUtil;
import org.junit.jupiter.api.Test;

class MetadataNormalizationTest {

    @Test
    void shouldNormalizeLibraryAndTableToUppercase() {
        assertThat(MetadataNormalizationUtil.normalizeLibrary(" bib ")).isEqualTo("BIB");
        assertThat(MetadataNormalizationUtil.normalizeTable(" my_table ")).isEqualTo("MY_TABLE");
    }

    @Test
    void shouldReturnEmptyForNullOrBlankValues() {
        assertThat(MetadataNormalizationUtil.normalizeLibrary(null)).isEmpty();
        assertThat(MetadataNormalizationUtil.normalizeLibrary("   ")).isEmpty();
        assertThat(MetadataNormalizationUtil.normalizeTable(null)).isEmpty();
        assertThat(MetadataNormalizationUtil.normalizeTable("\t")).isEmpty();
    }
}
