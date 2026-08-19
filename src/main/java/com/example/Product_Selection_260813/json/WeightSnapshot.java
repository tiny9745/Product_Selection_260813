package com.example.Product_Selection_260813.json;

import java.util.List;

public class WeightSnapshot {

    private String modeCode;

    private String modeName;

    private Integer version;

    private List<WeightFactorSnapshot> factors;

    public WeightSnapshot() {
    }

    public String getModeCode() {
        return modeCode;
    }

    public void setModeCode(String modeCode) {
        this.modeCode = modeCode;
    }

    public String getModeName() {
        return modeName;
    }

    public void setModeName(String modeName) {
        this.modeName = modeName;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<WeightFactorSnapshot> getFactors() {
        return factors;
    }

    public void setFactors(List<WeightFactorSnapshot> factors) {
        this.factors = factors;
    }
}