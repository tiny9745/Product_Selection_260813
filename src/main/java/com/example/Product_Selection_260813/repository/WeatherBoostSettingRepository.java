package com.example.Product_Selection_260813.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.WeatherBoostSetting;

/** 單列設定表（id=1，V26 建立時即寫入預設值）。 */
public interface WeatherBoostSettingRepository extends JpaRepository<WeatherBoostSetting, Integer> {
}
