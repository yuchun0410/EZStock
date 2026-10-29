package com.ezstock.dto;

import com.ezstock.backtest.strategy.ConditionType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 給前端畫表單用：一種條件的名稱、單位、範圍、預設值、能不能當買進條件。
 *
 * @param options 有值的話，前端顯示成下拉選單（例如月線、季線）；空的就顯示數字輸入框
 */
public record ConditionTypeInfo(ConditionType type, String label, String unit,
                                BigDecimal min, BigDecimal max, int defaultValue,
                                boolean entryAllowed, List<ConditionType.Option> options) {

    public static ConditionTypeInfo from(ConditionType type) {
        return new ConditionTypeInfo(type, type.label(), type.valueKind().unit(),
                type.valueKind().min(), type.valueKind().max(), type.defaultValue(), type.entryAllowed(),
                type.options());
    }
}
