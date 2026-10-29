/**
 * 一組條件的編輯器：每一列是「條件種類下拉選單 + 數值」，可以新增、刪除。
 * 數值有兩種：均線、高低點用常用天數選單（月線、季線…），停利停損、持有天數自己輸入數字。
 * 兩種選單的內容都來自後端 GET /api/backtests/conditions，後端新增條件或選項，這裡不用改。
 */
function ConditionEditor({ title, hint, rules, onChange, types, max }) {
  const typeByKey = Object.fromEntries(types.map((t) => [t.type, t]))

  function update(index, patch) {
    onChange(rules.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)))
  }

  function changeType(index, type) {
    // 換種類時，數值換成那一種的預設值（天數和百分比的範圍不一樣）
    update(index, { type, value: String(typeByKey[type]?.defaultValue ?? '') })
  }

  function add() {
    const used = new Set(rules.map((r) => r.type))
    const next = types.find((t) => !used.has(t.type)) ?? types[0]
    onChange([...rules, { type: next.type, value: String(next.defaultValue) }])
  }

  return (
    <fieldset className="condition-editor">
      <legend className="form-label">{title}</legend>
      <p className="muted form-hint">{hint}</p>
      {rules.map((rule, index) => {
        const info = typeByKey[rule.type]
        const hasOptions = info?.options?.length > 0
        const value = Number(rule.value)
        const invalid =
          !hasOptions && (!info || rule.value === '' || value < Number(info.min) || value > Number(info.max))
        return (
          <div key={index} className="condition-row">
            <select
              className="form-input condition-type"
              value={rule.type}
              onChange={(event) => changeType(index, event.target.value)}
              aria-label={`${title}第 ${index + 1} 個條件`}
            >
              {types.map((t) => (
                <option key={t.type} value={t.type}>
                  {t.label}
                </option>
              ))}
            </select>
            {hasOptions ? (
              <select
                className="form-input condition-period"
                value={rule.value}
                aria-label="天數"
                onChange={(event) => update(index, { value: event.target.value })}
              >
                {info.options.map((option) => (
                  <option key={option.value} value={String(option.value)}>
                    {option.label}
                  </option>
                ))}
              </select>
            ) : (
              <>
                <input
                  type="number"
                  className="form-input form-input-small"
                  value={rule.value}
                  min={info?.min}
                  max={info?.max}
                  step={info?.unit === '%' ? 0.1 : 1}
                  aria-invalid={invalid}
                  aria-label="數值"
                  onChange={(event) => update(index, { value: event.target.value })}
                />
                <span className="condition-unit">{info?.unit}</span>
              </>
            )}
            {rules.length > 1 && (
              <button
                type="button"
                className="icon-button"
                onClick={() => onChange(rules.filter((_, i) => i !== index))}
                aria-label={`刪除第 ${index + 1} 個條件`}
              >
                ✕
              </button>
            )}
            {invalid && info && (
              <span className="error form-hint condition-error">
                請輸入 {info.min}～{info.max} {info.unit}
              </span>
            )}
          </div>
        )
      })}
      {rules.length < max && (
        <button type="button" className="link-button" onClick={add}>
          + 新增條件
        </button>
      )}
    </fieldset>
  )
}

export default ConditionEditor
