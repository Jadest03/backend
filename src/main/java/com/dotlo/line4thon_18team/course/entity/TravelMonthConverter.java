package com.dotlo.line4thon_18team.course.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/** 실제 여행 연월만 저장한다. 일자를 임의로 붙이지 않는다. */
@Converter
public class TravelMonthConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth month) {
        return month == null ? null : month.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String month) {
        return month == null ? null : YearMonth.parse(month);
    }
}
