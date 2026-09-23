package com.example.Product_Selection_260813.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.RegionWeight;

/** PK 直接是 region 字串（NORTH/CENTRAL/SOUTH/EAST），固定四筆，不開放新增/刪除，見 RegionWeight 類別註解。 */
public interface RegionWeightRepository extends JpaRepository<RegionWeight, String> {
}
