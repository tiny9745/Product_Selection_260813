package com.example.Product_Selection_260813.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.ProductExportLog;

/** 匯出紀錄只新增、不修改；「未曾匯出」的判斷寫在 ProductRepository.search() 的 NOT EXISTS。 */
public interface ProductExportLogRepository extends JpaRepository<ProductExportLog, Long> {
}
