package com.ezstock.dto;

import com.ezstock.backtest.strategy.ConditionType;

import java.math.BigDecimal;

/** 給前端畫表單用：一種條件的名稱、單位、範圍、預設值、能不能當買進條件 */
public record ConditionTypeInfo(ConditionType type, String label, String unit,
                                BigDecimal min, BigDecimal max, int defaultValue,
                                boolean entryAllowed) {

    public static ConditionTypeInfo from(ConditionType type) {
        return new ConditionTypeInfo(type, type.label(), type.valueKind().unit(),
                type.valueKind().min(), type.valueKind().max(), type.defaultValue(), type.entryAllowed());
    }
}
