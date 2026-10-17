package com.ovengers.slotkey.space.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceRegionTest {

    @ParameterizedTest
    @ValueSource(strings = {"판교", "강남", "하남"})
    @DisplayName("판교·강남·하남은 지원하는 지역이다")
    void supportsThreeRegions(String location) {
        assertThat(SpaceRegion.isSupported(location)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "서울", "판교 ", "서울시 강남구", "PANGYO"})
    @DisplayName("그 밖의 값(공백·부분 일치·주소 문자열 포함)은 지원하지 않는 지역이다")
    void rejectsOtherValues(String location) {
        assertThat(SpaceRegion.isSupported(location)).isFalse();
    }

    @Test
    @DisplayName("null은 지원하지 않는 지역이다")
    void rejectsNull() {
        assertThat(SpaceRegion.isSupported(null)).isFalse();
    }
}
